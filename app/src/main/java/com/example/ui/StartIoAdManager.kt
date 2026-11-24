package com.example.ui

import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import android.app.Activity
import android.content.*
import android.net.*
import android.os.*
import com.startapp.sdk.adsbase.*
object StartIoAdManager {
fun isOnline(c: Context): Boolean { val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val n = cm.activeNetwork ?: return false; return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
fun findAct(c: Context): Activity? { var x = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }
fun showRewardedThenDownload(c: Context, onAct: () -> Unit) { val a = findAct(c) ?: return onAct(); val ad = StartAppAd(a); val h = Handler(Looper.getMainLooper()); var d = false; val to = Runnable { if (!d) { d = true; onAct() } }; fun fin() { if (!d) { d = true; h.removeCallbacks(to); onAct() } }; h.postDelayed(to, 10000L); ad.setAdRewardedListener(AdRewardedListener {}); ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener { override fun onReceiveAd(a2: Ad) { if (d) return; h.removeCallbacks(to); ad.showAd(object : AdDisplayListener { override fun adHidden(a3: Ad) = fin(); override fun adDisplayed(a3: Ad) {}; override fun adClicked(a3: Ad) {}; override fun adNotDisplayed(a3: Ad) = fin() }) }; override fun onFailedToReceiveAd(a2: Ad?) = fin() }) }
}
