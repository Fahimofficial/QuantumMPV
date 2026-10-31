/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.support.SupportConfig
import com.quantummpv.app.support.SupportIntents
import com.quantummpv.app.support.UpiQrCode
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** Ko-fi's brand red, only used to tint the Ko-fi cup. */
private val KofiRed = Color(0xFFFF5E5B)
private const val COPIED_FEEDBACK_MS = 2_000L
private val QrSize = 232.dp

/** What the Support screen can do. Plain lambdas keep [SupportContent] stateless and previewable. */
@Immutable
internal class SupportActions(
  val onOpenUrl: (String) -> Unit,
  val onCopyUpiId: () -> Unit,
  val onOpenUpiApp: () -> Unit,
  val onShareQr: () -> Unit,
  val onSaveQr: () -> Unit,
)

/**
 * "Support my work": Ko-fi and UPI payment options, then QuantumMPV's own project links.
 * Nothing is collected or stored, and no payment is ever reported as made.
 */
@Serializable
object SupportScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val saveLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SupportIntents.QR_MIME_TYPE)) { uri ->
        if (uri != null) {
          val saved = SupportIntents.saveQr(context, uri)
          Toast
            .makeText(
              context,
              if (saved) R.string.support_qr_saved else R.string.support_qr_save_failed,
              Toast.LENGTH_SHORT,
            ).show()
        }
      }
    val actions =
      remember(context) {
        SupportActions(
          onOpenUrl = { url -> SupportIntents.openUrl(context, url) },
          onCopyUpiId = { SupportIntents.copyUpiId(context) },
          onOpenUpiApp = { SupportIntents.openUpiApp(context) },
          onShareQr = {
            if (!SupportIntents.shareQr(context)) {
              Toast.makeText(context, R.string.support_share_qr_failed, Toast.LENGTH_SHORT).show()
            }
          },
          onSaveQr = { saveLauncher.launch(SupportIntents.QR_FILE_NAME) },
        )
      }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.support_my_work),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backstack.popSafely() }) {
              Icon(
                imageVector = Icons.RoundedFilled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      Column(
        Modifier
          .fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding(),
      ) {
        SupportContent(actions)
      }
    }
  }
}

@Composable
internal fun SupportContent(actions: SupportActions) {
  val cs = MaterialTheme.colorScheme
  Text(
    text = stringResource(R.string.support_intro),
    style = MaterialTheme.typography.bodyLarge,
    color = cs.onSurface,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
  )

  PreferenceSectionHeader(title = stringResource(R.string.support_payment_options))
  PreferenceCard {
    SupportLinkRow(
      painter = painterResource(R.drawable.ic_kofi),
      iconTint = KofiRed,
      title = stringResource(R.string.support_kofi),
      subtitle = SupportConfig.KOFI_URL.withoutScheme(),
      onClick = { actions.onOpenUrl(SupportConfig.KOFI_URL) },
    )
  }
  PreferenceCard(emphasis = true) { UpiSection(actions) }

  // Project and contact links, kept apart from the payment options.
  PreferenceSectionHeader(title = stringResource(R.string.support_project_and_contact))
  Text(
    text = stringResource(R.string.support_project_info),
    style = MaterialTheme.typography.bodyMedium,
    color = cs.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
  )
  PreferenceCard {
    SupportLinkRow(
      painter = painterResource(R.drawable.ic_github),
      title = stringResource(R.string.support_app_repository),
      subtitle = SupportConfig.APP_REPOSITORY.withoutScheme(),
      onClick = { actions.onOpenUrl(SupportConfig.APP_REPOSITORY) },
    )
    PreferenceDivider()
    SupportLinkRow(
      icon = Icons.RoundedFilled.Language,
      title = stringResource(R.string.support_website),
      subtitle = SupportConfig.WEBSITE.withoutScheme(),
      onClick = { actions.onOpenUrl(SupportConfig.WEBSITE) },
    )
    PreferenceDivider()
    SupportLinkRow(
      painter = painterResource(R.drawable.ic_github),
      title = stringResource(R.string.support_github_profile),
      subtitle = SupportConfig.GITHUB_PROFILE.withoutScheme(),
      onClick = { actions.onOpenUrl(SupportConfig.GITHUB_PROFILE) },
    )
  }
  Spacer(Modifier.height(24.dp))
}

