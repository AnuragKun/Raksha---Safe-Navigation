package com.arlabs.raksha

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RakshaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
            
            if (!com.google.android.libraries.places.api.Places.isInitialized() && !apiKey.isNullOrEmpty()) {
                com.google.android.libraries.places.api.Places.initialize(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            // Handle exception (e.g., NameNotFoundException)
             e.printStackTrace()
        }
    }
}