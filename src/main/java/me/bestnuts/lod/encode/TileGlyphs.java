package me.bestnuts.lod.encode;

import java.util.Set;
import java.util.function.IntUnaryOperator;
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
                if (!isSolid(cell) || covered[row * size + column]) {
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

    public static TileText.Glyph[] bottoms(LodCell[] grid, int size) {
        LodCell[] undersides = new LodCell[grid.length];
        for (int i = 0; i < grid.length; i++) {
            LodCell cell = grid[i];
            if (isSolid(cell) && cell.floating()) {
                undersides[i] = new LodCell(cell.sprite(), cell.bottom(), cell.tint());
            }
        }
        return tops(undersides, size);
    }

    public static TileText.Glyph[] walls(LodCell[] grid, int size, int cellBlocks, WallView view) {
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
        for (int i = 0; i < glyphs.length; i++) {
            if (glyphs[i] != null && !view.shows(i >= columns * columns, LodBits.wallFacesPositive(glyphs[i].packed()))) {
                glyphs[i] = null;
            }
        }
        for (int edge = 0; edge <= size; edge++) {
            int xColumn = edge;
            int zRow = columns + edge;
            mergeAlongEdge(glyphs, size, line -> line * columns + xColumn);
            mergeAlongEdge(glyphs, size, line -> zRow * columns + line);
        }
        return glyphs;
    }

    private static void mergeAlongEdge(TileText.Glyph[] glyphs, int size, IntUnaryOperator index) {
        int line = 1;
        while (line <= size) {
            TileText.Glyph glyph = glyphs[index.applyAsInt(line)];
            if (glyph == null) {
                line++;
                continue;
            }
            int run = 1;
            while (run < LodBits.MAX_RUN && line + run <= size && glyph.equals(glyphs[index.applyAsInt(line + run)])) {
                glyphs[index.applyAsInt(line + run)] = null;
                run++;
            }
            glyphs[index.applyAsInt(line)] = new TileText.Glyph(glyph.sprite(), LodBits.withWallRun(glyph.packed(), run));
            line += run;
        }
    }

    private static TileText.Glyph edgeWall(LodCell before, LodCell after, int edge, int size, boolean zAxis, int cellBlocks) {
        if (edge == 0) {
            return skirt(after, before, zAxis, false, cellBlocks);
        }
        if (edge == size) {
            return skirt(before, after, zAxis, true, cellBlocks);
        }
        return wall(before, after, cellBlocks, zAxis);
    }

    // Border walls hang one cell below the lower side to cover seams against tiles of another level.
    // Towards the void a floating cell shows its thickness instead, so no curtain hangs into empty space.
    private static TileText.Glyph skirt(LodCell inside, LodCell outside, boolean zAxis, boolean facesPositive, int cellBlocks) {
        if (!isSolid(inside)) {
            return null;
        }
        if (outside == LodCell.VOID && inside.floating()) {
            return thickness(inside, cellBlocks, zAxis, facesPositive);
        }
        int bottom = (isSolid(outside) ? Math.min(inside.height(), outside.height()) : inside.height()) - cellBlocks;
        return face(inside, inside.height() - floorOf(inside, bottom), cellBlocks, zAxis, facesPositive);
    }

    private static TileText.Glyph wall(LodCell cell, LodCell next, int cellBlocks, boolean zAxis) {
        if (isSolid(cell) && next == LodCell.VOID && cell.floating()) {
            return thickness(cell, cellBlocks, zAxis, true);
        }
        if (cell == LodCell.VOID && isSolid(next) && next.floating()) {
            return thickness(next, cellBlocks, zAxis, false);
        }
        if (!isSolid(cell) || !isSolid(next) || cell.height() == next.height()) {
            return null;
        }
        boolean facesPositive = cell.height() > next.height();
        LodCell upper = facesPositive ? cell : next;
        LodCell lower = facesPositive ? next : cell;
        return face(upper, upper.height() - floorOf(upper, lower.height()), cellBlocks, zAxis, facesPositive);
    }

    // A floating cell's side ends at its own underside instead of reaching across the void to whatever lies below.
    private static int floorOf(LodCell cell, int bottom) {
        return cell.floating() ? Math.max(bottom, cell.bottom()) : bottom;
    }

    private static TileText.Glyph thickness(LodCell cell, int cellBlocks, boolean zAxis, boolean facesPositive) {
        return face(cell, cell.height() - cell.bottom(), cellBlocks, zAxis, facesPositive);
    }

    private static boolean isSolid(LodCell cell) {
        return cell != null && cell.solid();
    }

    private static TileText.Glyph face(LodCell upper, int depth, int cellBlocks, boolean zAxis, boolean facesPositive) {
        String sprite = upper.sprite().value();
        if (DIRT_SIDED.contains(sprite)) {
            return new TileText.Glyph(DIRT, LodBits.wall(upper.height(), depth, cellBlocks, zAxis, facesPositive, LodBits.NO_TINT));
        }
        Key side = upper.sprite();
        if (sprite.endsWith(TOP_SUFFIX)) {
            String base = sprite.substring(BLOCK_PREFIX.length(), sprite.length() - TOP_SUFFIX.length());
            side = SpriteNames.find(base, base + "_side").orElse(upper.sprite());
        }
        return new TileText.Glyph(side, LodBits.wall(upper.height(), depth, cellBlocks, zAxis, facesPositive, upper.tint()));
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
