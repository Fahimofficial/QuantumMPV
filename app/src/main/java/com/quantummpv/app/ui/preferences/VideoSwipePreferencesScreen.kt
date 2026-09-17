package com.quantummpv.app.ui.preferences

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.VideoSwipeAction
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.cards.containerColor
import com.quantummpv.app.ui.browser.cards.contentColor
import com.quantummpv.app.ui.browser.cards.icon
import com.quantummpv.app.ui.browser.cards.labelRes
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import com.quantummpv.app.ui.utils.rememberAppHaptics
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object VideoSwipePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<BrowserPreferences>()
    val right by preferences.videoSwipeRight.collectAsState()
    val left by preferences.videoSwipeLeft.collectAsState()
    val backStack = LocalBackStack.current
    val haptics = rememberAppHaptics()
    var editingRight by rememberSaveable { mutableStateOf<Boolean?>(null) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              stringResource(R.string.pref_video_swipe_title),
              style = MaterialTheme.typography.titleLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) { Icon(Icons.RoundedFilled.ArrowBack, null) }
          },
        )
      },
    ) { padding ->
      val (listState, highlight) =
        rememberSettingsSearchList(VideoSwipePreferencesScreen, MaterialTheme.colorScheme.primary)
      LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).then(highlight)) {
        item {
          SwipeDirectionPreference(
            right = true,
            action = right,
            modifier = Modifier.settingsSearchTarget(R.string.pref_video_swipe_right),
            onChange = { editingRight = true },
          )
          HorizontalDivider()
        }
        item {
          SwipeDirectionPreference(
            right = false,
            action = left,
            modifier = Modifier.settingsSearchTarget(R.string.pref_video_swipe_left),
            onChange = { editingRight = false },
          )
        }
      }
    }

    editingRight?.let { isRight ->
      val selectedAction = if (isRight) right else left
      AlertDialog(
        onDismissRequest = { editingRight = null },
        title = {
          val titleRes = if (isRight) R.string.pref_video_swipe_choose_right else R.string.pref_video_swipe_choose_left
          Text(stringResource(titleRes))
        },
        text = {
          Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            VideoSwipeAction.entries.forEach { action ->
              Row(
                modifier = Modifier.fillMaxWidth()
                  .selectable(selected = action == selectedAction, role = Role.RadioButton) {
                    if (action != selectedAction) {
                      if (isRight) preferences.videoSwipeRight.set(action) else preferences.videoSwipeLeft.set(action)
                      haptics.selection(true)
                    }
                    editingRight = null
                  }.heightIn(min = 56.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                RadioButton(selected = action == selectedAction, onClick = null)
                Icon(action.icon(), null, modifier = Modifier.size(24.dp))
                Text(stringResource(action.labelRes(null)), modifier = Modifier.weight(1f))
              }
            }
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(onClick = { editingRight = null }) { Text(stringResource(R.string.generic_cancel)) }
        },
      )
    }
  }
}

@Composable
private fun SwipeDirectionPreference(
  right: Boolean,
  action: VideoSwipeAction,
  modifier: Modifier = Modifier,
  onChange: () -> Unit,
) {
  Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(if (right) R.string.pref_video_swipe_right else R.string.pref_video_swipe_left),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          stringResource(action.labelRes(null)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onChange) {
        Icon(Icons.RoundedFilled.Edit, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.pref_video_swipe_change))
      }
    }
    Spacer(Modifier.height(16.dp))
    SwipeActionPreview(right, action)
  }
}

@Composable
private fun SwipeActionPreview(right: Boolean, action: VideoSwipeAction) {
  val density = LocalDensity.current
  val offset by animateFloatAsState(
    targetValue = if (action == VideoSwipeAction.None) 0f else with(density) { 76.dp.toPx() } * if (right) 1f else -1f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.ExpressiveFast, snap()),
    label = "swipePreviewOffset",
  )
  val background by animateColorAsState(
    targetValue = action.containerColor(),
    animationSpec = AppMotion.spatial(AppMotion.Effect.Color, snap()),
    label = "swipePreviewColor",
  )
  Box(
    Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(8.dp))
      .background(background).clearAndSetSemantics { },
  ) {
    Crossfade(
      targetState = action,
      animationSpec = AppMotion.spatial(AppMotion.Effect.Alpha, snap()),
      modifier = Modifier
        .align(if (right) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
        .width(76.dp),
      label = "swipePreviewAction",
    ) { previewAction ->
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(previewAction.icon(), null, tint = previewAction.contentColor(), modifier = Modifier.size(28.dp))
      }
    }
    Row(
      modifier = Modifier.fillMaxSize().graphicsLayer { translationX = offset }
        .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(
        Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.RoundedFilled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(0.85f).height(8.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Box(
          Modifier.fillMaxWidth(0.55f).height(6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
      }
    }
  }
}