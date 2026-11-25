package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.example.BuildConfig
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.LogLevel
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsError
import com.unity3d.services.core.lifecycle.CachedLifecycle
import com.unity3d.services.core.properties.ClientProperties
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnityAdDebugState(
    val init: String = "Init: idle",
    val initDetail: String = "",
    val net: String = "",
    val banner: String = "Banner: idle",
    val rewarded: String = "Rewarded: idle",
    val interstitial: String = "Interstitial: idle",
    val gate: String = "Gate: idle",
    val reason: String = "",
    val lastError: String = "",
)

/**
 * Owns Unity Ads: the bottom banner and the rewarded/interstitial ad used as the download gate.
 *
 * Rewarded is preferred and interstitial is the fallback when rewarded has no fill. Only one
 * load is in flight at a time, a download requested during a load reuses it, and the download
 * callback runs exactly once when the ad closes, fails to load, fails to show, or times out.
 */
object UnityAdManager {
    const val GAME_ID = "800450404"
    const val REWARDED_PLACEMENT = "BP_Rewarded_Android"
    const val INTERSTITIAL_PLACEMENT = "BP_Interstitial_Android"
    const val BANNER_PLACEMENT = "BP_Banner_Android"

    private const val TAG = "UnityAdManager"
    // Production releases must never request Unity test ads.
    private const val TEST_MODE = false
    // SDK debug logging is allowed only in debug builds.
    private val SDK_DEBUG_MODE = BuildConfig.DEBUG
    private const val LOAD_TIMEOUT_MS = 12_000L
    // Unity 4.22 wraps the gateway round trip in a 120s timeout. The app watchdog must
    // outlive it; resetting SDK state earlier drops the SDK's eventual callback.
    private const val INIT_TIMEOUT_MS = 130_000L
    private const val INIT_POLL_MS = 1_000L
    private const val INIT_STUCK_POLL_MS = 5_000L
    // Never hold a download hostage to a slow ad-network init. Init continues in the
    // background after this gate timeout.
    private const val PENDING_INIT_TIMEOUT_MS = 20_000L
    private const val SHOW_TIMEOUT_MS = 65_000L

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _debugState = MutableStateFlow(UnityAdDebugState())
    val debugState: StateFlow<UnityAdDebugState> = _debugState.asStateFlow()

    private fun updateDebug(transform: (UnityAdDebugState) -> UnityAdDebugState) {
        _debugState.value = transform(_debugState.value)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var appContext: Context? = null
    private var initializing = false
    private var preloadRequested = false
    private var initAttempt = 0
    private var initStartedAt = 0L
    private var initListener: InitializationListener? = null
    private var initWatchdogToken: Any? = null
    private var initTimedOut = false

    private var rewardedReady = false
    private var interstitialReady = false
    private var loadingPlacement: String? = null
    private var showing = false
    private var loadGeneration = 0
    private var pendingDownload: (() -> Unit)? = null

    private var loadTimeoutToken: Any? = null
    private var pendingTimeoutToken: Any? = null
    private var showTimeoutToken: Any? = null

    fun init(activity: Activity) {
        activityRef = WeakReference(activity)
        appContext = activity.applicationContext
        ensureInitialized(activity.applicationContext)
    }

    fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
        appContext = activity.applicationContext
        if (!_isInitialized.value) ensureInitialized(activity.applicationContext)
    }

