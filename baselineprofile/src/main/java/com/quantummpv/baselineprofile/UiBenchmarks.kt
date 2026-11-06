package com.quantummpv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-build measurements for the motion work: `./gradlew :baselineprofile:connectedStandardBenchmarkReleaseAndroidTest`.
 * Each journey runs with and without the Baseline Profile so before/after is visible in one run.
 * Compare frameDurationCpuMs P50/P90/P99 and frameOverrunMs (jank) between runs.
 */
@RunWith(AndroidJUnit4::class)
class UiBenchmarks {
  @get:Rule
  val rule = MacrobenchmarkRule()

  @Before
  fun setUp() = grantMediaAccess()

  @Test fun startupNoProfile() = startup(CompilationMode.None())

  @Test fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

  @Test fun homeScrollNoProfile() = homeScroll(CompilationMode.None())

  @Test fun homeScrollWithProfile() = homeScroll(CompilationMode.Partial(BaselineProfileMode.Require))

  @Test fun folderOpenWithProfile() = folderOpen(CompilationMode.Partial(BaselineProfileMode.Require))

  @Test fun selectionModeWithProfile() = selection(CompilationMode.Partial(BaselineProfileMode.Require))

  private fun startup(mode: CompilationMode) =
    rule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(StartupTimingMetric()),
      compilationMode = mode,
      startupMode = StartupMode.COLD,
      iterations = 10,
      setupBlock = { pressHome() },
    ) {
      startActivityAndWait()
      waitForHome()
    }

  private fun homeScroll(mode: CompilationMode) =
    rule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      compilationMode = mode,
      startupMode = StartupMode.WARM,
      iterations = 5,
      setupBlock = {
        pressHome()
        startActivityAndWait()
        waitForHome()
      },
    ) { scrollHome() }

  private fun folderOpen(mode: CompilationMode) =
    rule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      compilationMode = mode,
      startupMode = StartupMode.WARM,
      iterations = 5,
      setupBlock = {
        pressHome()
        startActivityAndWait()
        waitForHome()
      },
    ) {
      check(openFirstFolder()) { "Folder-open benchmark requires at least one folder" }
    }

  private fun selection(mode: CompilationMode) =
    rule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      compilationMode = mode,
      startupMode = StartupMode.WARM,
      iterations = 5,
      setupBlock = {
        pressHome()
        startActivityAndWait()
        waitForHome()
        check(openFirstFolder()) { "Selection benchmark requires at least one folder" }
      },
    ) { selectionMode() }
}
