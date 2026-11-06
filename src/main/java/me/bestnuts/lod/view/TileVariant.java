package me.bestnuts.lod.view;

import me.bestnuts.lod.encode.WallView;

/**
 * Everything a tile's glyphs depend on. Viewers that resolve a tile to the same variant watch the same displays.
 */
record TileVariant(TileKey tile, WallView wallView, Hole hole) {

    /**
     * The circle left to real terrain around a viewer, minus surfaces below the lowest layer the client still draws;
     * a variant has none when no sampled cell reaches it.
     */
    record Hole(double centerX, double centerZ, double radius, int terrainFloorY) {

        boolean covers(double x, double z, int surfaceY) {
            return inside(x, z) && surfaceY > terrainFloorY;
        }

        boolean inside(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz < radius * radius;
        }
    }

    static TileVariant of(TileKey tile, double viewX, double viewZ, LodRange range) {
        WallView wallView = WallView.of(tile.originX(), tile.originZ(), tile.blocks(), viewX, viewZ);
        boolean reachesHole = tile.nearestDistance(viewX, viewZ, TileEncoder.SAMPLED_RING * tile.cellBlocks()) < range.inner();
        return new TileVariant(tile, wallView, reachesHole ? new Hole(viewX, viewZ, range.inner(), range.terrainFloorY()) : null);
    }

    boolean covered(double x, double z, int surfaceY) {
        return hole != null && hole.covers(x, z, surfaceY);
    }

    /**
     * Whether switching from this variant to the next only hands cells over to real terrain, never takes any back.
     */
    boolean onlyYieldsTo(TileVariant next) {
        boolean yields = false;
        int cellBlocks = tile.cellBlocks();
        for (int row = 0; row < TileKey.CELLS; row++) {
            for (int column = 0; column < TileKey.CELLS; column++) {
                double x = tile.originX() + (column + 0.5) * cellBlocks;
                double z = tile.originZ() + (row + 0.5) * cellBlocks;
                boolean before = hole != null && hole.inside(x, z);
                boolean after = next.hole != null && next.hole.inside(x, z);
                if (before && !after) {
                    return false;
                }
                yields |= after && !before;
            }
        }
        return yields;
    }
}
