package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

public final class TileText {

    public record Glyph(Key sprite, int packed) {
    }

    // An 8 unit sprite plus a 2 unit spacer; resourcepack/assets/vanillalod/font/cell.json defines the spacers.
    public static final int CELL_ADVANCE = 10;

    private static final Key BLOCK_ATLAS = Key.key("minecraft", "blocks");
    private static final Key CELL_FONT = Key.key("vanillalod", "cell");
    private static final Component SPACER = Component.text("");
    private static final Component EMPTY = Component.text("");

    private TileText() {
    }

    public static Component encode(Glyph[] glyphs, int columns) {
        TextComponent.Builder text = Component.text().font(CELL_FONT);
        for (int i = 0; i < glyphs.length; i++) {
            if (i > 0 && i % columns == 0) {
                text.append(Component.newline());
            }
            Glyph glyph = glyphs[i];
            if (glyph == null) {
                text.append(EMPTY);
            } else {
                text.append(sprite(glyph.sprite(), glyph.packed())).append(SPACER);
            }
        }
        return text.build();
    }

    public static int lineWidth(int columns) {
        return columns * CELL_ADVANCE;
    }

    private static Component sprite(Key sprite, int packed) {
        return Component.object(ObjectContents.sprite(BLOCK_ATLAS, sprite)).color(TextColor.color(packed));
    }
}
