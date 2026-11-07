package me.bestnuts.lod.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import me.bestnuts.lod.cache.ChunkFiller;
import me.bestnuts.lod.cache.LodCache;
import me.bestnuts.lod.cache.PregenTask;
import me.bestnuts.lod.view.LodViewService;
import me.bestnuts.lod.view.NetworkStats;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3d;

public final class LodCommand implements BasicCommand {

    // 2048 chunks reach the deepest horizon band, 32768 blocks.
    private static final int MAX_PREGEN_RADIUS = 2048;
    private static final int MAX_NETWORK_ANCHORS = 8;
    private static final float DEFAULT_MARKER_SIZE = 8.0f;

    private final Plugin plugin;
    private final LodCache cache;
    private final LodViewService views;
    private final ChunkFiller filler;
    private final ProxyMarkers markers;

    public LodCommand(Plugin plugin, LodCache cache, LodViewService views, ChunkFiller filler) {
        this.plugin = plugin;
        this.cache = cache;
        this.views = views;
        this.filler = filler;
        this.markers = new ProxyMarkers(plugin, views);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length == 0) {
            source.getSender().sendMessage(Component.text("/lod toggle | /lod pregen <radius chunks> | /lod save | /lod probe | /lod stats | /lod network | /lod marker <x> <y> <z> [size] | /lod marker clear"));
            return;
        }
        switch (args[0]) {
            case "toggle" -> toggle(source);
            case "pregen" -> pregen(source, args);
            case "probe" -> probe(source);
            case "stats" -> stats(source);
            case "network" -> network(source);
            case "marker" -> marker(source, args);
            case "save" -> {
                cache.saveAll();
                source.getSender().sendMessage(Component.text("LOD cache saved"));
            }
            default -> source.getSender().sendMessage(Component.text("Unknown subcommand: " + args[0]));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? List.of("toggle", "pregen", "save", "probe", "stats", "network", "marker") : List.of();
    }

    @Override
    public String permission() {
        return "vanillalod.command";
    }

    private void toggle(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only"));
            return;
        }
        boolean enabled = views.toggle(player);
        player.sendMessage(Component.text("LOD " + (enabled ? "on" : "off")));
    }

    private void stats(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only"));
            return;
        }
        player.sendMessage(Component.text("LOD cache: " + cache.world(player.getWorld()).size() + " chunks, filler queue " + filler.queued()));
        views.stats(player).ifPresentOrElse(
                stats -> player.sendMessage(Component.text("LOD view: " + stats.tiles() + " tiles (" + stats.sharedTiles() + " shared), "
                        + stats.displays() + " displays, "
                        + stats.topGlyphs() + " top glyphs, " + stats.wallGlyphs() + " wall glyphs, " + stats.bottomGlyphs() + " bottom glyphs, "
                        + stats.pendingBuilds() + " pending builds")),
                () -> player.sendMessage(Component.text("LOD view: off")));
    }

    private void network(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        List<NetworkStats> worlds = sender instanceof Player player
                ? views.network(player.getWorld()).stream().toList()
                : views.network();
        if (worlds.isEmpty()) {
            sender.sendMessage(Component.text("LOD network: no shared tiles"));
            return;
        }
        for (NetworkStats stats : worlds) {
            int saved = stats.displayViews() - stats.displays();
            int percent = stats.displayViews() == 0 ? 0 : Math.round(100.0f * saved / stats.displayViews());
            sender.sendMessage(Component.text("LOD network " + stats.world() + ": " + stats.tiles() + " tile groups ("
                    + stats.sharedTiles() + " shared, max " + stats.maxViewers() + " viewers), "
                    + stats.displays() + " displays for " + stats.displayViews() + " views (" + saved + " saved, " + percent + "%)"));
            sender.sendMessage(Component.text("  variants: " + stats.shownVariants() + " shown (" + stats.splitVariants() + " on several anchors), " + stats.cachedVariants() + " encoded, "
                    + stats.building() + " building"));
            stats.anchors().stream().limit(MAX_NETWORK_ANCHORS).forEach(anchor -> sender.sendMessage(Component.text(
                    "  anchor " + anchor.anchor().x() + ", " + anchor.anchor().z() + ": " + anchor.tiles() + " tiles, "
                            + anchor.displays() + " displays, viewers " + String.join(", ", anchor.viewers()))));
            if (stats.anchors().size() > MAX_NETWORK_ANCHORS) {
                sender.sendMessage(Component.text("  ... " + (stats.anchors().size() - MAX_NETWORK_ANCHORS) + " more anchors"));
            }
        }
    }

    public void shutdown() {
        markers.shutdown();
    }

    private void marker(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only"));
            return;
        }
        if (args.length == 2 && args[1].equals("clear")) {
            player.sendMessage(Component.text("Removed " + markers.clear(player) + " LOD markers"));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(Component.text("/lod marker <x> <y> <z> [size] | /lod marker clear"));
            return;
        }
        Vector3d origin;
        float size;
        try {
            origin = new Vector3d(Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
            size = args.length > 4 ? Float.parseFloat(args[4]) : DEFAULT_MARKER_SIZE;
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Coordinates and size must be numbers"));
            return;
        }
        if (markers.add(player, origin, size)) {
            player.sendMessage(Component.text("LOD marker at " + args[1] + " " + args[2] + " " + args[3]));
        } else {
            player.sendMessage(Component.text("LOD is not active for you here"));
        }
    }

    private void probe(CommandSourceStack source) {
        if (source.getSender() instanceof Player player) {
            LodProbe.spawn(plugin, player);
        } else {
            source.getSender().sendMessage(Component.text("Players only"));
        }
    }

    private void pregen(CommandSourceStack source, String[] args) {
        int radius;
        try {
            radius = Math.clamp(Integer.parseInt(args.length > 1 ? args[1] : "32"), 1, MAX_PREGEN_RADIUS);
        } catch (NumberFormatException e) {
            source.getSender().sendMessage(Component.text("Radius must be a number"));
            return;
        }
        Location center = source.getLocation();
        if (!cache.covers(center.getWorld())) {
            source.getSender().sendMessage(Component.text("LOD is disabled in " + center.getWorld().getName()));
            return;
        }
        PregenTask task = new PregenTask(center.getWorld(), center.getBlockX() >> 4, center.getBlockZ() >> 4, radius, filler,
                message -> source.getSender().sendMessage(Component.text("LOD pregen " + message)));
        source.getSender().sendMessage(Component.text("LOD pregen " + task.total() + " chunks"));
        task.start(plugin);
    }
}
