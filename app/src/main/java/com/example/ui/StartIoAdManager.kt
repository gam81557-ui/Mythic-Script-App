package com.example.ui
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.startapp.sdk.adsbase.StartAppAd
import com.example.MainActivity

object StartIoAdManager {
    fun isOnline(c: Context) = true
        fun preload(c: Context) {}
            fun showRewardedThenDownload(c: Context, onAct: () -> Unit) {
                    val a = MainActivity.instance ?: (c as? Activity)
                            if (a != null) {
                                        Toast.makeText(a, "Showing Ad...", Toast.LENGTH_SHORT).show()
                                                    StartAppAd.showAd(a)
                                                                Handler(Looper.getMainLooper()).postDelayed({ onAct() }, 2500L)
                                                                        } else onAct()
                                                                            }
                                                                            }
                                                                            