    fun isOnline(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    fun preload(context: Context) {
        appContext = context.applicationContext
        if (!_isInitialized.value) {
            preloadRequested = true
            ensureInitialized(context.applicationContext)
            return
        }
        if (showing || loadingPlacement != null || rewardedReady || interstitialReady) return
        loadRewarded()
    }

    /**
     * Shows a rewarded ad (interstitial as fallback) and runs [onDownload] once the ad is closed
     * or could not be shown. Returns false when another ad flow is already running; the caller
     * can tell the user instead of silently dropping the request.
     */
    fun showRewardedThenDownload(context: Context, onDownload: () -> Unit): Boolean {
        val activity = resolveActivity(context)
        if (activity == null) {
            runCatching { onDownload() }
                .onFailure { Log.e(TAG, "Download callback failed without an activity", it) }
            return true
        }
        if (showing || pendingDownload != null) return false

        pendingDownload = onDownload
        appContext = context.applicationContext

        when {
            rewardedReady -> showAd(activity, REWARDED_PLACEMENT)
            interstitialReady -> showAd(activity, INTERSTITIAL_PLACEMENT)
            !_isInitialized.value -> {
                preloadRequested = true
                ensureInitialized(activity.applicationContext)
                schedulePendingTimeout()
            }
            loadingPlacement != null -> schedulePendingTimeout()
            else -> loadRewarded()
        }
        return true
    }

    fun release() {
        activityRef = null
        // Keep the application context: an in-flight init callback may arrive after the
        // activity is recreated (for example on a locale change).
        loadGeneration++
        // Keep the init watchdog alive: initialization may still be running while the activity is recreated.
        loadTimeoutToken = null
        pendingTimeoutToken = null
        showTimeoutToken = null
        pendingDownload = null
        showing = false
        loadingPlacement = null
        rewardedReady = false
        interstitialReady = false
        updateDebug { it.copy(banner = "Banner: idle", gate = "Gate: idle") }
    }

    private fun ensureInitialized(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "UNITY_INIT_THREAD_REPOST caller=${Thread.currentThread().name}")
            mainHandler.post { ensureInitialized(context) }
            return
        }
        val app = context.applicationContext ?: context
        appContext = app
        repairSdkContext(app)

        if (UnityAds.isInitialized) {
            initializing = false
            initTimedOut = false
            initListener = null
            _isInitialized.value = true
            updateDebug {
                it.copy(
                    init = "Init: already initialized",
                    initDetail = "sdkInit=true v${UnityAds.version} wv=${webViewPackage()}",
                    reason = "INIT_ALREADY",
                )
            }
            if (preloadRequested) {
                preloadRequested = false
                preload(app)
            }
            return
        }
        if (initializing) {
            val state = sdkInitializationState()
            Log.i(
                TAG,
                "UNITY_INIT_IN_PROGRESS attempt=$initAttempt elapsed=${initElapsedMs()}ms " +
                    "state=${state ?: "n/a"} timedOut=$initTimedOut ${sdkDiagnostics()}",
            )
            updateDebug {
                it.copy(
                    init = if (initTimedOut) {
                        "Init: NO CALLBACK ${initElapsedMs()}ms"
                    } else {
                        "Init: already initializing"
                    },
                    initDetail = "attempt=$initAttempt elapsed=${initElapsedMs()}ms state=${state ?: "n/a"} " +
                        sdkDiagnostics(),
                    reason = when {
                        initTimedOut && state == "INITIALIZING" -> "INIT_STUCK_INITIALIZING"
                        initTimedOut -> "INIT_NO_CALLBACK"
                        else -> "INIT_IN_PROGRESS"
                    },
                )
            }
            return
        }

        if (GAME_ID.toIntOrNull() == null) {
            handleInitFailure(
                initAttempt,
                UnityAds.UnityAdsInitializationError.INVALID_ARGUMENT,
                "Game ID '$GAME_ID' is not numeric",
                "preflight",
            )
            return
        }

