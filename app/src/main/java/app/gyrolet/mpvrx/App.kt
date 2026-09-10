/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.fahim.quantummpv

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fahim.quantummpv.database.repository.VideoMetadataCacheRepository
import com.fahim.quantummpv.di.DatabaseModule
import com.fahim.quantummpv.di.FileManagerModule
import com.fahim.quantummpv.di.PreferencesModule
import com.fahim.quantummpv.preferences.AudioPreferences
import com.fahim.quantummpv.preferences.DecoderPreferences
import com.fahim.quantummpv.preferences.PlayerPreferences
import com.fahim.quantummpv.presentation.crash.CrashActivity
import com.fahim.quantummpv.presentation.crash.GlobalExceptionHandler
import com.fahim.quantummpv.repository.NetworkRepository
import com.fahim.quantummpv.ui.player.PlaybackPerformanceTrace
import com.fahim.quantummpv.ui.player.PlaybackPhase
import com.fahim.quantummpv.ui.player.PlaybackSession
import com.fahim.quantummpv.ui.player.PlayerActivity
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(KoinExperimentalAPI::class)
class App :
  Application(),
  Application.ActivityLifecycleCallbacks {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val networkAutoConnectStarted = AtomicBoolean(false)
  private val metadataMaintenanceStarted = AtomicBoolean(false)
  private val fastThumbnailsStarted = AtomicBoolean(false)
  private var startedActivityCount = 0

  companion object {
    private const val TAG = "App"
    private const val POST_START_MAINTENANCE_DELAY_MS = 10_000L
    private const val THUMBNAIL_WARMUP_DELAY_MS = 1_500L
    private const val IDLE_MPV_CORE_GRACE_MS = 3L * 60L * 1000L
  }

  override fun onCreate() {
    super.onCreate()
    configureDebugStrictMode()

    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        com.fahim.quantummpv.di.domainModule,
        com.fahim.quantummpv.di.DownloadModule,
      )
    }
    if (!BuildConfig.MPV_SUPPORTS_VULKAN) {
      getKoin().get<DecoderPreferences>().useVulkan.set(false)
    }
    registerActivityLifecycleCallbacks(this)
    PlaybackSession.addObserver(PlaybackPerformanceTrace)
    startPlaybackPerformanceTracing()
    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
    startIdleMpvCoreReaper()

    applicationScope.launch {
      runCatching {
        val preferences: PlayerPreferences = getKoin().get()
        val enableMediaInfo = preferences.enableMediaInfoIntent.get()
        val componentName = ComponentName(this@App, "com.fahim.quantummpv.ui.mediainfo.MediaInfoActivityAlias")
        val newState =
          if (enableMediaInfo) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
          else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
      }.onFailure { error -> Log.e(TAG, "Failed to initialize MediaInfoActivityAlias setting on launch", error) }

      runCatching {
        val preferences: PlayerPreferences = getKoin().get()
        val enableWebLinks = preferences.enableWebStreamLinkIntents.get()
        val componentName = ComponentName(this@App, "com.fahim.quantummpv.ui.player.WebStreamLinksActivityAlias")
        val newState =
          if (enableWebLinks) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
          else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
      }.onFailure { error -> Log.e(TAG, "Failed to initialize WebStreamLinksActivityAlias setting on launch", error) }
    }
  }

  override fun onActivityStarted(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.mark("PLAYER_ACTIVITY_STARTED")
    if (startedActivityCount++ == 0) {
      getKoin().get<com.fahim.quantummpv.domain.syncplay.SyncplayManager>().onAppForegrounded()
      scheduleFastThumbnailWarmupOnce()
      scheduleMetadataMaintenanceOnce()
    }
  }

  override fun onActivityStopped(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.mark("PLAYER_ACTIVITY_STOPPED")
    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
    if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
      pauseVideoWhenBackgroundPlaybackDisabled(activity)
      getKoin().get<com.fahim.quantummpv.domain.syncplay.SyncplayManager>().onAppBackgrounded()
    }
  }

  private fun pauseVideoWhenBackgroundPlaybackDisabled(activity: Activity) {
    val playerActivity = activity as? PlayerActivity ?: return
    if (playerActivity.isCurrentMediaKnownAudio()) return
    val isInPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerActivity.isInPictureInPictureMode
    if (isInPictureInPicture) return
    if (getKoin().get<AudioPreferences>().backgroundPlayback.get()) return
    val state = PlaybackSession.state.value
    if (state.currentItem == null || state.phase == PlaybackPhase.IDLE || state.phase == PlaybackPhase.UNINITIALIZED) return
    PlaybackSession.setPropertyBoolean("pause", true)
    playerActivity.abandonAudioFocus()
    Log.d(TAG, "Paused video because video background playback is disabled")
  }

  override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.mark("PLAYER_ACTIVITY_CREATE_START")
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.mark("PLAYER_ACTIVITY_CREATE_END")
    if (activity.javaClass.name.contains("leakcanary", ignoreCase = true)) {
      val rootView = activity.findViewById<View>(android.R.id.content)
      rootView?.let { view ->
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
          val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
          val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
          v.setPadding(v.paddingLeft, statusBarInsets.top, v.paddingRight, navBarInsets.bottom)
          insets
        }
      }
    }
  }

  override fun onActivityResumed(activity: Activity) {
    when (activity) {
      is PlayerActivity -> {
        PlaybackPerformanceTrace.mark("PLAYER_ACTIVITY_RESUMED")
        activity.window.decorView.postOnAnimation { PlaybackPerformanceTrace.mark("PLAYER_FIRST_FRAME") }
      }
      is MainActivity -> {
        PlaybackPerformanceTrace.mark("MAIN_ACTIVITY_RESUMED")
        activity.window.decorView.postOnAnimation { PlaybackPerformanceTrace.mark("BROWSER_FIRST_FRAME") }
      }
    }
  }

  override fun onActivityPrePaused(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.begin("ON_PAUSE")
  }

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivityPostPaused(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.end("ON_PAUSE")
  }

  override fun onActivityPreStopped(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.begin("ON_STOP")
  }

  override fun onActivityPostStopped(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.end("ON_STOP")
  }

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityPreDestroyed(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.begin("ON_DESTROY")
  }

  override fun onActivityDestroyed(activity: Activity) = Unit

  override fun onActivityPostDestroyed(activity: Activity) {
    if (activity is PlayerActivity) PlaybackPerformanceTrace.end("ON_DESTROY")
  }

  private fun startIdleMpvCoreReaper() {
    applicationScope.launch {
      PlaybackSession.state.collectLatest { state ->
        val isFullyIdle = state.phase == PlaybackPhase.IDLE && state.currentItem == null && !state.surfaceAttached && PlaybackSession.isInitialized
        if (!isFullyIdle) return@collectLatest
        delay(IDLE_MPV_CORE_GRACE_MS)
        val latest = PlaybackSession.state.value
        val stillFullyIdle = latest.phase == PlaybackPhase.IDLE && latest.currentItem == null && !latest.surfaceAttached && PlaybackSession.isInitialized
        if (stillFullyIdle) {
          Log.d(TAG, "Destroying libmpv after idle grace period")
          PlaybackSession.destroy()
        }
      }
    }
  }

  private fun startPlaybackPerformanceTracing() {
    applicationScope.launch {
      var previousPhase: PlaybackPhase? = null
      var previousSurfaceAttached: Boolean? = null
      var previousGeneration = -1L
      PlaybackSession.state.collect { state ->
        if (state.phase != previousPhase) {
          PlaybackPerformanceTrace.mark("SESSION_PHASE", state.phase.name)
          previousPhase = state.phase
        }
        if (state.surfaceAttached != previousSurfaceAttached) {
          PlaybackPerformanceTrace.mark(if (state.surfaceAttached) "SURFACE_BOUND" else "SURFACE_UNBOUND", "generation=${state.generation}")
          previousSurfaceAttached = state.surfaceAttached
        }
        if (state.generation != previousGeneration) {
          PlaybackPerformanceTrace.mark("SESSION_GENERATION", state.generation.toString())
          previousGeneration = state.generation
        }
      }
    }
  }

  private fun configureDebugStrictMode() {
    if (!BuildConfig.DEBUG) return
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
  }

  private fun scheduleFastThumbnailWarmupOnce() {
    if (!fastThumbnailsStarted.compareAndSet(false, true)) return
    applicationScope.launch(Dispatchers.Default) {
      try {
        delay(THUMBNAIL_WARMUP_DELAY_MS)
        FastThumbnails.initialize(this@App)
      } catch (cancellation: CancellationException) {
        fastThumbnailsStarted.set(false)
        throw cancellation
      } catch (error: Exception) {
        fastThumbnailsStarted.set(false)
        Log.w(TAG, "Deferred FastThumbnails initialization failed", error)
      }
    }
  }

  private fun scheduleMetadataMaintenanceOnce() {
    if (!metadataMaintenanceStarted.compareAndSet(false, true)) return
    applicationScope.launch(Dispatchers.IO) {
      try {
        delay(POST_START_MAINTENANCE_DELAY_MS)
        getKoin().get<VideoMetadataCacheRepository>().performMaintenance()
      } catch (cancellation: CancellationException) {
        metadataMaintenanceStarted.set(false)
        throw cancellation
      } catch (error: Exception) {
        metadataMaintenanceStarted.set(false)
        Log.w(TAG, "Deferred metadata maintenance failed", error)
      }
    }
  }

  internal fun autoConnectNetworksOnce() {
    if (!networkAutoConnectStarted.compareAndSet(false, true)) return
    applicationScope.launch {
      try {
        delay(500)
        getKoin().get<NetworkRepository>().getAutoConnectConnections().forEach { connection ->
          Log.d(TAG, "Auto-connecting to network share: ${connection.name}")
          getKoin().get<NetworkRepository>().connect(connection).onFailure { error ->
            Log.e(TAG, "Auto-connect failed for ${connection.name}: ${error.message}")
          }
        }
      } catch (cancellation: CancellationException) {
        networkAutoConnectStarted.set(false)
        throw cancellation
      } catch (error: Exception) {
        networkAutoConnectStarted.set(false)
        Log.e(TAG, "Failed to auto-connect saved network shares", error)
      }
    }
  }

  private fun getKoin() = GlobalContext.get()
}
