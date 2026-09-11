/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.di

import com.quantummpv.app.domain.download.AppDownloadManager
import com.quantummpv.app.domain.download.DownloadLocations
import com.quantummpv.app.domain.download.LinkDownloadCoordinator
import com.quantummpv.app.domain.download.YtdlpDownloadEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val DownloadModule =
  module {
    single { DownloadLocations(androidContext(), get()) }
    single { AppDownloadManager(androidContext(), get<com.quantummpv.app.database.MpvRxDatabase>().downloadItemDao(), get()) }
    single { YtdlpDownloadEngine(androidContext(), get()) }
    single { LinkDownloadCoordinator(get(), get()) }
  }
