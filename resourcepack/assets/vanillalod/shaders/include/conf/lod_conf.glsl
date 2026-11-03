#ifndef VANILLALOD_LOD_CONF_GLSL
#define VANILLALOD_LOD_CONF_GLSL

// Must match me.bestnuts.lod.encode.LodBits.
const float lodMarkerOffset = 4096.0f;
const float lodDetectY = -2048.0f;

const int lodLevelMask = 0x7;
const int lodKindShift = 3;
const int lodKindMask = 0x3;
const int lodKindTop = 0;
const int lodKindWall = 1;
const int lodKindBottom = 2;
const int lodKindUnderWall = 3;
const int lodEmptyCell = 0xFFFFFF;

const int lodHeightShift = 15;
const int lodHeightMask = 0x1FF;

const int lodTintedBit = 1 << 14;
const int lodRunXShift = 11;
const int lodRunZShift = 8;
const int lodTopRunMask = 0x7;
const int lodTopTintMask = 0xFF;
const int lodWallRunMask = 0x3;

// Wall depth is stored in steps of max(1, cell / 2) blocks, rounded up so walls never end short.
const int lodDepthShift = 10;
const int lodDepthMask = 0x1F;
const int lodWallRunShift = 8;
const int lodWallZAxisBit = 1 << 7;
const int lodWallPositiveBit = 1 << 6;
const int lodWallTintMask = 0x3F;

// A wall display starts one cell before the tile and stacks 17 x edge rows, then 17 z edge rows.
const float lodWallRows = 17.0f;

// Sprite glyphs are 8 units on a 10 unit cell pitch in both axes, so glyph edges land at 0 and 0.8 of a cell.
const float lodGlyphSize = 0.8f;
const float lodCornerPhaseLow = 0.4f;
const float lodCornerPhaseHigh = 0.9f;

const float lodWallXShade = 0.6f;
const float lodWallZShade = 0.8f;
const float lodBottomShade = 0.5f;

// Terrain and LOD share one render distance fog stretched towards the 4x far plane.
// lodFogEndScale must match me.bestnuts.lod.encode.LodBits.FOG_END_SCALE.
const float lodFogStartScale = 2.0f;
const float lodFogEndScale = 3.9f;

// Pushes LOD vertices slightly back along the view ray so overlapping real terrain always wins the depth test.
const float lodDepthPush = 0.004f;

#endif
