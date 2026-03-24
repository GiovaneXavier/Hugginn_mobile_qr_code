# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep data models for JSON serialization
-keep class com.srbr.huginn.core.security.HuginnCard { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }
