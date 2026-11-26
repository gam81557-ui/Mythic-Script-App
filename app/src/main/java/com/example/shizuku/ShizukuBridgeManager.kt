package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

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
    data class ServiceConnected(val uid: Int, val processLabel: String) : ShizukuConnectionState()
    data class ServiceDisconnected(val reason: String? = null) : ShizukuConnectionState()

    val isReadyForIo: Boolean
        get() = this is ServiceConnected

    fun statusAction(): ShizukuStatusAction = when (this) {
        is PermissionDenied -> ShizukuStatusAction.RequestPermission
        PermissionGranted,
        is ServiceConnected,
        -> ShizukuStatusAction.Refresh
        NotRunning,
        NotInstalled,
        is ServiceDisconnected,
        -> ShizukuStatusAction.ShowGuide
    }

    fun shortLabel(): String = when (this) {
        NotInstalled -> "Not Installed"
        NotRunning -> "Not Running"
        is PermissionDenied -> "Permission Denied"
        PermissionGranted -> "Permission Granted"
        is ServiceConnected -> "Connected ($processLabel)"
        is ServiceDisconnected -> "Service Disconnected"
    }
}

class ShizukuBridgeManager(private val appContext: Context) {

    private val _connectionState = MutableStateFlow<ShizukuConnectionState>(
        ShizukuConnectionState.NotRunning
    )
    val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceMutex = Mutex()

