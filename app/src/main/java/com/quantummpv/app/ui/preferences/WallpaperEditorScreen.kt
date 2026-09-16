/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.preferences

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.MultiChoiceSegmentedButton
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.WallpaperImage
import com.quantummpv.app.ui.theme.WallpaperScaleMode
import com.quantummpv.app.ui.theme.loadWallpaperBitmap
import com.quantummpv.app.ui.theme.saveWallpaperCopy
import com.quantummpv.app.ui.player.controls.components.tvFocusGroup
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class WallpaperEditorScreen(
  val sourceUri: String,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    BackHandler(enabled = isSaving) { }
    val isEditingCurrent = sourceUri == preferences.customWallpaperUri.get()
    var zoom by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperZoom.get() else 1f)
    }
    var offsetX by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperOffsetX.get() else 0f)
    }
    var offsetY by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperOffsetY.get() else 0f)
    }
    var scaleMode by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperScaleMode.get() else WallpaperScaleMode.Fit)
    }
    var blur by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperBlur.get() else 0f)
    }
    var alpha by rememberSaveable(sourceUri) {
      mutableStateOf(if (isEditingCurrent) preferences.customWallpaperAlpha.get() else 1f)
    }
    val bitmap =
      produceState<Bitmap?>(initialValue = null, sourceUri) {
        val loaded = withContext(Dispatchers.IO) { loadWallpaperBitmap(context, sourceUri) }
        value = loaded
      }.value
    DisposableEffect(bitmap) {
      val displayedBitmap = bitmap
      onDispose {
        if (displayedBitmap != null) {
          Handler(Looper.getMainLooper()).postDelayed(
            { if (!displayedBitmap.isRecycled) displayedBitmap.recycle() },
            WALLPAPER_EDITOR_RECYCLE_DELAY_MS,
          )
        }
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.pref_appearance_custom_wallpaper_crop_title)) },
          navigationIcon = {
            IconButton(enabled = !isSaving, onClick = { backStack.popSafely() }) {
              Icon(Icons.RoundedFilled.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            TextButton(
              enabled = bitmap != null && !isSaving,
              onClick = {
                isSaving = true
                scope.launch {
                  try {
                    val savedUri = saveWallpaperCopy(context, sourceUri)
                    preferences.customWallpaperZoom.set(zoom)
                    preferences.customWallpaperOffsetX.set(offsetX)
                    preferences.customWallpaperOffsetY.set(offsetY)
                    preferences.customWallpaperScaleMode.set(scaleMode)
                    preferences.customWallpaperBlur.set(blur)
                    preferences.customWallpaperAlpha.set(alpha)
                    preferences.customWallpaperUri.set(savedUri)
                    backStack.popSafely()
                  } catch (error: CancellationException) {
                    throw error
                  } catch (_: Exception) {
                    android.widget.Toast
                      .makeText(context, R.string.wallpaper_save_failed, android.widget.Toast.LENGTH_LONG)
                      .show()
                  } finally {
                    isSaving = false
                  }
                }
              },
            ) {
              Text(stringResource(R.string.pref_appearance_custom_wallpaper_save))
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).tvFocusGroup(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          MultiChoiceSegmentedButton(
            choices =
              listOf(
                stringResource(R.string.pref_appearance_custom_wallpaper_fit),
                stringResource(R.string.pref_appearance_custom_wallpaper_fill),
              ).toImmutableList(),
            selectedIndices = persistentListOf(WallpaperScaleMode.entries.indexOf(scaleMode)),
            onClick = { index, _ ->
              scaleMode = WallpaperScaleMode.entries[index]
              zoom = 1f
              offsetX = 0f
              offsetY = 0f
            },
          )
        }
        item {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(8.dp))
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .pointerInput(sourceUri) {
                  detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 3f)
                    if (size.width > 0 && size.height > 0) {
                      offsetX = (offsetX + pan.x / (size.width * 0.35f)).coerceIn(-1f, 1f)
                      offsetY = (offsetY + pan.y / (size.height * 0.35f)).coerceIn(-1f, 1f)
                    }
                  }
                },
          ) {
            bitmap?.let { loaded ->
              WallpaperImage(
                bitmap = loaded,
                zoom = zoom,
                offsetX = offsetX,
                offsetY = offsetY,
                scaleMode = scaleMode,
                blurRadius = blur,
                imageAlpha = alpha,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
        item {
          Text(
            text = stringResource(R.string.pref_appearance_custom_wallpaper_gesture_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        item {
          WallpaperSlider(
            label = stringResource(R.string.pref_appearance_custom_wallpaper_zoom),
            value = zoom,
            valueRange = 1f..3f,
            onValueChange = { zoom = it },
          )
        }
        item {
          WallpaperSlider(
            label = stringResource(R.string.pref_appearance_custom_wallpaper_horizontal),
            value = offsetX,
            valueRange = -1f..1f,
            onValueChange = { offsetX = it },
          )
        }
        item {
          WallpaperSlider(
            label = stringResource(R.string.pref_appearance_custom_wallpaper_vertical),
            value = offsetY,
            valueRange = -1f..1f,
            onValueChange = { offsetY = it },
          )
        }
        item {
          WallpaperSlider(
            label = stringResource(R.string.pref_appearance_custom_wallpaper_blur),
            value = blur,
            valueRange = 0f..40f,
            onValueChange = { blur = it },
          )
        }
        item {
          WallpaperSlider(
            label = stringResource(R.string.pref_appearance_custom_wallpaper_transparency),
            value = alpha,
            valueRange = 0f..1f,
            onValueChange = { alpha = it },
          )
        }
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            OutlinedButton(
              onClick = {
                zoom = 1f
                offsetX = 0f
                offsetY = 0f
                scaleMode = WallpaperScaleMode.Fit
                blur = 0f
                alpha = 1f
              },
              modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp), focusedScale = 1.03f),
            ) {
              Icon(Icons.RoundedFilled.Restore, contentDescription = null)
              Text(
                text = stringResource(R.string.pref_appearance_custom_wallpaper_reset),
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WallpaperSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.fillMaxWidth().tvFocusHighlight(RoundedCornerShape(12.dp), focusedScale = 1.01f),
    )
  }
}

private const val WALLPAPER_EDITOR_RECYCLE_DELAY_MS = 120L
