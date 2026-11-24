package com.example.ui
import android.app.Activity
import android.content.*
import android.net.*
import android.os.*
import com.startapp.sdk.adsbase.*
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
object StartIoAdManager {
    private var cachedAd : StartAppAd? = null
    private var isCachedReady = false
    fun isOnline(c: Context): Boolean { val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val n = cm.activeNetwork ?: return false; return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
    fun findAct(c: Context): Activity? { var x = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }
    fun preload(c: Context) { val a = findAct(c) ?: return; val ad = StartAppAd(a); ad.setVideoListener(VideoListener {}); ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener { override fun onReceiveAd(a2: Ad) { cachedAd = ad; isCachedReady = true }; override fun onFailedToReceiveAd(a2: Ad?) { isCachedReady = false } }) }
    fun showRewardedThenDownload(c: Context, onAct: () -> Unit) { val a = findAct(c) ?: return onAct(); val h = Handler(Looper.getMainLooper()); var d = false; fun fin() { if (!d) { d = true; onAct(); preload(a) } }; if (isCachedReady && cachedAd != null) { val ad = cachedAd!!; cachedAd = null; isCachedReady = false; ad.showAd(bindList( {fin()} )); return }; val to = Runnable { fin() }; h.postDelayed(to, 8000L); val ad = StartAppAd(a); ad.setVideoListener(VideoListener {}); ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener { override fun onReceiveAd(a2: Ad) { if (d) return; h.removeCallbacks(to); ad.showAd(bindList( z{fin()} )); }; override fun onFailedToReceiveAd(a2: Ad?) { android.widget.Toast.makeText(a, "Ad No-Fill: " + a2?.errorMessage, android.widget.Toast.LENGTH_LONG).show(); fin() } }) }
    private fun bindList(cb: () -> Unit): AdDisplayListener = object : AdDisplayListener { override fun adHidden(a3: Ad) = cb(); override fun adDisplayed(a3: Ad) {}; override fun adClicked(a3: Ad) {}; override fun adNotDisplayed(a3: Ad) = cb() }
}