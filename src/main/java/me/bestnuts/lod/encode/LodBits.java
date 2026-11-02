package me.bestnuts.lod.encode;

// Must match resourcepack/assets/vanillalod/shaders/include/conf/lod_conf.glsl.
public final class LodBits {

    public static final float MARKER_OFFSET = 4096.0f;
    public static final int MAX_LEVEL = 7;
    public static final int MAX_HEIGHT = 510;
    public static final int NO_TINT = -1;
    public static final int EMPTY_CELL = 0xFFFFFF;

    private static final int HEIGHT_SHIFT = 15;
    private static final int TINTED_BIT = 1 << 14;
    private static final int LEVEL_OPACITY_BASE = 0xF8;

    private LodBits() {
    }

    public static int cell(int height, int tintRgb) {
        int packed = Math.clamp(height, 0, MAX_HEIGHT) << HEIGHT_SHIFT;
        if (tintRgb != NO_TINT) {
            packed |= TINTED_BIT | tintPayload(tintRgb);
        }
        return packed;
    }

    public static byte opacity(int level) {
        return (byte) (LEVEL_OPACITY_BASE | Math.clamp(level, 0, MAX_LEVEL));
    }

    public static int cellBlocks(int level) {
        return 1 << level;
    }

    private static int tintPayload(int rgb) {
        int red = (rgb >> 16 & 0xFF) >> 3;
        int green = (rgb >> 8 & 0xFF) >> 3;
        int blue = (rgb & 0xFF) >> 4;
        return red << 9 | green << 4 | blue;
    }
}
