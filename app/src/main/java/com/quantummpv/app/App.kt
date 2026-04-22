package com.quantummpv.app

import android.app.Application
import com.quantummpv.app.database.repository.VideoMetadataCacheRepository
import com.quantummpv.app.di.DatabaseModule
import com.quantummpv.app.di.FileManagerModule
import com.quantummpv.app.di.PreferencesModule
import com.quantummpv.app.presentation.crash.CrashActivity
import com.quantummpv.app.presentation.crash.GlobalExceptionHandler
import com.quantummpv.app.utils.media.MediaLibraryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
class App : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()

  override fun onCreate() {
    super.onCreate()

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        com.quantummpv.app.di.domainModule,
      )
    }

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }

    applicationScope.launch {
      runCatching {
        triggerMediaScanOnLaunch()
      }
    }
  }

  private fun triggerMediaScanOnLaunch() {
    try {
      val externalStorage = android.os.Environment.getExternalStorageDirectory()

      android.media.MediaScannerConnection.scanFile(
        this,
        arrayOf(externalStorage.absolutePath),
        null,
      ) { path, _ ->
        android.util.Log.d("App", "Launch media scan completed for: $path")
        MediaLibraryEvents.notifyChanged()
      }

      android.util.Log.d("App", "Triggered media scan on app launch")
    } catch (error: Exception) {
      android.util.Log.e("App", "Failed to trigger media scan on launch", error)
    }
  }
}

