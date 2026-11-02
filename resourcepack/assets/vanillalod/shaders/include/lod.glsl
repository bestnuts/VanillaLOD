#ifndef VANILLALOD_LOD_GLSL
#define VANILLALOD_LOD_GLSL

// Must match me.bestnuts.lod.encode.LodBits.
const float LOD_MARKER_OFFSET = 4096.0;
const float LOD_DETECT_Y = -2048.0;

const int LOD_HEIGHT_SHIFT = 15;
const int LOD_HEIGHT_MASK = 0x1FF;
const int LOD_TINTED_BIT = 1 << 14;
const int LOD_PAYLOAD_MASK = 0x3FFF;
const int LOD_LEVEL_MASK = 0x7;

// Sprite glyphs are 8 units tall on a 10 unit text display line pitch.
const float LOD_ROW_GAP = 0.2;

bool lod_is_cell(vec3 position) {
    return position.y < LOD_DETECT_Y;
}

int lod_packed(vec4 color) {
    ivec3 c = ivec3(round(color.rgb * 255.0));
    return (c.r << 16) | (c.g << 8) | c.b;
}

float lod_cell_blocks(vec4 color) {
    int level = int(round(color.a * 255.0)) & LOD_LEVEL_MASK;
    return float(1 << level);
}

float lod_height(int packed) {
    return float((packed >> LOD_HEIGHT_SHIFT) & LOD_HEIGHT_MASK);
}

vec3 lod_tint(int packed) {
    if ((packed & LOD_TINTED_BIT) == 0) {
        return vec3(1.0);
    }
    int payload = packed & LOD_PAYLOAD_MASK;
    return vec3(
        float((payload >> 9) & 0x1F) / 31.0,
        float((payload >> 4) & 0x1F) / 31.0,
        float(payload & 0xF) / 15.0
    );
}

// Vertex order of a sprite glyph: 0 left-top, 1 left-bottom, 2 right-bottom, 3 right-top.
vec2 lod_corner(int vertexId) {
    int corner = vertexId % 4;
    return vec2(corner >= 2 ? 1.0 : 0.0, (corner == 1 || corner == 2) ? 1.0 : 0.0);
}

#endif
