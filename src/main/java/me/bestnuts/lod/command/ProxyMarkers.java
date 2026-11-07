package me.bestnuts.lod.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.bestnuts.lod.api.LodProjection;
import me.bestnuts.lod.api.LodRenderApi;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Debug proxies drawn through {@link LodRenderApi} for the player who placed them, re-projected every tick.
 */
final class ProxyMarkers {

    private record Marker(Vector3d origin, float size, ItemDisplay carrier) {
    }

    private final Plugin plugin;
    private final LodRenderApi api;
    private final Map<UUID, List<Marker>> markers = new HashMap<>();
    private BukkitTask task;

    ProxyMarkers(Plugin plugin, LodRenderApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    boolean add(Player viewer, Vector3d origin, float size) {
        Optional<LodProjection> projection = api.projection(viewer);
        if (projection.isEmpty()) {
            return false;
        }
        ItemDisplay carrier = viewer.getWorld().spawn(location(projection.get(), projection.get().anchor()), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.RED_CONCRETE));
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setDisplayWidth(0.0f);
            display.setDisplayHeight(0.0f);
        });
        viewer.showEntity(plugin, carrier);
        Marker marker = new Marker(origin, size, carrier);
        update(projection.get(), marker);
        markers.computeIfAbsent(viewer.getUniqueId(), id -> new ArrayList<>()).add(marker);
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        return true;
    }

    int clear(Player viewer) {
        List<Marker> removed = markers.remove(viewer.getUniqueId());
        if (removed == null) {
            return 0;
        }
        removed.forEach(marker -> marker.carrier().remove());
        return removed.size();
    }

    void shutdown() {
        markers.values().forEach(list -> list.forEach(marker -> marker.carrier().remove()));
        markers.clear();
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        markers.entrySet().removeIf(entry -> {
            Player viewer = plugin.getServer().getPlayer(entry.getKey());
            if (viewer == null) {
                entry.getValue().forEach(marker -> marker.carrier().remove());
                return true;
            }
            api.projection(viewer).ifPresent(projection -> entry.getValue().forEach(marker -> update(projection, marker)));
            return false;
        });
    }

    private void update(LodProjection projection, Marker marker) {
        ItemDisplay carrier = marker.carrier();
        Vector3d anchor = projection.anchor();
        Location location = carrier.getLocation();
        if (!carrier.getWorld().equals(projection.world()) || location.getX() != anchor.x || location.getY() != anchor.y || location.getZ() != anchor.z) {
            carrier.teleport(location(projection, anchor));
        }
        // The cube's centre sits half its size above the marked point, so the marker stands on the ground there.
        Matrix4f originLocal = new Matrix4f().translation(0.0f, marker.size() / 2.0f, 0.0f).scale(marker.size());
        carrier.setTransformationMatrix(projection.project(marker.origin()).toRenderLocal(anchor, originLocal));
        carrier.setBrightness(projection.brightness());
    }

    private static Location location(LodProjection projection, Vector3d position) {
        return new Location(projection.world(), position.x, position.y, position.z, 0.0f, 0.0f);
    }
}
