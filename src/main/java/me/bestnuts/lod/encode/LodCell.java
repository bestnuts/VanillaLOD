package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;

/**
 * One LOD cell; heights are relative to the world floor and bottom is the underside of a floating cell.
 */
public record LodCell(Key sprite, int height, int tint, int bottom) {

    public static final int GROUNDED = Integer.MIN_VALUE;

    private static final int HEIGHT_STEPS_PER_CELL = 8;

    public LodCell(Key sprite, int height, int tint) {
        this(sprite, height, tint, GROUNDED);
    }

    public boolean floating() {
        return bottom != GROUNDED;
    }

    // Coarse cells snap to a height step of 1/8 of their size, so neighbours match more often and merge into fewer glyphs.
    public LodCell quantized(int cellBlocks) {
        int step = Math.max(1, cellBlocks / HEIGHT_STEPS_PER_CELL);
        if (step == 1) {
            return this;
        }
        return new LodCell(sprite, snap(height, step), tint, floating() ? snap(bottom, step) : GROUNDED);
    }

    private static int snap(int value, int step) {
        return Math.round((float) value / step) * step;
    }
}
