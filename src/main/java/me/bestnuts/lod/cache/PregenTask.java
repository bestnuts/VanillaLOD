package me.bestnuts.lod.cache;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public final class PregenTask extends BukkitRunnable {

    private static final int REPORT_EVERY = 1024;

    private final World world;
    private final LodCache cache;
    private final int parallel;
    private final Consumer<String> progress;
    private final Deque<int[]> pending = new ArrayDeque<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final int total;
    private int started;

    public PregenTask(World world, int centerChunkX, int centerChunkZ, int radius, LodCache cache, int parallel, Consumer<String> progress) {
        this.world = world;
        this.cache = cache;
        this.parallel = parallel;
        this.progress = progress;
        LodWorldCache worldCache = cache.world(world);
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && !worldCache.contains(centerChunkX + dx, centerChunkZ + dz)) {
                        pending.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
                    }
                }
            }
        }
        this.total = pending.size();
    }

    public int total() {
        return total;
    }

    @Override
    public void run() {
        while (inFlight.get() < parallel && !pending.isEmpty()) {
            int[] chunk = pending.poll();
            inFlight.incrementAndGet();
            started++;
            if (started % REPORT_EVERY == 0) {
                progress.accept(started + "/" + total);
            }
            world.getChunkAtAsync(chunk[0], chunk[1], true).whenComplete((loaded, error) -> {
                if (loaded != null && !cache.world(world).contains(chunk[0], chunk[1])) {
                    cache.capture(loaded);
                }
                inFlight.decrementAndGet();
            });
        }
        if (pending.isEmpty() && inFlight.get() == 0) {
            progress.accept("done " + total);
            cancel();
        }
    }
}