    @Volatile
    private var fileBridgeService: IFileBridgeService? = null
    private var isBound = false
    private var isBinding = false
    private var fallbackActive = false
    private var isRegistered = false
    private var isListening = false

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, FileBridgeUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
            .tag(USER_SERVICE_TAG)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = false
            mainHandler.removeCallbacks(bindTimeout)
            if (!isListening) {
                fileBridgeService = null
                isBound = false
                return
            }
            runCatching {
                val stub = IFileBridgeService.Stub.asInterface(service)
                if (stub == null || service == null || !service.isBinderAlive) {
                    fileBridgeService = null
                    isBound = false
                    activateShellFallback("Binder interface returned null")
                    return
                }
                fileBridgeService = stub
                isBound = true
                fallbackActive = false
                publishConnected(stub)
            }.onFailure { err ->
                fileBridgeService = null
                isBound = false
                activateShellFallback(err.message ?: "Failed to connect UserService")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
            mainHandler.removeCallbacks(bindTimeout)
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            if (!isListening) return
            activateShellFallback("UserService process disconnected")
        }
    }

    private val bindTimeout: Runnable = Runnable {
        if (!isBinding || !isListening) return@Runnable
        isBinding = false
        runCatching {
            Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
        }
        activateShellFallback("UserService start timed out")
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (isListening) refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        clearServiceState()
        if (isListening) refreshStatus(disconnectedReason = "Shizuku binder stopped")
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE && isListening) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    _connectionState.value = ShizukuConnectionState.PermissionGranted
                    refreshStatus()
                } else {
                    val canAsk = runCatching {
                        !Shizuku.shouldShowRequestPermissionRationale()
                    }.getOrDefault(false)
                    _connectionState.value = ShizukuConnectionState.PermissionDenied(canAsk)
                }
            }
        }

    fun startListening() {
        isListening = true
        if (!isRegistered) {
            isRegistered = runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                true
            }.onFailure { err ->
                _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                    err.message ?: "Failed to register Shizuku listeners"
                )
            }.getOrDefault(false)
        }
        runCatching { refreshStatus() }
            .onFailure { err ->
                _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                    err.message ?: "Failed to refresh Shizuku status"
                )
            }
    }

    fun stopListening() {
        isListening = false
        mainHandler.removeCallbacks(bindTimeout)
        if (isRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
            isRegistered = false
        }
        unbindUserService()
    }

    fun refreshStatus(disconnectedReason: String? = null) {
        if (!isListening) return
        if (!isShizukuInstalled()) {
            clearServiceState()
            _connectionState.value = ShizukuConnectionState.NotInstalled
            return
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            clearServiceState()
            _connectionState.value = ShizukuConnectionState.NotRunning
            return
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            clearServiceState()
            val canAsk = runCatching {
                !Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(true)
            _connectionState.value = ShizukuConnectionState.PermissionDenied(canAsk)
            return
        }

        val existing = fileBridgeService
        if (existing != null && existing.asBinder().isBinderAlive) {
            publishConnected(existing)
            return
        }
        if (fallbackActive) fallbackActive = false

        if (isBinding) {
            _connectionState.value = ShizukuConnectionState.PermissionGranted
            return
        }

        _connectionState.value = if (disconnectedReason != null) {
            ShizukuConnectionState.ServiceDisconnected(disconnectedReason)
        } else {
            ShizukuConnectionState.PermissionGranted
        }

        if (userServiceSupported()) {
            bindUserService()
        } else {
            activateShellFallback("Shizuku API 11+ is required for UserService")
        }
    }

    fun requestPermission() {
        if (!isShizukuInstalled()) {
            _connectionState.value = ShizukuConnectionState.NotInstalled
            return
        }
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _connectionState.value = ShizukuConnectionState.NotRunning
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (granted) {
            _connectionState.value = ShizukuConnectionState.PermissionGranted
            refreshStatus()
        } else {
            runCatching {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }.onFailure { err ->
                _connectionState.value = ShizukuConnectionState.ServiceDisconnected(err.message)
            }
        }
    }

    fun bindUserService() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bindUserService() }
            return
        }
        tryBindUserService()
    }

    private fun tryBindUserService() {
        if (!isListening || isBinding || isBound) return
        if (fileBridgeService != null) return
        if (!userServiceSupported()) {
            activateShellFallback("Shizuku API 11+ is required for UserService")
            return
        }
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _connectionState.value = ShizukuConnectionState.NotRunning
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            _connectionState.value = ShizukuConnectionState.PermissionDenied(true)
            return
        }

        isBinding = true
        _connectionState.value = ShizukuConnectionState.PermissionGranted
        mainHandler.removeCallbacks(bindTimeout)
        mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS)
        runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        }.onFailure { err ->
            isBinding = false
            mainHandler.removeCallbacks(bindTimeout)
            activateShellFallback(err.message ?: "Failed to bind UserService")
        }
    }

    fun unbindUserService() {
        mainHandler.removeCallbacks(bindTimeout)
        val hadUserService = isBound || isBinding
        isBinding = false
        isBound = false
        fallbackActive = false
        if (hadUserService) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            }
        }
        fileBridgeService = null
    }

    private fun userServiceSupported(): Boolean =
        runCatching { Shizuku.getVersion() >= 11 }.getOrDefault(false)

    /**
     * Shizuku 13.6.0 can fail to start a UserService on Android 16 before it calls
     * ServiceConnection. The server drops its record after 30 s without notifying the
     * client, so this bridge keeps file I/O usable after that failure. It is also the
     * only path on pre-v11 Shizuku.
     */
    private fun activateShellFallback(reason: String): Boolean {
        if (!isListening) return false
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            _connectionState.value = ShizukuConnectionState.NotRunning
            return false
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            _connectionState.value = ShizukuConnectionState.PermissionDenied(true)
            return false
        }

        val current = fileBridgeService
        if (fallbackActive && current != null && current.asBinder().isBinderAlive) {
            publishConnected(current)
            return true
        }

        return runCatching {
            val bridge = ShellFileBridgeService()
            if (!bridge.isDirectory(FALLBACK_PROBE_PATH)) {
                throw IllegalStateException("Shell bridge cannot access $FALLBACK_PROBE_PATH")
            }
            fileBridgeService = bridge
            isBound = false
            isBinding = false
            fallbackActive = true
            mainHandler.removeCallbacks(bindTimeout)
            publishConnected(bridge)
            true
        }.getOrElse { err ->
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                "$reason; shell fallback failed: ${err.message}"
            )
            false
        }
    }

    private fun publishConnected(service: IFileBridgeService) {
        val uid = runCatching { service.uid }.getOrDefault(if (fallbackActive) 2000 else -1)
        val label = if (fallbackActive) {
            "Shell compatibility"
        } else {
            when (uid) {
                0 -> "UID 0 Root"
                2000 -> "UID 2000 Shell"
                else -> "UID $uid"
            }
        }
        _connectionState.value = ShizukuConnectionState.ServiceConnected(uid, label)
    }

    private fun clearServiceState() {
        mainHandler.removeCallbacks(bindTimeout)
        fileBridgeService = null
        isBound = false
        isBinding = false
        fallbackActive = false
    }

    fun openShizukuApp(): Boolean {
        return runCatching {
            val launchIntent = appContext.packageManager
                .getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: return@runCatching false
            appContext.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    suspend fun <T> withService(block: suspend (IFileBridgeService) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            serviceMutex.withLock {
                var service = fileBridgeService
                if (service == null || !service.asBinder().isBinderAlive) {
                    refreshStatus(disconnectedReason = "UserService not connected")
                    service = fileBridgeService
                }
                if (service == null || !service.asBinder().isBinderAlive) {
                    return@withLock Result.failure(
                        IllegalStateException(
                            "Shizuku service is not connected. Status: ${_connectionState.value.shortLabel()}"
                        )
                    )
                }
                runCatching { block(service) }
            }
        }

    fun isShizukuInstalled(): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val USER_SERVICE_TAG = "file_bridge"
        private const val USER_SERVICE_PROCESS_SUFFIX = "file_bridge"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4201
        private const val BIND_TIMEOUT_MS = 12_000L
        private val FALLBACK_PROBE_PATH = File(
            Environment.getExternalStorageDirectory(),
            "Android/data",
        ).absolutePath
    }
}
