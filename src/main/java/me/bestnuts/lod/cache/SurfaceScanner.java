package me.bestnuts.lod.cache;

import me.bestnuts.lod.encode.LodBits;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

public final class SurfaceScanner {

    private SurfaceScanner() {
    }

    public static ChunkColumns scan(ChunkSnapshot snapshot, int minY, SpritePalette palette) {
        ChunkColumns columns = new ChunkColumns();
        for (int z = 0; z < ChunkColumns.SIZE; z++) {
            for (int x = 0; x < ChunkColumns.SIZE; x++) {
                scanColumn(snapshot, minY, palette, columns, x, z);
            }
        }
        return columns;
    }

    private static void scanColumn(ChunkSnapshot snapshot, int minY, SpritePalette palette, ChunkColumns columns, int x, int z) {
        int y = snapshot.getHighestBlockYAt(x, z);
        Material material = snapshot.getBlockType(x, y, z);
        while (y > minY && !SurfaceMaterial.isSurface(material)) {
            y--;
            material = snapshot.getBlockType(x, y, z);
        }

        if (!SurfaceMaterial.isSurface(material)) {
            columns.set(x, z, y, ChunkColumns.NO_SURFACE, LodBits.NO_TINT);
            return;
        }
        SurfaceMaterial surface = SurfaceMaterial.of(material);
        int tint = BiomeTints.color(surface, snapshot.getBiome(x, y, z));
        columns.set(x, z, y, palette.id(surface.sprite()), tint);
    }
}
