package me.bestnuts.lod.view;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.LodWorldCache;
import me.bestnuts.lod.encode.LodCell;
import me.bestnuts.lod.encode.TileDisplays;
import me.bestnuts.lod.encode.TileText;
import me.bestnuts.lod.encode.TileTransform;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;

final class PlayerLodView {

    private static final int FAR_PLANE_SCALE = 4;
    private static final int FAR_PLANE_MARGIN = 16;

    private final Plugin plugin;
    private final Player player;
    private final World world;
    private final LodWorldCache cache;
    private final LodSettings settings;
    private final Executor builder;
    private final Map<TileKey, TextDisplay> displays = new HashMap<>();
    private final Set<TileKey> building = new HashSet<>();
    private final Set<TileKey> dirty = new LinkedHashSet<>();
    private final Deque<TileKey> missing = new ArrayDeque<>();
    private Set<TileKey> desired = Set.of();
    private Location anchor;
    private double innerRadius = -1;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private boolean closed;

    PlayerLodView(Plugin plugin, Player player, LodWorldCache cache, LodSettings settings, Executor builder) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.cache = cache;
        this.settings = settings;
        this.builder = builder;
    }

    World world() {
        return world;
    }

    void update(Collection<Long> cachedChunks) {
        Location location = player.getLocation();
        displays.values().removeIf(display -> !display.isValid());

        if (anchor == null || horizontalDistance(location, anchor) > settings.anchorStep()) {
            reanchor(location);
        }

        int clientDistance = Math.min(player.getSendViewDistance(), player.getClientViewDistance());
        double inner = clientDistance * 16.0;
        double outer = Math.min(settings.maxDistance(), player.getClientViewDistance() * 16.0 * FAR_PLANE_SCALE - FAR_PLANE_MARGIN);
        desired = TileSelector.select(location.getX(), location.getZ(), inner, outer, settings);

        Iterator<Map.Entry<TileKey, TextDisplay>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TileKey, TextDisplay> entry = iterator.next();
            if (!desired.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
        dirty.retainAll(desired);

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (chunkX != lastChunkX || chunkZ != lastChunkZ || inner != innerRadius) {
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
            innerRadius = inner;
            for (TileKey tile : displays.keySet()) {
                if (tile.nearestDistance(location.getX(), location.getZ()) < inner + tile.blocks()) {
                    dirty.add(tile);
                }
            }
        }

        for (long chunk : cachedChunks) {
            int cx = (int) (chunk >> 32);
            int cz = (int) chunk;
            for (TileKey tile : displays.keySet()) {
                if (tile.overlapsChunk(cx, cz)) {
                    dirty.add(tile);
                }
            }
        }

        missing.clear();
        desired.stream()
                .filter(tile -> !displays.containsKey(tile) && !building.contains(tile))
                .sorted((a, b) -> Double.compare(a.nearestDistance(location.getX(), location.getZ()), b.nearestDistance(location.getX(), location.getZ())))
                .forEach(missing::add);

        startBuilds(location);
    }

    void close() {
        closed = true;
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
    }

    private void startBuilds(Location location) {
        int budget = settings.buildsPerUpdate() - building.size();
        Set<TileKey> deferred = new HashSet<>();
        while (budget > 0 && (!missing.isEmpty() || !dirty.isEmpty())) {
            TileKey tile = !missing.isEmpty() ? missing.poll() : pollDirty();
            if (building.contains(tile)) {
                deferred.add(tile);
                continue;
            }
            budget--;
            building.add(tile);
            double x = location.getX();
            double z = location.getZ();
            double inner = innerRadius;
            CompletableFuture.supplyAsync(() -> encode(tile, x, z, inner), builder)
                    .whenComplete((text, error) -> {
                        if (plugin.isEnabled()) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> apply(tile, text));
                        }
                    });
        }
        dirty.addAll(deferred);
    }

    private TileKey pollDirty() {
        Iterator<TileKey> iterator = dirty.iterator();
        TileKey tile = iterator.next();
        iterator.remove();
        return tile;
    }

    private Component encode(TileKey tile, double playerX, double playerZ, double inner) {
        int cellBlocks = tile.cellBlocks();
        double innerSquared = inner * inner;
        LodCell[] cells = new LodCell[TileKey.CELLS * TileKey.CELLS];
        for (int row = 0; row < TileKey.CELLS; row++) {
            for (int column = 0; column < TileKey.CELLS; column++) {
                int cellX = tile.originX() + column * cellBlocks;
                int cellZ = tile.originZ() + row * cellBlocks;
                double dx = cellX + cellBlocks / 2.0 - playerX;
                double dz = cellZ + cellBlocks / 2.0 - playerZ;
                if (dx * dx + dz * dz >= innerSquared) {
                    cells[row * TileKey.CELLS + column] = cache.sample(cellX, cellZ, cellBlocks);
                }
            }
        }
        return TileText.encode(cells, TileKey.CELLS);
    }

    private void apply(TileKey tile, Component text) {
        building.remove(tile);
        if (text == null || closed || !player.isOnline() || !desired.contains(tile)) {
            return;
        }
        TextDisplay display = displays.get(tile);
        if (display != null && display.isValid()) {
            display.text(text);
            return;
        }
        display = TileDisplays.spawn(anchor, tile.level(), TileKey.CELLS, transformation(tile), text);
        player.showEntity(plugin, display);
        displays.put(tile, display);
    }

    private void reanchor(Location location) {
        anchor = new Location(world, location.getBlockX() + 0.5, world.getMaxHeight(), location.getBlockZ() + 0.5);
        displays.forEach((tile, display) -> {
            display.teleport(anchor);
            display.setTransformation(transformation(tile));
        });
    }

    private Transformation transformation(TileKey tile) {
        return TileTransform.of(tile.level(), TileKey.CELLS, tile.originX(), tile.originZ(), anchor, cache.minY());
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
