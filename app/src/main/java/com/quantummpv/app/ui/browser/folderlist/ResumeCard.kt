/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.folderlist

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.domain.thumbnail.ThumbnailRepository
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.repository.MediaFileRepository
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.PlaybackIdentity
import com.quantummpv.app.ui.player.ResumePlaybackMode
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.AppShapeScale
import com.quantummpv.app.ui.theme.Dimens
import com.quantummpv.app.ui.utils.pressScale
import com.quantummpv.app.utils.history.RecentlyPlayedOps
import com.quantummpv.app.utils.media.MediaUtils
import com.quantummpv.app.utils.media.cleanTitle
import com.quantummpv.app.utils.storage.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

@Immutable
internal data class ResumeCardModel(
  val filePath: String,
  val title: String,
  /** "12:30" when the player will resume there, null when it starts from the beginning. */
  val resumeAt: String?,
  /** 0..1, null when nothing was watched yet. */
  val progress: Float?,
  val video: Video?,
  /** Changes whenever something new is played, which un-hides a dismissed card. */
  val dismissKey: String,
)

/** Last played local video, re-read whenever history changes; null when none or the file is gone. */
@Composable
internal fun rememberResumeCardModel(): State<ResumeCardModel?> {
  val context = LocalContext.current
  val playbackStates = koinInject<PlaybackStateRepository>()
  val playerPreferences = koinInject<PlayerPreferences>()
  return produceState<ResumeCardModel?>(initialValue = null) {
    RecentlyPlayedOps.observeLastPlayedEntity().collect { entity ->
      value =
        entity?.let {
          withContext(Dispatchers.IO) {
            runCatching {
              buildResumeCardModel(context, it.filePath, it.videoTitle, it.fileName, it.timestamp, playbackStates, playerPreferences)
            }.getOrNull()
          }
        }
    }
  }
}

private suspend fun buildResumeCardModel(
  context: Context,
  rawPath: String,
  videoTitle: String?,
  fileName: String,
  timestamp: Long,
  playbackStates: PlaybackStateRepository,
  playerPreferences: PlayerPreferences,
): ResumeCardModel? {
  // Local files only: streams have no stable thumbnail and "missing" can't be checked cheaply.
  val path = if (rawPath.startsWith("file://")) rawPath.removePrefix("file://") else rawPath
  if (!path.startsWith("/") || !File(path).isFile) return null

  val state =
    listOf(PlaybackIdentity.forLocalPath(path), PlaybackIdentity.forUri(path), PlaybackIdentity.forUri("file://$path"))
      .firstNotNullOfOrNull { playbackStates.getVideoDataByTitle(it) }
  val video = MediaFileRepository.getVideosFromFiles(context, listOf(File(path))).firstOrNull()
  if (video?.isAudio == true) return null

  val position = state?.lastPosition ?: 0
  val remaining = state?.timeRemaining ?: 0
  val total = position + remaining
  // Mirrors PlayerActivity.restorePlaybackPosition so the label never promises a seek it won't do.
  val playerResumes =
    playerPreferences.savePositionOnQuit.get() &&
      playerPreferences.resumePlaybackMode.get() != ResumePlaybackMode.Never &&
      position > 3 &&
      remaining !in 1..5
  val rawTitle = videoTitle?.takeIf { it.isNotBlank() } ?: FileTypeUtils.stripExtension(fileName.ifBlank { File(path).name })
  return ResumeCardModel(
    filePath = path,
    title = cleanTitle(rawTitle).title,
    resumeAt = if (playerResumes) DateUtils.formatElapsedTime(position.toLong()) else null,
    progress = if (total > 0 && position > 0) (position.toFloat() / total).coerceIn(0f, 1f) else null,
    video = video,
    dismissKey = "$path#$timestamp",
  )
}

// Process-lifetime: survives tab switches (pager pages are disposed) and back navigation.
private var dismissedResumeKey by mutableStateOf<String?>(null)

/**
 * Elevated "Continue watching" card for the top of the home list. Slides and fades in and out, and
 * can be swiped away (either direction) until something else is played.
 */
@Composable
internal fun ResumeCard(
  model: ResumeCardModel?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val dismissedKey = dismissedResumeKey
  // Keeps the last model during the exit animation.
  var shown by remember { mutableStateOf(model) }
  if (model != null) shown = model
  val visible = model != null && model.dismissKey != dismissedKey
  val reduceMotion = com.quantummpv.app.ui.theme.LocalReduceMotion.current
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(AppMotion.defaultEffects()) +
        if (reduceMotion) androidx.compose.animation.EnterTransition.None else expandVertically(AppMotion.defaultSpatial()),
    exit =
      fadeOut(AppMotion.fastEffects()) +
        if (reduceMotion) androidx.compose.animation.ExitTransition.None else shrinkVertically(AppMotion.defaultSpatial()),
  ) {
    val current = shown ?: return@AnimatedVisibility
    val swipeState = rememberSwipeToDismissBoxState()
    LaunchedEffect(current.dismissKey) { swipeState.snapTo(SwipeToDismissBoxValue.Settled) }
    SwipeToDismissBox(
      state = swipeState,
      backgroundContent = {},
      onDismiss = { dismissedResumeKey = current.dismissKey },
    ) {
      ResumeCardContent(
        model = current,
        onClick = {
          MediaUtils.playFile(current.filePath, context, "recently_played_button", title = current.title)
        },
        onDismiss = { dismissedResumeKey = current.dismissKey },
      )
    }
  }
}

@Composable
private fun ResumeCardContent(
  model: ResumeCardModel,
  onClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  val dismissLabel = stringResource(R.string.ui_resume_card_dismiss)
  val interactionSource = remember { MutableInteractionSource() }
  val shape = AppShapeScale.large
  ElevatedCard(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = shape,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 6.dp)
        .pressScale(interactionSource)
        // Swipe has no TalkBack equivalent, so dismissal is also offered as a custom action.
        .semantics {
          role = Role.Button
          customActions =
            listOf(
              CustomAccessibilityAction(dismissLabel) {
                onDismiss()
                true
              },
            )
        },
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ResumeThumbnail(model)
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.ui_resume_card_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = model.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = model.resumeAt?.let { stringResource(R.string.ui_resume_from, it) } ?: stringResource(R.string.ui_play_again),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.RoundedFilled.PlayArrow,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp).size(28.dp),
      )
    }
  }
}

@Composable
private fun ResumeThumbnail(model: ResumeCardModel) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val widthDp = 112.dp
  val heightDp = 63.dp
  var bitmap by remember(model.filePath) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(model.video) {
    val video = model.video ?: return@LaunchedEffect
    val w = with(density) { widthDp.roundToPx() }
    val h = with(density) { heightDp.roundToPx() }
    bitmap = runCatching { thumbnailRepository.getThumbnail(video, w, h) }.getOrNull()
  }
  Box(
    modifier =
      Modifier
        .size(widthDp, heightDp)
        .clip(AppShapeScale.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
      Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    } else {
      Icon(Icons.RoundedFilled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val progress = model.progress
    if (progress != null) {
      Box(
        Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .height(Dimens.ThumbnailProgressHeight)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
      ) {
        Box(
          Modifier
            .fillMaxWidth(progress)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary),
        )
      }
    }
  }
}
