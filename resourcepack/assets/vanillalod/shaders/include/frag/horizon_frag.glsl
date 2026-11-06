#ifndef VANILLALOD_HORIZON_FRAG_GLSL
#define VANILLALOD_HORIZON_FRAG_GLSL

#include <minecraft:fog.glsl>
#include <vanillalod:conf/lod_conf.glsl>

float terrainHorizon() {
    return FogRenderDistanceEnd * lodTerrainFogEndScale;
}

float horizonFogStart(float horizon) {
    return horizon * lodFogStartFraction;
}

float horizonFogEnd(float horizon) {
    return horizon;
}

#endif
