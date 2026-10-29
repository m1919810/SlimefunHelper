#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, texCoord0).a;
    if (alpha <= 0.0) discard;
    fragColor = vec4(ColorModulator.rgb, ColorModulator.a * alpha);
}
