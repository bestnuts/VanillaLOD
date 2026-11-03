package me.bestnuts.lod.encode;

import net.kyori.adventure.key.Key;

public record LodCell(Key sprite, int height, int tint) {

    private static final int HEIGHT_STEPS_PER_CELL = 8;

    // Coarse cells snap to a height step of 1/8 of their size, so neighbours match more often and merge into fewer glyphs.
    public LodCell quantized(int cellBlocks) {
        int step = Math.max(1, cellBlocks / HEIGHT_STEPS_PER_CELL);
        if (step == 1) {
            return this;
        }
        return new LodCell(sprite, Math.round((float) height / step) * step, tint);
    }
}
