package com.example.ui
import android.app.Activity
import android.content.*
import android.net.*
import android.os.*
import com.startapp.sdk.adsbase.*
import com.startapp.sdk.adsbase.adlisteners.*
object StartIoAdManager {
    private var preloadedAd : StartAppAd? = null
    private var isReady = false
    fun isOnline(c: Context): Boolean { val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val n = cm.activeNetwork ?: return false; return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
    fun findAct(c: Context): Activity? { var x = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }
    fun preload(c: Context) { val a = findAct(c) ?: return; val ad = StartAppAd(a); ad.setVideoListener(VideoListener {}); ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener { override fun onReceiveAd(a2: Ad) { preloadedAd = ad; isReady = true }; override fun onFailedToReceiveAd(a2: Ad) { isReady = false } }) }
    fun showRewardedThenDownload(c: Context, onAct: () -> Unit) { val a = findAct(c) ?: return onAct(); val h = Handler(Looper.getMainLooper()); var d = false; fun fin() { if (!d) { d = true; h.removeCallbacksAndMessages(null); onAct(); preload(a) } }; if (isReady && preloadedAd != null) { val adToShow = preloadedAd!!; preloadedAd = null; isReady = false; adToShow.setVideoListener(VideoListener {}); adToShow.showAd(object : AdDisplayListener { override fun adHidden(a3: Ad) = fin(); override fun adDisplayed(a3: Ad) {}; override fun adClicked(a3: Ad) {}; override fun adNotDisplayed(a3: Ad) = fin() }); return }; val to = Runnable { fin() }; h.postDelayed(to, 8000L); val ad = StartAppAd(a); ad.setVideoListener(VideoListener {}); ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener { override fun onReceiveAd(a2: Ad) { if (d) return; h.removeCallbacks(to); ad.showAd(object : AdDisplayListener { override fun adHidden(a3: Ad) = fin(); override fun adDisplayed(a3: Ad) {}; override fun adClicked(a3: Ad) {}; override fun adNotDisplayed(a3: Ad) = fin() }) }; override fun onFailedToReceiveAd(a2: Ad) { fin() } }) }
}