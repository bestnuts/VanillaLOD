package me.bestnuts.lod.encode;

import java.util.Set;
import net.kyori.adventure.key.Key;

/**
 * Builds glyph rows from a (size + 1)^2 cell grid whose last row and column belong to the neighbouring tiles.
 * Wall rows hold the +x edge walls first, then the +z edge walls.
 */
public final class TileGlyphs {

    private static final Key DIRT = Key.key("minecraft", "block/dirt");
    private static final Set<String> DIRT_SIDED = Set.of("block/grass_block_top", "block/podzol_top", "block/mycelium_top", "block/dirt_path_top");
    private static final String TOP_SUFFIX = "_top";
    private static final String BLOCK_PREFIX = "block/";

    private TileGlyphs() {
    }

    public static TileText.Glyph[] tops(LodCell[] grid, int size) {
        TileText.Glyph[] glyphs = new TileText.Glyph[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                LodCell cell = grid[row * (size + 1) + column];
                if (cell != null) {
                    glyphs[row * size + column] = new TileText.Glyph(cell.sprite(), LodBits.cell(cell.height(), cell.tint()));
                }
            }
        }
        return glyphs;
    }

    public static TileText.Glyph[] walls(LodCell[] grid, int size) {
        TileText.Glyph[] glyphs = new TileText.Glyph[2 * size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                LodCell cell = grid[row * (size + 1) + column];
                glyphs[row * size + column] = wall(cell, grid[row * (size + 1) + column + 1], false);
                glyphs[(size + row) * size + column] = wall(cell, grid[(row + 1) * (size + 1) + column], true);
            }
        }
        return glyphs;
    }

    private static TileText.Glyph wall(LodCell cell, LodCell next, boolean zAxis) {
        if (cell == null || next == null || cell.height() == next.height()) {
            return null;
        }
        boolean facesPositive = cell.height() > next.height();
        LodCell upper = facesPositive ? cell : next;
        int depth = Math.abs(cell.height() - next.height());

        String sprite = upper.sprite().value();
        if (DIRT_SIDED.contains(sprite)) {
            return new TileText.Glyph(DIRT, LodBits.wall(upper.height(), depth, zAxis, facesPositive, LodBits.NO_TINT));
        }
        Key side = upper.sprite();
        if (sprite.endsWith(TOP_SUFFIX)) {
            String base = sprite.substring(BLOCK_PREFIX.length(), sprite.length() - TOP_SUFFIX.length());
            side = SpriteNames.find(base, base + "_side").orElse(upper.sprite());
        }
        return new TileText.Glyph(side, LodBits.wall(upper.height(), depth, zAxis, facesPositive, upper.tint()));
    }
}
