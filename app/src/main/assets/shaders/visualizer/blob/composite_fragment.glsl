#version 300 es
precision mediump float;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform float uExposure;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec4 scene = texture(uScene, vUv);
    vec3 bloom = texture(uBloom, vUv).rgb;
    vec3 hdr = scene.rgb + bloom * uBloomStrength;
    vec3 mapped = vec3(1.0) - exp(-hdr * uExposure);
    mapped = pow(mapped, vec3(1.0 / 2.2));
    float bloomCoverage = max(max(hdr.r, hdr.g), hdr.b) * 1.8;
    float visualCoverage = clamp(max(scene.a, bloomCoverage), 0.0, 1.0);
    float bottomFade = smoothstep(0.0, 0.22, vUv.y);
    float alpha = visualCoverage * bottomFade;
    fragColor = vec4(mapped * alpha, alpha);
}
