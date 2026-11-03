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
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.LodCell;
import me.bestnuts.lod.encode.TileDisplays;
import me.bestnuts.lod.encode.TileGlyphs;
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

    private record TileTexts(Component top, Component walls, int topGlyphs, int wallGlyphs) {
    }

    private static final double REANCHOR_FRACTION = 0.75;

    private final Plugin plugin;
    private final Player player;
    private final World world;
    private final LodWorldCache cache;
    private final LodSettings settings;
    private final Executor builder;
    private final Map<TileKey, TileEntities> displays = new HashMap<>();
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
        displays.values().removeIf(entities -> {
            if (entities.isValid()) {
                return false;
            }
            entities.remove();
            return true;
        });

        if (anchor == null || horizontalDistance(location, anchor) > anchorGrid() * REANCHOR_FRACTION) {
            reanchor(location);
        }

        LodRange range = LodRange.of(player, settings);
        double inner = range.inner();
        desired = TileSelector.select(location.getX(), location.getZ(), inner, range.outer(), settings);

        Iterator<Map.Entry<TileKey, TileEntities>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TileKey, TileEntities> entry = iterator.next();
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
        displays.values().forEach(TileEntities::remove);
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
                    .whenComplete((texts, error) -> {
                        if (plugin.isEnabled()) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> apply(tile, texts));
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

    private TileTexts encode(TileKey tile, double playerX, double playerZ, double inner) {
        int cellBlocks = tile.cellBlocks();
        int gridSize = TileGlyphs.gridSize(TileKey.CELLS);
        double innerSquared = inner * inner;
        LodCell[] grid = new LodCell[gridSize * gridSize];
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                int cellX = tile.originX() + (column - 1) * cellBlocks;
                int cellZ = tile.originZ() + (row - 1) * cellBlocks;
                double dx = cellX + cellBlocks / 2.0 - playerX;
                double dz = cellZ + cellBlocks / 2.0 - playerZ;
                if (dx * dx + dz * dz >= innerSquared) {
                    grid[row * gridSize + column] = cache.sample(cellX, cellZ, cellBlocks);
                }
            }
        }
        TileText.Glyph[] tops = TileGlyphs.tops(grid, TileKey.CELLS);
        TileText.Glyph[] walls = TileGlyphs.walls(grid, TileKey.CELLS, cellBlocks);
        int topGlyphs = glyphCount(tops);
        int wallGlyphs = glyphCount(walls);
        return new TileTexts(
                topGlyphs == 0 ? null : TileText.encode(tops, TileKey.CELLS),
                wallGlyphs == 0 ? null : TileText.encode(walls, TileGlyphs.wallColumns(TileKey.CELLS)),
                topGlyphs,
                wallGlyphs
        );
    }

    private static int glyphCount(TileText.Glyph[] glyphs) {
        int count = 0;
        for (TileText.Glyph glyph : glyphs) {
            if (glyph != null) {
                count++;
            }
        }
        return count;
    }

    private void apply(TileKey tile, TileTexts texts) {
        building.remove(tile);
        if (texts == null || closed || !player.isOnline() || !desired.contains(tile)) {
            return;
        }
        TileEntities previous = displays.get(tile);
        TextDisplay top = layer(previous == null ? null : previous.top(), texts.top(),
                LodBits.opacity(LodBits.KIND_TOP, tile.level()), TileKey.CELLS, topTransformation(tile));
        TextDisplay walls = layer(previous == null ? null : previous.walls(), texts.walls(),
                LodBits.opacity(LodBits.KIND_WALL, tile.level()), TileGlyphs.wallColumns(TileKey.CELLS), wallTransformation(tile));
        displays.put(tile, new TileEntities(top, walls, texts.topGlyphs(), texts.wallGlyphs()));
    }

    private TextDisplay layer(TextDisplay current, Component text, byte opacity, int columns, Transformation transformation) {
        if (text == null) {
            if (current != null) {
                current.remove();
            }
            return null;
        }
        if (current != null && current.isValid()) {
            current.text(text);
            return current;
        }
        TextDisplay display = TileDisplays.spawn(anchor, opacity, columns, transformation, text);
        player.showEntity(plugin, display);
        return display;
    }

    // Anchor moves stay multiples of every cell size, so a frame that sees the new position with the old translation keeps its glyph corners.
    private void reanchor(Location location) {
        int grid = anchorGrid();
        double x = Math.floorDiv(location.getBlockX(), grid) * grid + grid / 2.0;
        double z = Math.floorDiv(location.getBlockZ(), grid) * grid + grid / 2.0;
        anchor = new Location(world, x, world.getMaxHeight(), z);
        displays.forEach((tile, entities) -> {
            entities.displays().forEach(display -> display.teleport(anchor));
            if (entities.top() != null) {
                entities.top().setTransformation(topTransformation(tile));
            }
            if (entities.walls() != null) {
                entities.walls().setTransformation(wallTransformation(tile));
            }
        });
    }

    private Transformation topTransformation(TileKey tile) {
        return TileTransform.top(tile.level(), TileKey.CELLS, tile.originX(), tile.originZ(), anchor, cache.minY());
    }

    private Transformation wallTransformation(TileKey tile) {
        return TileTransform.walls(tile.level(), TileKey.CELLS, tile.originX(), tile.originZ(), anchor, cache.minY());
    }

    private int anchorGrid() {
        int largestCell = LodBits.cellBlocks(settings.maxLevel());
        return Math.max(largestCell, Math.ceilDiv(settings.anchorGrid(), largestCell) * largestCell);
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
