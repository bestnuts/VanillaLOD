package me.bestnuts.lod.api;

import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Registered in the Bukkit services manager. Lets other plugins draw proxies of far entities on top of the LOD terrain.
 */
public interface LodRenderApi {

    /**
     * Empty while the viewer has LOD off, stands in a world without LOD or has not received LOD yet.
     */
    Optional<LodProjection> projection(Player viewer);
}
