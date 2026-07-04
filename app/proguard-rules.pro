# ProGuard rules for Storyboard Editor

# Keep Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }

# Keep Media3
-keep class androidx.media3.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep data classes for Gson/serialization
-keep class com.storyboard.editor.domain.model.** { *; }
-keep class com.storyboard.editor.data.model.** { *; }

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
