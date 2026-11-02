package me.bestnuts.lod.cache;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Loads chunks missing from the LOD cache a few at a time, so manual pregen and automatic filling share one budget.
 */
public final class ChunkFiller {

    public interface Callback {
        void done();
    }

    private record Request(World world, int chunkX, int chunkZ, boolean generate, Callback callback) {
    }

    private record ChunkRef(UUID world, int chunkX, int chunkZ) {
    }

    private final LodCache cache;
    private final int parallel;
    private final Deque<Request> queue = new ArrayDeque<>();
    private final Set<ChunkRef> pending = ConcurrentHashMap.newKeySet();
    private final AtomicInteger inFlight = new AtomicInteger();

    public ChunkFiller(LodCache cache, int parallel) {
        this.cache = cache;
        this.parallel = parallel;
    }

    public void start(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean submit(World world, int chunkX, int chunkZ, boolean generate, Callback callback) {
        if (cache.world(world).contains(chunkX, chunkZ) || !pending.add(new ChunkRef(world.getUID(), chunkX, chunkZ))) {
            return false;
        }
        queue.add(new Request(world, chunkX, chunkZ, generate, callback));
        return true;
    }

    public int queued() {
        return queue.size();
    }

    private void tick() {
        while (inFlight.get() < parallel && !queue.isEmpty()) {
            Request request = queue.poll();
            if (cache.world(request.world()).contains(request.chunkX(), request.chunkZ())
                    || !request.generate() && !request.world().isChunkGenerated(request.chunkX(), request.chunkZ())) {
                finish(request);
                continue;
            }
            inFlight.incrementAndGet();
            request.world().getChunkAtAsync(request.chunkX(), request.chunkZ(), request.generate()).whenComplete((chunk, error) -> {
                if (chunk != null && !cache.world(request.world()).contains(request.chunkX(), request.chunkZ())) {
                    cache.capture(chunk);
                }
                inFlight.decrementAndGet();
                finish(request);
            });
        }
    }

    private void finish(Request request) {
        pending.remove(new ChunkRef(request.world().getUID(), request.chunkX(), request.chunkZ()));
        request.callback().done();
    }
}
