#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <vanillalod:lod.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 4) out vec2 lodQuad;
layout(location = 5) flat out float lodCellBlocks;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    vec3 position = Position;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    lodQuad = vec2(0.0);
    lodCellBlocks = 0.0;
    vec4 color = Color;

    if (lod_is_cell(Position)) {
        int packed = lod_packed(Color);
        float cellBlocks = lod_cell_blocks(Color);
        vec2 corner = lod_corner(gl_VertexID);

        position.y += LOD_MARKER_OFFSET + lod_height(packed);
        position.z += corner.y * LOD_ROW_GAP * cellBlocks;

        lodQuad = corner;
        lodCellBlocks = cellBlocks;
        color = vec4(lod_tint(packed), 1.0);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);
    vertexColor = color * sample_lightmap(Sampler2, UV2);
#else
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
