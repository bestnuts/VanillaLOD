package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

public final class TileText {

    public record Glyph(Key sprite, int packed) {
    }

    public static final int GLYPH_ADVANCE = 8;

    private static final Key BLOCK_ATLAS = Key.key("minecraft", "blocks");
    // Two default font spaces keep the 8 unit sprite advance without emitting any quad.
    private static final Component EMPTY = Component.text("  ");

    private TileText() {
    }

    public static Component encode(Glyph[] glyphs, int columns) {
        TextComponent.Builder text = Component.text();
        for (int i = 0; i < glyphs.length; i++) {
            if (i > 0 && i % columns == 0) {
                text.append(Component.newline());
            }
            Glyph glyph = glyphs[i];
            text.append(glyph == null ? EMPTY : sprite(glyph.sprite(), glyph.packed()));
        }
        return text.build();
    }

    public static int lineWidth(int columns) {
        return columns * GLYPH_ADVANCE;
    }

    private static Component sprite(Key sprite, int packed) {
        return Component.object(ObjectContents.sprite(BLOCK_ATLAS, sprite)).color(TextColor.color(packed));
    }
}
