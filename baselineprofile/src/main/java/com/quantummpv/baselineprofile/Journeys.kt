package com.quantummpv.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
  device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
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

/** Opens the first folder (its "N Videos" chip identifies a folder row). Returns false if none. */
fun MacrobenchmarkScope.openFirstFolder(): Boolean {
  val folder = device.findObject(By.textEndsWith("Videos")) ?: device.findObject(By.textEndsWith("Video")) ?: return false
  folder.click()
  device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
  device.waitForIdle()
  return true
}

/** Long-presses the first video row to enter selection mode, toggles a second row, then exits. */
fun MacrobenchmarkScope.selectionMode() {
  val list = device.findObject(By.scrollable(true)) ?: return
  val rows = list.children
  if (rows.size < 2) return
  rows[0].longClick()
  device.waitForIdle()
  rows[1].click()
  device.waitForIdle()
  device.pressBack()
  device.waitForIdle()
}

/** Opens the first video in the list, lets the player settle, and returns to the list. */
fun MacrobenchmarkScope.openPlayerAndBack() {
  val list = device.findObject(By.scrollable(true)) ?: return
  val first = list.children.firstOrNull() ?: return
  first.click()
  device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), WAIT_MS)
  // Long enough for the first frame and the controls' fade-in.
  Thread.sleep(2_000)
  device.pressBack()
  device.waitForIdle()
}
