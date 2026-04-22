package com.quantummpv.app.ui.player.controls

import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantummpv.app.preferences.PlayerButton
import com.quantummpv.app.ui.player.Panels
import com.quantummpv.app.ui.player.PlayerActivity
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.player.Sheets
import com.quantummpv.app.ui.player.VideoAspect
import com.quantummpv.app.ui.player.controls.components.ControlsButton
import com.quantummpv.app.ui.player.controls.components.ControlsGroup
import com.quantummpv.app.ui.theme.controlColor
import com.quantummpv.app.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopPlayerControlsPortrait(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  viewModel: PlayerViewModel,
) {
  val playlistModeEnabled = viewModel.hasPlaylistSupport()
  val clickEvent = LocalPlayerButtonsClickEvent.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = MaterialTheme.spacing.medium)
      .padding(horizontal = MaterialTheme.spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      ControlsButton(
        icon = Icons.Default.ArrowBack,
        onClick = onBackPress,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(45.dp),
      )

      val titleInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

      Surface(
        shape = CircleShape,
        color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        onClick = {
          clickEvent()
          onOpenSheet(Sheets.Playlist)
        },
        enabled = playlistModeEnabled,
        border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
          .padding(start = 4.dp)
          .height(45.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 14.dp),
        ) {
          Text(
            mediaTitle ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
          )
          viewModel.getPlaylistInfo()?.let { playlistInfo ->
            Text(
              " • $playlistInfo",
              maxLines = 1,
              style = MaterialTheme.typography.bodySmall,
              color = LocalContentColor.current.copy(alpha = 0.7f),
            )
          }
        }
      }
    }
  }
}

@Composable
fun BottomPlayerControlsPortrait(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: com.quantummpv.app.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(bottom = MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = true,
        isSpeedNonOne = isSpeedNonOne,
        currentZoom = currentZoom,
        aspect = aspect,
        mediaTitle = mediaTitle,
        hideBackground = hideBackground,
        onBackPress = onBackPress,
        onOpenSheet = onOpenSheet,
        onOpenPanel = onOpenPanel,
        viewModel = viewModel,
        activity = activity,
        decoder = decoder,
        playbackSpeed = playbackSpeed,
        buttonSize = 44.dp, // Slightly more compact size
      )
    }
  }
}




