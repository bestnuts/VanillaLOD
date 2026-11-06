#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <vanillalod:conf/lod_conf.glsl>
#include <vanillalod:frag/cell_frag.glsl>
#include <vanillalod:frag/horizon_frag.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 4) in vec2 ls_cellQuad;
layout(location = 5) flat in float cellBlockSize;
layout(location = 6) in vec2 ls_texBlocks;
layout(location = 7) flat in float cellHorizon;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

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
    if (cellBlockSize > 0.0f) {
        renderDistanceStart = horizonFogStart(cellHorizon);
        renderDistanceEnd = horizonFogEnd(cellHorizon);
    }

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, renderDistanceStart, renderDistanceEnd, fogColor);
    #endif

    return color;
}

void main() {
    #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    if (cellBlockSize > 0.0f) {
        vec4 cellColor = cellSampleSprite(Sampler0, texCoord0, ls_cellQuad, ls_texBlocks) * vertexColor * ColorModulator;
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, cellColor.a);
        #else
        fragColor = calculateFinalColor(cellColor);
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
