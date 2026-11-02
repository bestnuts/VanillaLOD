#ifndef VANILLALOD_HORIZON_FRAG_GLSL
#define VANILLALOD_HORIZON_FRAG_GLSL

#include <minecraft:fog.glsl>
#include <vanillalod:conf/lod_conf.glsl>

float horizonFogStart() {
    return FogRenderDistanceEnd * lodFogStartScale;
}

float horizonFogEnd() {
    return FogRenderDistanceEnd * lodFogEndScale;
}

#endif
