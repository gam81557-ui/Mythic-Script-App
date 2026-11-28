package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

class ShizukuServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

enum class ShizukuStatusAction {
    RequestPermission,
    Refresh,
    ShowGuide,
}

sealed class ShizukuConnectionState {
    data object NotInstalled : ShizukuConnectionState()
    data object NotRunning : ShizukuConnectionState()
    data class PermissionDenied(val canAskAgain: Boolean) : ShizukuConnectionState()
    data object PermissionGranted : ShizukuConnectionState()
    data object Binding : ShizukuConnectionState()
    data class ServiceConnected(val uid: Int, val processLabel: String) : ShizukuConnectionState()
    data class ServiceDisconnected(val reason: String? = null) : ShizukuConnectionState()

    val isReadyForIo: Boolean
        get() = this is ServiceConnected

    fun statusAction(): ShizukuStatusAction = when (this) {
        is PermissionDenied -> ShizukuStatusAction.RequestPermission
        PermissionGranted,
        Binding,
        is ServiceConnected,
        -> ShizukuStatusAction.Refresh
        NotRunning,
        NotInstalled,
        -> ShizukuStatusAction.ShowGuide
        is ServiceDisconnected -> ShizukuStatusAction.Refresh
    }

    fun shortLabel(): String = when (this) {
        NotInstalled -> "Not Installed"
        NotRunning -> "Not Running"
        is PermissionDenied -> "Permission Denied"
        PermissionGranted -> "Permission Granted"
        Binding -> "Binding…"
        is ServiceConnected -> "Connected ($processLabel)"
        is ServiceDisconnected -> "Service Disconnected"
    }
}

/**
 * Shizuku bridge with the same listener, permission and UserService lifecycle as DZM-App.
 *
 * The core rules are deliberately identical to DZM's ShizukuHelper:
 * - status means "Shizuku binder alive + permission granted", not "service permanently bound";
 * - one sticky binder listener set is registered while the screen is visible;
 * - permission requests use one CompletableDeferred and one request code;
 * - a UserService is bound only on demand, once per attempt, with a configurable 30 second timeout;
 * - every bind attempt owns its ServiceConnection and late callbacks are ignored;
 * - a SecurityException triggers one permission re-request and one bind retry.
 *
 * The extra ServiceConnected/Binding states are a thin adapter for Mythic's existing UI.
 */
