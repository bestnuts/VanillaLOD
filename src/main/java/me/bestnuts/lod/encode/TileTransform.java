package me.bestnuts.lod.encode;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Text display pose: glyph x maps to +x at 0.025 per unit, lines advance 10 units, sprites are 8 units wide.
 * Rotating -90 degrees around X lays the text flat with lines advancing towards +z.
 * Cell heights are relative to floorY; the shader recovers it from the marker offset regardless of the anchor height.
 */
public final class TileTransform {

    private static final float TEXT_UNIT = 0.025f;
    private static final float LINE_PITCH = 10.0f;
    private static final Quaternionf FLAT = new Quaternionf().rotationX((float) (-Math.PI / 2));
    private static final Quaternionf IDENTITY = new Quaternionf();

    private TileTransform() {
    }

    public static Transformation of(int level, int columns, int rows, double originX, double originZ, Location anchor, int floorY) {
        float cell = LodBits.cellBlocks(level);
        float scaleX = cell / (TileText.GLYPH_ADVANCE * TEXT_UNIT);
        float scaleZ = cell / (LINE_PITCH * TEXT_UNIT);

        double firstGlyphX = cell * (1.0 - TileText.GLYPH_ADVANCE * columns / 2.0) / TileText.GLYPH_ADVANCE;
        double firstRowZ = -cell * rows;

        Vector3f translation = new Vector3f(
                (float) (originX - anchor.getX() - firstGlyphX),
                (float) (floorY - anchor.getY()) - LodBits.MARKER_OFFSET,
                (float) (originZ - anchor.getZ() - firstRowZ)
        );
        return new Transformation(translation, new Quaternionf(FLAT), new Vector3f(scaleX, scaleZ, 1.0f), new Quaternionf(IDENTITY));
    }
}
