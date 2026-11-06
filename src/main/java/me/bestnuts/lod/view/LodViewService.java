package me.bestnuts.lod.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.LodCache;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class LodViewService implements LodCache.Listener, Listener {

    private record CachedChunk(UUID world, long chunk) {
    }

    private final Plugin plugin;
    private final LodCache cache;
    private final LodSettings settings;
    private final ExecutorService builder = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("VanillaLOD-build-", 0).daemon().factory());
    private final Map<UUID, PlayerLodView> views = new HashMap<>();
    private final Map<UUID, SharedTiles> worlds = new HashMap<>();
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Queue<CachedChunk> cachedChunks = new ConcurrentLinkedQueue<>();
    private BukkitTask task;

    public LodViewService(Plugin plugin, LodCache cache, LodSettings settings) {
        this.plugin = plugin;
        this.cache = cache;
        this.settings = settings;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, settings.updateIntervalTicks(), settings.updateIntervalTicks());
    }

    public Optional<ViewStats> stats(Player player) {
        return Optional.ofNullable(views.get(player.getUniqueId())).map(PlayerLodView::stats);
    }

    public Optional<NetworkStats> network(World world) {
        return Optional.ofNullable(worlds.get(world.getUID())).map(SharedTiles::network);
    }

    public List<NetworkStats> network() {
        return worlds.values().stream().map(SharedTiles::network).toList();
    }

    public boolean toggle(Player player) {
        if (disabledPlayers.remove(player.getUniqueId())) {
            return true;
        }
        disabledPlayers.add(player.getUniqueId());
        close(player.getUniqueId());
        return false;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        views.values().forEach(PlayerLodView::close);
        views.clear();
        worlds.values().forEach(SharedTiles::close);
        worlds.clear();
        builder.shutdownNow();
    }

    @Override
    public void onChunkCached(World world, int chunkX, int chunkZ) {
        cachedChunks.add(new CachedChunk(world.getUID(), (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer().getUniqueId());
        disabledPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        Map<UUID, Set<Long>> changed = new HashMap<>();
        for (CachedChunk chunk; (chunk = cachedChunks.poll()) != null; ) {
            changed.computeIfAbsent(chunk.world(), id -> new HashSet<>()).add(chunk.chunk());
        }
        worlds.forEach((world, tiles) -> tiles.chunksChanged(changed.getOrDefault(world, Set.of())));

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (disabledPlayers.contains(player.getUniqueId())) {
                continue;
            }
            PlayerLodView view = views.get(player.getUniqueId());
            if (view != null && !view.world().equals(player.getWorld())) {
                close(player.getUniqueId());
                view = null;
            }
            if (!settings.covers(player.getWorld())) {
                continue;
            }
            if (view == null) {
                World world = player.getWorld();
                SharedTiles tiles = worlds.computeIfAbsent(world.getUID(),
                        id -> new SharedTiles(plugin, world, cache.world(world), settings, builder));
                view = new PlayerLodView(player, tiles, settings);
                views.put(player.getUniqueId(), view);
            }
            view.update();
        }
        worlds.values().forEach(SharedTiles::endTick);
    }

    private void close(UUID player) {
        PlayerLodView view = views.remove(player);
        if (view != null) {
            view.close();
        }
    }
}
