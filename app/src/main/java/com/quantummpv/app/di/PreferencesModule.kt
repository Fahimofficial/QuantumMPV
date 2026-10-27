/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.di

import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.AiPreferences
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.AudioPreferences
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.DecoderPreferences
import com.quantummpv.app.preferences.DownloadPreferences
import com.quantummpv.app.preferences.FoldersPreferences
import com.quantummpv.app.preferences.GesturePreferences
import com.quantummpv.app.preferences.MediaServerPreferences
import com.quantummpv.app.preferences.NetworkBookmarkPreferences
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.preferences.SecureFolderPreferences
import com.quantummpv.app.preferences.SeerrPreferences
import com.quantummpv.app.preferences.SettingsManager
import com.quantummpv.app.preferences.SubtitlesPreferences
import com.quantummpv.app.preferences.YtdlPreferences
import com.quantummpv.app.preferences.preference.AndroidPreferenceStore
import com.quantummpv.app.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    single { BrowserPreferences(get(), androidContext()) }
    singleOf(::FoldersPreferences)
    singleOf(::NetworkBookmarkPreferences)
    singleOf(::AiPreferences)
    singleOf(::YtdlPreferences)
    single {
      SettingsManager(
        context = androidContext(),
        preferenceStore = get(),
        database = get(),
        loadPreferenceDeclarations = {
          // Resolving each holder registers its keys and types with the store, which is what
          // settings import validates against.
          get<AppearancePreferences>()
          get<PlayerPreferences>()
          get<GesturePreferences>()
          get<DecoderPreferences>()
          get<SubtitlesPreferences>()
          get<AudioPreferences>()
          get<AdvancedPreferences>()
          get<BrowserPreferences>()
          get<FoldersPreferences>()
          get<NetworkBookmarkPreferences>()
          get<AiPreferences>()
          get<YtdlPreferences>()
          get<SecureFolderPreferences>()
          get<SeerrPreferences>()
          get<MediaServerPreferences>()
          get<DownloadPreferences>()
        },
      )
    }
    single {
      // A separate file keeps the PIN state out of backups and out of reach of settings import.
      val context = androidContext()
      SecureFolderPreferences(
        preferenceStore =
          AndroidPreferenceStore(
            context,
            context.getSharedPreferences(SecureFolderPreferences.FILE_NAME, android.content.Context.MODE_PRIVATE),
          ),
        legacyStore = get(),
      )
    }
    singleOf(::SeerrPreferences)
    singleOf(::MediaServerPreferences)
    singleOf(::DownloadPreferences)
  }
