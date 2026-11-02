#ifndef VANILLALOD_CELL_LIB_GLSL
#define VANILLALOD_CELL_LIB_GLSL

#include <vanillalod:conf/lod_conf.glsl>

bool cellIsMarked(vec3 ws_position) {
    return ws_position.y < lodDetectY;
}

int cellPacked(vec4 color) {
    ivec3 channels = ivec3(round(color.rgb * 255.0f));
    return (channels.r << 16) | (channels.g << 8) | channels.b;
}

bool cellIsEmpty(int packed) {
    return packed == lodEmptyCell;
}

float cellBlocks(vec4 color) {
    int level = int(round(color.a * 255.0f)) & lodLevelMask;
    return float(1 << level);
}

float cellHeight(int packed) {
    return float((packed >> lodHeightShift) & lodHeightMask);
}

vec3 cellTint(int packed) {
    if ((packed & lodTintedBit) == 0) {
        return vec3(1.0f);
    }
    int payload = packed & lodPayloadMask;
    return vec3(
        float((payload >> 9) & 0x1F) / 31.0f,
        float((payload >> 4) & 0x1F) / 31.0f,
        float(payload & 0xF) / 15.0f
    );
}

// Vertex order of a sprite glyph: 0 left-top, 1 left-bottom, 2 right-bottom, 3 right-top.
vec2 cellCorner(int vertexIndex) {
    int corner = vertexIndex % 4;
    return vec2(corner >= 2 ? 1.0f : 0.0f, (corner == 1 || corner == 2) ? 1.0f : 0.0f);
}

#endif
