package me.bestnuts.lod.view;

import java.util.List;

/**
 * Shared display resources of one world. displayViews counts each display once per viewer, which is what spawning
 * per player would cost; splitVariants counts variants spawned at more than one anchor because no anchor reached every viewer.
 */
public record NetworkStats(String world, int tiles, int sharedTiles, int maxViewers, int displays, int displayViews,
                           int shownVariants, int splitVariants, int cachedVariants, int building, List<Anchor> anchors) {

    public record Anchor(TileAnchor anchor, List<String> viewers, int tiles, int displays) {
    }
}
