-keep class com.chaquo.python.** { *; }
-keep class pyatv.** { *; }

# Keep seluruh app
-keep class com.billingps.aptv.** { *; }

# Keep Kotlin data classes for serialization
-keepclassmembers class com.billingps.aptv.models.** { *; }
-keepclassmembers class com.billingps.aptv.utils.** { *; }
-keepclassmembers class com.billingps.aptv.cloud.** { *; }
-keepclassmembers class com.billingps.aptv.ui.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.firebase.** { *; }

# Keep JavaMail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }

# Keep ECDSA
-keep class java.security.** { *; }
-keep class java.security.spec.** { *; }

# Keep Python
-keep class org.python.** { *; }

# Keep AndroidX Lifecycle (untuk ViewModel)
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }
