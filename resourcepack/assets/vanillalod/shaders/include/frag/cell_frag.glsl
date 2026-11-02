#ifndef VANILLALOD_CELL_FRAG_GLSL
#define VANILLALOD_CELL_FRAG_GLSL

// Rebuilds the sprite rectangle from screen derivatives and repeats it once per block.
vec4 cellSampleSprite(sampler2D atlas, vec2 uv, vec2 ls_cellQuad, vec2 ls_texBlocks) {
    vec2 spriteSizeUv = fwidth(uv) / max(fwidth(ls_cellQuad), vec2(1e-6f));
    vec2 spriteOriginUv = uv - ls_cellQuad * spriteSizeUv;

    vec2 halfPixelUv = 0.5f / vec2(textureSize(atlas, 0));
    vec2 tiledUv = spriteOriginUv + clamp(fract(ls_texBlocks) * spriteSizeUv, halfPixelUv, spriteSizeUv - halfPixelUv);

    vec4 spriteColor = textureGrad(atlas, tiledUv, dFdx(ls_texBlocks) * spriteSizeUv, dFdy(ls_texBlocks) * spriteSizeUv);
    return vec4(spriteColor.rgb, 1.0f);
}

#endif
