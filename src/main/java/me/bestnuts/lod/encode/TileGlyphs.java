package me.bestnuts.lod.encode;

import java.util.Set;
import net.kyori.adventure.key.Key;

/**
 * Builds glyph rows from a padded cell grid: (size + 2)^2 cells starting one cell before the tile on both axes.
 * The wall layer also starts one cell before the tile and holds (size + 1) x edge rows, then (size + 1) z edge rows,
 * each (size + 1) wide, so the tile owns every one of its border edges.
 */
public final class TileGlyphs {

    private static final Key DIRT = Key.key("minecraft", "block/dirt");
    private static final Set<String> DIRT_SIDED = Set.of("block/grass_block_top", "block/podzol_top", "block/mycelium_top", "block/dirt_path_top");
    private static final String TOP_SUFFIX = "_top";
    private static final String BLOCK_PREFIX = "block/";

    private TileGlyphs() {
    }

    public static int gridSize(int size) {
        return size + 2;
    }

    public static int wallColumns(int size) {
        return size + 1;
    }

    public static int wallRows(int size) {
        return 2 * (size + 1);
    }

    public static TileText.Glyph[] tops(LodCell[] grid, int size) {
        TileText.Glyph[] glyphs = new TileText.Glyph[size * size];
        boolean[] covered = new boolean[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                LodCell cell = cell(grid, size, row, column);
                if (cell == null || covered[row * size + column]) {
                    continue;
                }
                int runX = 1;
                while (runX < LodBits.MAX_RUN && column + runX < size && !covered[row * size + column + runX]
                        && cell.equals(cell(grid, size, row, column + runX))) {
                    runX++;
                }
                int runZ = 1;
                while (runZ < LodBits.MAX_RUN && row + runZ < size && rowMatches(grid, size, covered, cell, row + runZ, column, runX)) {
                    runZ++;
                }
                for (int dz = 0; dz < runZ; dz++) {
                    for (int dx = 0; dx < runX; dx++) {
                        covered[(row + dz) * size + column + dx] = true;
                    }
                }
                glyphs[row * size + column] = new TileText.Glyph(cell.sprite(), LodBits.cell(cell.height(), cell.tint(), runX, runZ));
            }
        }
        return glyphs;
    }

    public static TileText.Glyph[] walls(LodCell[] grid, int size, int cellBlocks) {
        int columns = wallColumns(size);
        TileText.Glyph[] glyphs = new TileText.Glyph[wallRows(size) * columns];
        for (int edge = 0; edge <= size; edge++) {
            for (int line = 1; line <= size; line++) {
                int row = line - 1;
                glyphs[line * columns + edge] = edgeWall(cell(grid, size, row, edge - 1), cell(grid, size, row, edge), edge, size, false, cellBlocks);
                int column = line - 1;
                glyphs[(columns + edge) * columns + line] = edgeWall(cell(grid, size, edge - 1, column), cell(grid, size, edge, column), edge, size, true, cellBlocks);
            }
        }
        return glyphs;
    }

    private static TileText.Glyph edgeWall(LodCell before, LodCell after, int edge, int size, boolean zAxis, int cellBlocks) {
        if (edge == 0) {
            return skirt(after, before, zAxis, false, cellBlocks);
        }
        if (edge == size) {
            return skirt(before, after, zAxis, true, cellBlocks);
        }
        return wall(before, after, zAxis);
    }

    // Border walls hang one cell below the lower side to cover seams against tiles of another level.
    private static TileText.Glyph skirt(LodCell inside, LodCell outside, boolean zAxis, boolean facesPositive, int cellBlocks) {
        if (inside == null) {
            return null;
        }
        int bottom = outside == null ? inside.height() : Math.min(inside.height(), outside.height());
        return face(inside, inside.height() - bottom + cellBlocks, zAxis, facesPositive);
    }

    private static TileText.Glyph wall(LodCell cell, LodCell next, boolean zAxis) {
        if (cell == null || next == null || cell.height() == next.height()) {
            return null;
        }
        boolean facesPositive = cell.height() > next.height();
        LodCell upper = facesPositive ? cell : next;
        return face(upper, Math.abs(cell.height() - next.height()), zAxis, facesPositive);
    }

    private static TileText.Glyph face(LodCell upper, int depth, boolean zAxis, boolean facesPositive) {
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

    private static boolean rowMatches(LodCell[] grid, int size, boolean[] covered, LodCell cell, int row, int column, int runX) {
        for (int dx = 0; dx < runX; dx++) {
            if (covered[row * size + column + dx] || !cell.equals(cell(grid, size, row, column + dx))) {
                return false;
            }
        }
        return true;
    }

    private static LodCell cell(LodCell[] grid, int size, int row, int column) {
        return grid[(row + 1) * gridSize(size) + column + 1];
    }
}
