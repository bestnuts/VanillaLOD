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
import me.bestnuts.lod.encode.WallView;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;

final class PlayerLodView {

    private record TileTexts(Component top, Component walls, Component underWalls, Component bottom,
                             int topGlyphs, int wallGlyphs, int bottomGlyphs, WallView wallView) {
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

        displays.forEach((tile, entities) -> {
            if (!wallView(tile, location.getX(), location.getZ()).equals(entities.wallView())) {
                dirty.add(tile);
            }
        });

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

    ViewStats stats() {
        int entities = 0;
        int topGlyphs = 0;
        int wallGlyphs = 0;
        int bottomGlyphs = 0;
        for (TileEntities tile : displays.values()) {
            entities += tile.displays().size();
            topGlyphs += tile.topGlyphs();
            wallGlyphs += tile.wallGlyphs();
            bottomGlyphs += tile.bottomGlyphs();
        }
        return new ViewStats(displays.size(), entities, topGlyphs, wallGlyphs, bottomGlyphs, missing.size() + dirty.size() + building.size());
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
        int sampledSize = TileGlyphs.sampledGridSize(TileKey.CELLS);
        int ring = (sampledSize - TileKey.CELLS) / 2;
        double innerSquared = inner * inner;
        LodCell[] sampled = new LodCell[sampledSize * sampledSize];
        for (int row = 0; row < sampledSize; row++) {
            for (int column = 0; column < sampledSize; column++) {
                int cellX = tile.originX() + (column - ring) * cellBlocks;
                int cellZ = tile.originZ() + (row - ring) * cellBlocks;
                double dx = cellX + cellBlocks / 2.0 - playerX;
                double dz = cellZ + cellBlocks / 2.0 - playerZ;
                if (dx * dx + dz * dz >= innerSquared) {
                    LodCell cell = cache.sample(cellX, cellZ, cellBlocks);
                    sampled[row * sampledSize + column] = cell == null ? null : cell.quantized(cellBlocks);
                }
            }
        }
        LodCell[] grid = TileGlyphs.smoothUndersides(sampled, TileKey.CELLS, cellBlocks);
        int[] floors = ringFloors(tile, grid);
        TileText.Glyph[] tops = TileGlyphs.tops(grid, TileKey.CELLS);
        WallView wallView = wallView(tile, playerX, playerZ);
        TileText.Glyph[] walls = TileGlyphs.walls(grid, floors, TileKey.CELLS, cellBlocks, wallView);
        TileText.Glyph[] underWalls = TileGlyphs.underWalls(grid, TileKey.CELLS, cellBlocks, wallView);
        TileText.Glyph[] bottoms = TileGlyphs.bottoms(grid, TileKey.CELLS);
        int topGlyphs = glyphCount(tops);
        int wallGlyphs = glyphCount(walls);
        int underWallGlyphs = glyphCount(underWalls);
        int bottomGlyphs = glyphCount(bottoms);
        return new TileTexts(
                topGlyphs == 0 ? null : TileText.encode(tops, TileKey.CELLS),
                wallGlyphs == 0 ? null : TileText.encode(walls, TileGlyphs.wallColumns(TileKey.CELLS)),
                underWallGlyphs == 0 ? null : TileText.encode(underWalls, TileGlyphs.wallColumns(TileKey.CELLS)),
                bottomGlyphs == 0 ? null : TileText.encode(bottoms, TileKey.CELLS),
                topGlyphs,
                wallGlyphs + underWallGlyphs,
                bottomGlyphs,
                wallView
        );
    }

    private int[] ringFloors(TileKey tile, LodCell[] grid) {
        int gridSize = TileGlyphs.gridSize(TileKey.CELLS);
        int cellBlocks = tile.cellBlocks();
        int[] floors = TileGlyphs.noFloors(TileKey.CELLS);
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                boolean ring = row == 0 || column == 0 || row == gridSize - 1 || column == gridSize - 1;
                if (ring && grid[row * gridSize + column] != null) {
                    floors[row * gridSize + column] = cache.lowestSurface(
                            tile.originX() + (column - 1) * cellBlocks, tile.originZ() + (row - 1) * cellBlocks, cellBlocks);
                }
            }
        }
        return floors;
    }

    private static WallView wallView(TileKey tile, double playerX, double playerZ) {
        return WallView.of(tile.originX(), tile.originZ(), tile.blocks(), playerX, playerZ);
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
        TextDisplay underWalls = layer(previous == null ? null : previous.underWalls(), texts.underWalls(),
                LodBits.opacity(LodBits.KIND_UNDER_WALL, tile.level()), TileGlyphs.wallColumns(TileKey.CELLS), wallTransformation(tile));
        TextDisplay bottom = layer(previous == null ? null : previous.bottom(), texts.bottom(),
                LodBits.opacity(LodBits.KIND_BOTTOM, tile.level()), TileKey.CELLS, topTransformation(tile));
        displays.put(tile, new TileEntities(top, walls, underWalls, bottom, texts.topGlyphs(), texts.wallGlyphs(), texts.bottomGlyphs(), texts.wallView()));
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
            if (entities.underWalls() != null) {
                entities.underWalls().setTransformation(wallTransformation(tile));
            }
            if (entities.bottom() != null) {
                entities.bottom().setTransformation(topTransformation(tile));
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
