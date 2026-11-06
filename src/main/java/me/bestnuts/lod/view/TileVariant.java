package me.bestnuts.lod.view;

import me.bestnuts.lod.encode.WallView;

/**
 * Everything a tile's glyphs depend on. Viewers that resolve a tile to the same variant watch the same displays.
 */
record TileVariant(TileKey tile, WallView wallView, Hole hole) {

    /**
     * The circle left to real terrain around a viewer; a variant has none when no sampled cell reaches it.
     */
    record Hole(double centerX, double centerZ, double radius) {

        boolean covers(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz < radius * radius;
        }
    }

    static TileVariant of(TileKey tile, double viewX, double viewZ, double inner) {
        WallView wallView = WallView.of(tile.originX(), tile.originZ(), tile.blocks(), viewX, viewZ);
        boolean reachesHole = tile.nearestDistance(viewX, viewZ, TileEncoder.SAMPLED_RING * tile.cellBlocks()) < inner;
        return new TileVariant(tile, wallView, reachesHole ? new Hole(viewX, viewZ, inner) : null);
    }

    boolean covered(double x, double z) {
        return hole != null && hole.covers(x, z);
    }
}
