package me.bestnuts.lod.view;

import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.ChunkFiller;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Queues uncached chunks inside each player's LOD ring, nearest first, without flooding the shared chunk filler.
 */
public final class LodAutoFill {

    private static final ChunkFiller.Callback NOTHING = () -> {
    };

    private final Plugin plugin;
    private final ChunkFiller filler;
    private final LodSettings settings;

    public LodAutoFill(Plugin plugin, ChunkFiller filler, LodSettings settings) {
        this.plugin = plugin;
        this.filler = filler;
        this.settings = settings;
    }

    public void start() {
        if (settings.autoFill()) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::fillAll, settings.autoFillIntervalTicks(), settings.autoFillIntervalTicks());
        }
    }

    private void fillAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (filler.queued() >= settings.autoFillQueue()) {
                return;
            }
            fill(player);
        }
    }

    private void fill(Player player) {
        LodRange range = LodRange.of(player, settings);
        Location location = player.getLocation();
        World world = player.getWorld();
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int innerRing = (int) (range.inner() / 16.0);
        int outerRing = (int) Math.ceil(range.outer() / 16.0);
        double outerSquared = range.outer() * range.outer();

        for (int ring = innerRing; ring <= outerRing; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                int step = Math.abs(dz) == ring ? 1 : 2 * ring;
                for (int dx = -ring; dx <= ring; dx += step) {
                    double blockDx = dx * 16.0 + 8.0;
                    double blockDz = dz * 16.0 + 8.0;
                    if (blockDx * blockDx + blockDz * blockDz > outerSquared) {
                        continue;
                    }
                    filler.submit(world, centerX + dx, centerZ + dz, settings.autoFillGenerate(), NOTHING);
                    if (filler.queued() >= settings.autoFillQueue()) {
                        return;
                    }
                }
            }
        }
    }
}
