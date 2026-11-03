package me.bestnuts.lod.cache;

import org.bukkit.World;

/**
 * When a surface column counts as floating: its solid run must end within maxThickness blocks and be followed by
 * airGap blocks of air, or by air all the way to the floor when airGap is {@link #AIR_TO_FLOOR}.
 */
public record FloatingRule(int airGap, int maxThickness) {

    public static final int AIR_TO_FLOOR = 0;

    private static final FloatingRule GROUND = new FloatingRule(AIR_TO_FLOOR, 64);

    // End islands are often thicker than 64 blocks and overhang smaller ledges, so any air below an island floats it
    // there; elsewhere caves would make cliffs stop at their ceilings, so only columns over pure air float.
    public static FloatingRule of(World world) {
        if (world.getEnvironment() == World.Environment.THE_END) {
            return new FloatingRule(1, world.getMaxHeight() - world.getMinHeight());
        }
        return GROUND;
    }
}
