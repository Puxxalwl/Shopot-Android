# Stille-Messenger — ProGuard rules

# Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Hilt/Dagger
-keep class com.stille.messenger.di.** { *; }
-keep class * extends androidx.hilt.lifecycle.ViewModelAssistedFactory
-keep class * extends androidx.lifecycle.AbstractSavedStateViewModel

# Keep native library
-keep class com.stille.coreapi.** { *; }

# Enums
-keepclassmembers enum com.stille.coreapi.** {
    public static **[] values();
    public static *** valueOf(java.lang.String);
}

# Don't obfuscate the binary protocol
-assumenosideeffects class com.stille.coreapi.StilleProtocol {
    public static byte[] encodeFrame(...);
    public static kotlin.Pair decodeFrame(byte[]);
}
