/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File

private const val AVATAR_FILE_PREFIX = "profile_avatar_"
private const val AVATAR_SIZE_PX = 512

/** Rotates [bitmap] clockwise by [degrees], returning the original instance when no rotation is needed. */
internal fun rotateBitmap(
  bitmap: Bitmap,
  degrees: Int,
): Bitmap =
  if (degrees % 360 == 0) {
    bitmap
  } else {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

/** Maps the on-screen circular viewport back onto the rotated source and scales it to the avatar size. */
internal fun performCrop(
  bitmap: Bitmap,
  rotation: Int,
  userScale: Float,
  userOffset: Offset,
  viewportSizePx: Float,
): Bitmap {
  val rotated = rotateBitmap(bitmap, rotation)
  val bw = rotated.width.toFloat()
  val bh = rotated.height.toFloat()
  val totalScale = maxOf(viewportSizePx / bw, viewportSizePx / bh) * userScale
  val cropSize = (viewportSizePx / totalScale).coerceAtMost(minOf(bw, bh))
  val cropCenterX = bw / 2f - userOffset.x / totalScale
  val cropCenterY = bh / 2f - userOffset.y / totalScale
  val srcX = (cropCenterX - cropSize / 2f).toInt().coerceIn(0, (bw - cropSize).toInt().coerceAtLeast(0))
  val srcY = (cropCenterY - cropSize / 2f).toInt().coerceIn(0, (bh - cropSize).toInt().coerceAtLeast(0))
  val side = minOf(cropSize.toInt().coerceAtMost(rotated.width - srcX), cropSize.toInt().coerceAtMost(rotated.height - srcY)).coerceAtLeast(1)
  val scale = AVATAR_SIZE_PX.toFloat() / side
  return Bitmap.createBitmap(rotated, srcX, srcY, side, side, Matrix().apply { postScale(scale, scale) }, true)
}

/** Decodes [uri] into an upright, downsampled bitmap suitable for avatar cropping. */
internal fun decodeRawProfileAvatar(
  context: Context,
  uri: Uri,
): Bitmap? =
  runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / (sampleSize * 2) >= 2048) sampleSize *= 2
    val decoded =
      resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
      } ?: return null

    val rotation =
      resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90
          ExifInterface.ORIENTATION_ROTATE_180 -> 180
          ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 0
        }
      } ?: 0
    rotateBitmap(decoded, rotation)
  }.getOrNull()

/** Writes a replacement avatar and returns its path without deleting the currently referenced file. */
internal fun storeProfileAvatar(
  context: Context,
  bitmap: Bitmap?,
): String {
  if (bitmap == null) return ""
  val file = File(context.filesDir, "$AVATAR_FILE_PREFIX${System.currentTimeMillis()}.png")
  try {
    file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw java.io.IOException("PNG encode failed") }
  } catch (error: Exception) {
    file.delete()
    throw error
  }
  return file.absolutePath
}

/** Removes stale avatar files after the saved preference has been updated to [keepPath]. */
internal fun deleteStoredProfileAvatars(
  context: Context,
  keepPath: String,
) {
  context.filesDir.listFiles { file ->
    file.name.startsWith(AVATAR_FILE_PREFIX) && file.absolutePath != keepPath
  }.orEmpty().forEach(File::delete)
}
