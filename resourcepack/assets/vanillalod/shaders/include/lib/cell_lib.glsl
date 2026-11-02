#ifndef VANILLALOD_CELL_LIB_GLSL
#define VANILLALOD_CELL_LIB_GLSL

#include <minecraft:globals.glsl>
#include <vanillalod:conf/lod_conf.glsl>

struct CellVertex {
    vec3 ws_position;
    vec2 ls_texBlocks;
    vec3 tint;
};

bool cellIsMarked(vec3 ws_position) {
    return ws_position.y < lodDetectY;
}

int cellPacked(vec4 color) {
    ivec3 channels = ivec3(round(color.rgb * 255.0f));
    return (channels.r << 16) | (channels.g << 8) | channels.b;
}

int cellOpacity(vec4 color) {
    return int(round(color.a * 255.0f));
}

bool cellIsEmpty(int packed) {
    return packed == lodEmptyCell;
}

float cellBlocks(int opacity) {
    return float(1 << (opacity & lodLevelMask));
}

int cellKind(int opacity) {
    return (opacity >> lodKindShift) & lodKindMask;
}

float cellHeight(int packed) {
    return float((packed >> lodHeightShift) & lodHeightMask);
}

vec3 cellTopTint(int packed) {
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

vec3 cellWallTint(int packed) {
    int tint = packed & lodWallTintMask;
    return vec3(float((tint >> 4) & 0x3), float((tint >> 2) & 0x3), float(tint & 0x3)) / 3.0f;
}

float cellCornerAxis(float phase) {
    return phase > lodCornerPhaseLow && phase < lodCornerPhaseHigh ? 1.0f : 0.0f;
}

// Vertex indices carry a per draw base offset, so corners come from the glyph edge phase on the world cell grid.
vec2 cellCorner(vec3 ws_position, float blocks) {
    ivec2 cameraCell = CameraBlockPos.xz & ivec2(int(blocks) - 1);
    vec2 phase = fract((ws_position.xz - CameraOffset.xz + vec2(cameraCell)) / blocks);
    return vec2(cellCornerAxis(phase.x), cellCornerAxis(phase.y));
}

vec3 cellOrigin(vec3 ws_position, vec2 corner, float blocks) {
    return ws_position - vec3(corner.x, 0.0f, corner.y) * blocks * lodGlyphSize;
}

CellVertex cellTopVertex(vec3 ws_origin, vec2 corner, float blocks, int packed) {
    vec3 ws_position = ws_origin + vec3(corner.x * blocks, lodMarkerOffset + cellHeight(packed), corner.y * blocks);
    return CellVertex(ws_position, corner * blocks, cellTopTint(packed));
}

// Walls face the lower neighbour so their winding stays front facing from that side.
CellVertex cellWallVertex(vec3 ws_origin, vec2 corner, float blocks, int packed) {
    float top = lodMarkerOffset + cellHeight(packed);
    float depth = float((packed >> lodDepthShift) & lodDepthMask);
    bool positive = (packed & lodWallPositiveBit) != 0;
    vec3 tint = cellWallTint(packed);

    if ((packed & lodWallZAxisBit) == 0) {
        float along = positive ? 1.0f - corner.y : corner.y;
        float height = corner.x > 0.5f ? top : top - depth;
        vec3 ws_position = ws_origin + vec3(blocks, height, along * blocks);
        return CellVertex(ws_position, vec2(along * blocks, top - height), tint * lodWallXShade);
    }

    bool upper = positive ? corner.y < 0.5f : corner.y > 0.5f;
    float height = upper ? top : top - depth;
    vec3 ws_position = ws_origin + vec3(corner.x * blocks, height, blocks - lodTileCells * blocks);
    return CellVertex(ws_position, vec2(corner.x * blocks, top - height), tint * lodWallZShade);
}

#endif
