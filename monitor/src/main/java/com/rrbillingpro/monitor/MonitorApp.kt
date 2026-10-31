package com.rrbillingpro.monitor

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class MonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAqOxWEBD9_AqaMiMtFwbyTvh-d_mdVofQ")
                .setApplicationId("1:291349089700:android:07c7229ad3a24bbf378531")
                .setProjectId("rrbillingpro")
                .setStorageBucket("rrbillingpro.firebasestorage.app")
                .setGcmSenderId("291349089700")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
