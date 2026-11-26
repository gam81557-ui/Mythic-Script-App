package com.example.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.lang.ref.WeakReference

/**
 * Owns the Start.io rewarded/interstitial ad used as the download gate.
 *
 * A single [StartAppAd] instance is kept at a time: one load is in flight or one ad is ready or
 * one ad is on screen. A download requested while a load is in flight reuses that load instead of
 * starting a second request, and [onDownload] runs exactly once per accepted request.
 */
object StartIoAdManager {
    const val APP_ID = "210526324"

    private const val TAG = "StartIoAdManager"
    private const val LOAD_TIMEOUT_MS = 12_000L

    // Start.io test ads are disabled for release builds. Set to true only in a local QA build;
    // shipping with test ads shows the "Test Ad" label and earns no revenue.
    private const val TEST_ADS_ENABLED = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var initialized = false
    private var currentAd: StartAppAd? = null
    private var loading = false
    private var showing = false
    private var generation = 0
    private var pendingDownload: (() -> Unit)? = null
    private var loadTimeoutToken: Any? = null

    fun init(activity: Activity) {
        activityRef = WeakReference(activity)
        if (initialized) return
        initialized = true
        StartAppSDK.setTestAdsEnabled(TEST_ADS_ENABLED)
        StartAppSDK.init(activity.applicationContext, APP_ID)
    }

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun preload(context: Context) {
        val activity = resolveActivity(context) ?: return
        if (loading || showing || currentAd?.isReady() == true) return
        load(activity)
    }

    /**
     * Shows a rewarded/interstitial ad and runs [onDownload] once the ad is closed or could not be
     * shown. Returns false when another ad flow is already in progress, in which case nothing is
     * scheduled; the caller can inform the user instead of silently losing the request.
     */
    fun showRewardedThenDownload(context: Context, onDownload: () -> Unit): Boolean {
        val activity = resolveActivity(context)
        if (activity == null) {
            onDownload()
            return true
        }
        if (showing || pendingDownload != null) return false

        pendingDownload = onDownload
        val ad = currentAd
        when {
            ad != null && ad.isReady() -> showAd(activity, ad)
            loading -> Unit // The in-flight load will show the ad when it completes.
            else -> load(activity)
        }
        return true
    }

    fun release() {
        generation++
        activityRef = null
        initialized = false
        mainHandler.removeCallbacksAndMessages(null)
        loadTimeoutToken = null
        pendingDownload = null
        showing = false
        loading = false
        currentAd = null
    }

    private fun load(activity: Activity) {
        val loadGeneration = ++generation
        val ad = StartAppAd(activity.applicationContext)
        currentAd = ad
        loading = true
        scheduleLoadTimeout(loadGeneration)

        ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(received: Ad) {
                if (loadGeneration != generation) return
                loading = false
                cancelLoadTimeout()
                if (pendingDownload == null) return

                val host = resolveActivity(activity)
                if (host == null) {
                    completePendingDownload()
                } else {
                    showAd(host, ad)
                }
            }

            override fun onFailedToReceiveAd(received: Ad?) {
                if (loadGeneration != generation) return
                loading = false
                currentAd = null
                cancelLoadTimeout()
                Log.w(TAG, "Ad load failed, continuing with download: ${received?.errorMessage}")
                completePendingDownload()
            }
        })
    }

    private fun showAd(activity: Activity, ad: StartAppAd) {
        cancelLoadTimeout()
        showing = true
        val showGeneration = generation
        var finished = false

        fun finishOnce() {
            if (finished || showGeneration != generation) return
            finished = true
            finishAdFlow(activity)
        }

        val displayed = ad.showAd(object : AdDisplayListener {
            override fun adDisplayed(ad: Ad) = Unit

            override fun adClicked(ad: Ad) = Unit

            override fun adHidden(ad: Ad) {
                finishOnce()
            }

            override fun adNotDisplayed(ad: Ad) {
                Log.w(TAG, "Ad not displayed, continuing with download")
                finishOnce()
            }
        })

        if (!displayed) {
            Log.w(TAG, "showAd() returned false, continuing with download")
            finishOnce()
        }
    }

    private fun finishAdFlow(activity: Activity) {
        showing = false
        currentAd = null
        completePendingDownload()
        preload(activity)
    }

    private fun completePendingDownload() {
        cancelLoadTimeout()
        val action = pendingDownload ?: return
        pendingDownload = null
        action()
    }

    private fun scheduleLoadTimeout(loadGeneration: Int) {
        cancelLoadTimeout()
        val token = Any()
        loadTimeoutToken = token
        mainHandler.postDelayed({
            if (loadTimeoutToken !== token || loadGeneration != generation) return@postDelayed
            loadTimeoutToken = null
            loading = false
            currentAd = null
            generation++ // Invalidate a load that outlives the timeout.
            Log.w(TAG, "Ad load timed out, continuing with download")
            completePendingDownload()
        }, LOAD_TIMEOUT_MS)
    }

    private fun cancelLoadTimeout() {
        loadTimeoutToken = null
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
