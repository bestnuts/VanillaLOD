package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

public final class TileText {

    public static final int GLYPH_ADVANCE = 8;

    private static final Key BLOCK_ATLAS = Key.key("minecraft", "blocks");
    private static final Key EMPTY_SPRITE = Key.key("minecraft", "block/stone");

    private TileText() {
    }

    public static Component encode(LodCell[] cells, int size) {
        TextComponent.Builder text = Component.text();
        for (int row = 0; row < size; row++) {
            if (row > 0) {
                text.append(Component.newline());
            }
            for (int column = 0; column < size; column++) {
                text.append(glyph(cells[row * size + column]));
            }
        }
        return text.build();
    }

    public static int lineWidth(int size) {
        return size * GLYPH_ADVANCE;
    }

    private static Component glyph(LodCell cell) {
        if (cell == null) {
            return sprite(EMPTY_SPRITE, LodBits.EMPTY_CELL);
        }
        return sprite(cell.sprite(), LodBits.cell(cell.height(), cell.tint()));
    }

    private static Component sprite(Key sprite, int packed) {
        return Component.object(ObjectContents.sprite(BLOCK_ATLAS, sprite)).color(TextColor.color(packed));
    }
}
