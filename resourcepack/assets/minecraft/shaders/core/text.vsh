#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <vanillalod:lib/cell_lib.glsl>
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
layout(location = 4) out vec2 ls_cellQuad;
layout(location = 5) flat out float cellBlockSize;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    vec3 ws_position = Position;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    ls_cellQuad = vec2(0.0f);
    cellBlockSize = 0.0f;
    vec4 color = Color;

    if (cellIsMarked(Position)) {
        int packed = cellPacked(Color);
        float blocks = cellBlocks(Color);
        vec2 corner = cellCorner(gl_VertexID);

        ws_position.y += lodMarkerOffset + cellHeight(packed);
        ws_position.z += corner.y * lodRowGap * blocks;

        ls_cellQuad = corner;
        cellBlockSize = blocks;
        color = vec4(cellTint(packed), 1.0f);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(ws_position, 1.0f);
    sphericalVertexDistance = fog_spherical_distance(ws_position);
    cylindricalVertexDistance = fog_cylindrical_distance(ws_position);
    vertexColor = color * sample_lightmap(Sampler2, UV2);
#else
    gl_Position = ProjMat * ModelViewMat * vec4(ws_position, 1.0f);
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
