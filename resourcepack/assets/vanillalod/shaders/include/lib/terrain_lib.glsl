#ifndef VANILLALOD_TERRAIN_LIB_GLSL
#define VANILLALOD_TERRAIN_LIB_GLSL

// Vanilla's cylindrical fog distance also counts height, which fogs out all real terrain once the camera climbs past
// the render distance fog while the LOD ring around it stays clear. Chunks load by horizontal distance, so only that
// counts here.
float terrainFogDistance(vec3 ws_position) {
    return length(ws_position.xz);
}

#endif
