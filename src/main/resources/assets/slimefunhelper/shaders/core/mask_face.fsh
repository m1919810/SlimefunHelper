#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float alpha = texture(InSampler, texCoord).a;
    if (alpha <= 0.0) discard;
    fragColor = vec4(ColorModulator.rgb, ColorModulator.a * alpha);
}
