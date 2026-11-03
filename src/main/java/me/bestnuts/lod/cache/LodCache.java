package me.bestnuts.lod.cache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

public final class LodCache {

    public interface Listener {
        void onChunkCached(World world, int chunkX, int chunkZ);
    }

    private final Path root;
    private final Logger logger;
    private final ExecutorService scanner = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("VanillaLOD-scan-", 0).daemon().factory());
    private final Map<UUID, LodWorldCache> worlds = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public LodCache(Path root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public LodWorldCache world(World world) {
        return worlds.computeIfAbsent(world.getUID(), id -> {
            LodWorldCache cache = new LodWorldCache(root.resolve(world.getName()), world.getMinHeight(), FloatingRule.of(world));
            try {
                cache.load();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load LOD cache for " + world.getName(), e);
            }
            return cache;
        });
    }

    public void capture(Chunk chunk) {
        World world = chunk.getWorld();
        LodWorldCache cache = world(world);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, false);
        scanner.execute(() -> {
            ChunkColumns columns;
            try {
                columns = SurfaceScanner.scan(snapshot, cache.minY(), cache.floatingRule(), cache.palette());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failed to scan chunk " + snapshot.getX() + ", " + snapshot.getZ() + " in " + world.getName(), e);
                return;
            }
            cache.put(snapshot.getX(), snapshot.getZ(), columns);
            for (Listener listener : listeners) {
                listener.onChunkCached(world, snapshot.getX(), snapshot.getZ());
            }
        });
    }

    public void saveAll() {
        worlds.forEach((id, cache) -> {
            try {
                cache.saveDirty();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to save LOD cache " + id, e);
            }
        });
    }

    public void shutdown() {
        scanner.shutdown();
        try {
            scanner.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        saveAll();
    }
}
