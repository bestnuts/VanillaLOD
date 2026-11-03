package me.bestnuts.lod.view;

import java.util.List;
import java.util.stream.Stream;
import org.bukkit.entity.TextDisplay;

/**
 * Displays of one tile; a layer without any glyph has no display at all.
 */
record TileEntities(TextDisplay top, TextDisplay walls, int topGlyphs, int wallGlyphs) {

    List<TextDisplay> displays() {
        return Stream.of(top, walls).filter(display -> display != null).toList();
    }

    boolean isValid() {
        return displays().stream().allMatch(TextDisplay::isValid);
    }

    void remove() {
        displays().forEach(TextDisplay::remove);
    }
}