@Composable
private fun UpiSection(actions: SupportActions) {
  val cs = MaterialTheme.colorScheme
  Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
    Text(
      text = stringResource(R.string.support_upi),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = cs.onSurface,
      modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = stringResource(R.string.support_upi_scan_hint),
      style = MaterialTheme.typography.bodySmall,
      color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    UpiQr(Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.support_upi_id_label),
      style = MaterialTheme.typography.labelSmall,
      color = cs.outline,
    )
    SelectionContainer {
      Text(
        text = SupportConfig.UPI_ID,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = cs.onSurface,
      )
    }
    Text(
      text = stringResource(R.string.support_upi_payee, SupportConfig.UPI_PAYEE_NAME),
      style = MaterialTheme.typography.bodySmall,
      color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CopyUpiIdButton(actions.onCopyUpiId)
      SupportButton(
        icon = Icons.RoundedFilled.Favorite,
        text = stringResource(R.string.support_open_upi_app),
        primary = true,
        onClick = actions.onOpenUpiApp,
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SupportButton(Icons.RoundedFilled.Share, stringResource(R.string.support_share_qr), actions.onShareQr)
      SupportButton(Icons.RoundedFilled.Download, stringResource(R.string.support_save_qr), actions.onSaveQr)
    }
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.support_upi_verify_hint, SupportConfig.UPI_PAYEE_NAME),
      style = MaterialTheme.typography.bodySmall,
      color = cs.onSurfaceVariant,
    )
  }
}

/** Black on a white tile in both themes; one pixel per module, scaled without filtering. */
@Composable
private fun UpiQr(modifier: Modifier = Modifier) {
  val bitmap =
    remember { UpiQrCode.toBitmap(UpiQrCode.encode(SupportConfig.upiPaymentUri())).asImageBitmap() }
  val description =
    stringResource(R.string.support_upi_qr_description, SupportConfig.UPI_ID, SupportConfig.UPI_PAYEE_NAME)
  Box(
    modifier
      .clip(RoundedCornerShape(20.dp))
      .background(Color.White)
      .padding(8.dp)
      .semantics(mergeDescendants = true) { contentDescription = description },
  ) {
    Image(
      bitmap = bitmap,
      contentDescription = null,
      modifier = Modifier.size(QrSize),
      contentScale = ContentScale.FillBounds,
      filterQuality = FilterQuality.None,
    )
  }
}

@Composable
private fun RowScope.CopyUpiIdButton(onCopy: () -> Unit) {
  var copied by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(COPIED_FEEDBACK_MS)
      copied = false
    }
  }
  SupportButton(
    icon = if (copied) Icons.RoundedFilled.Check else Icons.RoundedFilled.ContentCopy,
    text = stringResource(if (copied) R.string.support_upi_id_copied else R.string.support_copy_upi_id),
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    onClick = {
      onCopy()
      copied = true
    },
  )
}

@Composable
private fun RowScope.SupportButton(
  icon: AppIcon,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  primary: Boolean = false,
) {
  val cs = MaterialTheme.colorScheme
  FilledTonalButton(
    onClick = onClick,
    modifier = modifier.weight(1f).heightIn(min = 48.dp),
    shape = RoundedCornerShape(12.dp),
    colors =
      if (primary) {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
          containerColor = cs.primary,
          contentColor = cs.onPrimary,
        )
      } else {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
      },
  ) {
    AnimatedContent(
      targetState = icon to text,
      transitionSpec = { fadeIn() togetherWith fadeOut() },
      label = "support_button_content",
    ) { (shownIcon, shownText) ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(shownIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(shownText, fontWeight = FontWeight.SemiBold, maxLines = 1)
      }
    }
  }
}

@Composable
private fun SupportLinkRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  icon: AppIcon? = null,
  painter: Painter? = null,
  iconTint: Color = MaterialTheme.colorScheme.primary,
) {
  val cs = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when {
      painter != null -> androidx.compose.material3.Icon(painter, null, Modifier.size(24.dp), tint = iconTint)
      icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
    }
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    Icon(Icons.RoundedFilled.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
  }
}

private fun String.withoutScheme(): String = removePrefix("https://").removeSuffix("/")
