package me.bestnuts.lod.view;

import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.encode.LodBits;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * LOD covers the ring between the chunks the client actually renders and the horizon, where its fog ends, plus any
 * real terrain inside that ring the client stops drawing because it lies too far below the camera.
 */
public record LodRange(double inner, double outer, int terrainFloorY) {

    public static LodRange of(Player player, LodSettings settings) {
        World world = player.getWorld();
        int renderDistance = Math.min(player.getSendViewDistance(), player.getClientViewDistance());
        // The client's section graph never steps to a section more than the render distance away vertically from the
        // camera's section, so surfaces below that layer vanish when flying high even though their chunks are loaded.
        int cameraSection = Math.floorDiv(player.getEyeLocation().getBlockY(), 16);
        int terrainFloorY = Math.max(world.getMinHeight(), (cameraSection - renderDistance) * 16);
        double inner = terrainFloorY >= world.getMaxHeight() ? 0.0 : renderDistance * 16.0;
        return new LodRange(inner, Math.max(inner, LodBits.horizon(settings.maxDistance())), terrainFloorY);
    }

    /**
     * Tiles wholly inside the render distance are left out only while the client draws every section down to the
     * world floor.
     */
    double selectionInner(World world) {
        return terrainFloorY > world.getMinHeight() ? 0.0 : inner;
    }
}
