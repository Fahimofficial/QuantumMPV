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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quantummpv.app.BuildConfig
import com.quantummpv.app.R
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.support.SupportConfig
import com.quantummpv.app.support.SupportIntents
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.LocalShowSettingsBackArrow
import com.quantummpv.app.ui.utils.popSafely
import com.quantummpv.app.utils.clipboard.SafeClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Ko-fi's brand red, used only to tint the Ko-fi mark. */
private val KofiRed = Color(0xFFFF5E5B)

/** Large enough to stay sharp when enlarged or saved, small enough to encode instantly. */
private const val QR_SIZE_PX = 768

/**
 * Support My Work: voluntary Ko-fi and UPI support, kept apart from project links.
 * All values come from [SupportConfig]; the QR code is generated on the device.
 */
@Serializable
object SupportScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val searchHighlight =
      rememberSettingsSearchHighlight(SupportScreen, scrollState, MaterialTheme.colorScheme.primary)
    val reduceMotion = AppMotion.shouldReduceMotion()
    var showLargeQr by remember { mutableStateOf(false) }

    val qrBitmap by produceState<Bitmap?>(initialValue = null) {
      value = withContext(Dispatchers.Default) { SupportIntents.createQrBitmap(QR_SIZE_PX) }
    }

    fun notify(message: String) {
      scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
      }
    }

    val linkFailed = stringResource(R.string.support_link_open_failed)
    val openLink: (String) -> Unit = { url ->
      if (!SupportIntents.openUrl(context, url)) notify(linkFailed)
    }

    val qrSaved = stringResource(R.string.support_qr_saved)
    val qrSaveFailed = stringResource(R.string.support_qr_save_failed)
    val saveLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = qrBitmap
        if (uri == null || bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
          val ok =
            withContext(Dispatchers.IO) {
              runCatching {
                context.contentResolver.openOutputStream(uri)?.use { SupportIntents.writeQrPng(bitmap, it) }
                  ?: error("No output stream")
              }.isSuccess
            }
          notify(if (ok) qrSaved else qrSaveFailed)
        }
      }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbar) },
      topBar = {
        TopAppBar(
          title = {
            Text(
              modifier = Modifier.settingsSearchTarget(R.string.support_title),
              text = stringResource(R.string.support_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            if (LocalShowSettingsBackArrow.current) {
              IconButton(onClick = { backstack.popSafely() }) {
                Icon(
                  imageVector = Icons.RoundedFilled.ArrowBack,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          },
        )
      },
    ) { padding ->
      val cs = MaterialTheme.colorScheme
      Column(
        modifier =
          Modifier
            .padding(padding)
            .then(searchHighlight)
            .verticalScroll(scrollState),
      ) {
        // Introduction
        PreferenceCard(emphasis = true) {
          Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.RoundedFilled.Favorite,
              contentDescription = null,
              tint = cs.primary,
              modifier = Modifier.padding(top = 2.dp).size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
              text = stringResource(R.string.support_intro),
              style = MaterialTheme.typography.bodyLarge,
              color = cs.onSurface,
            )
          }
        }

        // Payment options
        PreferenceSectionHeader(
          title = stringResource(R.string.support_section_payment),
          modifier = Modifier.semantics { heading() },
        )

        PreferenceCard {
          Column(modifier = Modifier.padding(16.dp)) {
            MethodHeader(
              painter = R.drawable.ic_kofi,
              tint = KofiRed,
              title = stringResource(R.string.support_kofi_title),
              subtitle = SupportConfig.displayUrl(SupportConfig.KOFI_URL),
            )
            Spacer(Modifier.height(10.dp))
            Text(
              text = stringResource(R.string.support_kofi_summary),
              style = MaterialTheme.typography.bodyMedium,
              color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
              onClick = { openLink(SupportConfig.KOFI_URL) },
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(painter = painterResource(R.drawable.ic_kofi), contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(8.dp))
              Text(stringResource(R.string.support_kofi_button), fontWeight = FontWeight.SemiBold)
            }
          }
        }

        PreferenceCard(modifier = Modifier.animateContentSize()) {
          Column(modifier = Modifier.padding(16.dp)) {
            MethodHeader(
              icon = Icons.RoundedFilled.MonetizationOn,
              tint = cs.tertiary,
              title = stringResource(R.string.support_upi_title),
              subtitle = SupportConfig.UPI_ID,
            )
            Spacer(Modifier.height(10.dp))
            Text(
              text = stringResource(R.string.support_upi_summary),
              style = MaterialTheme.typography.bodyMedium,
              color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            QrPanel(
              bitmap = qrBitmap,
              reduceMotion = reduceMotion,
              onClick = { showLargeQr = true },
              modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(16.dp))
            DetailRow(label = stringResource(R.string.support_upi_payee_label), value = SupportConfig.UPI_PAYEE_NAME)
            PreferenceDivider(Modifier.padding(vertical = 2.dp))
            val copied = stringResource(R.string.support_upi_id_copied)
            DetailRow(
              label = stringResource(R.string.support_upi_id_label),
              value = SupportConfig.UPI_ID,
              trailing = {
                IconButton(
                  onClick = {
                    SafeClipboard.copyPlainText(context, "simplyfahim_upi_id", SupportConfig.UPI_ID, showToast = false)
                    notify(copied)
                  },
                ) {
                  Icon(
                    imageVector = Icons.RoundedFilled.ContentCopy,
                    contentDescription = stringResource(R.string.support_copy_upi_id),
                    tint = cs.primary,
                  )
                }
              },
            )

            Spacer(Modifier.height(14.dp))
            val chooseApp = stringResource(R.string.support_choose_upi_app)
            val noUpiApp = stringResource(R.string.support_no_upi_app)
            Button(
              onClick = { if (!SupportIntents.openUpiApp(context, chooseApp)) notify(noUpiApp) },
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
            ) {
              Text(stringResource(R.string.support_open_upi_app), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              val shareText =
                stringResource(R.string.support_share_qr_text, SupportConfig.UPI_ID, SupportConfig.UPI_PAYEE_NAME)
              val shareTitle = stringResource(R.string.support_share_qr)
              val shareFailed = stringResource(R.string.support_qr_share_failed)
              OutlinedButton(
                onClick = {
                  val bitmap = qrBitmap ?: return@OutlinedButton
                  scope.launch {
                    val ok =
                      withContext(Dispatchers.IO) {
                        runCatching { SupportIntents.shareQr(context, bitmap, shareText, shareTitle) }.isSuccess
                      }
                    if (!ok) notify(shareFailed)
                  }
                },
                enabled = qrBitmap != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.RoundedFilled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(shareTitle)
              }
              OutlinedButton(
                onClick = { saveLauncher.launch(SupportIntents.QR_FILE_NAME) },
                enabled = qrBitmap != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(Icons.RoundedFilled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.support_save_qr))
              }
            }
            Spacer(Modifier.height(14.dp))
            Text(
              text = stringResource(R.string.support_upi_safety_note, SupportConfig.UPI_PAYEE_NAME),
              style = MaterialTheme.typography.bodySmall,
              color = cs.onSurfaceVariant,
            )
          }
        }

        // About the application: project and personal links, separate from payments.
        val appName = stringResource(R.string.app_name)
        PreferenceSectionHeader(
          title = stringResource(R.string.support_section_about, appName),
          modifier = Modifier.semantics { heading() },
        )
        PreferenceCard {
          Text(
            text =
              stringResource(
                R.string.support_project_info,
                appName,
                SupportConfig.PROJECT_LICENSE,
                BuildConfig.VERSION_NAME.substringBefore('-'),
              ),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
          PreferenceDivider()
          LinkRow(
            painter = R.drawable.ic_github,
            title = stringResource(R.string.support_repo_title),
            subtitle = SupportConfig.displayUrl(SupportConfig.PROJECT_REPOSITORY_URL),
            onClick = { openLink(SupportConfig.PROJECT_REPOSITORY_URL) },
          )
          PreferenceDivider()
          LinkRow(
            icon = Icons.RoundedFilled.Language,
            title = stringResource(R.string.support_website_title),
            subtitle = SupportConfig.displayUrl(SupportConfig.WEBSITE_URL),
            onClick = { openLink(SupportConfig.WEBSITE_URL) },
          )
          PreferenceDivider()
          LinkRow(
            icon = Icons.RoundedFilled.Person,
            title = stringResource(R.string.support_profile_title),
            subtitle = SupportConfig.displayUrl(SupportConfig.GITHUB_PROFILE_URL),
            onClick = { openLink(SupportConfig.GITHUB_PROFILE_URL) },
          )
        }

        Spacer(Modifier.height(24.dp))
      }
    }

    val bitmap = qrBitmap
    if (showLargeQr && bitmap != null) {
      LargeQrDialog(bitmap = bitmap, onDismiss = { showLargeQr = false })
    }
  }
}

@Composable
private fun MethodHeader(
  title: String,
  subtitle: String,
  tint: Color,
  icon: AppIcon? = null,
  @DrawableRes painter: Int? = null,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {
      Box(contentAlignment = Alignment.Center) {
        if (painter != null) {
          Icon(painter = painterResource(painter), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        } else if (icon != null) {
          Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
      }
    }
    Spacer(Modifier.size(12.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
  }
}

/** Always dark modules on white, in both themes, so every scanner can read it. */
@Composable
private fun QrPanel(
  bitmap: Bitmap?,
  reduceMotion: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val description =
    stringResource(R.string.support_upi_qr_description, SupportConfig.UPI_PAYEE_NAME, SupportConfig.UPI_ID)
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color.White,
    modifier =
      modifier
        .widthIn(max = 260.dp)
        .fillMaxWidth()
        .aspectRatio(1f)
        .clickable(role = Role.Image, onClickLabel = stringResource(R.string.support_qr_enlarged_title), onClick = onClick)
        .semantics(mergeDescendants = true) {},
  ) {
    AnimatedContent(
      targetState = bitmap,
      transitionSpec = {
        val ms = if (reduceMotion) 0 else 220
        fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
      },
      contentAlignment = Alignment.Center,
      label = "upi_qr",
    ) { current ->
      if (current == null) {
        Box(contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(32.dp))
        }
      } else {
        Image(
          bitmap = remember(current) { current.asImageBitmap() },
          contentDescription = description,
          filterQuality = FilterQuality.None,
          modifier = Modifier.padding(8.dp),
        )
      }
    }
  }
}

@Composable
private fun DetailRow(
  label: String,
  value: String,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
      Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    trailing?.invoke()
  }
}

@Composable
private fun LinkRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  icon: AppIcon? = null,
  @DrawableRes painter: Int? = null,
) {
  val cs = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (painter != null) {
      Icon(painter = painterResource(painter), contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
    } else if (icon != null) {
      Icon(imageVector = icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.size(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.outline)
    }
    Icon(imageVector = Icons.RoundedFilled.ChevronRight, contentDescription = null, tint = cs.outline)
  }
}

@Composable
private fun LargeQrDialog(
  bitmap: Bitmap,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
        Text(
          text = stringResource(R.string.support_qr_enlarged_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
          Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription =
              stringResource(R.string.support_upi_qr_description, SupportConfig.UPI_PAYEE_NAME, SupportConfig.UPI_ID),
            filterQuality = FilterQuality.None,
            modifier = Modifier.padding(8.dp),
          )
        }
        Spacer(Modifier.height(12.dp))
        Text(SupportConfig.UPI_ID, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(SupportConfig.UPI_PAYEE_NAME, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
          Text(stringResource(R.string.support_close), fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
