package me.bestnuts.lod.view;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.LodWorldCache;
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.TileDisplays;
import me.bestnuts.lod.encode.TileGlyphs;
import me.bestnuts.lod.encode.TileTransform;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;

/**
 * Tile displays of one world, spawned once per anchor and variant and shown to every player that needs them.
 */
final class SharedTiles {

    private record Key(TileAnchor anchor, TileVariant variant) {
    }

    private static final class Shared {
        private TileEntities entities = TileEntities.EMPTY;
        private final Set<Player> viewers = new LinkedHashSet<>();
    }

    private static final double REANCHOR_FRACTION = 0.75;

    private final Plugin plugin;
    private final World world;
    private final LodWorldCache cache;
    private final LodSettings settings;
    private final Executor builder;
    private final Consumer<TileVariant> onReady;
    private final Map<TileVariant, TileTexts> texts = new HashMap<>();
    private final Set<TileVariant> building = new HashSet<>();
    private final Set<TileVariant> stale = new HashSet<>();
    private final Set<TileVariant> wanted = new HashSet<>();
    private final Map<Key, Shared> tiles = new HashMap<>();
    private boolean closed;

    SharedTiles(Plugin plugin, World world, LodWorldCache cache, LodSettings settings, Executor builder, Consumer<TileVariant> onReady) {
        this.plugin = plugin;
        this.world = world;
        this.cache = cache;
        this.settings = settings;
        this.builder = builder;
        this.onReady = onReady;
    }

    double reanchorDistance() {
        return anchorGrid() * REANCHOR_FRACTION;
    }

    // Prefers an anchor other players already stand on, so tiles spawned here are the ones nearby players can join.
    TileAnchor anchorFor(Location location) {
        int grid = anchorGrid();
        TileAnchor own = new TileAnchor(
                Math.floorDiv(location.getBlockX(), grid) * grid + grid / 2,
                Math.floorDiv(location.getBlockZ(), grid) * grid + grid / 2);
        return nearest(tiles.keySet().stream().map(Key::anchor), location).orElse(own);
    }

    // Any anchor within tracking range works for a single tile, so a variant already spawned nearby is joined instead of spawned again.
    TileAnchor placementFor(TileVariant variant, Location location, TileAnchor home) {
        return nearest(tiles.keySet().stream().filter(key -> key.variant().equals(variant)).map(Key::anchor), location).orElse(home);
    }

    private Optional<TileAnchor> nearest(Stream<TileAnchor> anchors, Location location) {
        return anchors
                .filter(anchor -> anchor.distance(location) < reanchorDistance())
                .min(Comparator.comparingDouble(anchor -> anchor.distance(location)));
    }

    boolean ready(TileVariant variant) {
        return texts.containsKey(variant);
    }

    boolean building(TileVariant variant) {
        return building.contains(variant);
    }

    void want(TileVariant variant) {
        wanted.add(variant);
    }

