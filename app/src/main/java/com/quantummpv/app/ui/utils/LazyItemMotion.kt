/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.utils

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantummpv.app.ui.theme.AppMotion

/**
 * Item motion for keyed lazy lists: removed rows fade out while their neighbours glide into the gap,
 * and re-sorted rows travel to their new slot instead of jumping. Reduced motion keeps the fades but
 * drops the travel; Off motion disables both. animateItem() is only reachable from the item scope,
 * so these can't be plain Modifier extensions.
 */
@Suppress("ModifierFactoryExtensionFunction")
@Composable
internal fun LazyItemScope.appItemMotion(): Modifier {
  if (AppMotion.shouldDisableAnimations()) return Modifier
  val reduceMotion = AppMotion.shouldReduceMotion()
  return Modifier.animateItem(
    fadeInSpec = AppMotion.Effect.Alpha,
    placementSpec = if (reduceMotion) null else AppMotion.Spatial.StandardOffset,
    fadeOutSpec = AppMotion.Effect.Alpha,
  )
}

/** Grid counterpart of [appItemMotion]. */
@Suppress("ModifierFactoryExtensionFunction")
@Composable
internal fun LazyGridItemScope.appItemMotion(): Modifier {
  if (AppMotion.shouldDisableAnimations()) return Modifier
  val reduceMotion = AppMotion.shouldReduceMotion()
  return Modifier.animateItem(
    fadeInSpec = AppMotion.Effect.Alpha,
    placementSpec = if (reduceMotion) null else AppMotion.Spatial.StandardOffset,
    fadeOutSpec = AppMotion.Effect.Alpha,
  )
}
