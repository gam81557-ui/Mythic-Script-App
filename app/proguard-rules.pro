# Project-specific R8/ProGuard rules.

# Unity Ads 4.22 ships consumer rules for its public API and protobuf reflection.
# Keep the SDK and its service layer intact for release builds.
-dontwarn com.unity3d.**
-keep class com.unity3d.** { *; }
-keep interface com.unity3d.** { *; }

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

# Shizuku loads FileBridgeUserService by name in a separate process. Keep both the
# no-arg and Context constructors because Shizuku selects one reflectively.
-keep class com.example.shizuku.FileBridgeUserService { *; }
-keepclassmembers class com.example.shizuku.FileBridgeUserService {
    public <init>();
    public <init>(android.content.Context);
}
-keep interface com.example.shizuku.IFileBridgeService { *; }
-keep class com.example.shizuku.IFileBridgeService$Stub { *; }
-keep class com.example.shizuku.IFileBridgeService$Stub$Proxy { *; }
-keep class com.example.shizuku.LocalFileBridgeService { *; }
-keep class com.example.shizuku.ShellFileBridgeService { *; }

# The compatibility bridge calls Shizuku's process AIDL directly.
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn moe.shizuku.**
-dontwarn rikka.shizuku.**
