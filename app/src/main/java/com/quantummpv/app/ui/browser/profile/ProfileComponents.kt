/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.browser.cards.PlaylistCard
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppShapeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfPlaylistCard(
  playlist: PlaylistEntity,
  itemCount: Int,
  onOpen: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  // QuantumMPV's PlaylistCard has no delete slot, so long-press offers the delete action.
  PlaylistCard(
    playlist = playlist,
    itemCount = itemCount,
    onClick = onOpen,
    onLongClick = { onDelete?.invoke() },
    onThumbClick = onOpen,
    modifier = Modifier.width(SHELF_VIDEO_CARD_WIDTH),
    isGridMode = true,
  )
}

/** The user's avatar, or the account glyph when none is set. */
@Composable
internal fun ProfileAvatar(
  size: Dp,
  tint: Color,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color? = null,
) {
  val path by koinInject<AppearancePreferences>().profileImagePath.collectAsState()
  val avatar = rememberProfileAvatar(path)
  when {
    avatar != null ->
      Image(
        bitmap = avatar,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape),
      )

    containerColor != null ->
      Surface(shape = CircleShape, color = containerColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.RoundedFilled.Person,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.54f),
          )
        }
      }

    else ->
      Icon(Icons.RoundedFilled.AccountCircle, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
  }
}

@Composable
internal fun rememberProfileAvatar(path: String): ImageBitmap? {
  val avatar by produceState<ImageBitmap?>(initialValue = null, path) {
    value =
      if (path.isBlank()) {
        null
      } else {
        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
      }
  }
  return avatar
}

private data class ProfileShortcutDestination(
  val icon: AppIcon,
  val label: String,
  val count: Int?,
  val accent: Color,
  val onClick: () -> Unit,
)

@Composable
internal fun ProfileOverviewCard(
  name: String,
  recentCount: Int,
  playlistCount: Int,
  collapseProgress: () -> Float,
  onEditProfile: () -> Unit,
  onRecents: () -> Unit,
  onPlaylists: () -> Unit,
  onInsights: () -> Unit,
  onNetwork: () -> Unit,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  val editLabel = stringResource(R.string.profile_edit)
  val horizontalPadding = if (isTablet) 24.dp else 16.dp

  Column(
    modifier = Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        contentAlignment = Alignment.BottomEnd,
        // Read in the layer phase only, so scrolling never recomposes the hero.
        modifier =
          Modifier.graphicsLayer {
            val progress = collapseProgress()
            val scale = 1f - 0.25f * progress
            scaleX = scale
            scaleY = scale
            alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
          },
      ) {
        ProfileAvatar(
          size = if (isTablet) 116.dp else 96.dp,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentDescription = null,
        )
        // Small visual badge with an independent 48 dp touch target.
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .offset(x = 6.dp, y = 6.dp)
              .tvFocusHighlight(CircleShape)
              .clip(CircleShape)
              .clickable(onClick = onEditProfile),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
            shadowElevation = 2.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.RoundedFilled.Edit, contentDescription = editLabel, modifier = Modifier.size(14.dp))
            }
          }
        }
      }

      Text(
        text = name.ifBlank { stringResource(R.string.ui_profile) },
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    val shortcuts =
      listOf(
        ProfileShortcutDestination(
          Icons.RoundedFilled.History,
          stringResource(R.string.ui_recents),
          recentCount,
          MaterialTheme.colorScheme.primary,
          onRecents,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.PlaylistPlay,
          stringResource(R.string.ui_playlists),
          playlistCount,
          MaterialTheme.colorScheme.secondary,
          onPlaylists,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.Insights,
          stringResource(R.string.library_insights_title),
          null,
          MaterialTheme.colorScheme.tertiary,
          onInsights,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.BringYourOwnIp,
          stringResource(R.string.ui_network),
          null,
          MaterialTheme.colorScheme.primary,
          onNetwork,
        ),
      )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val columns =
        when {
          maxWidth < 300.dp -> 1
          maxWidth >= 680.dp -> 4
          else -> 2
        }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.chunked(columns).forEach { rowShortcuts ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowShortcuts.forEach { shortcut ->
              ProfileShortcutTile(shortcut = shortcut, modifier = Modifier.weight(1f))
            }
            repeat(columns - rowShortcuts.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileShortcutTile(
  shortcut: ProfileShortcutDestination,
  modifier: Modifier = Modifier,
) {
  val shape = AppShapeScale.largeIncreased
  Surface(
    onClick = shortcut.onClick,
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = shortcut.accent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    modifier = modifier.heightIn(min = 80.dp).tvFocusHighlight(shape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = shortcut.accent.copy(alpha = 0.14f),
        contentColor = shortcut.accent,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (shortcut.count != null) {
          Text(
            text = shortcut.count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = shortcut.label,
          style = if (shortcut.count == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        Icons.RoundedFilled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
internal fun SectionHeader(
  title: String,
  count: Int?,
  onViewAll: () -> Unit,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = if (isTablet) 24.dp else 16.dp, end = if (isTablet) 16.dp else 8.dp, top = 16.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (count != null && count > 0) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
          Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }
    }
    TextButton(onClick = onViewAll, shape = CircleShape) {
      Text(stringResource(R.string.profile_view_all), style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.width(2.dp))
      Icon(Icons.RoundedFilled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
internal fun ShelfEmptyCard(
  icon: AppIcon,
  title: String,
  message: String,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  Surface(
    shape = AppShapeScale.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 2.dp),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
