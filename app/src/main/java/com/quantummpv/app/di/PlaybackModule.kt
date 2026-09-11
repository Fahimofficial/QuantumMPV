/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.di

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.quantummpv.app.domain.playback.Media3PlaybackEngine
import com.quantummpv.app.domain.playback.PlaybackEnginePreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playbackModule =
  module {
    single {
      PlaybackEnginePreferences(
        androidContext().getSharedPreferences("playback_engine", MODE_PRIVATE),
      )
    }

    factory { (context: Context) -> Media3PlaybackEngine(context) }
  }
