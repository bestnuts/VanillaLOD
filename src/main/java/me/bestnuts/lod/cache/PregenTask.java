package me.bestnuts.lod.cache;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Fills a square of chunks one region at a time, nearest regions first. Regions keep the cache's full resolution loaded
 * only while they fill, and topping the filler up each second keeps the queue small however large the square is.
 */
public final class PregenTask extends BukkitRunnable {

    private static final int REPORT_EVERY = 16384;
    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final int QUEUE_TARGET = 4096;

    private final World world;
    private final ChunkFiller filler;
    private final Consumer<String> progress;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private final int centerRegionX;
    private final int centerRegionZ;
    private final int regionRings;
    private final long total;
    private final AtomicLong completed = new AtomicLong();
    private int ring;
    private int step;
    private long reported;

    public PregenTask(World world, int centerChunkX, int centerChunkZ, int radius, ChunkFiller filler, Consumer<String> progress) {
        this.world = world;
        this.filler = filler;
        this.progress = progress;
        minChunkX = centerChunkX - radius;
        maxChunkX = centerChunkX + radius;
        minChunkZ = centerChunkZ - radius;
        maxChunkZ = centerChunkZ + radius;
        centerRegionX = centerChunkX >> LodWorldCache.REGION_SHIFT;
        centerRegionZ = centerChunkZ >> LodWorldCache.REGION_SHIFT;
        regionRings = Math.max(
                Math.max(centerRegionX - (minChunkX >> LodWorldCache.REGION_SHIFT), (maxChunkX >> LodWorldCache.REGION_SHIFT) - centerRegionX),
                Math.max(centerRegionZ - (minChunkZ >> LodWorldCache.REGION_SHIFT), (maxChunkZ >> LodWorldCache.REGION_SHIFT) - centerRegionZ));
        long side = 2L * radius + 1;
        total = side * side;
    }

    public long total() {
        return total;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 1L, CHECK_INTERVAL_TICKS);
    }

    @Override
    public void run() {
        while (ring <= regionRings && filler.queued() < QUEUE_TARGET) {
            int[] offset = ringOffset(ring, step);
            submitRegion(centerRegionX + offset[0], centerRegionZ + offset[1]);
            if (++step >= Math.max(1, ring * 8)) {
                ring++;
                step = 0;
            }
        }
        long done = completed.get();
        if (ring > regionRings && done >= total) {
            progress.accept("done " + total);
            cancel();
            return;
        }
        if (done / REPORT_EVERY > reported) {
            reported = done / REPORT_EVERY;
            progress.accept(done + "/" + total);
        }
    }

    // Chunks the filler already has queued or cached are counted as done right away.
    private void submitRegion(int regionX, int regionZ) {
        int fromX = Math.max(minChunkX, regionX << LodWorldCache.REGION_SHIFT);
        int toX = Math.min(maxChunkX, (regionX << LodWorldCache.REGION_SHIFT) + LodWorldCache.REGION_CHUNKS - 1);
        int fromZ = Math.max(minChunkZ, regionZ << LodWorldCache.REGION_SHIFT);
        int toZ = Math.min(maxChunkZ, (regionZ << LodWorldCache.REGION_SHIFT) + LodWorldCache.REGION_CHUNKS - 1);
        for (int chunkZ = fromZ; chunkZ <= toZ; chunkZ++) {
            for (int chunkX = fromX; chunkX <= toX; chunkX++) {
                if (!filler.submit(world, chunkX, chunkZ, true, completed::incrementAndGet)) {
                    completed.incrementAndGet();
                }
            }
        }
    }

    // Walks the square ring of Chebyshev radius `ring` clockwise from its top-left corner.
    private static int[] ringOffset(int ring, int step) {
        if (ring == 0) {
            return new int[]{0, 0};
        }
        int side = 2 * ring;
        int edge = step / side;
        int along = step % side;
        return switch (edge) {
            case 0 -> new int[]{-ring + along, -ring};
            case 1 -> new int[]{ring, -ring + along};
            case 2 -> new int[]{ring - along, ring};
            default -> new int[]{-ring, ring - along};
        };
    }
}
