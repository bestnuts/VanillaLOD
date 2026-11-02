#ifndef VANILLALOD_LOD_CONF_GLSL
#define VANILLALOD_LOD_CONF_GLSL

// Must match me.bestnuts.lod.encode.LodBits.
const float lodMarkerOffset = 4096.0f;
const float lodDetectY = -2048.0f;

const int lodHeightShift = 15;
const int lodHeightMask = 0x1FF;
const int lodTintedBit = 1 << 14;
const int lodPayloadMask = 0x3FFF;
const int lodLevelMask = 0x7;
const int lodEmptyCell = 0xFFFFFF;

// Sprite glyphs are 8 units tall on a 10 unit text display line pitch.
const float lodRowGap = 0.2f;

// LOD cells sit beyond the vanilla render distance, up to the 4x far plane.
const float lodFogStartScale = 3.0f;
const float lodFogEndScale = 3.9f;

#endif
