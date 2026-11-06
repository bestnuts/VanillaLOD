package me.bestnuts.lod.view;

import me.bestnuts.lod.cache.LodWorldCache;
import me.bestnuts.lod.encode.LodCell;
import me.bestnuts.lod.encode.TileGlyphs;
import me.bestnuts.lod.encode.TileText;

final class TileEncoder {

    static final int SAMPLED_RING = (TileGlyphs.sampledGridSize(TileKey.CELLS) - TileKey.CELLS) / 2;

    private TileEncoder() {
    }

    static TileTexts encode(LodWorldCache cache, TileVariant variant) {
        TileKey tile = variant.tile();
        int cellBlocks = tile.cellBlocks();
        int sampledSize = TileGlyphs.sampledGridSize(TileKey.CELLS);
        LodCell[] sampled = new LodCell[sampledSize * sampledSize];
        for (int row = 0; row < sampledSize; row++) {
            for (int column = 0; column < sampledSize; column++) {
                int cellX = tile.originX() + (column - SAMPLED_RING) * cellBlocks;
                int cellZ = tile.originZ() + (row - SAMPLED_RING) * cellBlocks;
                LodCell cell = cache.sample(cellX, cellZ, cellBlocks);
                if (cell != null && !variant.covered(cellX + cellBlocks / 2.0, cellZ + cellBlocks / 2.0, surfaceY(cache, cell))) {
                    sampled[row * sampledSize + column] = cell.quantized(cellBlocks);
                }
            }
        }
        LodCell[] grid = TileGlyphs.smoothUndersides(sampled, TileKey.CELLS, cellBlocks);
        int[] floors = ringFloors(cache, tile, grid);
        TileText.Glyph[] tops = TileGlyphs.tops(grid, TileKey.CELLS);
        TileText.Glyph[] walls = TileGlyphs.walls(grid, floors, TileKey.CELLS, cellBlocks, variant.wallView());
        TileText.Glyph[] underWalls = TileGlyphs.underWalls(grid, TileKey.CELLS, cellBlocks, variant.wallView());
        TileText.Glyph[] bottoms = TileGlyphs.bottoms(grid, TileKey.CELLS);
        int topGlyphs = glyphCount(tops);
        int wallGlyphs = glyphCount(walls);
        int underWallGlyphs = glyphCount(underWalls);
        int bottomGlyphs = glyphCount(bottoms);
        return new TileTexts(
                topGlyphs == 0 ? null : TileText.encode(tops, TileKey.CELLS),
                wallGlyphs == 0 ? null : TileText.encode(walls, TileGlyphs.wallColumns(TileKey.CELLS)),
                underWallGlyphs == 0 ? null : TileText.encode(underWalls, TileGlyphs.wallColumns(TileKey.CELLS)),
                bottomGlyphs == 0 ? null : TileText.encode(bottoms, TileKey.CELLS),
                topGlyphs,
                wallGlyphs + underWallGlyphs,
                bottomGlyphs
        );
    }

    private static int surfaceY(LodWorldCache cache, LodCell cell) {
        return cell.solid() ? cache.minY() + cell.height() : Integer.MAX_VALUE;
    }

    private static int[] ringFloors(LodWorldCache cache, TileKey tile, LodCell[] grid) {
        int gridSize = TileGlyphs.gridSize(TileKey.CELLS);
        int cellBlocks = tile.cellBlocks();
        int[] floors = TileGlyphs.noFloors(TileKey.CELLS);
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                boolean ring = row == 0 || column == 0 || row == gridSize - 1 || column == gridSize - 1;
                if (ring && grid[row * gridSize + column] != null) {
                    floors[row * gridSize + column] = cache.lowestSurface(
                            tile.originX() + (column - 1) * cellBlocks, tile.originZ() + (row - 1) * cellBlocks, cellBlocks);
                }
            }
        }
        return floors;
    }

    private static int glyphCount(TileText.Glyph[] glyphs) {
        int count = 0;
        for (TileText.Glyph glyph : glyphs) {
            if (glyph != null) {
                count++;
            }
        }
        return count;
    }
}
