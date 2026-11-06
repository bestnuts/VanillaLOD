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

// Stretches the clear sky environmental fog so it ends at the horizon instead of hiding all LOD past 1024 blocks.
vec2 horizonEnvironmentalFog(float horizon) {
    vec2 fog = vec2(FogEnvironmentalStart, FogEnvironmentalEnd);
    if (FogEnvironmentalEnd < lodAtmosphericFogMin || FogEnvironmentalEnd >= horizon) {
        return fog;
    }
    return fog * (horizon / FogEnvironmentalEnd);
}

// Real terrain only exists within the horizontal render distance, so clear sky fog reads its horizontal distance and
// looking down from high above no longer fogs it out while the LOD around it stays clear. Water, lava and effect fog
// keep the true distance.
float horizonEnvironmentalDistance(float sphericalDistance, float horizontalDistance) {
    return FogEnvironmentalEnd < lodAtmosphericFogMin ? sphericalDistance : horizontalDistance;
}

#endif
