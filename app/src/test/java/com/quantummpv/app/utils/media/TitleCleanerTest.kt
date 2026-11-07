package com.quantummpv.app.utils.media

import com.quantummpv.app.ui.browser.folderlist.folderSections
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {
  private fun assertClean(
    raw: String,
    title: String,
    id: String?,
  ) = assertEquals(CleanTitle(title, id), cleanTitle(raw))

  @Test
  fun stripsYoutubeStyleId() = assertClean("5 Minute Meditation Before Sleep [2K4T9HmEhWE]", "5 Minute Meditation Before Sleep", "2K4T9HmEhWE")

  @Test
  fun keepsEmojiAndStripsNumericId() = assertClean("Golden Era🫠 [792070653251895752]", "Golden Era🫠", "792070653251895752")

  @Test
  fun stripsPinterestNumberAndId() =
    assertClean("Pinterest video #1087056428830782977 [1087056428830782977]", "Pinterest video", "1087056428830782977")

  @Test
  fun stripsInstagramStyleId() = assertClean("Video 1 [CrFP98hpukB]", "Video 1", "CrFP98hpukB")

  @Test
  fun leavesNamesWithoutIdAlone() = assertClean("Holiday 2024 final cut", "Holiday 2024 final cut", null)

  @Test
  fun stripsOnlyTheTrailingIdFromSeveralBracketGroups() =
    assertClean("Song [Official Video] [live] [dQw4w9WgXcQ]", "Song [Official Video] [live]", "dQw4w9WgXcQ")

  @Test
  fun keepsTrailingBracketThatIsNotAnId() = assertClean("Concert [Official Video]", "Concert [Official Video]", null)

  @Test
  fun fallsBackToPinterestVideoWhenOnlyTheNumberIsLeft() = assertClean("Pinterest video #123456789", "Pinterest video", null)

  @Test
  fun fallsBackToRawNameWhenOnlyAnIdIsLeft() = assertClean("[2K4T9HmEhWE]", "[2K4T9HmEhWE]", null)

  @Test
  fun folderSectionsSplitPinnedFromTheRest() {
    assertEquals(listOf(0 to 2, 2 to 5), folderSections(pinnedCount = 2, total = 5))
    assertEquals(listOf(0 to 5), folderSections(pinnedCount = 0, total = 5))
    assertEquals(listOf(0 to 3), folderSections(pinnedCount = 3, total = 3))
    assertEquals(emptyList<Pair<Int, Int>>(), folderSections(pinnedCount = 0, total = 0))
  }
}
