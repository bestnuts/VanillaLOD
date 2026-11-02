#ifndef VANILLALOD_CELL_FRAG_GLSL
#define VANILLALOD_CELL_FRAG_GLSL

// Rebuilds the sprite rectangle from screen derivatives and repeats it once per block.
vec4 cellSampleSprite(sampler2D atlas, vec2 uv, vec2 ls_cellQuad, float blocks) {
    vec2 ls_blocks = ls_cellQuad * blocks;
    vec2 uvPerBlock = fwidth(uv) / max(fwidth(ls_blocks), vec2(1e-6f));
    vec2 spriteOriginUv = uv - ls_blocks * uvPerBlock;

    vec2 halfPixelUv = 0.5f / vec2(textureSize(atlas, 0));
    vec2 tiledUv = spriteOriginUv + clamp(fract(ls_blocks) * uvPerBlock, halfPixelUv, uvPerBlock - halfPixelUv);

    vec4 spriteColor = textureGrad(atlas, tiledUv, dFdx(uv) * blocks, dFdy(uv) * blocks);
    return vec4(spriteColor.rgb, 1.0f);
}

#endif
