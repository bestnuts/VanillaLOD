package me.bestnuts.lod.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.ChunkFiller;
import me.bestnuts.lod.cache.LodCache;
import me.bestnuts.lod.cache.LodWorldCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Queues uncached chunks inside each player's LOD ring, nearest region first, without flooding the shared chunk filler.
 * Whole regions are skipped once cached, and a region fills in one go so its full resolution cache is read once.
 */
public final class LodAutoFill {

    private static final ChunkFiller.Callback NOTHING = () -> {
    };

    private record Region(int x, int z, double distance) {
    }

    private static final int REGION_BLOCKS = LodWorldCache.REGION_CHUNKS * 16;

    private final Plugin plugin;
    private final LodCache cache;
    private final ChunkFiller filler;
    private final LodSettings settings;

    public LodAutoFill(Plugin plugin, LodCache cache, ChunkFiller filler, LodSettings settings) {
        this.plugin = plugin;
        this.cache = cache;
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
            if (settings.covers(player.getWorld())) {
                fill(player);
            }
        }
    }

    private void fill(Player player) {
        LodRange range = LodRange.of(player, settings);
        Location location = player.getLocation();
        World world = player.getWorld();
        LodWorldCache worldCache = cache.world(world);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int innerRing = (int) (range.inner() / 16.0);
        double outerSquared = range.outer() * range.outer();

        for (Region region : regions(location, range.outer())) {
            if (worldCache.cachedChunks(region.x(), region.z()) == LodWorldCache.REGION_CHUNKS * LodWorldCache.REGION_CHUNKS) {
                continue;
            }
            int baseX = region.x() << LodWorldCache.REGION_SHIFT;
            int baseZ = region.z() << LodWorldCache.REGION_SHIFT;
            for (int chunkZ = baseZ; chunkZ < baseZ + LodWorldCache.REGION_CHUNKS; chunkZ++) {
                for (int chunkX = baseX; chunkX < baseX + LodWorldCache.REGION_CHUNKS; chunkX++) {
                    int dx = chunkX - centerX;
                    int dz = chunkZ - centerZ;
                    double blockDx = dx * 16.0 + 8.0;
                    double blockDz = dz * 16.0 + 8.0;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < innerRing || blockDx * blockDx + blockDz * blockDz > outerSquared) {
                        continue;
                    }
                    filler.submit(world, chunkX, chunkZ, settings.autoFillGenerate(), NOTHING);
                    if (filler.queued() >= settings.autoFillQueue()) {
                        return;
                    }
                }
            }
        }
    }

    private static List<Region> regions(Location location, double outer) {
        double x = location.getX();
        double z = location.getZ();
        int minX = Math.floorDiv((int) Math.floor(x - outer), REGION_BLOCKS);
        int maxX = Math.floorDiv((int) Math.ceil(x + outer), REGION_BLOCKS);
        int minZ = Math.floorDiv((int) Math.floor(z - outer), REGION_BLOCKS);
        int maxZ = Math.floorDiv((int) Math.ceil(z + outer), REGION_BLOCKS);
        List<Region> regions = new ArrayList<>();
        for (int regionZ = minZ; regionZ <= maxZ; regionZ++) {
            for (int regionX = minX; regionX <= maxX; regionX++) {
                double dx = Math.max(Math.max(regionX * (double) REGION_BLOCKS - x, 0.0), x - (regionX + 1) * (double) REGION_BLOCKS);
                double dz = Math.max(Math.max(regionZ * (double) REGION_BLOCKS - z, 0.0), z - (regionZ + 1) * (double) REGION_BLOCKS);
                double distance = Math.hypot(dx, dz);
                if (distance <= outer) {
                    regions.add(new Region(regionX, regionZ, distance));
                }
            }
        }
        regions.sort(Comparator.comparingDouble(Region::distance));
        return regions;
    }
}
