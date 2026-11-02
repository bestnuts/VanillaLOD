package me.bestnuts.lod.view;

import java.util.HashSet;
import java.util.Set;
import me.bestnuts.lod.LodSettings;

public final class TileSelector {

    private TileSelector() {
    }

    public static Set<TileKey> select(double x, double z, double innerRadius, double outerRadius, LodSettings settings) {
        Set<TileKey> tiles = new HashSet<>();
        int rootBlocks = TileKey.CELLS << settings.maxLevel();
        int minTileX = Math.floorDiv((int) Math.floor(x - outerRadius), rootBlocks);
        int maxTileX = Math.floorDiv((int) Math.ceil(x + outerRadius), rootBlocks);
        int minTileZ = Math.floorDiv((int) Math.floor(z - outerRadius), rootBlocks);
        int maxTileZ = Math.floorDiv((int) Math.ceil(z + outerRadius), rootBlocks);

        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                visit(new TileKey(settings.maxLevel(), tileX, tileZ), x, z, innerRadius, outerRadius, settings, tiles);
            }
        }
        return tiles;
    }

    private static void visit(TileKey tile, double x, double z, double innerRadius, double outerRadius, LodSettings settings, Set<TileKey> tiles) {
        double nearest = tile.nearestDistance(x, z);
        if (nearest > outerRadius || tile.farthestDistance(x, z) < innerRadius) {
            return;
        }
        if (tile.level() > settings.minLevel() && nearest < settings.detail() * tile.blocks()) {
            int childLevel = tile.level() - 1;
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) {
                    visit(new TileKey(childLevel, tile.tileX() * 2 + dx, tile.tileZ() * 2 + dz), x, z, innerRadius, outerRadius, settings, tiles);
                }
            }
            return;
        }
        tiles.add(tile);
    }
}
