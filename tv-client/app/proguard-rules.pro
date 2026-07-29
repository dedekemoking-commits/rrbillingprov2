-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes Signature
-keepattributes *Annotation*

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.billingps.tvclient.firestore.** { *; }
