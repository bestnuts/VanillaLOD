package me.bestnuts.lod.cache;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class ChunkCaptureListener implements Listener {

    private final LodCache cache;

    public ChunkCaptureListener(LodCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(ChunkLoadEvent event) {
        if (cache.covers(event.getWorld()) && !cache.world(event.getWorld()).contains(event.getChunk().getX(), event.getChunk().getZ())) {
            cache.capture(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        cache.capture(event.getChunk());
    }
}
