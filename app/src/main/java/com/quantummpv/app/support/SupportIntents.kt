/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.quantummpv.app.R
import com.quantummpv.app.utils.clipboard.SafeClipboard
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Android side of the Support screen: opening links and UPI apps, and saving or sharing the QR
 * code. Every action is started by the user and never throws.
 */
object SupportIntents {
  const val QR_FILE_NAME = "SimplyFahim-UPI-QR.png"
  const val QR_MIME_TYPE = "image/png"

  /** Inside the existing `shared_files` cache path of the app's FileProvider. */
  private const val SHARE_DIR = "shared/support"
  private const val PNG_QUALITY = 100

  /** Opens an https link; if no app can, copies it and says so. */
  fun openUrl(
    context: Context,
    url: String,
  ) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      SafeClipboard.copyPlainText(context, label = "link", text = url, showToast = false)
      Toast.makeText(context, context.getString(R.string.support_no_app_for_link, url), Toast.LENGTH_LONG).show()
    }
  }

  fun copyUpiId(context: Context) {
    SafeClipboard.copyPlainText(context, label = "UPI ID", text = SupportConfig.UPI_ID, showToast = false)
  }

  /**
   * Opens the user's UPI app with the payee filled in: directly if there is one, with a chooser
   * if there are several. With none, copies the UPI ID and explains the options. Launching a
   * UPI app says nothing about whether a payment is made.
   */
  fun openUpiApp(context: Context) {
    val pay = Intent(Intent.ACTION_VIEW, SupportConfig.upiPaymentUri().toUri())

    // Visible through the `upi` <queries> entry in the manifest.
    @Suppress("DEPRECATION")
    val apps = context.packageManager.queryIntentActivities(pay, 0)
    try {
      when {
        apps.isEmpty() -> throw ActivityNotFoundException("No UPI app installed")
        apps.size == 1 -> context.startActivity(pay)
        else -> context.startActivity(Intent.createChooser(pay, context.getString(R.string.support_choose_upi_app)))
      }
    } catch (_: ActivityNotFoundException) {
      copyUpiId(context)
      Toast.makeText(context, R.string.support_no_upi_app, Toast.LENGTH_LONG).show()
    }
  }

  private fun writeQrPng(out: OutputStream) {
    val bitmap =
      UpiQrCode.toBitmap(UpiQrCode.encode(SupportConfig.upiPaymentUri()), UpiQrCode.EXPORT_PIXELS_PER_MODULE)
    try {
      check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG encoding failed" }
    } finally {
      bitmap.recycle()
    }
  }

  /** Writes the QR PNG to [uri] (from ACTION_CREATE_DOCUMENT, so no storage permission). */
  fun saveQr(
    context: Context,
    uri: Uri,
  ): Boolean =
    try {
      val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("No output stream")
      stream.use(::writeQrPng)
      true
    } catch (_: IOException) {
      false
    } catch (_: IllegalStateException) {
      false
    } catch (_: SecurityException) {
      false
    }

  /** Shares the QR PNG through the system share sheet with temporary read access only. */
  fun shareQr(context: Context): Boolean =
    try {
      val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
      val file = File(dir, QR_FILE_NAME)
      file.outputStream().use(::writeQrPng)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
      val text =
        context.getString(R.string.support_share_qr_text, SupportConfig.UPI_ID, SupportConfig.UPI_PAYEE_NAME)
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = QR_MIME_TYPE
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, text)
          clipData = ClipData.newRawUri(QR_FILE_NAME, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(
        Intent
          .createChooser(send, context.getString(R.string.support_share_qr))
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
      )
      true
    } catch (_: IOException) {
      false
    } catch (_: IllegalArgumentException) {
      false
    } catch (_: IllegalStateException) {
      false
    } catch (_: ActivityNotFoundException) {
      false
    }
}
