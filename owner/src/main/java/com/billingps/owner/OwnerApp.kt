package com.billingps.owner

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class OwnerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(FIREBASE_APP_ID)
                    .setApiKey(FIREBASE_API_KEY)
                    .setProjectId(FIREBASE_PROJECT_ID)
                    .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (t: Throwable) {
            Log.e("OwnerApp", "Firebase init failed: ${t.message}", t)
        }
    }

    companion object {
        private const val FIREBASE_APP_ID = "1:291349089700:android:07c7229ad3a24bbf378531"
        private const val FIREBASE_API_KEY = "AIzaSyAqOxWEBD9_AqaMiMtFwbyTvh-d_mdVofQ"
        private const val FIREBASE_PROJECT_ID = "rrbillingpro"
        private const val FIREBASE_STORAGE_BUCKET = "rrbillingpro.firebasestorage.app"
    }
}
