package com.quantummpv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline and Startup Profiles:
 * `./gradlew :app:generateStandardReleaseBaselineProfile` with a device connected (put a few videos
 * on it first). Output lands in app/src/standardRelease/generated/baselineProfiles/ and is used by
 * AGP automatically; commit it.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule
  val rule = BaselineProfileRule()

  @Test
  fun generate() {
    grantMediaAccess()
    rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
      // Startup profile covers the launch only, so it stays small enough for classes.dex.
      pressHome()
      startActivityAndWait()
      waitForHome()
    }
    rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
      pressHome()
      startActivityAndWait()
      waitForHome()
      scrollHome()
      if (openFirstFolder()) {
        selectionMode()
        openPlayerAndBack()
      }
    }
  }
}
