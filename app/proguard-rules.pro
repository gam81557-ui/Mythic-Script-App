# Project-specific R8/ProGuard rules.

# Unity Ads ships its own consumer ProGuard rules inside the AAR (proguard.txt), including
# keeps for com.unity3d.ads.**, com.unity3d.services.**, and the WebView JavaScript bridge.
-dontwarn com.unity3d.**

# Attributes required by the SDK (serialization, annotations, WebView JS bridges).
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# App classes accessed through reflection (Room/KSP generated code, JSON models).
-keep class com.example.** { *; }
