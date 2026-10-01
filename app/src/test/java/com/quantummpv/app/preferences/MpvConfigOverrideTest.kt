/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.preferences

import org.junit.Assert.assertTrue
import org.junit.Test

class MpvConfigOverrideTest {
  @Test
  fun subtitleLoadingOverrideOwnsSubFontsDirectory() {
    val overriddenOptions =
      MpvConfigOverride.resolveOptionNames(setOf(MpvConfigOverride.SUBTITLE_LOADING.preferenceKey))

    assertTrue("sub-fonts-dir must remain owned by mpv.conf", "sub-fonts-dir" in overriddenOptions)
  }

  @Test
  fun subtitleStyleOverrideOwnsBidiCompatibilityOption() {
    val overriddenOptions =
      MpvConfigOverride.resolveOptionNames(setOf(MpvConfigOverride.SUBTITLE_STYLE.preferenceKey))

    assertTrue(
      "subtitle bidi compatibility must remain owned by mpv.conf",
      "sub-vsfilter-bidi-compat" in overriddenOptions,
    )
  }

  @Test
  fun subtitleStyleOverrideOwnsIndependentSecondarySubtitleControls() {
    val overriddenOptions =
      MpvConfigOverride.resolveOptionNames(setOf(MpvConfigOverride.SUBTITLE_STYLE.preferenceKey))

    assertTrue("secondary subtitle scale must remain owned by mpv.conf", "secondary-sub-scale" in overriddenOptions)
    assertTrue("secondary subtitle position must remain owned by mpv.conf", "secondary-sub-pos" in overriddenOptions)
  }

  @Test
  fun osdOverrideOwnsFontSelection() {
    val overriddenOptions = MpvConfigOverride.resolveOptionNames(setOf(MpvConfigOverride.OSD.preferenceKey))

    assertTrue("OSD font must remain owned by mpv.conf", "osd-font" in overriddenOptions)
  }
}
