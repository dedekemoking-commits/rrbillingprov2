-keep class com.chaquo.python.** { *; }
-keep class pyatv.** { *; }

# Keep Kotlin data classes used for serialization
-keep class com.billingps.aptv.models.** { *; }
-keep class com.billingps.aptv.utils.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep JavaMail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep ECDSA
-keep class java.security.** { *; }

# Keep Python
-keep class org.python.** { *; }
