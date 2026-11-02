package me.bestnuts.lod.view;

import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.encode.LodBits;
import org.bukkit.entity.Player;

/**
 * LOD covers the ring between the chunks the client actually renders and the shared fog end.
 */
public record LodRange(double inner, double outer) {

    public static LodRange of(Player player, LodSettings settings) {
        int renderDistance = Math.min(player.getSendViewDistance(), player.getClientViewDistance());
        double inner = renderDistance * 16.0;
        return new LodRange(inner, Math.min(settings.maxDistance(), inner * LodBits.FOG_END_SCALE));
    }
}
