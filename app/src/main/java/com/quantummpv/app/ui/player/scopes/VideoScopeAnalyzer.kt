/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.scopes

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

internal object VideoScopeAnalyzer {
  private const val OUTPUT_WIDTH = 320
  private const val OUTPUT_HEIGHT = 180
  private const val VECTOR_SIZE = 220

  fun analyze(source: Bitmap, mode: VideoScopeMode): Bitmap =
    when (mode) {
      VideoScopeMode.LumaWaveform -> lumaWaveform(source)
      VideoScopeMode.RgbyParade -> rgbyParade(source)
      VideoScopeMode.Vectorscope -> vectorscope(source)
    }

  private fun lumaWaveform(source: Bitmap): Bitmap {
    val sourcePixels = source.readPixels()
    val bins = IntArray(OUTPUT_WIDTH * OUTPUT_HEIGHT)
    val sumRed = LongArray(bins.size)
    val sumGreen = LongArray(bins.size)
    val sumBlue = LongArray(bins.size)

    for (y in 0 until source.height) {
      val row = y * source.width
      for (x in 0 until source.width) {
        val color = sourcePixels[row + x]
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val luma = (red * 0.2126f + green * 0.7152f + blue * 0.0722f) / 255f
        val column = x * OUTPUT_WIDTH / source.width
        val level = (luma * (OUTPUT_HEIGHT - 1)).toInt().coerceIn(0, OUTPUT_HEIGHT - 1)
        val index = column * OUTPUT_HEIGHT + level
        bins[index]++
        sumRed[index] += red
        sumGreen[index] += green
        sumBlue[index] += blue
      }
    }

    val maximum = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
    val logMaximum = ln(1f + maximum)
    val output = IntArray(OUTPUT_WIDTH * OUTPUT_HEIGHT)
    for (column in 0 until OUTPUT_WIDTH) {
      for (level in 0 until OUTPUT_HEIGHT) {
        val sourceIndex = column * OUTPUT_HEIGHT + level
        val count = bins[sourceIndex]
        if (count == 0) continue
        val intensity = (ln(1f + count) / logMaximum * 2.4f).coerceIn(0f, 1f)
        val averageRed = sumRed[sourceIndex].toFloat() / count
        val averageGreen = sumGreen[sourceIndex].toFloat() / count
        val averageBlue = sumBlue[sourceIndex].toFloat() / count
        val colorMax = max(averageRed, max(averageGreen, averageBlue)).coerceAtLeast(1f)
        val outputIndex = (OUTPUT_HEIGHT - 1 - level) * OUTPUT_WIDTH + column
        output[outputIndex] = Color.argb(
          (intensity * 255).toInt(),
          (averageRed / colorMax * 255).toInt(),
          (averageGreen / colorMax * 255).toInt(),
          (averageBlue / colorMax * 255).toInt(),
        )
      }
    }
    return output.toBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT)
  }

  private fun rgbyParade(source: Bitmap): Bitmap {
    val sourcePixels = source.readPixels()
    val gap = 2
    val channelWidth = (OUTPUT_WIDTH - gap * 3) / 4
    val bins = Array(4) { IntArray(channelWidth * OUTPUT_HEIGHT) }

    for (y in 0 until source.height) {
      val row = y * source.width
      for (x in 0 until source.width) {
        val color = sourcePixels[row + x]
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val column = x * channelWidth / source.width
        val columnOffset = column * OUTPUT_HEIGHT
        bins[0][columnOffset + red * (OUTPUT_HEIGHT - 1) / 255]++
        bins[1][columnOffset + green * (OUTPUT_HEIGHT - 1) / 255]++
        bins[2][columnOffset + blue * (OUTPUT_HEIGHT - 1) / 255]++
        val lumaLevel =
          ((red * 0.2126f + green * 0.7152f + blue * 0.0722f) * (OUTPUT_HEIGHT - 1) / 255f)
            .toInt()
            .coerceIn(0, OUTPUT_HEIGHT - 1)
        bins[3][columnOffset + lumaLevel]++
      }
    }

    val maximum = bins.maxOf { it.maxOrNull() ?: 0 }.coerceAtLeast(1)
    val colors = intArrayOf(Color.rgb(255, 45, 45), Color.rgb(45, 255, 70), Color.rgb(65, 95, 255), Color.rgb(225, 225, 225))
    val output = IntArray(OUTPUT_WIDTH * OUTPUT_HEIGHT)
    for (channel in bins.indices) {
      val xOffset = channel * (channelWidth + gap)
      for (column in 0 until channelWidth) {
        for (level in 0 until OUTPUT_HEIGHT) {
          val count = bins[channel][column * OUTPUT_HEIGHT + level]
          if (count == 0) continue
          val intensity = (count.toFloat() / (maximum * 0.22f)).coerceIn(0f, 1f)
          val tint = colors[channel]
          output[(OUTPUT_HEIGHT - 1 - level) * OUTPUT_WIDTH + xOffset + column] = Color.argb(
            255,
            (Color.red(tint) * intensity).toInt(),
            (Color.green(tint) * intensity).toInt(),
            (Color.blue(tint) * intensity).toInt(),
          )
        }
      }
    }
    return output.toBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT)
  }

  private fun vectorscope(source: Bitmap): Bitmap {
    val sourcePixels = source.readPixels()
    val bins = IntArray(VECTOR_SIZE * VECTOR_SIZE)
    val sumRed = LongArray(bins.size)
    val sumGreen = LongArray(bins.size)
    val sumBlue = LongArray(bins.size)
    val half = VECTOR_SIZE / 2f
    val scale = half * 0.9f

    for (color in sourcePixels) {
      val red = Color.red(color) / 255f
      val green = Color.green(color) / 255f
      val blue = Color.blue(color) / 255f
      val cb = -0.1146f * red - 0.3854f * green + 0.5f * blue
      val cr = 0.5f * red - 0.4542f * green - 0.0458f * blue
      val x = (half + cb * scale * 2f).toInt()
      val y = (half - cr * scale * 2f).toInt()
      if (x !in 0 until VECTOR_SIZE || y !in 0 until VECTOR_SIZE) continue
      val index = y * VECTOR_SIZE + x
      bins[index]++
      sumRed[index] += Color.red(color)
      sumGreen[index] += Color.green(color)
      sumBlue[index] += Color.blue(color)
    }

    val maximum = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
    val logMaximum = ln(1f + maximum)
    val output = IntArray(VECTOR_SIZE * VECTOR_SIZE)
    for (index in bins.indices) {
      val count = bins[index]
      if (count == 0) continue
      val intensity = (ln(1f + count) / logMaximum * 2.8f).coerceIn(0f, 1f)
      val averageRed = sumRed[index].toFloat() / count
      val averageGreen = sumGreen[index].toFloat() / count
      val averageBlue = sumBlue[index].toFloat() / count
      val colorMax = max(averageRed, max(averageGreen, averageBlue)).coerceAtLeast(1f)
      output[index] = Color.argb(
        (intensity * 255).toInt(),
        (averageRed / colorMax * 255).toInt(),
        (averageGreen / colorMax * 255).toInt(),
        (averageBlue / colorMax * 255).toInt(),
      )
    }
    return output.toBitmap(VECTOR_SIZE, VECTOR_SIZE)
  }

  private fun Bitmap.readPixels(): IntArray =
    IntArray(width * height).also { pixels ->
      getPixels(pixels, 0, width, 0, 0, width, height)
    }

  private fun IntArray.toBitmap(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(this, width, height, Bitmap.Config.ARGB_8888)
}