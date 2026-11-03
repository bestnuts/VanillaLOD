package me.bestnuts.lod;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record LodSettings(
        double detail,
        int minLevel,
        int maxLevel,
        int maxDistance,
        int anchorGrid,
        int updateIntervalTicks,
        int buildsPerUpdate,
        int pregenParallel,
        int saveIntervalMinutes,
        boolean autoFill,
        boolean autoFillGenerate,
        int autoFillIntervalTicks,
        int autoFillQueue,
        boolean ceilingWorlds
) {

    // Ceiling dimensions such as the Nether fog out everything past ~96 blocks and scan their bedrock roof as surface,
    // so they get no LOD unless explicitly enabled.
    public boolean covers(World world) {
        return ceilingWorlds || !world.hasCeiling();
    }

    public static LodSettings from(ConfigurationSection config) {
        return new LodSettings(
                config.getDouble("detail", 2.0),
                config.getInt("min-level", 0),
                config.getInt("max-level", 6),
                config.getInt("max-distance", 4096),
                config.getInt("anchor-grid", 128),
                config.getInt("update-interval-ticks", 10),
                config.getInt("builds-per-update", 8),
                config.getInt("pregen-parallel", 8),
                config.getInt("save-interval-minutes", 5),
                config.getBoolean("auto-fill", true),
                config.getBoolean("auto-fill-generate", true),
                config.getInt("auto-fill-interval-ticks", 100),
                config.getInt("auto-fill-queue", 256),
                config.getBoolean("ceiling-worlds", false)
        );
    }
}
