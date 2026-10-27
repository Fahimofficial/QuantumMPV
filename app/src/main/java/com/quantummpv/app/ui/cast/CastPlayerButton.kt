/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.cast

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.quantummpv.app.preferences.PlayerButton
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.theme.controlColor
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * True while the Cast framework sees at least one receiver (or a session is active). Google's Cast design
 * guidelines show the Cast button only then; without Play services the button stays hidden.
 */
@Composable
fun rememberCastDevicesAvailable(): Boolean {
  val context = LocalContext.current
  var available by remember { mutableStateOf(false) }
  DisposableEffect(context) {
    var castContext: CastContext? = null
    var disposed = false
    val listener = CastStateListener { state -> available = state != CastState.NO_DEVICES_AVAILABLE }
    try {
      CastContext
        .getSharedInstance(context.applicationContext, ContextCompat.getMainExecutor(context))
        .addOnSuccessListener { shared ->
          if (disposed) return@addOnSuccessListener
          castContext = shared
          available = shared.castState != CastState.NO_DEVICES_AVAILABLE
          shared.addCastStateListener(listener)
        }.addOnFailureListener { available = false }
    } catch (_: RuntimeException) {
      available = false
    }
    onDispose {
      disposed = true
      castContext?.removeCastStateListener(listener)
    }
  }
  return available
}

/** Uses the SDK button for Cast behavior while keeping the app's rounded symbol visible. */
@Composable
fun CastPlayerButton(
  hideBackground: Boolean,
  buttonSize: Dp,
  onInvoked: () -> Unit = {},
  contentColor: ComposeColor? = null,
) {
  val castContentDescription =
    androidx.compose.ui.res
      .stringResource(com.quantummpv.app.R.string.ui_cast)
  Surface(
    shape = CircleShape,
    color =
      if (hideBackground) {
        ComposeColor.Transparent
      } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
      },
    // Callers pass the surrounding content color (e.g. drawer tiles) so the icon
    // matches its sibling buttons instead of staying fixed white.
    contentColor = contentColor ?: if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
    border =
      if (hideBackground) {
        null
      } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      },
    modifier = Modifier.size(buttonSize),
  ) {
    Box(contentAlignment = Alignment.Center) {
      AndroidView(
        factory = { context ->
          MediaRouteButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = castContentDescription
            CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
            setRemoteIndicatorDrawable(ColorDrawable(Color.TRANSPARENT))
          }
        },
        update = { button ->
          button.setOnClickListener { onInvoked() }
        },
        modifier = Modifier.fillMaxSize(),
      )
      Icon(
        imageVector = PlayerButton.CAST.icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
