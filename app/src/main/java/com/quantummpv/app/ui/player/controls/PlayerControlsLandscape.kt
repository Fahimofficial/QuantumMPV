/*
 * SPDX-License-Identifier: CC-BY-NC-4.0
 *
 * This work is licensed under Creative Commons Attribution-NonCommercial 4.0 International License.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc/4.0/
 */

package com.quantummpv.app.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.PlayerButton
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.Panels
import com.quantummpv.app.ui.player.PlayerActivity
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.player.Sheets
import com.quantummpv.app.ui.player.VideoAspect
import com.quantummpv.app.ui.player.controls.components.ControlsButton
import com.quantummpv.app.ui.theme.controlColor
import com.quantummpv.app.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopLeftPlayerControlsLandscape(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  viewModel: PlayerViewModel,
  isTranslatingSub: Boolean = false,
  isRealtimeSubsActive: Boolean = false,
  realtimeSubsLanguage: String = "",
  translationStatus: String = "",
  translatingTrackName: String = "",
) {
  val playlistModeEnabled = viewModel.hasPlaylistSupport()
  val clickEvent = LocalPlayerButtonsClickEvent.current

  Column(
    modifier = Modifier.width(IntrinsicSize.Max),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
      ControlsButton(
        icon = Icons.RoundedFilled.ArrowBack,
        onClick = onBackPress,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(45.dp),
      )

      Column {
        val titleInteractionSource = remember { MutableInteractionSource() }

        Box(
          modifier =
            Modifier
              .height(45.dp)
              .clip(CircleShape)
              .clickable(
                interactionSource = titleInteractionSource,
                indication = ripple(bounded = true),
                enabled = playlistModeEnabled,
                onClick = {
                  clickEvent()
                  onOpenSheet(Sheets.Playlist)
                },
              ),
        ) {
          Surface(
            shape = CircleShape,
            color =
              if (hideBackground) {
                Color.Transparent
              } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(
                  alpha = 0.55f,
                )
              },
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border =
              if (hideBackground) {
                null
              } else {
                BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
              },
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier
                  .padding(
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.medium,
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.small,
                  ),
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
                  overflow = TextOverflow.Visible,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    }

    val syncplayManager = org.koin.compose.koinInject<com.quantummpv.app.domain.syncplay.SyncplayManager>()
    val syncplayState by syncplayManager.state.collectAsState()

    androidx.compose.animation.AnimatedVisibility(
      visible = syncplayState.isConnected,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.RoundedFilled.CloudDownload,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
          text =
            stringResource(
              R.string.syncplay_player_status,
              syncplayState.room.orEmpty(),
              syncplayState.users.size,
            ),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }

    androidx.compose.animation.AnimatedVisibility(
      visible = isTranslatingSub || isRealtimeSubsActive,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.RoundedFilled.Translate,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
          text =
            if (isRealtimeSubsActive) {
              "Real-time subs: ${realtimeSubsLanguage.ifBlank { "?" }} ${translationStatus.ifBlank { "" }}"
            } else {
              "Translating ${translatingTrackName.ifBlank { "subs" }} ${translationStatus.ifBlank { "" }}"
            },
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}

@Composable
fun TopRightPlayerControlsLandscape(
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
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
        isSpeedNonOne = isSpeedNonOne,
        currentZoom = currentZoom,
        aspect = aspect,
        mediaTitle = mediaTitle,
        hideBackground = hideBackground,
        decoder = decoder,
        playbackSpeed = playbackSpeed,
        onBackPress = onBackPress,
        onOpenSheet = onOpenSheet,
        onOpenPanel = onOpenPanel,
        viewModel = viewModel,
        activity = activity,
        buttonSize = 45.dp,
      )
    }
  }
}

@Composable
fun BottomRightPlayerControlsLandscape(
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
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
        isSpeedNonOne = isSpeedNonOne,
        currentZoom = currentZoom,
        aspect = aspect,
        mediaTitle = mediaTitle,
        hideBackground = hideBackground,
        decoder = decoder,
        playbackSpeed = playbackSpeed,
        onBackPress = onBackPress,
        onOpenSheet = onOpenSheet,
        onOpenPanel = onOpenPanel,
        viewModel = viewModel,
        activity = activity,
        buttonSize = 45.dp,
      )
    }
  }
}

@Composable
fun BottomLeftPlayerControlsLandscape(
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
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
        isSpeedNonOne = isSpeedNonOne,
        currentZoom = currentZoom,
        aspect = aspect,
        mediaTitle = mediaTitle,
        hideBackground = hideBackground,
        decoder = decoder,
        playbackSpeed = playbackSpeed,
        onBackPress = onBackPress,
        onOpenSheet = onOpenSheet,
        onOpenPanel = onOpenPanel,
        viewModel = viewModel,
        activity = activity,
        buttonSize = 45.dp,
      )
    }
  }
}