class ShizukuBridgeManager(
    private val appContext: Context,
    private val serviceBindTimeoutMs: Long = DEFAULT_SERVICE_BIND_TIMEOUT_MS,
    private val permissionTimeoutMs: Long = DEFAULT_PERMISSION_TIMEOUT_MS,
) {

    private val _connectionState = MutableStateFlow<ShizukuConnectionState>(
        ShizukuConnectionState.NotRunning
    )
    val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

    private val serviceMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var permissionRejectedByServer = false
    private var permissionRequest: CompletableDeferred<Boolean>? = null

    @Volatile
    private var boundService: IFileBridgeService? = null

    private var listenersRegistered = false
    private var activeConnection: ServiceConnection? = null

    @Volatile
    private var fallbackActive = false

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, FileBridgeUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(FILE_SERVICE_VERSION)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        permissionRejectedByServer = false
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died")
        boundService = null
        activeConnection = null
        fallbackActive = false
        finishPermissionRequest(false)
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_PERMISSION) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                if (granted) permissionRejectedByServer = false
                refreshStatus()
                finishPermissionRequest(granted)
            }
        }

    fun registerListeners() {
        if (listenersRegistered) return
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        listenersRegistered = true
        refreshStatus()
    }

    fun unregisterListeners() {
        if (!listenersRegistered) return
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        listenersRegistered = false
        finishPermissionRequest(false)
        unbindUserServiceQuietly()
    }

    fun startListening() = registerListeners()

    fun stopListening() = unregisterListeners()

    fun refreshStatus() {
        _connectionState.value = computeStatus()
    }

    fun isShizukuBinderAlive(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun requestPermission(): Boolean {
        registerListeners()
        refreshStatus()
        return when (_connectionState.value) {
            is ShizukuConnectionState.ServiceConnected,
            ShizukuConnectionState.PermissionGranted,
            ShizukuConnectionState.Binding,
            is ShizukuConnectionState.ServiceDisconnected,
            -> true
            ShizukuConnectionState.NotInstalled,
            ShizukuConnectionState.NotRunning,
            -> false
            is ShizukuConnectionState.PermissionDenied -> startPermissionRequest() != null
        }
    }

    fun bindUserService() {
        scope.launch {
            runCatching { acquireUserService() }.onFailure { error ->
                _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                    error.message ?: "Failed to bind Shizuku service"
                )
            }
        }
    }

    fun unbindUserService() {
        unbindUserServiceQuietly()
        refreshStatus()
    }

    suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            withTimeout(timeoutMs) {
                awaitPermission()
                true
            }
        } catch (_: TimeoutCancellationException) {
            finishPermissionRequest(false)
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Binds/reuses the UserService and surfaces a typed failure with a user-actionable message.
     * Call this before starting a multi-file injection so a dead binder is detected early.
     */
    suspend fun prepareServiceForIo(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = acquireUserService()
            if (!isServiceAlive(service)) {
                throw ShizukuServiceUnavailableException(
                    "Shizuku file service stopped before the inject started. Reconnect and retry."
                )
            }
            Log.d(TAG, "Shizuku file service ready (fallback=$fallbackActive)")
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            Log.w(TAG, "Shizuku file service preflight failed", t)
            Result.failure(t)
        }
    }

    suspend fun <T> withService(block: suspend (IFileBridgeService) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val service = acquireUserService()
                if (!isServiceAlive(service)) {
                    throw ShizukuServiceUnavailableException(
                        "Shizuku file service disconnected before the file operation."
                    )
                }
                try {
                    Result.success(block(service))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Exception) {
                    Result.failure(t)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                Result.failure(t)
            }
        }

    fun isShizukuInstalled(): Boolean = SHIZUKU_PACKAGE_NAMES.any { packageName ->
        runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    fun openShizukuApp(): Boolean {
        for (packageName in SHIZUKU_PACKAGE_NAMES) {
            val launchIntent = appContext.packageManager
                .getLaunchIntentForPackage(packageName)
                ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                appContext.startActivity(launchIntent)
                true
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }

    private suspend fun acquireUserService(): IFileBridgeService =
        withContext(Dispatchers.Main.immediate) {
            awaitPermission()
            try {
                serviceMutex.withLock { bindUserServiceInternal() }
            } catch (security: SecurityException) {
                // The SDK can cache a grant that the server has since revoked.
                permissionRejectedByServer = true
                awaitPermission()
                try {
                    serviceMutex.withLock { bindUserServiceInternal() }
                } catch (_: SecurityException) {
                    permissionRejectedByServer = true
                    refreshStatus()
                    throw permissionRequired()
                } catch (unavailable: ShizukuServiceUnavailableException) {
                    fallbackOrThrow(unavailable)
                }
            } catch (unavailable: ShizukuServiceUnavailableException) {
                fallbackOrThrow(unavailable)
            }
        }

    private suspend fun fallbackOrThrow(error: ShizukuServiceUnavailableException): IFileBridgeService {
        val fallback = tryShellFallback(error.message ?: "Shizuku UserService is unavailable")
        if (fallback != null) return fallback
        throw ShizukuServiceUnavailableException(
            "${error.message ?: "Shizuku UserService is unavailable."} " +
                "The shell fallback could not access /Android/data either. " +
                "Verify Shizuku is running and permission is granted, then retry.",
            error,
        )
    }

    private suspend fun tryShellFallback(reason: String): IFileBridgeService? =
        withContext(Dispatchers.IO) {
            if (!hasPermissionReady()) return@withContext null

            val existing = boundService
            if (existing is ShellFileBridgeService && existing.asBinder().isBinderAlive) {
                fallbackActive = true
                publishConnected()
                return@withContext existing
            }

            runCatching {
                val bridge = ShellFileBridgeService()
                if (!bridge.isDirectory(FALLBACK_PROBE_PATH)) {
                    error("Shell bridge cannot access $FALLBACK_PROBE_PATH")
                }
                activeConnection = null
                boundService = bridge
                fallbackActive = true
                publishConnected()
                Log.w(TAG, "UserService unavailable ($reason); using shell file bridge")
                bridge
            }.getOrElse { fallbackError ->
                fallbackActive = false
                Log.w(TAG, "Shell file bridge fallback failed", fallbackError)
                null
            }
        }

    private suspend fun bindUserServiceInternal(): IFileBridgeService {
        requirePermission()
        val existing = boundService
        if (existing != null && isServiceAlive(existing)) {
            Log.d(TAG, "Reusing Shizuku file service (fallback=$fallbackActive)")
            publishConnected()
            return existing
        }

        unbindUserServiceQuietly()
        _connectionState.value = ShizukuConnectionState.Binding
        return try {
            Log.d(TAG, "Binding Shizuku UserService (timeout=${serviceBindTimeoutMs}ms)")
            awaitServiceConnection()
        } catch (timeout: TimeoutCancellationException) {
            unbindUserServiceQuietly()
            val message = "Shizuku UserService did not connect within " +
                "${serviceBindTimeoutMs / 1_000}s. Keep Shizuku running, then retry."
            Log.e(TAG, message, timeout)
            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(message)
            throw ShizukuServiceUnavailableException(message, timeout)
        } catch (t: ShizukuServiceUnavailableException) {
            unbindUserServiceQuietly()
            Log.w(TAG, "Shizuku UserService unavailable: ${t.message}", t)
            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(t.message)
            throw t
        } catch (t: Exception) {
            unbindUserServiceQuietly()
            val message = t.message?.takeIf { it.isNotBlank() }
                ?: "Shizuku UserService could not be bound. Keep Shizuku running, then retry."
            Log.e(TAG, message, t)
            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(message)
            throw ShizukuServiceUnavailableException(message, t)
        }
    }

    private suspend fun awaitServiceConnection(): IFileBridgeService =
        withTimeout(serviceBindTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (!continuation.isActive || activeConnection !== this) return
                        val fileService = IFileBridgeService.Stub.asInterface(service)
                        if (fileService != null) {
                            fallbackActive = false
                            boundService = fileService
                            Log.d(TAG, "Shizuku UserService connected")
                            continuation.resume(fileService)
                        } else {
                            activeConnection = null
                            Log.e(TAG, "Shizuku returned a null file service")
                            continuation.resumeWith(
                                Result.failure(
                                    IllegalStateException("Shizuku returned a null file service")
                                )
                            )
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (activeConnection === this) {
                            Log.w(TAG, "Shizuku UserService disconnected by the system")
                            boundService = null
                            activeConnection = null
                            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                                "Shizuku service disconnected"
                            )
                        }
                    }
                }

                activeConnection = connection
                try {
                    requirePermission()
                    Shizuku.bindUserService(userServiceArgs, connection)
                } catch (t: Exception) {
                    activeConnection = null
                    continuation.resumeWith(Result.failure(t))
                }
            }
        }

    private suspend fun awaitPermission() = withContext(Dispatchers.Main.immediate) {
        registerListeners()
        refreshStatus()
        val state = _connectionState.value
        if (state is ShizukuConnectionState.PermissionDenied) {
            if (!state.canAskAgain && !permissionRejectedByServer) {
                throw permissionRequired(
                    "Shizuku permission is denied. Enable Mythic Script in Shizuku, then retry."
                )
            }
            Log.d(TAG, "Requesting Shizuku permission (timeout=${permissionTimeoutMs}ms)")
            val request = startPermissionRequest()
                ?: throw permissionRequired(
                    "Could not request Shizuku permission. Open Shizuku, grant access, then retry."
                )
            val granted = withTimeoutOrNull(permissionTimeoutMs) { request.await() } == true
            if (!granted) {
                Log.w(TAG, "Shizuku permission request timed out or was denied")
                finishPermissionRequest(false)
                refreshStatus()
                val updated = _connectionState.value
                if (updated is ShizukuConnectionState.NotRunning ||
                    updated is ShizukuConnectionState.NotInstalled
                ) {
                    requirePermission()
                }
                throw permissionRequired(
                    "Shizuku permission was not granted. Grant access in Shizuku, then retry."
                )
            }
            refreshStatus()
        }
        requirePermission()
    }

    private fun permissionRequired(
        message: String = "Shizuku permission is not granted. Open Shizuku, grant access, then retry.",
    ) = ShizukuServiceUnavailableException(message)

    private fun requirePermission() {
        refreshStatus()
        when (_connectionState.value) {
            is ShizukuConnectionState.ServiceConnected,
            ShizukuConnectionState.PermissionGranted,
            ShizukuConnectionState.Binding,
            is ShizukuConnectionState.ServiceDisconnected,
            -> Unit
            is ShizukuConnectionState.PermissionDenied ->
                throw permissionRequired("Shizuku permission is denied. Grant access in Shizuku, then retry.")
            ShizukuConnectionState.NotRunning ->
                throw ShizukuServiceUnavailableException(
                    "Shizuku is not running. Open Shizuku, start it, then retry."
                )
            ShizukuConnectionState.NotInstalled ->
                throw ShizukuServiceUnavailableException(
                    "Shizuku is not installed. Install Shizuku, then retry."
                )
        }
    }

    private fun computeStatus(): ShizukuConnectionState {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!binderAlive) {
            return if (isShizukuInstalled()) {
                ShizukuConnectionState.NotRunning
            } else {
                ShizukuConnectionState.NotInstalled
            }
        }

        val granted = try {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                !permissionRejectedByServer
        } catch (_: Throwable) {
            false
        }
        if (!granted) return ShizukuConnectionState.PermissionDenied(canAskPermission())

        val service = boundService
        if (service != null && isServiceAlive(service)) return connectedState()
        return ShizukuConnectionState.PermissionGranted
    }

    private fun hasPermissionReady(): Boolean {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!binderAlive) return false
        return try {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                !permissionRejectedByServer
        } catch (_: Throwable) {
            false
        }
    }

    private fun publishConnected() {
        _connectionState.value = connectedState()
    }

    private fun connectedState(): ShizukuConnectionState {
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        val label = if (fallbackActive) {
            "Shell bridge (UID $uid)"
        } else {
            when (uid) {
                0 -> "UID 0 Root"
                2000 -> "UID 2000 Shell"
                else -> "UID $uid"
            }
        }
        return ShizukuConnectionState.ServiceConnected(uid, label)
    }

    private fun finishPermissionRequest(granted: Boolean) {
        val request = permissionRequest
        permissionRequest = null
        request?.complete(granted)
    }

    private fun startPermissionRequest(): CompletableDeferred<Boolean>? {
        permissionRequest?.let { return it }
        val request = CompletableDeferred<Boolean>()
        permissionRequest = request
        return try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                finishPermissionRequest(false)
                return null
            }
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            request
        } catch (_: Exception) {
            finishPermissionRequest(false)
            refreshStatus()
            null
        }
    }

    private fun unbindUserServiceQuietly() {
        val connection = activeConnection
        val hadFallback = fallbackActive
        boundService = null
        activeConnection = null
        fallbackActive = false
        if (connection != null && hasPermissionReady()) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            }.onFailure { error ->
                Log.w(TAG, "Unable to unbind Shizuku service", error)
            }
        } else if (hadFallback) {
            Log.d(TAG, "Released shell file bridge fallback")
        }
    }

    private fun canAskPermission(): Boolean =
        runCatching { !Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(true)

    private fun isServiceAlive(service: IFileBridgeService): Boolean =
        runCatching {
            val binder = service.asBinder()
            if (!binder.isBinderAlive) return@runCatching false
            if (service is ShellFileBridgeService) return@runCatching hasPermissionReady()
            binder.pingBinder()
        }.getOrDefault(false)

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        const val DEFAULT_SERVICE_BIND_TIMEOUT_MS = 30_000L
        const val DEFAULT_PERMISSION_TIMEOUT_MS = 30_000L
        private const val FALLBACK_PROBE_PATH = "/storage/emulated/0/Android/data"
        private val SHIZUKU_PACKAGE_NAMES = listOf(
            SHIZUKU_PACKAGE_NAME,
            "rikka.shizuku",
        )
        private const val TAG = "ShizukuBridgeManager"
        private const val REQUEST_CODE_PERMISSION = 1001
        private const val USER_SERVICE_PROCESS_SUFFIX = "file_service"
        // Bump when the Binder interface changes so Shizuku replaces the old service.
        private const val FILE_SERVICE_VERSION = 2
        private const val READY_TIMEOUT_MS = 45_000L
    }
}
