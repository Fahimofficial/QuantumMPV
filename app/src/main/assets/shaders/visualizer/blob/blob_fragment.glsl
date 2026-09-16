#version 300 es
precision mediump float;

uniform vec3 uColor;
uniform float uIntensity;
in float vEnergy;
in float vSpectrum;
out vec4 fragColor;

void main() {
    vec3 emissive = uColor * uIntensity * (0.62 + vEnergy * 0.32 + vSpectrum * 0.2);
    fragColor = vec4(emissive, 0.72);
}
