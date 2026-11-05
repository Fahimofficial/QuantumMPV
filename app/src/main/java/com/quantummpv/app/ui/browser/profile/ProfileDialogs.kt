/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppShapeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileEditDialog(
  initialName: String,
  hasPhoto: Boolean,
  isSaving: Boolean,
  showSaveError: Boolean,
  onDismiss: () -> Unit,
  onSave: (name: String, newPhoto: Bitmap?, removePhoto: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentPath by koinInject<AppearancePreferences>().profileImagePath.collectAsState()
  val currentAvatar = rememberProfileAvatar(currentPath)
  var name by rememberSaveable { mutableStateOf(initialName) }
  var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var rawCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var removePhoto by remember { mutableStateOf(false) }
  val pendingImage = remember(pendingPhoto) { pendingPhoto?.asImageBitmap() }
  val preview = pendingImage ?: currentAvatar.takeUnless { removePhoto }
  val showsPhoto = pendingPhoto != null || (hasPhoto && !removePhoto)

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          withContext(Dispatchers.IO) { decodeRawProfileAvatar(context, uri) }?.let { rawCropBitmap = it }
        }
      }
    }

  rawCropBitmap?.let { source ->
    ProfileAvatarCropDialog(
      sourceBitmap = source,
      onDismiss = { rawCropBitmap = null },
      onCropped = { cropped ->
        rawCropBitmap = null
        pendingPhoto = cropped
        removePhoto = false
      },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text(stringResource(R.string.profile_edit)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Surface(
          onClick = { picker.launch("image/*") },
          enabled = !isSaving,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(96.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (preview != null) {
              Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
              Icon(
                Icons.RoundedFilled.Person,
                contentDescription = stringResource(R.string.profile_choose_photo),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
              )
            }
          }
        }
        // FlowRow wraps the third button instead of squeezing it once a photo exists.
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(enabled = !isSaving, onClick = { picker.launch("image/*") }) {
            Text(stringResource(R.string.profile_choose_photo), maxLines = 1)
          }
          if (showsPhoto) {
            OutlinedButton(
              enabled = !isSaving,
              onClick = {
                scope.launch {
                  val toCrop =
                    pendingPhoto
                      ?: currentPath.takeIf { it.isNotBlank() }?.let { path ->
                        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                      }
                  if (toCrop != null) rawCropBitmap = toCrop
                }
              },
            ) {
              Text(stringResource(R.string.clip_crop), maxLines = 1)
            }
            OutlinedButton(
              enabled = !isSaving,
              onClick = {
                pendingPhoto = null
                removePhoto = true
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) {
              Text(stringResource(R.string.profile_remove_photo), maxLines = 1)
            }
          }
        }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(40) },
          label = { Text(stringResource(R.string.profile_display_name)) },
          singleLine = true,
          enabled = !isSaving,
          modifier = Modifier.fillMaxWidth(),
        )
        if (showSaveError) {
          Text(
            text = stringResource(R.string.profile_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      TextButton(enabled = !isSaving, onClick = { onSave(name, pendingPhoto, removePhoto) }) {
        Text(stringResource(R.string.ui_save))
      }
    },
    dismissButton = {
      TextButton(enabled = !isSaving, onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
    },
  )
}

@Composable
private fun ProfileAvatarCropDialog(
  sourceBitmap: Bitmap,
  onDismiss: () -> Unit,
  onCropped: (Bitmap) -> Unit,
) {
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  val isTablet = configuration.smallestScreenWidthDp >= 600
  // Height cap leaves room for the title, rotate and action rows (~220 dp) so Crop stays reachable in landscape.
  val cropBoxDp =
    minOf(
      configuration.screenWidthDp.dp - 64.dp,
      configuration.screenHeightDp.dp - 220.dp,
      if (isTablet) 360.dp else 280.dp,
    ).coerceAtLeast(120.dp)
  val cropBoxPx = with(density) { cropBoxDp.toPx() }

  var rotation by remember { mutableIntStateOf(0) }
  var userScale by remember { mutableFloatStateOf(1f) }
  var userOffset by remember { mutableStateOf(Offset.Zero) }

  val rotatedBitmap = remember(sourceBitmap, rotation) { rotateBitmap(sourceBitmap, rotation) }
  val rotatedImageBitmap = remember(rotatedBitmap) { rotatedBitmap.asImageBitmap() }
  val baseScale =
    remember(rotatedImageBitmap, cropBoxPx) {
      maxOf(cropBoxPx / rotatedImageBitmap.width, cropBoxPx / rotatedImageBitmap.height)
    }

  fun maxPan(
    extent: Int,
    scale: Float,
  ) = ((extent * baseScale * scale - cropBoxPx) / 2f).coerceAtLeast(0f)

  fun resetTransform() {
    userScale = 1f
    userOffset = Offset.Zero
  }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(if (isTablet) 0.65f else 0.94f),
      shape = AppShapeScale.large,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.clip_crop),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.RoundedFilled.Close, contentDescription = stringResource(R.string.generic_cancel))
          }
        }

        Box(
          modifier =
            Modifier
              .size(cropBoxDp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black)
              .pointerInput(rotatedImageBitmap, cropBoxPx) {
                detectTransformGestures { _, pan, zoom, _ ->
                  userScale = (userScale * zoom).coerceIn(1f, 5f)
                  val maxX = maxPan(rotatedImageBitmap.width, userScale)
                  val maxY = maxPan(rotatedImageBitmap.height, userScale)
                  userOffset =
                    Offset(
                      (userOffset.x + pan.x).coerceIn(-maxX, maxX),
                      (userOffset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
              },
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = rotatedImageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier
                .requiredSize(
                  width = with(density) { (rotatedImageBitmap.width * baseScale).toDp() },
                  height = with(density) { (rotatedImageBitmap.height * baseScale).toDp() },
                ).graphicsLayer {
                  scaleX = userScale
                  scaleY = userScale
                  translationX = userOffset.x
                  translationY = userOffset.y
                },
          )
          Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f
            val mask =
              Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
              }
            drawPath(mask, color = Color.Black.copy(alpha = 0.60f))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = radius - 1f, center = center, style = Stroke(width = 2.dp.toPx()))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
          CropToolButton(Icons.RoundedFilled.RotateLeft, stringResource(R.string.profile_rotate_left)) {
            rotation = (rotation + 270) % 360
            resetTransform()
          }
          CropToolButton(Icons.RoundedFilled.RotateRight, stringResource(R.string.profile_rotate_right)) {
            rotation = (rotation + 90) % 360
            resetTransform()
          }
          CropToolButton(Icons.RoundedFilled.Refresh, stringResource(R.string.generic_reset)) {
            rotation = 0
            resetTransform()
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
          Button(
            onClick = {
              onCropped(performCrop(sourceBitmap, rotation, userScale, userOffset, cropBoxPx))
            },
          ) {
            Text(stringResource(R.string.clip_crop))
          }
        }
      }
    }
  }
}

@Composable
private fun CropToolButton(
  icon: AppIcon,
  label: String,
  onClick: () -> Unit,
) {
  FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
  }
}
