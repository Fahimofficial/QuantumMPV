/*
 * SPDX-License-Identifier: CC-BY-NC-4.0
 *
 * This work is licensed under Creative Commons Attribution-NonCommercial 4.0 International License.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc/4.0/
 */

package com.quantummpv.app.di

import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.AiPreferences
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.AudioPreferences
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.DecoderPreferences
import com.quantummpv.app.preferences.FoldersPreferences
import com.quantummpv.app.preferences.GesturePreferences
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.preferences.SecureFolderPreferences
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
    singleOf(::AiPreferences)
    singleOf(::YtdlPreferences)
    singleOf(::SettingsManager)
    singleOf(::SecureFolderPreferences)
  }
