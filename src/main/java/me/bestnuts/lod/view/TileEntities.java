package me.bestnuts.lod.view;

import java.util.List;
import java.util.stream.Stream;
import me.bestnuts.lod.encode.WallView;
import org.bukkit.entity.TextDisplay;

/**
 * Displays of one tile; a layer without any glyph has no display at all.
 */
record TileEntities(TextDisplay top, TextDisplay walls, TextDisplay bottom, int topGlyphs, int wallGlyphs, int bottomGlyphs, WallView wallView) {

    List<TextDisplay> displays() {
        return Stream.of(top, walls, bottom).filter(display -> display != null).toList();
    }

    boolean isValid() {
        return displays().stream().allMatch(TextDisplay::isValid);
    }

    void remove() {
        displays().forEach(TextDisplay::remove);
    }
}
