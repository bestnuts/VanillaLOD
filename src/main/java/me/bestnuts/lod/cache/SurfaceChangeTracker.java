package me.bestnuts.lod.cache;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;

/**
 * Collects chunks whose blocks changed and rescans them in batches, so bursts of edits cost one snapshot per chunk.
 */
public final class SurfaceChangeTracker implements Listener {

    private static final long RESCAN_INTERVAL_TICKS = 40L;

    private final LodCache cache;
    private final Set<Chunk> changed = new HashSet<>();

    public SurfaceChangeTracker(LodCache cache) {
        this.cache = cache;
    }

    public void start(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::rescan, RESCAN_INTERVAL_TICKS, RESCAN_INTERVAL_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        mark(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        markAll(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        markAll(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        markAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        markAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        for (BlockState state : event.getBlocks()) {
            mark(state.getBlock());
        }
    }

    private void markAll(Collection<Block> blocks) {
        for (Block block : blocks) {
            mark(block);
        }
    }

    private void mark(Block block) {
        changed.add(block.getChunk());
    }

    private void rescan() {
        for (Chunk chunk : changed) {
            if (chunk.isLoaded()) {
                cache.capture(chunk);
            }
        }
        changed.clear();
    }
}
