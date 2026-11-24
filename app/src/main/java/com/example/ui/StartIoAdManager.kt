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
 * One [StartAppAd] is preloaded at startup and re-preloaded after every impression.
 */
object StartIoAdManager {
    const val APP_ID = "210526324"

    private const val TAG = "StartIoAdManager"
    private const val LOAD_TIMEOUT_MS = 12_000L

    // Start.io test ads show instantly with the "Test Ad" label and must be turned off
    // before publishing to production.
    private const val TEST_ADS_ENABLED = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var interstitial: StartAppAd? = null
    private var isLoading = false
    private var isShowing = false
    private var loadGeneration = 0
    private var pendingDownload: (() -> Unit)? = null
    private var loadTimeoutToken: Any? = null

    fun init(activity: Activity) {
        activityRef = WeakReference(activity)
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
        if (isLoading || interstitial?.isReady == true) return

        val generation = ++loadGeneration
        val ad = StartAppAd(activity.applicationContext)
        interstitial = ad
        isLoading = true
        ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                if (generation != loadGeneration) return
                isLoading = false
                Log.d(TAG, "Ad preloaded and ready")
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                if (generation != loadGeneration) return
                isLoading = false
                Log.w(TAG, "Ad preload failed: ${ad?.errorMessage}")
            }
        })
    }

    /**
     * Shows a rewarded/interstitial ad and runs [onDownload] once the ad is closed or could
     * not be shown. The download is never blocked indefinitely.
     */
    fun showRewardedThenDownload(context: Context, onDownload: () -> Unit) {
        val activity = resolveActivity(context)
        if (activity == null) {
            onDownload()
            return
        }
        // Ignore repeated taps while an ad flow is already running.
        if (isShowing || pendingDownload != null) return

        pendingDownload = onDownload
        val ad = interstitial
        if (ad != null && ad.isReady) {
            showAd(activity, ad)
        } else {
            loadAndShow(activity)
        }
    }

    fun release() {
        activityRef = null
        mainHandler.removeCallbacksAndMessages(null)
        loadTimeoutToken = null
        pendingDownload = null
        isShowing = false
        isLoading = false
        interstitial = null
    }

    private fun loadAndShow(activity: Activity) {
        val generation = ++loadGeneration
        val ad = StartAppAd(activity.applicationContext)
        interstitial = ad
        isLoading = true
        scheduleLoadTimeout()

        ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(received: Ad) {
                if (generation != loadGeneration) return
                isLoading = false
                if (pendingDownload == null) return
                cancelLoadTimeout()
                showAd(activity, ad)
            }

            override fun onFailedToReceiveAd(received: Ad?) {
                if (generation != loadGeneration) return
                isLoading = false
                Log.w(TAG, "Ad load failed, continuing with download: ${received?.errorMessage}")
                completePendingDownload()
            }
        })
    }

    private fun showAd(activity: Activity, ad: StartAppAd) {
        cancelLoadTimeout()
        isShowing = true

        val displayed = ad.showAd(object : AdDisplayListener {
            override fun adDisplayed(ad: Ad) = Unit

            override fun adClicked(ad: Ad) = Unit

            override fun adHidden(ad: Ad) {
                finishAdFlow(activity)
            }

            override fun adNotDisplayed(ad: Ad) {
                Log.w(TAG, "Ad not displayed, continuing with download")
                finishAdFlow(activity)
            }
        })

        if (!displayed) {
            Log.w(TAG, "showAd() returned false, continuing with download")
            finishAdFlow(activity)
        }
    }

    private fun finishAdFlow(activity: Activity) {
        isShowing = false
        interstitial = null
        completePendingDownload()
        preload(activity)
    }

    private fun completePendingDownload() {
        cancelLoadTimeout()
        val action = pendingDownload ?: return
        pendingDownload = null
        action()
    }

    private fun scheduleLoadTimeout() {
        cancelLoadTimeout()
        val token = Any()
        loadTimeoutToken = token
        mainHandler.postDelayed({
            if (loadTimeoutToken === token) {
                loadTimeoutToken = null
                Log.w(TAG, "Ad load timed out, continuing with download")
                completePendingDownload()
            }
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
