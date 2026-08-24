/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AmbientRenderContext(
  val scaleX: Double,
  val scaleY: Double,
)

data class AmbientSharedShaderConfig(
  val bezelDepth: Float,
  val vignetteStrength: Float,
  val opacity: Float,
)

data class AmbientGlowShaderSpec(
  val context: AmbientRenderContext,
  val shared: AmbientSharedShaderConfig,
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val warmth: Float,
  val fadeCurve: Float,
)

data class AmbientGlowPreset(
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val vignetteStrength: Float,
  val warmth: Float,
  val fadeCurve: Float,
  val opacity: Float,
)

object AmbientShaderPresets {
  val glowFast = AmbientGlowPreset(8, 0.15f, 1.2f, 1.0f, 0.3f, 0.0f, 1.2f, 0.8f)
  val glowBalanced = AmbientGlowPreset(18, 0.28f, 1.45f, 1.25f, 0.55f, 0.0f, 1.7f, 1.0f)
  val glowHighQuality = AmbientGlowPreset(24, 0.35f, 1.5f, 1.3f, 0.7f, 0.0f, 1.8f, 1.0f)
}

fun matchesGlowPreset(
  preset: AmbientGlowPreset,
  blurSamples: Int,
  maxRadius: Float,
  glowIntensity: Float,
  satBoost: Float,
  vignetteStrength: Float,
  warmth: Float,
  fadeCurve: Float,
  opacity: Float,
): Boolean =
  blurSamples == preset.blurSamples &&
    closeTo(maxRadius, preset.maxRadius) &&
    closeTo(glowIntensity, preset.glowIntensity) &&
    closeTo(satBoost, preset.satBoost) &&
    closeTo(vignetteStrength, preset.vignetteStrength) &&
    closeTo(warmth, preset.warmth) &&
    closeTo(fadeCurve, preset.fadeCurve) &&
    closeTo(opacity, preset.opacity)

private fun closeTo(left: Float, right: Float, tolerance: Float = 0.01f): Boolean = abs(left - right) <= tolerance

private const val GOLDEN_ANGLE = 2.399963229728653

private fun glslFloat(value: Double): String {
  val normalized = if (abs(value) < 0.0000005) 0.0 else value
  val formatted = String.format(Locale.US, "%.8f", normalized).trimEnd('0').trimEnd('.')
  return if (formatted.contains('.')) formatted else "$formatted.0"
}

private fun buildGlowTapTable(samples: Int, maxRadius: Float, fadeCurve: Float): String {
  val count = samples.coerceAtLeast(1)
  val radius = maxRadius.toDouble()
  val curve = fadeCurve.toDouble()
  val taps =
    (0 until count).joinToString(",\n") { index ->
      val radiusNorm = sqrt((index.toDouble() + 0.5) / count.toDouble())
      val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
      val x = cos(theta) * radiusNorm * radius
      val y = sin(theta) * radiusNorm * radius
      val weight = (1.0 / (1.0 + radiusNorm * radius * 40.0)).pow(curve)
      "    vec3(${glslFloat(x)}, ${glslFloat(y)}, ${glslFloat(weight)})"
    }
  return "const vec3 GLOW_TAPS[$count] = vec3[$count](\n$taps\n);"
}

object AmbientShaderBuilder {
  fun build(
    @Suppress("UNUSED_PARAMETER") context: Context,
    spec: AmbientGlowShaderSpec,
  ): String =
    """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode
precision mediump float;

#define BLUR_SAMPLES     ${spec.blurSamples}
#define MAX_RADIUS       ${spec.maxRadius}
#define GLOW_INTENSITY   ${spec.glowIntensity}
#define SAT_BOOST        ${spec.satBoost}
#define BEZEL_DEPTH      ${spec.shared.bezelDepth}
#define VIGNETTE_STR     ${spec.shared.vignetteStrength}
#define WARMTH           ${spec.warmth}
#define OPACITY          ${spec.shared.opacity}
#define SCALE_X          ${spec.context.scaleX}
#define SCALE_Y          ${spec.context.scaleY}

${buildGlowTapTable(spec.blurSamples, spec.maxRadius, spec.fadeCurve)}

float luma(vec3 rgb) { return dot(rgb, vec3(0.2126, 0.7152, 0.0722)); }
vec3 adjust_saturation(vec3 rgb, float amount) { return mix(vec3(luma(rgb)), rgb, amount); }
vec3 apply_warmth(vec3 rgb, float amount) {
    rgb.r = clamp(rgb.r + amount * 0.060, 0.0, 1.0);
    rgb.g = clamp(rgb.g + amount * 0.025, 0.0, 1.0);
    rgb.b = clamp(rgb.b - amount * 0.080, 0.0, 1.0);
    return rgb;
}

vec4 hook() {
    highp vec2 uv = HOOKED_pos;
    highp vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;
    if (video_uv.x >= 0.0 && video_uv.x <= 1.0 && video_uv.y >= 0.0 && video_uv.y <= 1.0) {
        return HOOKED_tex(video_uv);
    }

    highp vec2 edge_origin = clamp(video_uv, 0.0, 1.0);
    float edge_dist = length(video_uv - edge_origin);
    float edge_fade = exp(-edge_dist * (3.0 / max(MAX_RADIUS, 0.001)));
    vec2 aspect_fix = vec2(HOOKED_size.y / HOOKED_size.x, 1.0);
    vec3 acc_color = vec3(0.0);
    float acc_weight = 0.0;
    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = GLOW_TAPS[i];
        vec3 sample_rgb = HOOKED_tex(clamp(edge_origin + tap.xy * aspect_fix, 0.0, 1.0)).rgb;
        float weight = tap.z * (1.0 + luma(sample_rgb) * 2.0);
        acc_color += sample_rgb * weight;
        acc_weight += weight;
    }

    vec3 glow = adjust_saturation((acc_color / max(acc_weight, 1e-5)) * GLOW_INTENSITY, SAT_BOOST);
    glow = apply_warmth(glow, WARMTH) * edge_fade;
    glow *= mix(1.0, smoothstep(1.3, 0.1, length(uv - 0.5) * 2.0), VIGNETTE_STR);
    float bezel = max(BEZEL_DEPTH, 0.001);
    vec2 outside_dist = max(max(-video_uv, video_uv - vec2(1.0)), vec2(0.0));
    float bezel_alpha = smoothstep(0.0, bezel, max(outside_dist.x, outside_dist.y));
    return mix(HOOKED_tex(edge_origin), vec4(glow * OPACITY, 1.0), bezel_alpha);
}
    """.trimIndent()
}
