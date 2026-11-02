package me.bestnuts.lod;

import org.bukkit.configuration.ConfigurationSection;

public record LodSettings(
        double detail,
        int minLevel,
        int maxLevel,
        int maxDistance,
        int anchorStep,
        int updateIntervalTicks,
        int buildsPerUpdate,
        int pregenParallel,
        int saveIntervalMinutes
) {

    public static LodSettings from(ConfigurationSection config) {
        return new LodSettings(
                config.getDouble("detail", 2.0),
                config.getInt("min-level", 0),
                config.getInt("max-level", 6),
                config.getInt("max-distance", 1024),
                config.getInt("anchor-step", 16),
                config.getInt("update-interval-ticks", 10),
                config.getInt("builds-per-update", 8),
                config.getInt("pregen-parallel", 8),
                config.getInt("save-interval-minutes", 5)
        );
    }
}
