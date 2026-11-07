package com.quantummpv.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Release package; the profile/benchmark plugin installs the matching non-debug build. */
const val TARGET_PACKAGE = "com.quantummpv.app"
private const val WAIT_MS = 5_000L

/**
 * Grants media access up front so journeys land on the library instead of onboarding. Covers the
 * Android 13+ media permission, the legacy storage permission and all-files access (best effort).
 */
fun grantMediaAccess() {
  val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
  listOf(
    "pm grant $TARGET_PACKAGE android.permission.READ_MEDIA_VIDEO",
    "pm grant $TARGET_PACKAGE android.permission.READ_MEDIA_AUDIO",
    "pm grant $TARGET_PACKAGE android.permission.READ_EXTERNAL_STORAGE",
    "appops set $TARGET_PACKAGE MANAGE_EXTERNAL_STORAGE allow",
  ).forEach { command -> automation.executeShellCommand(command).close() }
}

fun MacrobenchmarkScope.waitForHome() {
  check(device.wait(Until.hasObject(By.res("home_folder_list")), WAIT_MS)) {
    "Home folder list did not appear within ${WAIT_MS}ms"
  }
  device.waitForIdle()
}

/** Flings the home folder list down and back up. */
fun MacrobenchmarkScope.scrollHome() {
  val list = device.findObject(By.scrollable(true)) ?: return
  // Keep clear of the system gesture areas so flings never become back/home gestures.
  list.setGestureMargin(device.displayWidth / 5)
  repeat(2) { list.fling(Direction.DOWN) }
  list.fling(Direction.UP)
  device.waitForIdle()
}

/** Opens the first folder row. Returns false when the folder list has no row. */
fun MacrobenchmarkScope.openFirstFolder(): Boolean {
  val folder = device.wait(Until.findObject(By.res("folder_row")), WAIT_MS) ?: return false
  folder.click()
  check(device.wait(Until.hasObject(By.res("video_list_screen")), WAIT_MS)) {
    "Opening a folder did not navigate to its video list"
  }
  device.waitForIdle()
  return true
}

/** Long-presses the first video row to enter selection mode, toggles a second row, then exits. */
fun MacrobenchmarkScope.selectionMode() {
  val rows = waitForVideoRows(minimumRows = 2, journey = "Selection-mode journey")
  rows[0].longClick()
  device.waitForIdle()
  rows[1].click()
  device.waitForIdle()
  device.pressBack()
  device.waitForIdle()
}

/** Opens the first video in the list, lets the player settle, and returns to the list. */
fun MacrobenchmarkScope.openPlayerAndBack() {
  val first = waitForVideoRows(minimumRows = 1, journey = "Player journey").first()
  first.click()
  check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "controls")), WAIT_MS)) {
    "Player controls did not appear after opening the first video"
  }
  // Long enough for the first frame and the controls' fade-in.
  Thread.sleep(2_000)
  device.pressBack()
  device.waitForIdle()
}

private fun MacrobenchmarkScope.waitForVideoRows(
  minimumRows: Int,
  journey: String,
): List<UiObject2> {
  val videoScreen =
    device.wait(Until.findObject(By.res("video_list_screen")), WAIT_MS)
      ?: error("$journey did not show the video list within ${WAIT_MS}ms")

  val deadline = SystemClock.uptimeMillis() + WAIT_MS
  var rows = videoScreen.findObjects(By.res("video_row"))
  while (rows.size < minimumRows && SystemClock.uptimeMillis() < deadline) {
    SystemClock.sleep(100)
    rows = videoScreen.findObjects(By.res("video_row"))
  }
  check(rows.size >= minimumRows) {
    "$journey requires at least $minimumRows video row(s), but only ${rows.size} loaded within ${WAIT_MS}ms"
  }
  return rows
}
