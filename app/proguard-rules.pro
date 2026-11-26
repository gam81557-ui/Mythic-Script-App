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

# Unity Ads 4.22 ships consumer rules for its public API and protobuf reflection.
-dontwarn com.unity3d.**

# UnityAdManager reads these by name for init diagnostics and the manual retry path.
-keep class com.unity3d.services.core.properties.SdkProperties { *; }
-keep class com.unity3d.services.core.properties.ClientProperties { *; }
-keep class com.unity3d.services.core.misc.Utilities { *; }
-keep interface com.unity3d.ads.core.data.repository.SessionRepository { *; }
-keep class com.unity3d.ads.core.data.model.InitializationState { *; }

# Attributes required by the SDK (serialization, annotations, WebView JS bridges).
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# App classes accessed through reflection (Room/KSP generated code, JSON models).
-keep class com.example.** { *; }

# Shizuku starts FileBridgeUserService by class name in a separate process.
-keep class com.example.shizuku.FileBridgeUserService { *; }
-keep class com.example.shizuku.IFileBridgeService$Stub { *; }

# The shell compatibility bridge calls Shizuku's process AIDL directly.
-keep class moe.shizuku.server.** { *; }
-keep class rikka.shizuku.** { *; }
