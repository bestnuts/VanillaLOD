package me.bestnuts.lod.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import me.bestnuts.lod.LodSettings;
import me.bestnuts.lod.cache.LodCache;
import me.bestnuts.lod.cache.PregenTask;
import me.bestnuts.lod.view.LodViewService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class LodCommand implements BasicCommand {

    private static final int MAX_PREGEN_RADIUS = 512;

    private final Plugin plugin;
    private final LodCache cache;
    private final LodViewService views;
    private final LodSettings settings;

    public LodCommand(Plugin plugin, LodCache cache, LodViewService views, LodSettings settings) {
        this.plugin = plugin;
        this.cache = cache;
        this.views = views;
        this.settings = settings;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length == 0) {
            source.getSender().sendMessage(Component.text("/lod toggle | /lod pregen <radius chunks> | /lod save"));
            return;
        }
        switch (args[0]) {
            case "toggle" -> toggle(source);
            case "pregen" -> pregen(source, args);
            case "save" -> {
                cache.saveAll();
                source.getSender().sendMessage(Component.text("LOD cache saved"));
            }
            default -> source.getSender().sendMessage(Component.text("Unknown subcommand: " + args[0]));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? List.of("toggle", "pregen", "save") : List.of();
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

    private void pregen(CommandSourceStack source, String[] args) {
        int radius;
        try {
            radius = Math.clamp(Integer.parseInt(args.length > 1 ? args[1] : "32"), 1, MAX_PREGEN_RADIUS);
        } catch (NumberFormatException e) {
            source.getSender().sendMessage(Component.text("Radius must be a number"));
            return;
        }
        Location center = source.getLocation();
        PregenTask task = new PregenTask(center.getWorld(), center.getBlockX() >> 4, center.getBlockZ() >> 4, radius, cache, settings.pregenParallel(),
                message -> source.getSender().sendMessage(Component.text("LOD pregen " + message)));
        source.getSender().sendMessage(Component.text("LOD pregen " + task.total() + " chunks"));
        task.runTaskTimer(plugin, 1L, 1L);
    }
}
