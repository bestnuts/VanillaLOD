package me.bestnuts.lod.view;

import net.kyori.adventure.text.Component;

/**
 * Encoded layers of one tile variant; a layer without any glyph is null.
 */
record TileTexts(Component top, Component walls, Component underWalls, Component bottom,
                 int topGlyphs, int wallGlyphs, int bottomGlyphs) {
}
