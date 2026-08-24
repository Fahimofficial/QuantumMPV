/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.jellyfin.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantummpv.app.R
import com.quantummpv.app.domain.seerr.JellyseerrRequest
import com.quantummpv.app.domain.seerr.MediaStatus
import com.quantummpv.app.domain.seerr.MediaType
import com.quantummpv.app.domain.seerr.RequestStatus
import com.quantummpv.app.domain.seerr.SearchResultItem
import com.quantummpv.app.presentation.components.RemoteImage
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons

@Composable
fun SeerrMediaCard(
  item: SearchResultItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  cardWidth: Dp = 135.dp,
) {
  Column(
    modifier = modifier
      .width(cardWidth)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick),
  ) {
    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        val posterUrl = item.getPosterUrl()
        if (!posterUrl.isNullOrBlank()) {
          RemoteImage(
            url = posterUrl,
            contentDescription = item.getDisplayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (item.getMediaType() == MediaType.TV) Icons.RoundedFilled.Tv else Icons.RoundedFilled.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(36.dp),
            )
          }
        }

        // Top Badges (Rating on left, Status on right)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          item.getRating()?.let { rating ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.Black.copy(alpha = 0.72f),
              contentColor = Color(0xFFFFC107),
              modifier = Modifier.padding(2.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
              ) {
                Icon(
                  imageVector = Icons.RoundedFilled.Star,
                  contentDescription = null,
                  modifier = Modifier.size(11.dp),
                  tint = Color(0xFFFFC107),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = rating,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                )
              }
            }
          } ?: Spacer(modifier = Modifier.size(1.dp))

          val displayStatus = item.getDisplayStatus()
          if (displayStatus != null && displayStatus != MediaStatus.UNKNOWN) {
            SeerrStatusChip(status = displayStatus)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = item.getDisplayTitle(),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.padding(top = 1.dp),
    ) {
      item.getReleaseYear()?.let { year ->
        Text(
          text = year,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "•",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = if (item.getMediaType() == MediaType.TV) "TV" else "Movie",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun SeerrStatusChip(
  status: MediaStatus,
  modifier: Modifier = Modifier,
) {
  val (bgColor, textColor, textRes) = when (status) {
    MediaStatus.AVAILABLE -> Triple(
      Color(0xFF1B5E20).copy(alpha = 0.85f),
      Color(0xFFE8F5E9),
      R.string.seerr_status_available,
    )
    MediaStatus.PARTIALLY_AVAILABLE -> Triple(
      Color(0xFFE65100).copy(alpha = 0.85f),
      Color(0xFFFFF3E0),
      R.string.seerr_status_partially_available,
    )
    MediaStatus.PROCESSING -> Triple(
      Color(0xFF0D47A1).copy(alpha = 0.85f),
      Color(0xFFE3F2FD),
      R.string.seerr_status_processing,
    )
    MediaStatus.PENDING -> Triple(
      Color(0xFF4A148C).copy(alpha = 0.85f),
      Color(0xFFF3E5F5),
      R.string.seerr_status_pending,
    )
    else -> Triple(
      Color.Black.copy(alpha = 0.7f),
      Color.White,
      R.string.seerr_status_requested,
    )
  }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = bgColor,
    contentColor = textColor,
    modifier = modifier,
  ) {
    Text(
      text = stringResource(textRes),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      fontSize = 9.5.sp,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
    )
  }
}

@Composable
fun SeerrRequestStatusChip(
  status: RequestStatus,
  modifier: Modifier = Modifier,
) {
  val (bgColor, textColor, textRes) = when (status) {
    RequestStatus.COMPLETED -> Triple(
      Color(0xFF1B5E20).copy(alpha = 0.85f),
      Color(0xFFE8F5E9),
      R.string.seerr_status_available,
    )
    RequestStatus.APPROVED -> Triple(
      Color(0xFF0D47A1).copy(alpha = 0.85f),
      Color(0xFFE3F2FD),
      R.string.seerr_status_processing,
    )
    RequestStatus.PENDING -> Triple(
      Color(0xFFE65100).copy(alpha = 0.85f),
      Color(0xFFFFF3E0),
      R.string.seerr_status_pending,
    )
    RequestStatus.DECLINED -> Triple(
      Color(0xFFB71C1C).copy(alpha = 0.85f),
      Color(0xFFFFEBEE),
      R.string.seerr_decline,
    )
    RequestStatus.FAILED -> Triple(
      Color(0xFFB71C1C).copy(alpha = 0.85f),
      Color(0xFFFFEBEE),
      R.string.clip_cancel,
    )
  }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = bgColor,
    contentColor = textColor,
    modifier = modifier,
  ) {
    Text(
      text = stringResource(textRes),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      fontSize = 10.sp,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun SeerrRequestCard(
  request: JellyseerrRequest,
  baseUrl: String?,
  isAdmin: Boolean,
  onClick: () -> Unit,
  onApprove: () -> Unit,
  onDecline: () -> Unit,
  onDelete: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  cardWidth: Dp = 260.dp,
) {
  val isPending = request.status == RequestStatus.PENDING.value

  Column(
    modifier = modifier
      .width(cardWidth)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      ) {
        val backdropUrl = request.media.getBackdropUrl() ?: request.media.getPosterUrl()
        if (!backdropUrl.isNullOrBlank()) {
          RemoteImage(
            url = backdropUrl,
            contentDescription = request.media.getDisplayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Gradient dark overlay
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = 0.45f),
                  Color.Black.copy(alpha = 0.88f),
                ),
              ),
            ),
        )

        // Poster thumbnail on the left
        val posterUrl = request.media.getPosterUrl()
        if (!posterUrl.isNullOrBlank()) {
          RemoteImage(
            url = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .align(Alignment.CenterStart)
              .padding(8.dp)
              .fillMaxHeight()
              .aspectRatio(2f / 3f)
              .clip(RoundedCornerShape(10.dp)),
          )
        } else {
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .padding(8.dp)
              .fillMaxHeight()
              .aspectRatio(2f / 3f)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (request.getMediaType() == MediaType.TV) Icons.RoundedFilled.Tv else Icons.RoundedFilled.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(24.dp),
            )
          }
        }

        // Top-right status badge
        SeerrRequestStatusChip(
          status = request.getRequestStatus(),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp),
        )

        // Content on the right: Title + Requester User Info
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = if (!posterUrl.isNullOrBlank()) 84.dp else 12.dp, end = 8.dp, bottom = 8.dp, top = 8.dp)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.Bottom,
        ) {
          Text(
            text = request.media.getDisplayTitle(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
          ) {
            val rawAvatar = request.requestedBy.avatar
            val avatarUrl = when {
              rawAvatar.isNullOrBlank() -> null
              rawAvatar.startsWith("http") -> rawAvatar
              !baseUrl.isNullOrBlank() -> "${baseUrl.trimEnd('/')}/${rawAvatar.trimStart('/')}"
              else -> null
            }

            if (avatarUrl != null) {
              RemoteImage(
                url = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape),
              )
            } else {
              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = (request.requestedBy.displayName ?: request.requestedBy.username ?: "U").take(1).uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            Text(
              text = request.requestedBy.displayName ?: request.requestedBy.username ?: "User",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.85f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          // Admin Approve / Decline Quick Buttons
          if (isPending && isAdmin) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 6.dp),
            ) {
              FilledIconButton(
                onClick = onApprove,
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(30.dp),
              ) {
                Icon(
                  Icons.RoundedFilled.Check,
                  contentDescription = stringResource(R.string.seerr_approve),
                  modifier = Modifier.size(16.dp),
                )
              }

              FilledTonalIconButton(
                onClick = onDecline,
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.size(30.dp),
              ) {
                Icon(
                  Icons.RoundedFilled.Close,
                  contentDescription = stringResource(R.string.seerr_decline),
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          } else if (onDelete != null && (isAdmin || request.status == RequestStatus.DECLINED.value || request.status == RequestStatus.FAILED.value)) {
            Row(
              modifier = Modifier.padding(top = 6.dp),
            ) {
              FilledTonalIconButton(
                onClick = onDelete,
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                  contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.size(28.dp),
              ) {
                Icon(
                  Icons.RoundedFilled.Delete,
                  contentDescription = "Delete Request",
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SeerrSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )

    if (actionText != null && onActionClick != null) {
      Text(
        text = actionText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(onClick = onActionClick)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
  }
}

@Composable
fun SeerrSliderRow(
  title: String,
  items: List<SearchResultItem>,
  onItemClick: (SearchResultItem) -> Unit,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
) {
  if (items.isEmpty()) return

  Column(modifier = modifier.fillMaxWidth()) {
    SeerrSectionHeader(
      title = title,
      actionText = actionText,
      onActionClick = onActionClick,
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(items, key = { "${it.mediaType}_${it.id}" }) { item ->
        SeerrMediaCard(
          item = item,
          onClick = { onItemClick(item) },
        )
      }
    }
  }
}
