package me.bestnuts.lod.view;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Horizontal position all displays of a tile group stand on; it always lies on the anchor grid.
 */
public record TileAnchor(int x, int z) {

    Location location(World world) {
        return new Location(world, x, world.getMaxHeight(), z);
    }

    double distance(Location location) {
        return Math.hypot(location.getX() - x, location.getZ() - z);
    }
}
