#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 4) in vec2 lodQuad;
layout(location = 5) flat in float lodCellBlocks;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// LOD cells sit beyond the vanilla render distance, up to the 4x far plane.
const float LOD_FOG_START_SCALE = 3.0;
const float LOD_FOG_END_SCALE = 3.9;

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif

    #if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

    #ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    float renderDistanceStart = FogRenderDistanceStart;
    float renderDistanceEnd = FogRenderDistanceEnd;
    if (lodCellBlocks > 0.0) {
        renderDistanceStart = FogRenderDistanceEnd * LOD_FOG_START_SCALE;
        renderDistanceEnd = FogRenderDistanceEnd * LOD_FOG_END_SCALE;
    }

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, renderDistanceStart, renderDistanceEnd, fogColor);
    #endif

    return color;
}

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
vec4 sampleLodCell() {
    vec2 blocks = lodQuad * lodCellBlocks;
    vec2 uvPerBlock = fwidth(texCoord0) / max(fwidth(blocks), vec2(1e-6));
    vec2 spriteOrigin = texCoord0 - blocks * uvPerBlock;

    vec2 halfTexel = 0.5 / vec2(textureSize(Sampler0, 0));
    vec2 uv = spriteOrigin + clamp(fract(blocks) * uvPerBlock, halfTexel, uvPerBlock - halfTexel);

    vec4 texel = textureGrad(Sampler0, uv, dFdx(texCoord0) * lodCellBlocks, dFdy(texCoord0) * lodCellBlocks);
    return vec4(texel.rgb, 1.0);
}
#endif

void main() {
    #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    if (lodCellBlocks > 0.0) {
        vec4 lodColor = sampleLodCell() * vertexColor * ColorModulator;
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, lodColor.a);
        #else
        fragColor = calculateFinalColor(lodColor);
        #endif
        return;
    }
    #endif

    #ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
    #else
    vec4 texColor = texture(Sampler0, texCoord0);
    #endif

    vec4 color = texColor * vertexColor * ColorModulator;

    if (color.a < 0.1) {
        discard;
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
