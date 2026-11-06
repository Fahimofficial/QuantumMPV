/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.FolderSortType
import com.quantummpv.app.preferences.SortOrder
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppMotion

/** The four one-tap folder orders offered next to the top bar's sort button. */
internal enum class FolderQuickSort(
  val type: FolderSortType,
  val order: SortOrder,
  @param:StringRes val label: Int,
) {
  NameAscending(FolderSortType.Title, SortOrder.Ascending, R.string.sort_name_a_z),
  NameDescending(FolderSortType.Title, SortOrder.Descending, R.string.sort_name_z_a),
  SizeLargest(FolderSortType.Size, SortOrder.Descending, R.string.sort_size_largest),
  SizeSmallest(FolderSortType.Size, SortOrder.Ascending, R.string.sort_size_smallest),
  ;

  companion object {
    /** Null when the active sort is one only the full dialog offers (date, item count). */
    fun of(
      type: FolderSortType,
      order: SortOrder,
    ): FolderQuickSort? = entries.firstOrNull { it.type == type && it.order == order }
  }
}

/**
 * Sort button that opens a compact menu: name A–Z/Z–A, folder size largest/smallest, and the
 * existing full sort-and-view dialog. Sizes are the totals the folder scan already stores
 * (recursive in the file-tree view), so choosing one never triggers a storage rescan.
 */
@Composable
internal fun FolderQuickSortButton(
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortChange: (FolderSortType, SortOrder) -> Unit,
  onMoreOptions: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val current = FolderQuickSort.of(sortType, sortOrder)
  val currentLabel = current?.let { stringResource(it.label) } ?: sortType.displayName
  val bySize = sortType == FolderSortType.Size
  val iconSpec = AppMotion.spatial(AppMotion.Spatial.Snappy, AppMotion.ReducedAlpha)

  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.padding(horizontal = 2.dp)) {
      // The glyph tells name sorting from size sorting at a glance; it swaps with a short scale.
      AnimatedContent(
        targetState = bySize,
        transitionSpec = { (scaleIn(iconSpec, 0.7f) + fadeIn()) togetherWith (scaleOut(iconSpec, 0.7f) + fadeOut()) },
        label = "folder_sort_icon",
      ) { size ->
        Icon(
          if (size) Icons.RoundedFilled.SwapVert else Icons.RoundedFilled.SortByAlpha,
          contentDescription = stringResource(R.string.sort_current, currentLabel),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = RoundedCornerShape(16.dp),
    ) {
      FolderQuickSort.entries.forEach { option ->
        val selected = option == current
        DropdownMenuItem(
          text = {
            Text(
              stringResource(option.label),
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
          },
          leadingIcon = {
            Icon(
              if (option.type == FolderSortType.Size) Icons.RoundedFilled.SwapVert else Icons.RoundedFilled.SortByAlpha,
              contentDescription = null,
              tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = {
            if (selected) Icon(Icons.RoundedFilled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          },
          onClick = {
            expanded = false
            if (!selected) onSortChange(option.type, option.order)
          },
          modifier =
            Modifier.semantics {
              role = Role.RadioButton
              this.selected = selected
            },
        )
      }
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      DropdownMenuItem(
        text = { Text(stringResource(R.string.sort_more_options)) },
        leadingIcon = { Icon(Icons.RoundedFilled.Tune, contentDescription = null) },
        onClick = {
          expanded = false
          onMoreOptions()
        },
      )
    }
  }
}
