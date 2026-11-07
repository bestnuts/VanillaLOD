#ifndef VANILLALOD_LOD_CONF_GLSL
#define VANILLALOD_LOD_CONF_GLSL

// Must match me.bestnuts.lod.encode.LodBits.
// Glyphs sit lodMarkerOffset x horizon steps below their floor, so the band they fall in carries the horizon distance.
const float lodMarkerOffset = 4096.0f;
const float lodDetectY = -2048.0f;
const float lodHorizonStep = 1024.0f;

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
// Top tints are an index into a 6 x 7 x 6 colour cube (red, green, blue) that keeps more blue than RGB332.
const int lodTopTintMask = 0xFF;
const int lodTintRedLevels = 6;
const int lodTintGreenLevels = 7;
const int lodTintBlueLevels = 6;
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

// LOD fog ends at the horizon and starts halfway there. Real terrain keeps a fog stretched towards the 4x far plane,
// which only starts past its own edge.
const float lodFogStartFraction = 0.5f;
const float lodTerrainFogEndScale = 3.9f;

// Vanilla's clear sky environmental fog ends at 1024 blocks and rain only pulls it in by 256. Anything ending sooner
// is water, lava, a mob effect or boss fog, which LOD keeps as is.
const float lodAtmosphericFogMin = 256.0f;

// Vanilla clips at 4x the render distance. LOD past lodDepthKeepScale x render distance is squeezed linearly so the
// horizon lands at lodDepthLimitScale x render distance.
const float lodDepthKeepScale = 1.5f;
const float lodDepthLimitScale = 3.8f;
const float lodMinDepthRenderDistance = 16.0f;

// Render proxies carry horizon steps - 1 in their brightness override: the low digit as block light, the high one as
// sky light.
const float lodProxyLightLevels = 16.0f;

// Pushes LOD vertices slightly back along the view ray so overlapping real terrain always wins the depth test.
const float lodDepthPush = 0.004f;

#endif
