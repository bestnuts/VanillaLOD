package me.bestnuts.lod;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.bestnuts.lod.cache.ChunkCaptureListener;
import me.bestnuts.lod.cache.ChunkFiller;
import me.bestnuts.lod.cache.LodCache;
import me.bestnuts.lod.cache.SurfaceChangeTracker;
import me.bestnuts.lod.command.LodCommand;
import me.bestnuts.lod.view.LodAutoFill;
import me.bestnuts.lod.view.LodViewService;
import org.bukkit.plugin.java.JavaPlugin;

public final class VanillaLodPlugin extends JavaPlugin {

    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private LodCache cache;
    private LodViewService views;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LodSettings settings = LodSettings.from(getConfig());

        cache = new LodCache(getDataFolder().toPath().resolve("cache"), getLogger(), settings::covers);
        getServer().getWorlds().stream().filter(settings::covers).forEach(cache::world);
        views = new LodViewService(this, cache, settings);
        cache.addListener(views);

        getServer().getPluginManager().registerEvents(new ChunkCaptureListener(cache), this);
        SurfaceChangeTracker changes = new SurfaceChangeTracker(cache);
        getServer().getPluginManager().registerEvents(changes, this);
        changes.start(this);
        getServer().getPluginManager().registerEvents(views, this);
        ChunkFiller filler = new ChunkFiller(cache, settings.pregenParallel());
        filler.start(this);
        new LodAutoFill(this, cache, filler, settings).start();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("lod", new LodCommand(this, cache, views, filler)));

        long saveInterval = settings.saveIntervalMinutes() * TICKS_PER_MINUTE;
        getServer().getScheduler().runTaskTimerAsynchronously(this, cache::saveAll, saveInterval, saveInterval);
        views.start();
    }

    @Override
    public void onDisable() {
        if (views != null) {
            views.shutdown();
        }
        if (cache != null) {
            cache.shutdown();
        }
    }
}
