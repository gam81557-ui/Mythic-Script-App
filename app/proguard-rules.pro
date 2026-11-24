# Project-specific R8/ProGuard rules.

# Start.io SDK: ads, listeners and activities are instantiated reflectively, so the whole
# package has to survive shrinking and obfuscation in release builds.
-keep class com.startapp.** { *; }
-keep interface com.startapp.** { *; }
-keepclassmembers class com.startapp.** { *; }
-keep class * extends com.startapp.sdk.adsbase.Ad { *; }
-keep class * implements com.startapp.sdk.adsbase.adlisteners.AdEventListener { *; }
-keep class * implements com.startapp.sdk.adsbase.adlisteners.AdDisplayListener { *; }
-keep class * implements com.startapp.sdk.ads.banner.BannerListener { *; }
-dontwarn com.startapp.**

# Attributes required by the SDK (serialization, annotations, WebView JS bridges).
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# App classes accessed through reflection (Room/KSP generated code, JSON models).
-keep class com.example.** { *; }
