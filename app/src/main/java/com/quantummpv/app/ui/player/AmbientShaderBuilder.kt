/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
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
  val formatted =
    String.format(Locale.US, "%.8f", normalized)
      .trimEnd('0')
      .trimEnd('.')
  return if (formatted.contains('.')) formatted else "$formatted.0"
}

private fun buildSpiralTapTable(
  name: String,
  samples: Int,
  thirdComponent: (radiusNorm: Double, indexNorm: Double) -> Double,
): String {
  val count = samples.coerceAtLeast(1)
  val taps =
    (0 until count).joinToString(",\n") { index ->
      val indexNorm = (index.toDouble() + 0.5) / count.toDouble()
      val radiusNorm = sqrt(indexNorm)
      val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
      val x = cos(theta) * radiusNorm
      val y = sin(theta) * radiusNorm
      "    vec3(${glslFloat(x)}, ${glslFloat(y)}, ${glslFloat(thirdComponent(radiusNorm, indexNorm))})"
    }
  return "const vec3 $name[$count] = vec3[$count](\n$taps\n);"
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

#define BLUR_SAMPLES     ${spec.blurSamples}
#define MAX_RADIUS       ${spec.maxRadius}
#define GLOW_INTENSITY   ${spec.glowIntensity}
#define SAT_BOOST        ${spec.satBoost}
#define BEZEL_DEPTH      ${spec.shared.bezelDepth}
#define VIGNETTE_STR     ${spec.shared.vignetteStrength}
#define WARMTH           ${spec.warmth}
#define FADE_CURVE       ${spec.fadeCurve}
#define OPACITY          ${spec.shared.opacity}
#define SCALE_X          ${spec.context.scaleX}
#define SCALE_Y          ${spec.context.scaleY}

const float PI = 3.14159265358979;
${buildSpiralTapTable("GLOW_TAPS", spec.blurSamples) { radiusNorm, _ -> radiusNorm }}

float rand(vec2 seed) {
    return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
}

float luma(vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec3 adjust_saturation(vec3 rgb, float amount) {
    return mix(vec3(luma(rgb)), rgb, amount);
}

vec3 apply_warmth(vec3 rgb, float amount) {
    rgb.r = clamp(rgb.r + amount * 0.060, 0.0, 1.0);
    rgb.g = clamp(rgb.g + amount * 0.025, 0.0, 1.0);
    rgb.b = clamp(rgb.b - amount * 0.080, 0.0, 1.0);
    return rgb;
}

vec4 hook() {
    vec2 uv = HOOKED_pos;
    vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // Stay half a texel inside the decoded frame when sampling the video edge.
    // Sampling exactly at 0/1 can pull in the texture border on some GPU/MPV paths,
    // which shows up as a one-pixel black seam between the video and ambient fill.
    vec2 half_texel = vec2(0.5) / HOOKED_size;
    vec2 safe_min = half_texel;
    vec2 safe_max = vec2(1.0) - half_texel;

    if (video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
        video_uv.y >= 0.0 && video_uv.y <= 1.0) {
        return HOOKED_tex(clamp(video_uv, safe_min, safe_max));
    }

    vec2 edge_origin = clamp(video_uv, safe_min, safe_max);
    float edge_dist = length(video_uv - clamp(video_uv, 0.0, 1.0));
    float edge_fade = exp(-edge_dist * (3.0 / max(MAX_RADIUS, 0.001)));

    float jitter = rand(uv * HOOKED_size) * (PI * 2.0);
    float jitter_s = sin(jitter);
    float jitter_c = cos(jitter);
    vec2 aspect_fix = vec2(HOOKED_size.y / HOOKED_size.x, 1.0);

    vec3 acc_color = vec3(0.0);
    float acc_weight = 0.0;

    for (int i = 0; i < BLUR_SAMPLES; i++) {
        vec3 tap = GLOW_TAPS[i];
        vec2 base_offset = tap.xy * MAX_RADIUS;
        float r = tap.z * MAX_RADIUS;

        vec2 offset = vec2(
            base_offset.x * jitter_c - base_offset.y * jitter_s,
            base_offset.x * jitter_s + base_offset.y * jitter_c
        ) * aspect_fix;
        vec2 sample_uv = clamp(edge_origin + offset, safe_min, safe_max);
        vec3 sample_rgb = HOOKED_tex(sample_uv).rgb;

        float dist_w = pow(max(1.0 / (1.0 + r * 40.0), 0.0), FADE_CURVE);
        float luma_w = 1.0 + luma(sample_rgb) * 2.0;
        float weight = dist_w * luma_w;

        acc_color += sample_rgb * weight;
        acc_weight += weight;
    }

    vec3 glow = (acc_color / max(acc_weight, 1e-5)) * GLOW_INTENSITY;
    glow = adjust_saturation(glow, SAT_BOOST);
    glow = apply_warmth(glow, WARMTH);
    glow *= edge_fade;

    float vig_r = length(uv - 0.5) * 2.0;
    glow *= mix(1.0, smoothstep(1.3, 0.1, vig_r), VIGNETTE_STR);

    vec4 ambient_out = vec4(glow * OPACITY, 1.0);

    // A zero bezel means a hard, gap-free handoff from video to ambience.
    // The old max(BEZEL_DEPTH, 0.001) fallback forced a tiny transition even
    // when bezel depth was disabled, which can become a visible ~1 px line.
    if (BEZEL_DEPTH <= 0.0) {
        return ambient_out;
    }

    vec2 outside_dist = max(max(-video_uv, video_uv - vec2(1.0)), vec2(0.0));
    float dist_to_edge = max(outside_dist.x, outside_dist.y);
    float bezel_alpha = smoothstep(0.0, BEZEL_DEPTH, dist_to_edge);

    vec4 edge_pixel = HOOKED_tex(edge_origin);
    return mix(edge_pixel, ambient_out, bezel_alpha);
}
    """.trimIndent()
}
