package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

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

    private val serviceMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var fileBridgeService: IFileBridgeService? = null
    private var isBound = false
    private var isBinding = false
    private var fallbackActive = false
    private var isRegistered = false

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, FileBridgeUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("file_bridge")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = false
            fallbackActive = false
            mainHandler.removeCallbacks(bindTimeout)
            runCatching {
                val stub = IFileBridgeService.Stub.asInterface(service)
                fileBridgeService = stub
                isBound = stub != null
                if (stub != null) {
                    val uid = runCatching { stub.uid }.getOrDefault(-1)
                    val label = when (uid) {
                        0 -> "UID 0 Root"
                        2000 -> "UID 2000 Shell"
                        else -> "UID $uid"
                    }
                    _connectionState.value = ShizukuConnectionState.ServiceConnected(uid, label)
                } else {
                    activateShellFallback("Binder interface returned null")
                }
            }.onFailure { err ->
                fileBridgeService = null
                isBound = false
                activateShellFallback(
                    err.message ?: "Failed to connect UserService"
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fileBridgeService = null
            isBound = false
            isBinding = false
            mainHandler.removeCallbacks(bindTimeout)
            activateShellFallback("UserService process disconnected")
        }
    }

    private val bindTimeout = Runnable {
        if (!isBinding || fileBridgeService != null) return@Runnable
        isBinding = false
        activateShellFallback(
            "UserService did not start within ${BIND_TIMEOUT_MS / 1000}s"
        )
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBinding = false
        fallbackActive = false
        mainHandler.removeCallbacks(bindTimeout)
        fileBridgeService = null
        isBound = false
        refreshStatus(disconnectedReason = "Shizuku binder stopped")
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    _connectionState.value = ShizukuConnectionState.PermissionGranted
                    // Re-read the live binder/UID immediately after the grant so the pill
                    // switches from "Grant" to the connected UID without another tap.
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
        isBinding = false
        fallbackActive = false
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
        mainHandler.removeCallbacks(bindTimeout)
        isBinding = false
        fallbackActive = false
        if (isRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
            isRegistered = false
        }
        unbindUserService()
    }

    fun refreshStatus(disconnectedReason: String? = null) {
        if (!isShizukuInstalled()) {
            fileBridgeService = null
            isBound = false
            _connectionState.value = ShizukuConnectionState.NotInstalled
            return
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            fileBridgeService = null
            isBound = false
            _connectionState.value = ShizukuConnectionState.NotRunning
            return
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (!granted) {
            fileBridgeService = null
            isBound = false
            val canAsk = runCatching {
                !Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(true)
            _connectionState.value = ShizukuConnectionState.PermissionDenied(canAsk)
            return
        }

        val existing = fileBridgeService
        if (existing != null && existing.asBinder().isBinderAlive) {
            val uid = runCatching { existing.uid }.getOrDefault(2000)
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
            return
        }

        if (isBinding) {
            _connectionState.value = ShizukuConnectionState.PermissionGranted
            return
        }

        _connectionState.value = if (disconnectedReason != null) {
            ShizukuConnectionState.ServiceDisconnected(disconnectedReason)
        } else {
            ShizukuConnectionState.PermissionGranted
        }

        // Automatically bind once permission is granted and binder is live.
        bindUserService()
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
            bindUserService()
        } else {
            runCatching {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }.onFailure { err ->
                _connectionState.value = ShizukuConnectionState.ServiceDisconnected(err.message)
            }
        }
    }

    fun bindUserService() {
        if (isBinding || (fileBridgeService != null && !fallbackActive)) return

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

        // A previous fallback is only a placeholder; keep it until the real service connects.
        fallbackActive = false
        fileBridgeService = null
        isBound = false
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
        isBinding = false
        fallbackActive = false
        if (isBound) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            }
        }
        fileBridgeService = null
        isBound = false
        if (isRegistered && runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            _connectionState.value = if (granted) {
                ShizukuConnectionState.PermissionGranted
            } else {
                ShizukuConnectionState.PermissionDenied(true)
            }
        }
    }

    private fun activateShellFallback(reason: String) {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            _connectionState.value = ShizukuConnectionState.NotRunning
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            _connectionState.value = ShizukuConnectionState.PermissionDenied(true)
            return
        }

        runCatching {
            val fallback = ShellFileBridgeService()
            if (!fallback.isDirectory("/storage/emulated/0/Android/data")) {
                throw IllegalStateException("Shell compatibility bridge cannot access /Android/data/")
            }
            fileBridgeService = fallback
            isBound = false
            fallbackActive = true
            val uid = fallback.getUid()
            Log.w(TAG, "UserService unavailable ($reason); using shell compatibility bridge")
            _connectionState.value = ShizukuConnectionState.ServiceConnected(
                uid = uid,
                processLabel = "Shell compatibility",
            )
        }.onFailure { err ->
            fileBridgeService = null
            isBound = false
            fallbackActive = false
            Log.e(TAG, "Shell compatibility bridge failed after: $reason", err)
            _connectionState.value = ShizukuConnectionState.ServiceDisconnected(
                "$reason; shell fallback failed: ${err.message}"
            )
        }
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
                val service = fileBridgeService
                if (service == null || !service.asBinder().isBinderAlive) {
                    refreshStatus(disconnectedReason = "UserService not connected")
                    return@withLock Result.failure(
                        IllegalStateException(
                            "Shizuku UserService is not connected. Status: ${_connectionState.value.shortLabel()}"
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
        private const val TAG = "ShizukuBridgeManager"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4201
        private const val BIND_TIMEOUT_MS = 12_000L
    }
}
