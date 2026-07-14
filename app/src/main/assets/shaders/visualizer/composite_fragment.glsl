#version 300 es
precision mediump float;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform float uExposure;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 scene = texture(uScene, vUv).rgb;
    vec3 bloom = texture(uBloom, vUv).rgb;
    vec3 hdr = scene + bloom * uBloomStrength;
    vec3 mapped = vec3(1.0) - exp(-hdr * uExposure);
    mapped = pow(mapped, vec3(1.0 / 2.2));
    fragColor = vec4(mapped, 1.0);
}
