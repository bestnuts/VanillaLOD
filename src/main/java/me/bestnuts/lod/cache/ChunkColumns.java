package me.bestnuts.lod.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class ChunkColumns {

    public static final int SIZE = 16;
    public static final int COLUMNS = SIZE * SIZE;

    private final short[] heights = new short[COLUMNS];
    private final short[] sprites = new short[COLUMNS];
    private final int[] tints = new int[COLUMNS];

    public void set(int x, int z, int topY, int sprite, int tint) {
        int index = index(x, z);
        heights[index] = (short) topY;
        sprites[index] = (short) sprite;
        tints[index] = tint;
    }

    public int topY(int x, int z) {
        return heights[index(x, z)];
    }

    public int sprite(int x, int z) {
        return sprites[index(x, z)];
    }

    public int tint(int x, int z) {
        return tints[index(x, z)];
    }

    public void write(DataOutput out) throws IOException {
        for (int i = 0; i < COLUMNS; i++) {
            out.writeShort(heights[i]);
            out.writeShort(sprites[i]);
            out.writeInt(tints[i]);
        }
    }

    public static ChunkColumns read(DataInput in) throws IOException {
        ChunkColumns columns = new ChunkColumns();
        for (int i = 0; i < COLUMNS; i++) {
            columns.heights[i] = in.readShort();
            columns.sprites[i] = in.readShort();
            columns.tints[i] = in.readInt();
        }
        return columns;
    }

    private static int index(int x, int z) {
        return z << 4 | x;
    }
}
