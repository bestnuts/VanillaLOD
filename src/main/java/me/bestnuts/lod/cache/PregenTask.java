package me.bestnuts.lod.cache;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class PregenTask extends BukkitRunnable {

    private static final int REPORT_EVERY = 1024;
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private final Consumer<String> progress;
    private final AtomicInteger completed = new AtomicInteger();
    private final int total;
    private int reported;

    public PregenTask(World world, int centerChunkX, int centerChunkZ, int radius, ChunkFiller filler, Consumer<String> progress) {
        this.progress = progress;
        int submitted = 0;
        for (int ring = 0; ring <= radius; ring++) {
            for (int step = 0; step < Math.max(1, ring * 8); step++) {
                int[] offset = ringOffset(ring, step);
                if (filler.submit(world, centerChunkX + offset[0], centerChunkZ + offset[1], true, completed::incrementAndGet)) {
                    submitted++;
                }
            }
        }
        this.total = submitted;
    }

    public int total() {
        return total;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    @Override
    public void run() {
        int done = completed.get();
        if (done >= total) {
            progress.accept("done " + total);
            cancel();
            return;
        }
        if (done / REPORT_EVERY > reported) {
            reported = done / REPORT_EVERY;
            progress.accept(done + "/" + total);
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
