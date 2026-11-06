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
layout(location = 6) out vec2 ls_texBlocks;
layout(location = 7) flat out float cellHorizon;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    vec3 ws_position = Position;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    ls_cellQuad = vec2(0.0f);
    ls_texBlocks = vec2(0.0f);
    cellBlockSize = 0.0f;
    cellHorizon = 0.0f;
    vec4 color = Color;
    vec3 ws_depthPosition = ws_position;

    if (cellIsMarked(Position)) {
        int packed = cellPacked(Color);
        if (cellIsEmpty(packed)) {
            gl_Position = vec4(2.0f, 2.0f, 2.0f, 1.0f);
            return;
        }

        float horizonSteps = cellHorizonSteps(Position);
        vec3 ws_marked = cellFirstBand(Position, horizonSteps);
        int opacity = cellOpacity(Color);
        float blocks = cellBlocks(opacity);
        vec2 corner = cellCorner(ws_marked, blocks);
        vec3 ws_origin = cellOrigin(ws_marked, corner, blocks);

        int kind = cellKind(opacity);
        CellVertex cell;
        if (kind == lodKindWall || kind == lodKindUnderWall) {
            cell = cellWallVertex(ws_origin, corner, blocks, packed);
        } else if (kind == lodKindBottom) {
            cell = cellBottomVertex(ws_origin, corner, blocks, packed);
        } else {
            cell = cellTopVertex(ws_origin, corner, blocks, packed);
        }

        ws_position = cell.ws_position;
        ls_cellQuad = corner;
        ls_texBlocks = cell.ls_texBlocks;
        cellBlockSize = blocks;
        cellHorizon = horizonSteps * lodHorizonStep;
        color = vec4(cell.tint, 1.0f);
        ws_depthPosition = cellDepthPosition(ws_position, cellHorizon);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(ws_depthPosition, 1.0f);
    sphericalVertexDistance = fog_spherical_distance(ws_position);
    cylindricalVertexDistance = fog_cylindrical_distance(ws_position);
    vertexColor = color * sample_lightmap(Sampler2, UV2);
#else
    gl_Position = ProjMat * ModelViewMat * vec4(ws_position, 1.0f);
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
