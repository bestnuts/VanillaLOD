#ifndef VANILLALOD_PROXY_LIB_GLSL
#define VANILLALOD_PROXY_LIB_GLSL

#include <minecraft:fog.glsl>
#include <vanillalod:conf/lod_conf.glsl>
#include <vanillalod:frag/horizon_frag.glsl>

// Render proxies of far entities (LodRenderApi) are drawn shrunk towards the camera so their depth matches the LOD
// terrain. Only proxies sit past the render distance, since entities are tracked well inside it.
bool proxyIsFar(vec3 ws_position) {
    return length(ws_position) > max(FogRenderDistanceEnd, lodMinDepthRenderDistance);
}

// The server writes horizon steps - 1 into the proxy's brightness override, as block light plus sky light x levels.
float proxyHorizon(ivec2 lightUv) {
    int levels = int(lodProxyLightLevels);
    int index = lightUv.x / levels + (lightUv.y / levels) * levels;
    return float(index + 1) * lodHorizonStep;
}

// The brightness override is spent on the horizon, so proxies are lit as open sky.
ivec2 proxyLightUv() {
    int levels = int(lodProxyLightLevels);
    return ivec2(0, (levels - 1) * levels);
}

// Undoes cellDepthPosition without its depth push, giving the place the proxy stands for, which fog is measured from.
vec3 proxyOriginPosition(vec3 ws_position, float horizon) {
    float renderDistance = max(FogRenderDistanceEnd, lodMinDepthRenderDistance);
    float keep = renderDistance * lodDepthKeepScale;
    float limit = renderDistance * lodDepthLimitScale;
    float squeezed = length(ws_position);
    if (squeezed <= keep || horizon <= limit) {
        return ws_position;
    }
    float rayLength = keep + (min(squeezed, limit) - keep) * (horizon - keep) / (limit - keep);
    return ws_position * (rayLength / squeezed);
}

vec4 proxyApplyFog(vec4 color, float sphericalVertexDistance, float cylindricalVertexDistance, float horizon, vec4 fogColor) {
    vec2 environmentalFog = horizonEnvironmentalFog(horizon);
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, environmentalFog.x, environmentalFog.y,
            horizonFogStart(horizon), horizonFogEnd(horizon), fogColor);
}

#endif
