package com.quantummpv.app.ui.browser.components

import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.icons.AppIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class BarLayoutParams(
  val buttonSize: androidx.compose.ui.unit.Dp,
  val iconSize: androidx.compose.ui.unit.Dp,
  val spacing: androidx.compose.ui.unit.Dp,
  val rowPaddingHorizontal: androidx.compose.ui.unit.Dp,
  val surfacePaddingHorizontal: androidx.compose.ui.unit.Dp
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserBottomBar(
  isSelectionMode: Boolean,
  onCopyClick: () -> Unit,
  onMoveClick: () -> Unit,
  onDownscaleClick: () -> Unit = {},
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onAddToPlaylistClick: () -> Unit,
  modifier: Modifier = Modifier,
  showCopy: Boolean = true,
  showMove: Boolean = true,
  showDownscale: Boolean = false,
  showRename: Boolean = true,
  showDelete: Boolean = true,
  showAddToPlaylist: Boolean = true,
) {
  AnimatedVisibility(
    visible = isSelectionMode,
    modifier = modifier,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val availableWidth = maxWidth
      val visibleCount = listOf(showCopy, showMove, showDownscale, showRename, showAddToPlaylist, showDelete).count { it }

      val layoutParams = when {
        visibleCount <= 1 -> {
          val surfPad = if (availableWidth < 360.dp) 12.dp else 24.dp
          val rowPad = if (availableWidth < 360.dp) 8.dp else 16.dp
          BarLayoutParams(
            buttonSize = 48.dp,
            iconSize = 24.dp,
            spacing = 0.dp,
            rowPaddingHorizontal = rowPad,
            surfacePaddingHorizontal = surfPad
          )
        }
        else -> {
          val options = listOf(
            BarLayoutParams(48.dp, 24.dp, 20.dp, 16.dp, 24.dp), // Standard
            BarLayoutParams(40.dp, 20.dp, 12.dp, 8.dp, 12.dp),  // Medium
            BarLayoutParams(36.dp, 18.dp, 8.dp, 4.dp, 6.dp),    // Small
            BarLayoutParams(32.dp, 16.dp, 4.dp, 2.dp, 4.dp)     // Tiny
          )
          options.firstOrNull { opt ->
            val totalWidth = (opt.buttonSize * visibleCount) + (opt.spacing * (visibleCount - 1)) + (opt.rowPaddingHorizontal * 2) + (opt.surfacePaddingHorizontal * 2)
            totalWidth <= availableWidth
          } ?: options.last()
        }
      }

      val surfacePaddingVertical = if (availableWidth < 380.dp) 6.dp else 12.dp
      val rowPaddingVertical = 6.dp

      Surface(
        modifier = Modifier
          .windowInsetsPadding(WindowInsets.systemBars)
          .align(Alignment.BottomCenter)
          .padding(horizontal = layoutParams.surfacePaddingHorizontal, vertical = surfacePaddingVertical),
        shape = RoundedCornerShape(percent = 100),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
      ) {
        Row(
          modifier = Modifier.padding(horizontal = layoutParams.rowPaddingHorizontal, vertical = rowPaddingVertical),
          horizontalArrangement = Arrangement.spacedBy(layoutParams.spacing),
          verticalAlignment = Alignment.CenterVertically
        ) {
          BrowserBottomBarButton(showCopy, onCopyClick, Icons.Filled.ContentCopy, "Copy", layoutParams.buttonSize, layoutParams.iconSize)
          BrowserBottomBarButton(showMove, onMoveClick, Icons.Filled.DriveFileMove, "Move", layoutParams.buttonSize, layoutParams.iconSize)
          BrowserBottomBarButton(showDownscale, onDownscaleClick, Icons.Default.FitScreen, "Compressor", layoutParams.buttonSize, layoutParams.iconSize)
          BrowserBottomBarButton(showRename, onRenameClick, Icons.Filled.DriveFileRenameOutline, "Rename", layoutParams.buttonSize, layoutParams.iconSize)
          BrowserBottomBarButton(showAddToPlaylist, onAddToPlaylistClick, Icons.Filled.PlaylistAdd, "Add to Playlist", layoutParams.buttonSize, layoutParams.iconSize)
          BrowserBottomBarButton(showDelete, onDeleteClick, Icons.Filled.Delete, "Delete", layoutParams.buttonSize, layoutParams.iconSize, tint = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}

@Composable
private fun BrowserBottomBarButton(
  show: Boolean,
  onClick: () -> Unit,
  icon: AppIcon,
  contentDescription: String,
  buttonSize: androidx.compose.ui.unit.Dp,
  iconSize: androidx.compose.ui.unit.Dp,
  tint: Color = MaterialTheme.colorScheme.primary,
) {
  if (show) {
    IconButton(
      onClick = onClick,
      modifier = Modifier.size(buttonSize)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(iconSize),
        tint = tint,
      )
    }
  }
}
