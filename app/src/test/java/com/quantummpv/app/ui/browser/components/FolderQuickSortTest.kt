package com.quantummpv.app.ui.browser.components

import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.preferences.FolderSortType
import com.quantummpv.app.preferences.SortOrder
import com.quantummpv.app.utils.sort.SortUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderQuickSortTest {
  private fun folder(
    name: String,
    size: Long,
  ) = VideoFolder(bucketId = name, name = name, path = "/storage/$name", videoCount = 1, totalSize = size)

  private val folders = listOf(folder("b", 300), folder("a", 0), folder("c", 1_000))

  private fun sorted(option: FolderQuickSort) = SortUtils.sortFolders(folders, option.type, option.order).map { it.name }

  @Test
  fun eachQuickOptionProducesTheExpectedOrder() {
    assertEquals(listOf("a", "b", "c"), sorted(FolderQuickSort.NameAscending))
    assertEquals(listOf("c", "b", "a"), sorted(FolderQuickSort.NameDescending))
    assertEquals(listOf("c", "b", "a"), sorted(FolderQuickSort.SizeLargest))
    // An empty (0-byte) folder sorts as the smallest instead of failing.
    assertEquals(listOf("a", "b", "c"), sorted(FolderQuickSort.SizeSmallest))
  }

  @Test
  fun mapsStoredPreferencesBackToTheSelectedOption() {
    assertEquals(FolderQuickSort.SizeLargest, FolderQuickSort.of(FolderSortType.Size, SortOrder.Descending))
    assertEquals(FolderQuickSort.NameAscending, FolderQuickSort.of(FolderSortType.Title, SortOrder.Ascending))
    assertNull(FolderQuickSort.of(FolderSortType.Date, SortOrder.Ascending))
  }
}