        initializing = true
        initTimedOut = false
        initAttempt++
        initStartedAt = System.currentTimeMillis()
        val online = isOnline(app)
        val webView = webViewPackage()
        if (webView == "MISSING") {
            Log.w(TAG, "UNITY_INIT_WEBVIEW_MISSING Android System WebView package is unavailable")
        }
        updateDebug {
            it.copy(
                init = "Init: starting attempt $initAttempt",
                initDetail = "attempt=$initAttempt game=$GAME_ID test=$TEST_MODE v${UnityAds.version} " +
                    "wv=$webView",
                net = "net: ${if (online) "online" else "offline"}",
                reason = "INIT_START",
            )
        }
        Log.i(
            TAG,
            "UNITY_INIT_START attempt=$initAttempt gameId=$GAME_ID testMode=$TEST_MODE " +
                "ctx=${app.javaClass.name} mainThread=${Looper.myLooper() == Looper.getMainLooper()} " +
                "supported=${UnityAds.isSupported} version=${UnityAds.version} online=$online wv=$webView",
        )
        startSdkInitialization(app)
    }

    private fun startSdkInitialization(context: Context) {
        val attempt = initAttempt
        repairSdkContext(context)
        runCatching { UnityAds.debugMode = SDK_DEBUG_MODE }
            .onFailure { Log.w(TAG, "Unable to set Unity debug mode: ${it.message}", it) }
        val configuration = InitializationConfiguration.Builder(GAME_ID)
            .withTestMode(TEST_MODE)
            .withLogLevel(if (SDK_DEBUG_MODE) LogLevel.DEBUG else LogLevel.ERROR)
            .withContext(context)
            .build()
        updateDebug {
            it.copy(
                init = "Init: attempt $attempt",
                initDetail = "ctx=${context.javaClass.simpleName} game=$GAME_ID test=$TEST_MODE " +
                    "sdkDebug=$SDK_DEBUG_MODE sdkInit=${UnityAds.isInitialized}",
                reason = "INIT_CALL",
            )
        }
        Log.i(
            TAG,
            "UNITY_INIT_CALL_API attempt=$attempt gameId=$GAME_ID testMode=$TEST_MODE " +
                "sdkDebug=$SDK_DEBUG_MODE ctx=${context.javaClass.name} sdkInit=${UnityAds.isInitialized} " +
                "supported=${UnityAds.isSupported} version=${UnityAds.version} " +
                "clientApp=${ClientProperties.getApplication()?.javaClass?.name ?: "null"} " +
                "clientCtx=${ClientProperties.getApplicationContext()?.javaClass?.name ?: "null"}",
        )
        val listener = object : InitializationListener {
            override fun onInitializationComplete(error: UnityAdsError?) {
                if (error == null) {
                    Log.i(
                        TAG,
                        "UNITY_INIT_CALLBACK_SUCCESS attempt=$attempt thread=${Thread.currentThread().name} " +
                            "elapsed=${initElapsedMs()}ms",
                    )
                    mainHandler.post { handleInitSuccess(attempt, "callback") }
                } else {
                    Log.e(
                        TAG,
                        "UNITY_INIT_CALLBACK_FAILURE attempt=$attempt code=${error.code} " +
                            "message=${error.message} thread=${Thread.currentThread().name} " +
                            "elapsed=${initElapsedMs()}ms",
                    )
                    mainHandler.post {
                        handleInitFailure(
                            attempt,
                            UnityAds.UnityAdsInitializationError.INTERNAL_ERROR,
                            "code=${error.code} ${error.message}",
                            "callback",
                        )
                    }
                }
            }
        }
        initListener = listener
        try {
            UnityAds.initialize(configuration, listener)
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "UNITY_INIT_EXCEPTION attempt=$attempt ${t.javaClass.name}: ${t.message}",
                t,
            )
            handleInitFailure(
                attempt,
                UnityAds.UnityAdsInitializationError.INTERNAL_ERROR,
                "${t.javaClass.name}: ${t.message ?: "no message"}",
                "initialize threw",
            )
            return
        }
        scheduleInitWatchdog(attempt)
    }

    private fun repairSdkContext(context: Context) {
        runCatching {
            val app = context.applicationContext ?: context
            ClientProperties.setApplicationContext(app)
            (app as? Application)?.let { ClientProperties.setApplication(it) }
            CachedLifecycle.register()
            Log.i(
                TAG,
                "UNITY_INIT_CONTEXT app=${app.javaClass.name} " +
                    "clientApp=${ClientProperties.getApplication()?.javaClass?.name ?: "null"} " +
                    "clientCtx=${ClientProperties.getApplicationContext()?.javaClass?.name ?: "null"}",
            )
        }.onFailure {
            Log.w(TAG, "UNITY_INIT_CONTEXT_FAIL ${it.javaClass.simpleName}: ${it.message}", it)
        }
    }

    private fun sdkInitializationState(): String? =
        boldInitializationState() ?: legacyInitializationState()

    private fun boldInitializationState(): String? = runCatching {
        val repoClass = Class.forName("com.unity3d.ads.core.data.repository.SessionRepository")
        val repo = Class.forName("com.unity3d.services.core.misc.Utilities")
            .getMethod("getService", Class::class.java)
            .invoke(null, repoClass)
        repoClass.getMethod("getInitializationState").invoke(repo)?.toString()
    }.getOrNull()

    private fun legacyInitializationState(): String? = runCatching {
        Class.forName("com.unity3d.services.core.properties.SdkProperties")
            .getMethod("getCurrentInitializationState")
            .invoke(null)
            ?.toString()
    }.getOrNull()

    private fun webViewPackage(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "api<26"
        return runCatching {
            WebView.getCurrentWebViewPackage()?.let { "${it.packageName}/${it.versionName}" } ?: "MISSING"
        }.getOrElse { "ERR:${it.javaClass.simpleName}" }
    }

    private fun sdkDiagnostics(): String {
        val sdk = runCatching {
            Class.forName("com.unity3d.services.core.properties.SdkProperties")
        }.getOrNull()
        val client = runCatching {
            Class.forName("com.unity3d.services.core.properties.ClientProperties")
        }.getOrNull()

        fun call(target: Class<*>?, method: String): String = runCatching {
            target?.getMethod(method)?.invoke(null)?.toString() ?: "n/a"
        }.getOrElse { "err:${it.javaClass.simpleName}" }

        fun present(target: Class<*>?, method: String): String = runCatching {
            (target?.getMethod(method)?.invoke(null) != null).toString()
        }.getOrElse { "err:${it.javaClass.simpleName}" }

        val listeners = runCatching {
            val field = runCatching { sdk?.getDeclaredField("_legacyInitializationListeners") }.getOrNull()
                ?: runCatching { sdk?.getDeclaredField("_initializationListeners") }.getOrNull()
            field?.isAccessible = true
            (field?.get(null) as? Collection<*>)?.size?.toString() ?: "n/a"
        }.getOrElse { "err:${it.javaClass.simpleName}" }

        return "state=${sdkInitializationState() ?: "n/a"} listeners=$listeners " +
            "sdkInit=${call(sdk, "isInitialized")} gameId=${call(client, "getGameId")} " +
            "app=${present(client, "getApplication")} appCtx=${present(client, "getApplicationContext")} " +
            "cache=${present(sdk, "getCacheDirectory")} wv=${webViewPackage()}"
    }

    private fun initElapsedMs(): Long =
        if (initStartedAt == 0L) 0L else System.currentTimeMillis() - initStartedAt

    private fun handleInitSuccess(attempt: Int, source: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleInitSuccess(attempt, source) }
            return
        }
        if (attempt != initAttempt) {
            Log.w(TAG, "UNITY_INIT_STALE_SUCCESS attempt=$attempt current=$initAttempt source=$source")
            return
        }
        val elapsed = initElapsedMs()
        val diag = sdkDiagnostics()
        initializing = false
        initTimedOut = false
        initListener = null
        initWatchdogToken = null
        _isInitialized.value = true
        updateDebug {
            it.copy(
                init = "Init: OK ${elapsed}ms",
                initDetail = "$source sdkInit=${UnityAds.isInitialized} v${UnityAds.version} $diag",
                reason = "INIT_OK",
                lastError = "",
            )
        }
        Log.i(
            TAG,
            "UNITY_INIT_SUCCESS attempt=$attempt source=$source elapsed=${elapsed}ms " +
                "(sdkInit=${UnityAds.isInitialized}, supported=${UnityAds.isSupported}, v${UnityAds.version}) $diag",
        )
        val context = activityRef?.get() ?: appContext
        if (context != null && preloadRequested) {
            preloadRequested = false
            preload(context)
        }
    }

    private fun handleInitFailure(
        attempt: Int,
        error: UnityAds.UnityAdsInitializationError,
        message: String,
        source: String,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleInitFailure(attempt, error, message, source) }
            return
        }
        if (_isInitialized.value) return
        if (attempt != initAttempt) {
            Log.w(TAG, "UNITY_INIT_STALE_FAILURE attempt=$attempt current=$initAttempt error=$error")
            return
        }
        val elapsed = initElapsedMs()
        val diag = sdkDiagnostics()
        initializing = false
        initTimedOut = false
        initListener = null
        initWatchdogToken = null
        preloadRequested = false
        updateDebug {
            it.copy(
                init = "Init: FAILED $error",
                initDetail = "$source after ${elapsed}ms $diag",
                reason = "INIT_FAIL_$error",
                lastError = "init $error: $message | $diag",
            )
        }
        Log.e(
            TAG,
            "UNITY_INIT_FAILURE attempt=$attempt source=$source elapsed=${elapsed}ms error=$error " +
                "message=$message (gameId=$GAME_ID, supported=${UnityAds.isSupported}) $diag",
        )
        completePendingDownload()
    }

    private fun scheduleInitWatchdog(attempt: Int, delayMs: Long = INIT_POLL_MS) {
        initWatchdogToken = null
        val token = Any()
        initWatchdogToken = token
        mainHandler.postDelayed({
            if (initWatchdogToken !== token) return@postDelayed
            if (!initializing || attempt != initAttempt) return@postDelayed
            if (UnityAds.isInitialized) {
                handleInitSuccess(attempt, "poll")
                return@postDelayed
            }
            val elapsed = initElapsedMs()
            val state = sdkInitializationState()
            val diag = sdkDiagnostics()
            if (state == "INITIALIZED_SUCCESSFULLY" || state == "INITIALIZED") {
                handleInitSuccess(attempt, "state poll")
                return@postDelayed
            }
            if (state == "INITIALIZED_FAILED" || state == "FAILED") {
                handleInitFailure(
                    attempt,
                    UnityAds.UnityAdsInitializationError.INTERNAL_ERROR,
                    "SDK state is $state but no callback arrived",
                    "state poll",
                )
                return@postDelayed
            }
            if (elapsed >= INIT_TIMEOUT_MS) {
                if (state == "NOT_INITIALIZED") {
                    handleInitFailure(
                        attempt,
                        UnityAds.UnityAdsInitializationError.INTERNAL_ERROR,
                        "SDK stayed NOT_INITIALIZED and never called back",
                        "timeout",
                    )
                    return@postDelayed
                }
                // Do not reset the SDK here. Unity 4.22 may still be inside its own
                // 120s gateway timeout; resetting state/listeners drops the late callback
                // and makes every retry return without a callback too.
                if (!initTimedOut) {
                    initTimedOut = true
                    updateDebug {
                        it.copy(
                            init = "Init: NO CALLBACK ${elapsed}ms",
                            initDetail = "state=${state ?: "n/a"} $diag",
                            reason = if (state == "INITIALIZING") {
                                "INIT_STUCK_INITIALIZING"
                            } else {
                                "INIT_NO_CALLBACK"
                            },
                            lastError = "no init callback after ${elapsed}ms | state=${state ?: "n/a"} | $diag",
                        )
                    }
                    Log.e(
                        TAG,
                        "UNITY_INIT_NO_CALLBACK attempt=$attempt elapsed=${elapsed}ms " +
                            "state=${state ?: "n/a"} (listener kept registered) $diag",
                    )
                    completePendingDownload()
                } else {
                    updateDebug {
                        it.copy(
                            init = "Init: NO CALLBACK ${elapsed}ms",
                            initDetail = "state=${state ?: "n/a"} $diag",
                            reason = if (state == "INITIALIZING") {
                                "INIT_STUCK_INITIALIZING"
                            } else {
                                "INIT_NO_CALLBACK"
                            },
                        )
                    }
                    if (elapsed % 15_000L < INIT_STUCK_POLL_MS) {
                        Log.i(
                            TAG,
                            "UNITY_INIT_LATE_WAIT attempt=$attempt elapsed=${elapsed}ms " +
                                "state=${state ?: "n/a"} $diag",
                        )
                    }
                }
                scheduleInitWatchdog(attempt, INIT_STUCK_POLL_MS)
                return@postDelayed
            }
            updateDebug {
                it.copy(
                    init = "Init: waiting ${elapsed}ms",
                    initDetail = "attempt=$attempt state=${state ?: "n/a"} $diag",
                    reason = "INIT_WAITING",
                )
            }
            if (elapsed % 5_000L < INIT_POLL_MS) {
                Log.i(TAG, "UNITY_INIT_WAIT attempt=$attempt elapsed=${elapsed}ms state=${state ?: "n/a"} $diag")
            }
            scheduleInitWatchdog(attempt)
        }, delayMs)
    }

    private fun resetSdkInitializationState() {
        runCatching {
            val sdk = Class.forName("com.unity3d.services.core.properties.SdkProperties")
            val legacyStateClass = Class.forName("com.unity3d.services.core.properties.SdkProperties\$InitializationState")
            legacyStateClass.enumConstants?.firstOrNull { it.toString() == "NOT_INITIALIZED" }?.let { state ->
                sdk.getMethod("setInitializeState", legacyStateClass).invoke(null, state)
                sdk.getMethod("setInitialized", Boolean::class.javaPrimitiveType).invoke(null, false)
            }
            runCatching { sdk.getMethod("resetInitializationListeners").invoke(null) }
        }.onFailure {
            Log.w(TAG, "UNITY_INIT_RESET_LEGACY failed ${it.javaClass.simpleName}: ${it.message}", it)
        }

        runCatching {
            val repoClass = Class.forName("com.unity3d.ads.core.data.repository.SessionRepository")
            val repo = Class.forName("com.unity3d.services.core.misc.Utilities")
                .getMethod("getService", Class::class.java)
                .invoke(null, repoClass)
            val boldStateClass = Class.forName("com.unity3d.ads.core.data.model.InitializationState")
            boldStateClass.enumConstants?.firstOrNull { it.toString() == "NOT_INITIALIZED" }?.let { state ->
                repoClass.getMethod("setInitializationState", boldStateClass).invoke(repo, state)
                repoClass.getMethod("setShouldInitialize", Boolean::class.javaPrimitiveType).invoke(repo, true)
            }
        }.onFailure {
            Log.w(TAG, "UNITY_INIT_RESET_BOLD failed ${it.javaClass.simpleName}: ${it.message}", it)
        }
        Log.i(TAG, "UNITY_INIT_RESET_STATE done")
    }

    fun retryInit() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { retryInit() }
            return
        }
        val state = sdkInitializationState()
        if (initializing || state == "INITIALIZING") {
            Log.w(
                TAG,
                "UNITY_INIT_RETRY_BLOCKED attempt=$initAttempt initializing=$initializing " +
                    "state=${state ?: "n/a"} (waiting for the SDK callback instead of resetting)",
            )
            updateDebug {
                it.copy(
                    init = "Init: NO CALLBACK ${initElapsedMs()}ms",
                    initDetail = "state=${state ?: "n/a"} ${sdkDiagnostics()}",
                    reason = "INIT_RETRY_BLOCKED_INITIALIZING",
                )
            }
            return
        }
        val context = activityRef?.get()?.applicationContext ?: appContext
        if (context == null) {
            Log.w(TAG, "UNITY_INIT_RETRY ignored: no application context")
            return
        }
        Log.i(TAG, "UNITY_INIT_RETRY requested previousAttempt=$initAttempt state=${state ?: "n/a"}")
        resetSdkInitializationState()
        _isInitialized.value = false
        initTimedOut = false
        preloadRequested = true
        updateDebug {
            it.copy(init = "Init: retrying", reason = "INIT_RETRY", lastError = "")
        }
        ensureInitialized(context)
    }

    private fun loadRewarded() {
        val generation = ++loadGeneration
        loadingPlacement = REWARDED_PLACEMENT
        scheduleLoadTimeout(generation)
        updateDebug { it.copy(rewarded = "Rewarded: loading") }
        Log.i(TAG, "Loading rewarded ad $REWARDED_PLACEMENT")

        try {
            UnityAds.load(REWARDED_PLACEMENT, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    if (generation != loadGeneration) return
                    loadingPlacement = null
                    rewardedReady = true
                    cancelLoadTimeout()
                    updateDebug { it.copy(rewarded = "Rewarded: loaded") }
                    Log.i(TAG, "Rewarded ad loaded")
                    if (pendingDownload != null) showPendingAd()
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    if (generation != loadGeneration) return
                    loadingPlacement = null
                    rewardedReady = false
                    cancelLoadTimeout()
                    updateDebug {
                        it.copy(
                            rewarded = "Rewarded: FAILED $error",
                            lastError = "rewarded $error: $message",
                        )
                    }
                    Log.e(TAG, "Rewarded load failed: $error — $message; trying interstitial")
                    loadInterstitial()
                }
            })
        } catch (t: Throwable) {
            if (generation != loadGeneration) return
            loadingPlacement = null
            rewardedReady = false
            cancelLoadTimeout()
            updateDebug {
                it.copy(
                    rewarded = "Rewarded: load exception",
                    lastError = "rewarded load exception: ${t.message}",
                )
            }
            Log.e(TAG, "Rewarded load threw ${t.javaClass.name}; trying interstitial", t)
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        val generation = ++loadGeneration
        loadingPlacement = INTERSTITIAL_PLACEMENT
        scheduleLoadTimeout(generation)
        updateDebug { it.copy(interstitial = "Interstitial: loading") }
        Log.i(TAG, "Loading interstitial ad $INTERSTITIAL_PLACEMENT")

        try {
            UnityAds.load(INTERSTITIAL_PLACEMENT, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    if (generation != loadGeneration) return
                    loadingPlacement = null
                    interstitialReady = true
                    cancelLoadTimeout()
                    updateDebug { it.copy(interstitial = "Interstitial: loaded") }
                    Log.i(TAG, "Interstitial ad loaded")
                    if (pendingDownload != null) showPendingAd()
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    if (generation != loadGeneration) return
                    loadingPlacement = null
                    interstitialReady = false
                    cancelLoadTimeout()
                    updateDebug {
                        it.copy(
                            interstitial = "Interstitial: FAILED $error",
                            lastError = "interstitial $error: $message",
                        )
                    }
                    Log.e(TAG, "Interstitial load failed: $error — $message; continuing with download")
                    completePendingDownload()
                }
            })
        } catch (t: Throwable) {
            if (generation != loadGeneration) return
            loadingPlacement = null
            interstitialReady = false
            cancelLoadTimeout()
            updateDebug {
                it.copy(
                    interstitial = "Interstitial: load exception",
                    lastError = "interstitial load exception: ${t.message}",
                )
            }
            Log.e(TAG, "Interstitial load threw ${t.javaClass.name}; continuing with download", t)
            completePendingDownload()
        }
    }

    private fun showPendingAd() {
        val context = appContext ?: run {
            completePendingDownload()
            return
        }
        val activity = resolveActivity(context) ?: run {
            completePendingDownload()
            return
        }
        when {
            rewardedReady -> showAd(activity, REWARDED_PLACEMENT)
            interstitialReady -> showAd(activity, INTERSTITIAL_PLACEMENT)
        }
    }

    private fun showAd(activity: Activity, placementId: String) {
        cancelLoadTimeout()
        cancelPendingTimeout()
        showing = true

        val isRewarded = placementId == REWARDED_PLACEMENT
        updateDebug {
            if (isRewarded) {
                it.copy(rewarded = "Rewarded: showing")
            } else {
                it.copy(interstitial = "Interstitial: showing")
            }
        }
        Log.i(TAG, "Showing ad $placementId")

        val showToken = Any()
        showTimeoutToken = showToken
        var finished = false

        fun finishOnce() {
            if (finished) return
            finished = true
            if (showTimeoutToken === showToken) showTimeoutToken = null
            finishAdFlow(activity)
        }

        mainHandler.postDelayed({
            if (showTimeoutToken === showToken) {
                showTimeoutToken = null
                updateDebug {
                    it.copy(
                        rewarded = if (isRewarded) "Rewarded: show timeout" else it.rewarded,
                        interstitial = if (isRewarded) it.interstitial else "Interstitial: show timeout",
                        lastError = "show timeout after ${SHOW_TIMEOUT_MS}ms for $placementId",
                    )
                }
                Log.w(TAG, "Ad display timed out for $placementId, continuing with download")
                finishOnce()
            }
        }, SHOW_TIMEOUT_MS)

        try {
            UnityAds.show(activity, placementId, object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String,
                ) {
                    updateDebug {
                        it.copy(
                            rewarded = if (isRewarded) "Rewarded: show FAILED $error" else it.rewarded,
                            interstitial = if (isRewarded) it.interstitial else "Interstitial: show FAILED $error",
                            lastError = "show $error: $message",
                        )
                    }
                    Log.e(TAG, "Ad show failed for $placementId: $error — $message; continuing with download")
                    finishOnce()
                }

                override fun onUnityAdsShowStart(placementId: String) = Unit

                override fun onUnityAdsShowClick(placementId: String) = Unit

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState,
                ) {
                    updateDebug {
                        it.copy(
                            rewarded = if (isRewarded) "Rewarded: closed $state" else it.rewarded,
                            interstitial = if (isRewarded) it.interstitial else "Interstitial: closed $state",
                        )
                    }
                    Log.i(TAG, "Ad closed ($placementId): $state")
                    finishOnce()
                }
            })
        } catch (t: Throwable) {
            updateDebug {
                it.copy(lastError = "show exception for $placementId: ${t.message}")
            }
            Log.e(TAG, "Ad show threw ${t.javaClass.name} for $placementId; continuing with download", t)
            finishOnce()
        }
    }

    private fun finishAdFlow(activity: Activity) {
        showing = false
        rewardedReady = false
        interstitialReady = false
        cancelShowTimeout()
        completePendingDownload()
        preload(activity)
    }

    private fun completePendingDownload() {
        cancelLoadTimeout()
        cancelPendingTimeout()
        cancelShowTimeout()
        val action = pendingDownload ?: return
        pendingDownload = null
        runCatching { action() }
            .onFailure { Log.e(TAG, "Pending download callback failed", it) }
    }

    private fun scheduleLoadTimeout(loadGeneration: Int) {
        cancelLoadTimeout()
        val token = Any()
        loadTimeoutToken = token
        mainHandler.postDelayed({
            if (loadTimeoutToken !== token || loadGeneration != this.loadGeneration) return@postDelayed
            val timedOutPlacement = loadingPlacement
            loadTimeoutToken = null
            loadingPlacement = null
            rewardedReady = false
            interstitialReady = false
            this.loadGeneration++ // Invalidate a load that outlives the timeout.
            updateDebug {
                when (timedOutPlacement) {
                    REWARDED_PLACEMENT -> it.copy(
                        rewarded = "Rewarded: load timeout",
                        lastError = "rewarded load timeout after ${LOAD_TIMEOUT_MS}ms",
                    )
                    INTERSTITIAL_PLACEMENT -> it.copy(
                        interstitial = "Interstitial: load timeout",
                        lastError = "interstitial load timeout after ${LOAD_TIMEOUT_MS}ms",
                    )
                    else -> it
                }
            }
            Log.w(TAG, "Ad load timed out for $timedOutPlacement, continuing with download")
            completePendingDownload()
        }, LOAD_TIMEOUT_MS)
    }

    private fun schedulePendingTimeout() {
        cancelPendingTimeout()
        val waitingForInit = !_isInitialized.value
        val timeoutMs = if (waitingForInit) PENDING_INIT_TIMEOUT_MS else LOAD_TIMEOUT_MS
        if (waitingForInit) {
            updateDebug { it.copy(gate = "Gate: waiting for init") }
        }
        val token = Any()
        pendingTimeoutToken = token
        mainHandler.postDelayed({
            if (pendingTimeoutToken !== token) return@postDelayed
            pendingTimeoutToken = null
            updateDebug {
                it.copy(
                    gate = if (waitingForInit) "Gate: init wait timeout" else "Gate: wait timeout",
                    lastError = "ad not ready within ${timeoutMs}ms",
                )
            }
            Log.w(TAG, "Ad did not become ready within ${timeoutMs}ms, continuing with download")
            completePendingDownload()
        }, timeoutMs)
    }

    private fun cancelLoadTimeout() {
        loadTimeoutToken = null
    }

    private fun cancelPendingTimeout() {
        pendingTimeoutToken = null
    }

    private fun cancelShowTimeout() {
        showTimeoutToken = null
    }

    fun onGateAttached() {
        updateDebug { it.copy(gate = "Gate: attached") }
        Log.i(TAG, "Ad gate attached to UI")
    }

    fun onGateRequested() {
        updateDebug { it.copy(gate = "Gate: requested") }
        Log.i(TAG, "Ad gate requested by download")
    }

    fun onBannerLoading() {
        updateDebug { it.copy(banner = "Banner: loading") }
        Log.i(TAG, "Loading banner $BANNER_PLACEMENT")
    }

    fun onBannerLoaded() {
        updateDebug { it.copy(banner = "Banner: loaded") }
        Log.i(TAG, "Banner loaded")
    }

    fun onBannerShown() {
        updateDebug { it.copy(banner = "Banner: shown") }
        Log.i(TAG, "Banner shown")
    }

    fun onBannerFailed(errorCode: String, message: String) {
        updateDebug {
            it.copy(banner = "Banner: FAILED $errorCode", lastError = "banner $errorCode: $message")
        }
        Log.e(TAG, "Banner load failed: $errorCode — $message")
    }

    private fun resolveActivity(context: Context): Activity? {
        val current = activityRef?.get()
        if (current != null && !current.isFinishing && !current.isDestroyed) return current
        val fromContext = context as? Activity
        return if (fromContext != null && !fromContext.isFinishing && !fromContext.isDestroyed) {
            fromContext
        } else {
            null
        }
    }
}
