package com.example.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private const val TEST_MODE = false
    private const val LOAD_TIMEOUT_MS = 12_000L
    private const val SHOW_TIMEOUT_MS = 65_000L

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var appContext: Context? = null
    private var initializing = false
    private var preloadRequested = false

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

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

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
            onDownload()
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
        appContext = null
        loadGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        loadTimeoutToken = null
        pendingTimeoutToken = null
        showTimeoutToken = null
        pendingDownload = null
        showing = false
        loadingPlacement = null
        rewardedReady = false
        interstitialReady = false
    }

    private fun ensureInitialized(context: Context) {
        if (UnityAds.isInitialized) {
            _isInitialized.value = true
            return
        }
        if (initializing) return

        initializing = true
        UnityAds.debugMode = false
        UnityAds.initialize(context, GAME_ID, TEST_MODE, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                initializing = false
                _isInitialized.value = true
                Log.d(TAG, "Unity Ads ${UnityAds.version} initialized")
                val context = appContext
                if (context != null && preloadRequested) {
                    preloadRequested = false
                    preload(context)
                }
            }

            override fun onInitializationFailed(
                error: UnityAds.UnityAdsInitializationError,
                message: String,
            ) {
                initializing = false
                _isInitialized.value = false
                Log.e(TAG, "Unity Ads initialization failed: $error $message")
                completePendingDownload()
            }
        })
    }

    private fun loadRewarded() {
        val generation = ++loadGeneration
        loadingPlacement = REWARDED_PLACEMENT
        scheduleLoadTimeout(generation)
        Log.d(TAG, "Loading rewarded ad")

        UnityAds.load(REWARDED_PLACEMENT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                if (generation != loadGeneration) return
                loadingPlacement = null
                rewardedReady = true
                cancelLoadTimeout()
                Log.d(TAG, "Rewarded ad loaded")
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
                Log.w(TAG, "Rewarded load failed: $error $message; trying interstitial")
                loadInterstitial()
            }
        })
    }

    private fun loadInterstitial() {
        val generation = ++loadGeneration
        loadingPlacement = INTERSTITIAL_PLACEMENT
        scheduleLoadTimeout(generation)
        Log.d(TAG, "Loading interstitial ad")

        UnityAds.load(INTERSTITIAL_PLACEMENT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                if (generation != loadGeneration) return
                loadingPlacement = null
                interstitialReady = true
                cancelLoadTimeout()
                Log.d(TAG, "Interstitial ad loaded")
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
                Log.w(TAG, "Interstitial load failed: $error $message; continuing with download")
                completePendingDownload()
            }
        })
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
                Log.w(TAG, "Ad display timed out, continuing with download")
                finishOnce()
            }
        }, SHOW_TIMEOUT_MS)

        UnityAds.show(activity, placementId, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String,
                error: UnityAds.UnityAdsShowError,
                message: String,
            ) {
                Log.w(TAG, "Ad show failed: $error $message; continuing with download")
                finishOnce()
            }

            override fun onUnityAdsShowStart(placementId: String) = Unit

            override fun onUnityAdsShowClick(placementId: String) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String,
                state: UnityAds.UnityAdsShowCompletionState,
            ) {
                Log.d(TAG, "Ad closed: $state")
                finishOnce()
            }
        })
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
        action()
    }

    private fun scheduleLoadTimeout(loadGeneration: Int) {
        cancelLoadTimeout()
        val token = Any()
        loadTimeoutToken = token
        mainHandler.postDelayed({
            if (loadTimeoutToken !== token || loadGeneration != this.loadGeneration) return@postDelayed
            loadTimeoutToken = null
            loadingPlacement = null
            rewardedReady = false
            interstitialReady = false
            this.loadGeneration++ // Invalidate a load that outlives the timeout.
            Log.w(TAG, "Ad load timed out, continuing with download")
            completePendingDownload()
        }, LOAD_TIMEOUT_MS)
    }

    private fun schedulePendingTimeout() {
        cancelPendingTimeout()
        val token = Any()
        pendingTimeoutToken = token
        mainHandler.postDelayed({
            if (pendingTimeoutToken !== token) return@postDelayed
            pendingTimeoutToken = null
            Log.w(TAG, "Ad did not become ready in time, continuing with download")
            completePendingDownload()
        }, LOAD_TIMEOUT_MS)
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
