package me.bestnuts.lod.cache;

import me.bestnuts.lod.encode.LodBits;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

public final class SurfaceScanner {

    public static final int AIR_TO_FLOOR = 0;

    private static final int THICKNESS_SCAN = 64;

    private SurfaceScanner() {
    }

    public static ChunkColumns scan(ChunkSnapshot snapshot, int minY, int floatingGap, SpritePalette palette) {
        ChunkColumns columns = new ChunkColumns();
        for (int z = 0; z < ChunkColumns.SIZE; z++) {
            for (int x = 0; x < ChunkColumns.SIZE; x++) {
                scanColumn(snapshot, minY, floatingGap, palette, columns, x, z);
            }
        }
        return columns;
    }

    private static void scanColumn(ChunkSnapshot snapshot, int minY, int floatingGap, SpritePalette palette, ChunkColumns columns, int x, int z) {
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
        columns.set(x, z, y, bottom(snapshot, minY, floatingGap, x, y, z), palette.id(surface.sprite()), tint);
    }

    // A column floats when the solid run under its surface ends within the scan and is followed by floatingGap blocks
    // of air, or by air all the way to the floor when floatingGap is AIR_TO_FLOOR.
    private static int bottom(ChunkSnapshot snapshot, int minY, int floatingGap, int x, int topY, int z) {
        int bottom = topY;
        while (bottom > minY && topY - bottom < THICKNESS_SCAN && !snapshot.getBlockType(x, bottom - 1, z).isAir()) {
            bottom--;
        }
        if (bottom == minY || topY - bottom >= THICKNESS_SCAN) {
            return ChunkColumns.GROUNDED;
        }
        int lowestChecked = floatingGap == AIR_TO_FLOOR ? minY : Math.max(minY, bottom - floatingGap);
        for (int below = bottom - 1; below >= lowestChecked; below--) {
            if (!snapshot.getBlockType(x, below, z).isAir()) {
                return ChunkColumns.GROUNDED;
            }
        }
        return bottom;
    }
}
