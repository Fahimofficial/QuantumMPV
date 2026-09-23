package com.quantummpv.app.ui.utils

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveGridUtilsTest {

  @Test
  fun testLcmAndGcd() {
    assertEquals(12, lcm(4, 6))
    assertEquals(0, lcm(0, 5))
    assertEquals(2, gcd(4, 6))
    assertEquals(5, gcd(0, 5))
  }

  @Test
  fun testCalculateGridSpans_NonGridMode() {
    val result = calculateGridSpans(
      isGridMode = false,
      manualGridColumnsEnabled = false,
      folderGridColumnsPref = 3,
      videoGridColumnsPref = 3,
      maxWidth = 400.dp,
      contentHorizontalPadding = 8.dp,
      itemSpacing = 2.dp,
      folderMinWidth = 100.dp,
      videoMinWidth = 130.dp,
    )

    assertEquals(1, result.spans)
    assertEquals(1, result.folderSpan)
    assertEquals(1, result.videoSpan)
  }

  @Test
  fun testCalculateGridSpans_ManualMode() {
    val result = calculateGridSpans(
      isGridMode = true,
      manualGridColumnsEnabled = true,
      folderGridColumnsPref = 4,
      videoGridColumnsPref = 6,
      maxWidth = 400.dp, // Shouldn't be used
      contentHorizontalPadding = 8.dp, // Shouldn't be used
      itemSpacing = 2.dp, // Shouldn't be used
      folderMinWidth = 100.dp, // Shouldn't be used
      videoMinWidth = 130.dp, // Shouldn't be used
    )

    // LCM(4, 6) = 12
    assertEquals(12, result.spans)
    assertEquals(3, result.folderSpan) // 12 / 4
    assertEquals(2, result.videoSpan)  // 12 / 6
  }

  @Test
  fun testCalculateGridSpans_ManualMode_CoerceToAtLeastOne() {
    val result = calculateGridSpans(
      isGridMode = true,
      manualGridColumnsEnabled = true,
      folderGridColumnsPref = 0,
      videoGridColumnsPref = -5,
      maxWidth = 400.dp,
      contentHorizontalPadding = 8.dp,
      itemSpacing = 2.dp,
      folderMinWidth = 100.dp,
      videoMinWidth = 130.dp,
    )

    // Should coerce to 1 and 1
    // LCM(1, 1) = 1
    assertEquals(1, result.spans)
    assertEquals(1, result.folderSpan) // 1 / 1
    assertEquals(1, result.videoSpan)  // 1 / 1
  }

  @Test
  fun testCalculateGridSpans_AutoMode() {
    val maxWidth = 416.dp // (usableWidth = 416 - 16 - 2 = 398)
    // maxFolders = 398 / 100 = 3
    // maxVideos = 398 / 130 = 3

    val result = calculateGridSpans(
      isGridMode = true,
      manualGridColumnsEnabled = false,
      folderGridColumnsPref = 4, // Shouldn't be used
      videoGridColumnsPref = 6, // Shouldn't be used
      maxWidth = maxWidth,
      contentHorizontalPadding = 8.dp,
      itemSpacing = 2.dp,
      folderMinWidth = 100.dp,
      videoMinWidth = 130.dp,
    )

    // LCM(3, 3) = 3
    assertEquals(3, result.spans)
    assertEquals(1, result.folderSpan) // 3 / 3
    assertEquals(1, result.videoSpan)  // 3 / 3
  }

  @Test
  fun testCalculateGridSpans_AutoMode_DifferentCols() {
    val maxWidth = 516.dp // (usableWidth = 516 - 16 - 2 = 498)
    // maxFolders = 498 / 100 = 4
    // maxVideos = 498 / 130 = 3

    val result = calculateGridSpans(
      isGridMode = true,
      manualGridColumnsEnabled = false,
      folderGridColumnsPref = 4,
      videoGridColumnsPref = 6,
      maxWidth = maxWidth,
      contentHorizontalPadding = 8.dp,
      itemSpacing = 2.dp,
      folderMinWidth = 100.dp,
      videoMinWidth = 130.dp,
    )

    // LCM(4, 3) = 12
    assertEquals(12, result.spans)
    assertEquals(3, result.folderSpan) // 12 / 4
    assertEquals(4, result.videoSpan)  // 12 / 3
  }

  @Test
  fun testCalculateGridSpans_AutoMode_CoerceToAtLeastOne() {
    val maxWidth = 10.dp // Very small width

    val result = calculateGridSpans(
      isGridMode = true,
      manualGridColumnsEnabled = false,
      folderGridColumnsPref = 4,
      videoGridColumnsPref = 6,
      maxWidth = maxWidth,
      contentHorizontalPadding = 8.dp,
      itemSpacing = 2.dp,
      folderMinWidth = 100.dp,
      videoMinWidth = 130.dp,
    )

    // Should coerce maxFolders and maxVideos to 1
    // LCM(1, 1) = 1
    assertEquals(1, result.spans)
    assertEquals(1, result.folderSpan) // 1 / 1
    assertEquals(1, result.videoSpan)  // 1 / 1
  }
}
