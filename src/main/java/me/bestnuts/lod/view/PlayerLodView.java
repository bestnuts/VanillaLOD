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

    private final Player player;
    private final World world;
    private final SharedTiles shared;
    private final LodSettings settings;
    private final Map<TileKey, TileVariant> shown = new HashMap<>();
    private TileAnchor anchor;
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
        if (anchor == null || anchor.distance(location) > shared.reanchorDistance()) {
            reanchor(shared.anchorFor(location));
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
            shared.hide(player, anchor, entry.getValue());
            return true;
        });

        List<TileVariant> missing = new ArrayList<>();
        for (TileKey tile : desired) {
            TileVariant variant = TileVariant.of(tile, viewX, viewZ, range.inner());
            TileVariant current = shown.get(tile);
            if (variant.equals(current)) {
                continue;
            }
            shared.want(variant);
            if (shared.ready(variant)) {
                shared.show(player, anchor, variant);
                if (current != null) {
                    shared.hide(player, anchor, current);
                }
                shown.put(tile, variant);
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
        for (TileVariant variant : shown.values()) {
            if (shared.viewers(anchor, variant) > 1) {
                sharedTiles++;
            }
            displays += shared.entities(anchor, variant).displays().size();
            TileTexts texts = shared.texts(variant);
            topGlyphs += texts.topGlyphs();
            wallGlyphs += texts.wallGlyphs();
            bottomGlyphs += texts.bottomGlyphs();
        }
        return new ViewStats(shown.size(), sharedTiles, displays, topGlyphs, wallGlyphs, bottomGlyphs, pending);
    }

    void close() {
        shown.values().forEach(variant -> shared.hide(player, anchor, variant));
        shown.clear();
    }

    // Shown variants stay encoded while displayed, so moving them only spawns (or joins) displays at the new anchor.
    private void reanchor(TileAnchor next) {
        if (next.equals(anchor)) {
            return;
        }
        TileAnchor previous = anchor;
        anchor = next;
        shown.values().forEach(variant -> {
            shared.show(player, next, variant);
            shared.hide(player, previous, variant);
        });
    }
}
