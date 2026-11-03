package me.bestnuts.lod.cache;

import me.bestnuts.lod.encode.LodBits;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

public final class SurfaceScanner {

    private SurfaceScanner() {
    }

    public static ChunkColumns scan(ChunkSnapshot snapshot, int minY, FloatingRule rule, SpritePalette palette) {
        ChunkColumns columns = new ChunkColumns();
        for (int z = 0; z < ChunkColumns.SIZE; z++) {
            for (int x = 0; x < ChunkColumns.SIZE; x++) {
                scanColumn(snapshot, minY, rule, palette, columns, x, z);
            }
        }
        return columns;
    }

    private static void scanColumn(ChunkSnapshot snapshot, int minY, FloatingRule rule, SpritePalette palette, ChunkColumns columns, int x, int z) {
        int y = snapshot.getHighestBlockYAt(x, z);
        if (y < minY) {
            columns.set(x, z, minY, ChunkColumns.GROUNDED, ChunkColumns.NO_SURFACE, LodBits.NO_TINT);
            return;
        }
        Material material = snapshot.getBlockType(x, y, z);
        while (y > minY && !SurfaceMaterial.isSurface(material)) {
            y--;
            material = snapshot.getBlockType(x, y, z);
        }

        if (!SurfaceMaterial.isSurface(material)) {
            columns.set(x, z, y, ChunkColumns.GROUNDED, ChunkColumns.NO_SURFACE, LodBits.NO_TINT);
            return;
        }
        SurfaceMaterial surface = SurfaceMaterial.of(material);
        int tint = BiomeTints.color(surface, snapshot.getBiome(x, y, z));
        columns.set(x, z, y, bottom(snapshot, minY, rule, x, y, z), palette.id(surface.sprite()), tint);
    }

    private static int bottom(ChunkSnapshot snapshot, int minY, FloatingRule rule, int x, int topY, int z) {
        int bottom = topY;
        while (bottom > minY && topY - bottom < rule.maxThickness() && !snapshot.getBlockType(x, bottom - 1, z).isAir()) {
            bottom--;
        }
        if (bottom == minY || topY - bottom >= rule.maxThickness()) {
            return ChunkColumns.GROUNDED;
        }
        int lowestChecked = rule.airGap() == FloatingRule.AIR_TO_FLOOR ? minY : Math.max(minY, bottom - rule.airGap());
        for (int below = bottom - 1; below >= lowestChecked; below--) {
            if (!snapshot.getBlockType(x, below, z).isAir()) {
                return ChunkColumns.GROUNDED;
            }
        }
        return bottom;
    }
}
