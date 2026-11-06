package me.bestnuts.lod.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Columns of one region at a fixed stride. Each entry stands for a stride x stride group: it keeps the column at the
 * group origin, which is what cell sampling reads, plus the lowest surface anywhere in the group.
 */
final class ColumnGrid {

    static final int REGION_BLOCKS = 512;
    static final short NOT_CACHED = -2;
    static final short NO_FLOOR = Short.MAX_VALUE;

    private final int stride;
    private final int size;
    private final short[] tops;
    private final short[] bottoms;
    private final short[] sprites;
    private final int[] tints;
    // Null at full resolution, where the lowest surface of a group is the column itself.
    private final short[] lowest;

    ColumnGrid(int stride) {
        this.stride = stride;
        this.size = REGION_BLOCKS / stride;
        int entries = size * size;
        tops = new short[entries];
        bottoms = new short[entries];
        sprites = new short[entries];
        tints = new int[entries];
        lowest = stride == 1 ? null : new short[entries];
        Arrays.fill(sprites, NOT_CACHED);
        if (lowest != null) {
            Arrays.fill(lowest, NO_FLOOR);
        }
    }

    int stride() {
        return stride;
    }

    int index(int localX, int localZ) {
        return (localZ / stride) * size + localX / stride;
    }

    int top(int index) {
        return tops[index];
    }

    int bottom(int index) {
        return bottoms[index];
    }

    int sprite(int index) {
        return sprites[index];
    }

    int tint(int index) {
        return tints[index];
    }

    int lowest(int index) {
        if (lowest != null) {
            return lowest[index];
        }
        return sprites[index] >= 0 ? tops[index] : NO_FLOOR;
    }

    boolean cached(int index) {
        return sprites[index] != NOT_CACHED;
    }

    void setChunk(int chunkX, int chunkZ, ChunkColumns columns) {
        for (int z = 0; z < ChunkColumns.SIZE; z++) {
            for (int x = 0; x < ChunkColumns.SIZE; x++) {
                int index = index(chunkX * ChunkColumns.SIZE + x, chunkZ * ChunkColumns.SIZE + z);
                tops[index] = (short) columns.topY(x, z);
                bottoms[index] = (short) columns.bottomY(x, z);
                sprites[index] = (short) columns.sprite(x, z);
                tints[index] = columns.tint(x, z);
            }
        }
    }

    ChunkColumns chunk(int chunkX, int chunkZ) {
        ChunkColumns columns = new ChunkColumns();
        for (int z = 0; z < ChunkColumns.SIZE; z++) {
            for (int x = 0; x < ChunkColumns.SIZE; x++) {
                int index = index(chunkX * ChunkColumns.SIZE + x, chunkZ * ChunkColumns.SIZE + z);
                columns.set(x, z, tops[index], bottoms[index], sprites[index], tints[index]);
            }
        }
        return columns;
    }

    void refreshChunk(ColumnGrid fine, int chunkX, int chunkZ) {
        int baseX = chunkX * ChunkColumns.SIZE;
        int baseZ = chunkZ * ChunkColumns.SIZE;
        for (int z = baseZ; z < baseZ + ChunkColumns.SIZE; z += stride) {
            for (int x = baseX; x < baseX + ChunkColumns.SIZE; x += stride) {
                int target = index(x, z);
                int origin = fine.index(x, z);
                tops[target] = fine.tops[origin];
                bottoms[target] = fine.bottoms[origin];
                sprites[target] = fine.sprites[origin];
                tints[target] = fine.tints[origin];
                int low = NO_FLOOR;
                for (int dz = 0; dz < stride; dz++) {
                    for (int dx = 0; dx < stride; dx++) {
                        low = Math.min(low, fine.lowest(fine.index(x + dx, z + dz)));
                    }
                }
                lowest[target] = (short) low;
            }
        }
    }

    void write(DataOutput out) throws IOException {
        for (int i = 0; i < sprites.length; i++) {
            out.writeShort(tops[i]);
            out.writeShort(bottoms[i]);
            out.writeShort(sprites[i]);
            out.writeInt(tints[i]);
            out.writeShort(lowest(i));
        }
    }

    static ColumnGrid read(DataInput in, int stride) throws IOException {
        ColumnGrid grid = new ColumnGrid(stride);
        for (int i = 0; i < grid.sprites.length; i++) {
            grid.tops[i] = in.readShort();
            grid.bottoms[i] = in.readShort();
            grid.sprites[i] = in.readShort();
            grid.tints[i] = in.readInt();
            short low = in.readShort();
            if (grid.lowest != null) {
                grid.lowest[i] = low;
            }
        }
        return grid;
    }
}
