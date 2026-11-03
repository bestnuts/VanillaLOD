package me.bestnuts.lod.encode;

// Must match resourcepack/assets/vanillalod/shaders/include/conf/lod_conf.glsl.
public final class LodBits {

    public static final float MARKER_OFFSET = 4096.0f;
    // Clients clip at 4x the effective render distance; the shared fog fully hides LOD at this multiple.
    public static final double FOG_END_SCALE = 3.9;
    public static final int MAX_LEVEL = 7;
    public static final int MAX_HEIGHT = 510;
    public static final int MAX_WALL_DEPTH_STEPS = 31;
    public static final int MAX_TOP_RUN = 8;
    public static final int MAX_WALL_RUN = 4;
    public static final int NO_TINT = -1;
    public static final int EMPTY_CELL = 0xFFFFFF;

    public static final int KIND_TOP = 0;
    public static final int KIND_WALL = 1;
    public static final int KIND_BOTTOM = 2;
    public static final int KIND_UNDER_WALL = 3;

    private static final int OPACITY_BASE = 0xE0;
    private static final int KIND_SHIFT = 3;

    private static final int HEIGHT_SHIFT = 15;
    private static final int TINTED_BIT = 1 << 14;
    private static final int RUN_X_SHIFT = 11;
    private static final int RUN_Z_SHIFT = 8;

    private static final int DEPTH_SHIFT = 10;
    private static final int WALL_RUN_SHIFT = 8;
    private static final int WALL_Z_AXIS_BIT = 1 << 7;
    private static final int WALL_POSITIVE_BIT = 1 << 6;
    private static final int WALL_UNTINTED = 0x3F;

    private LodBits() {
    }

    public static int cell(int height, int tintRgb, int runX, int runZ) {
        int packed = height(height) | (Math.clamp(runX, 1, MAX_TOP_RUN) - 1) << RUN_X_SHIFT | (Math.clamp(runZ, 1, MAX_TOP_RUN) - 1) << RUN_Z_SHIFT;
        if (tintRgb != NO_TINT) {
            packed |= TINTED_BIT | topTint(tintRgb);
        }
        return packed;
    }

    public static int wall(int top, int depth, int cellBlocks, boolean zAxis, boolean facesPositive, int tintRgb) {
        int steps = Math.ceilDiv(Math.max(depth, 0), wallDepthStep(cellBlocks));
        int packed = height(top) | Math.min(steps, MAX_WALL_DEPTH_STEPS) << DEPTH_SHIFT;
        if (zAxis) {
            packed |= WALL_Z_AXIS_BIT;
        }
        if (facesPositive) {
            packed |= WALL_POSITIVE_BIT;
        }
        return packed | (tintRgb == NO_TINT ? WALL_UNTINTED : wallTint(tintRgb));
    }

    public static boolean wallFacesPositive(int packed) {
        return (packed & WALL_POSITIVE_BIT) != 0;
    }

    public static int withWallRun(int packed, int run) {
        return packed | (Math.clamp(run, 1, MAX_WALL_RUN) - 1) << WALL_RUN_SHIFT;
    }

    // Rounded up depth only extends walls below the lower neighbour, where they stay hidden.
    private static int wallDepthStep(int cellBlocks) {
        return Math.max(1, cellBlocks / 2);
    }

    public static byte opacity(int kind, int level) {
        return (byte) (OPACITY_BASE | kind << KIND_SHIFT | Math.clamp(level, 0, MAX_LEVEL));
    }

    public static int cellBlocks(int level) {
        return 1 << level;
    }

    private static int height(int height) {
        return Math.clamp(height, 0, MAX_HEIGHT) << HEIGHT_SHIFT;
    }

    private static int topTint(int rgb) {
        int red = (rgb >> 16 & 0xFF) >> 5;
        int green = (rgb >> 8 & 0xFF) >> 5;
        int blue = (rgb & 0xFF) >> 6;
        return red << 5 | green << 2 | blue;
    }

    private static int wallTint(int rgb) {
        int red = (rgb >> 16 & 0xFF) >> 6;
        int green = (rgb >> 8 & 0xFF) >> 6;
        int blue = (rgb & 0xFF) >> 6;
        return red << 4 | green << 2 | blue;
    }
}
