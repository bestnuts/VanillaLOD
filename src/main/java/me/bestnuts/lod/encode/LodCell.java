package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;

/**
 * One LOD cell; heights are relative to the world floor and bottom is the underside of a floating cell.
 * {@link #VOID} marks a cached cell without any surface, as opposed to null for a cell that is not cached.
 */
public record LodCell(Key sprite, int height, int tint, int bottom) {

    public static final int GROUNDED = Integer.MIN_VALUE;
    public static final LodCell VOID = new LodCell(null, 0, LodBits.NO_TINT, GROUNDED);

    private static final int HEIGHT_STEPS_PER_CELL = 8;

    public LodCell(Key sprite, int height, int tint) {
        this(sprite, height, tint, GROUNDED);
    }

    public boolean solid() {
        return this != VOID;
    }

    public boolean floating() {
        return bottom != GROUNDED;
    }

    // Coarse cells snap to a height step of 1/8 of their size, so neighbours match more often and merge into fewer glyphs.
    public LodCell quantized(int cellBlocks) {
        int step = Math.max(1, cellBlocks / HEIGHT_STEPS_PER_CELL);
        if (step == 1 || !solid()) {
            return this;
        }
        return new LodCell(sprite, snap(height, step), tint, floating() ? snap(bottom, step) : GROUNDED);
    }

    private static int snap(int value, int step) {
        return Math.round((float) value / step) * step;
    }
}
