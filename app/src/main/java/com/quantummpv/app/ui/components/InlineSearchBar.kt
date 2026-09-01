/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.collect

@Composable
fun InlineSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: (String) -> Unit,
  modifier: Modifier = Modifier,
  inputFieldModifier: Modifier = Modifier,
  placeholder: (@Composable () -> Unit)? = null,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  shape: Shape = SearchBarDefaults.inputFieldShape,
  tonalElevation: Dp = SearchBarDefaults.TonalElevation,
  shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
  windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
) {
  val textFieldState = rememberTextFieldState(query)
  val currentQuery by rememberUpdatedState(query)
  val currentOnQueryChange by rememberUpdatedState(onQueryChange)

  LaunchedEffect(query, textFieldState) {
    if (textFieldState.text.toString() != query) {
      textFieldState.setTextAndPlaceCursorAtEnd(query)
    }
  }
  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.text.toString() }
      .collect { updatedQuery ->
        if (updatedQuery != currentQuery) currentOnQueryChange(updatedQuery)
      }
  }

  val colors = SearchBarDefaults.colors()
  Surface(
    // M3 SearchBar applied status-bar insets by default; keep that so top-bar usages
    // don't render under the status bar / display cutout.
    modifier = Modifier.windowInsetsPadding(windowInsets).then(modifier),
    shape = shape,
    color = colors.containerColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
  ) {
    TextField(
      state = textFieldState,
      modifier = inputFieldModifier.fillMaxWidth(),
      placeholder = placeholder,
      leadingIcon = leadingIcon,
      trailingIcon = trailingIcon,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      onKeyboardAction = { onSearch(textFieldState.text.toString()) },
      lineLimits = TextFieldLineLimits.SingleLine,
      shape = shape,
      colors = colors.inputFieldColors,
    )
  }
}