    void build(TileVariant variant) {
        if (!building.add(variant)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> TileEncoder.encode(cache, variant), builder)
                .whenComplete((encoded, error) -> {
                    if (plugin.isEnabled()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> finish(variant, encoded));
                    }
                });
    }

    void show(Player player, TileAnchor anchor, TileVariant variant) {
        Key key = new Key(anchor, variant);
        Shared shared = tiles.computeIfAbsent(key, created -> {
            Shared spawned = new Shared();
            spawned.entities = layers(created, TileEntities.EMPTY, texts.get(variant), Set.of());
            return spawned;
        });
        if (shared.viewers.add(player)) {
            shared.entities.displays().forEach(display -> player.showEntity(plugin, display));
        }
    }

    void hide(Player player, TileAnchor anchor, TileVariant variant) {
        Key key = new Key(anchor, variant);
        Shared shared = tiles.get(key);
        if (shared == null) {
            return;
        }
        if (shared.viewers.remove(player) && player.isOnline()) {
            shared.entities.displays().forEach(display -> player.hideEntity(plugin, display));
        }
        if (shared.viewers.isEmpty()) {
            shared.entities.remove();
            tiles.remove(key);
        }
    }

    TileTexts texts(TileVariant variant) {
        return texts.get(variant);
    }

    TileEntities entities(TileAnchor anchor, TileVariant variant) {
        Shared shared = tiles.get(new Key(anchor, variant));
        return shared == null ? TileEntities.EMPTY : shared.entities;
    }

    int viewers(TileAnchor anchor, TileVariant variant) {
        Shared shared = tiles.get(new Key(anchor, variant));
        return shared == null ? 0 : shared.viewers.size();
    }

    void chunksChanged(Collection<Long> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        Set<TileVariant> known = new HashSet<>(texts.keySet());
        known.addAll(building);
        for (TileVariant variant : known) {
            if (chunks.stream().anyMatch(chunk -> variant.tile().overlapsChunk((int) (chunk >> 32), (int) (long) chunk))) {
                if (building.contains(variant)) {
                    stale.add(variant);
                } else {
                    build(variant);
                }
            }
        }
    }

    // Runs after every view updated this tick: drops encodings nobody shows or waits for and respawns displays removed from outside.
    void endTick() {
        Set<TileVariant> used = tiles.keySet().stream().map(Key::variant).collect(Collectors.toSet());
        texts.keySet().removeIf(variant -> !used.contains(variant) && !wanted.contains(variant));
        wanted.clear();
        tiles.forEach((key, shared) -> {
            if (!shared.entities.isValid()) {
                shared.entities.remove();
                shared.entities = layers(key, TileEntities.EMPTY, texts.get(key.variant()), shared.viewers);
            }
        });
    }

    NetworkStats network() {
        Map<TileAnchor, List<Map.Entry<Key, Shared>>> byAnchor = tiles.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getKey().anchor()));
        List<NetworkStats.Anchor> anchors = byAnchor.entrySet().stream()
                .map(group -> {
                    Set<String> names = new TreeSet<>();
                    int displays = 0;
                    for (Map.Entry<Key, Shared> entry : group.getValue()) {
                        entry.getValue().viewers.forEach(player -> names.add(player.getName()));
                        displays += entry.getValue().entities.displays().size();
                    }
                    return new NetworkStats.Anchor(group.getKey(), List.copyOf(names), group.getValue().size(), displays);
                })
                .sorted(Comparator.comparingInt(NetworkStats.Anchor::displays).reversed())
                .toList();
        int sharedTiles = 0;
        int maxViewers = 0;
        int displays = 0;
        int displayViews = 0;
        for (Shared shared : tiles.values()) {
            int count = shared.entities.displays().size();
            displays += count;
            displayViews += count * shared.viewers.size();
            maxViewers = Math.max(maxViewers, shared.viewers.size());
            if (shared.viewers.size() > 1) {
                sharedTiles++;
            }
        }
        Map<TileVariant, Long> anchorsPerVariant = tiles.keySet().stream()
                .collect(Collectors.groupingBy(Key::variant, Collectors.counting()));
        int splitVariants = (int) anchorsPerVariant.values().stream().filter(count -> count > 1).count();
        return new NetworkStats(world.getName(), tiles.size(), sharedTiles, maxViewers, displays, displayViews,
                anchorsPerVariant.size(), splitVariants, texts.size(), building.size(), anchors);
    }

    void close() {
        closed = true;
        tiles.values().forEach(shared -> shared.entities.remove());
        tiles.clear();
        texts.clear();
    }

    private void finish(TileVariant variant, TileTexts encoded) {
        building.remove(variant);
        if (closed || encoded == null) {
            return;
        }
        texts.put(variant, encoded);
        tiles.forEach((key, shared) -> {
            if (key.variant().equals(variant)) {
                shared.entities = layers(key, shared.entities, encoded, shared.viewers);
            }
        });
        if (stale.remove(variant)) {
            build(variant);
        }
        onReady.accept(variant);
    }

    private TileEntities layers(Key key, TileEntities previous, TileTexts encoded, Set<Player> viewers) {
        TileKey tile = key.variant().tile();
        Location anchor = key.anchor().location(world);
        int horizonSteps = LodBits.horizonSteps(settings.maxDistance());
        Transformation top = TileTransform.top(tile.level(), TileKey.CELLS, tile.originX(), tile.originZ(), anchor, cache.minY(), horizonSteps);
        Transformation walls = TileTransform.walls(tile.level(), TileKey.CELLS, tile.originX(), tile.originZ(), anchor, cache.minY(), horizonSteps);
        int wallColumns = TileGlyphs.wallColumns(TileKey.CELLS);
        return new TileEntities(
                layer(previous.top(), encoded.top(), LodBits.opacity(LodBits.KIND_TOP, tile.level()), TileKey.CELLS, top, anchor, viewers),
                layer(previous.walls(), encoded.walls(), LodBits.opacity(LodBits.KIND_WALL, tile.level()), wallColumns, walls, anchor, viewers),
                layer(previous.underWalls(), encoded.underWalls(), LodBits.opacity(LodBits.KIND_UNDER_WALL, tile.level()), wallColumns, walls, anchor, viewers),
                layer(previous.bottom(), encoded.bottom(), LodBits.opacity(LodBits.KIND_BOTTOM, tile.level()), TileKey.CELLS, top, anchor, viewers));
    }

    private TextDisplay layer(TextDisplay current, Component text, byte opacity, int columns, Transformation transformation,
                              Location anchor, Set<Player> viewers) {
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
        viewers.forEach(player -> player.showEntity(plugin, display));
        return display;
    }

    private int anchorGrid() {
        int largestCell = LodBits.cellBlocks(settings.maxLevel());
        return Math.max(largestCell, Math.ceilDiv(settings.anchorGrid(), largestCell) * largestCell);
    }
}
