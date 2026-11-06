package me.bestnuts.lod.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.bestnuts.lod.LodSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class PlayerLodView {

    private record Placement(TileAnchor anchor, TileVariant variant) {
    }

    private final Player player;
    private final World world;
    private final SharedTiles shared;
    private final LodSettings settings;
    private final Map<TileKey, Placement> shown = new HashMap<>();
    private final Map<TileVariant, TileKey> waiting = new HashMap<>();
    private final List<TileVariant> queue = new ArrayList<>();
    private Set<TileKey> desired = Set.of();
    private TileAnchor home;

    PlayerLodView(Player player, SharedTiles shared, LodSettings settings) {
        this.player = player;
        this.world = player.getWorld();
        this.shared = shared;
        this.settings = settings;
    }

    World world() {
        return world;
    }

    void update() {
        Location location = player.getLocation();
        if (home == null || home.distance(location) > shared.reanchorDistance()) {
            home = shared.anchorFor(location);
        }

        // Selection, holes and wall facings follow the chunk centre, as the client's render distance does,
        // so every player in one chunk resolves to the same variants.
        double viewX = (location.getBlockX() >> 4 << 4) + 8.0;
        double viewZ = (location.getBlockZ() >> 4 << 4) + 8.0;
        LodRange range = LodRange.of(player, settings);
        desired = TileSelector.select(viewX, viewZ, range.inner(), range.outer(), settings);

        waiting.clear();
        queue.clear();
        for (TileKey tile : desired) {
            TileVariant variant = TileVariant.of(tile, viewX, viewZ, range.inner());
            Placement current = shown.get(tile);
            if (current != null && current.variant().equals(variant)) {
                if (current.anchor().distance(location) > shared.reanchorDistance()) {
                    place(tile, variant, location, current);
                }
                continue;
            }
            shared.want(variant);
            if (shared.ready(variant)) {
                place(tile, variant, location, current);
            } else {
                waiting.put(variant, tile);
                queue.add(variant);
            }
        }
        queue.sort(Comparator.comparingDouble(variant -> variant.tile().nearestDistance(viewX, viewZ)));
        retire();
        startBuilds();
    }

    // Called as soon as any variant finishes encoding, so a tile shows up and the next build starts without waiting for the next update.
    void ready(TileVariant variant) {
        TileKey tile = waiting.remove(variant);
        if (tile == null) {
            return;
        }
        queue.remove(variant);
        place(tile, variant, player.getLocation(), shown.get(tile));
        retire();
        startBuilds();
    }

    ViewStats stats() {
        int sharedTiles = 0;
        int displays = 0;
        int topGlyphs = 0;
        int wallGlyphs = 0;
        int bottomGlyphs = 0;
        for (Placement placement : shown.values()) {
            if (shared.viewers(placement.anchor(), placement.variant()) > 1) {
                sharedTiles++;
            }
            displays += shared.entities(placement.anchor(), placement.variant()).displays().size();
            TileTexts texts = shared.texts(placement.variant());
            topGlyphs += texts.topGlyphs();
            wallGlyphs += texts.wallGlyphs();
            bottomGlyphs += texts.bottomGlyphs();
        }
        return new ViewStats(shown.size(), sharedTiles, displays, topGlyphs, wallGlyphs, bottomGlyphs, queue.size());
    }

    void close() {
        shown.values().forEach(this::hide);
        shown.clear();
        waiting.clear();
        queue.clear();
    }

    // A tile that left the selection stays until every selected tile over its area is shown, so a level change
    // never opens a hole while the finer or coarser replacement is still encoding.
    private void retire() {
        shown.entrySet().removeIf(entry -> {
            TileKey tile = entry.getKey();
            if (desired.contains(tile)) {
                return false;
            }
            boolean replaced = desired.stream()
                    .filter(other -> other.overlaps(tile))
                    .allMatch(shown::containsKey);
            if (!replaced) {
                return false;
            }
            hide(entry.getValue());
            return true;
        });
    }

    private void startBuilds() {
        int budget = settings.buildsPerUpdate() - (int) queue.stream().filter(shared::building).count();
        for (TileVariant variant : queue) {
            if (budget <= 0) {
                break;
            }
            if (!shared.building(variant)) {
                shared.build(variant);
                budget--;
            }
        }
    }

    // The replacement is shown before the old placement is hidden, so the tile never leaves a gap for a frame.
    private void place(TileKey tile, TileVariant variant, Location location, Placement current) {
        Placement next = new Placement(shared.placementFor(variant, location, home), variant);
        if (next.equals(current)) {
            return;
        }
        if (current != null && current.variant().equals(variant) && shared.move(player, current.anchor(), next.anchor(), variant)) {
            shown.put(tile, next);
            return;
        }
        shared.show(player, next.anchor(), variant);
        if (current != null) {
            hide(current);
        }
        shown.put(tile, next);
    }

    private void hide(Placement placement) {
        shared.hide(player, placement.anchor(), placement.variant());
    }
}
