package com.example.eldgps

import android.app.Application
import com.google.firebase.FirebaseApp
import timber.log.Timber

class MockApplication :Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        try {
            FirebaseApp.initializeApp(this)
        }
        catch (ignored: Exception) {

        }

    }
}