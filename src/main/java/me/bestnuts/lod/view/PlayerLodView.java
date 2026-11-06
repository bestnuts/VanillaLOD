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
    private TileAnchor home;
    private int pending;

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
        Set<TileKey> desired = TileSelector.select(viewX, viewZ, range.inner(), range.outer(), settings);

        shown.entrySet().removeIf(entry -> {
            if (desired.contains(entry.getKey())) {
                return false;
            }
            hide(entry.getValue());
            return true;
        });

        List<TileVariant> missing = new ArrayList<>();
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
                missing.add(variant);
            }
        }
        pending = missing.size();

        missing.sort(Comparator.comparingDouble(variant -> variant.tile().nearestDistance(viewX, viewZ)));
        int budget = settings.buildsPerUpdate() - (int) missing.stream().filter(shared::building).count();
        for (TileVariant variant : missing) {
            if (budget <= 0) {
                break;
            }
            if (!shared.building(variant)) {
                shared.build(variant);
                budget--;
            }
        }
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
        return new ViewStats(shown.size(), sharedTiles, displays, topGlyphs, wallGlyphs, bottomGlyphs, pending);
    }

    void close() {
        shown.values().forEach(this::hide);
        shown.clear();
    }

    // The replacement is shown before the old placement is hidden, so the tile never leaves a gap for a frame.
    private void place(TileKey tile, TileVariant variant, Location location, Placement current) {
        Placement next = new Placement(shared.placementFor(variant, location, home), variant);
        if (next.equals(current)) {
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
