package me.bestnuts.lod.command;

import java.util.List;
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.LodCell;
import me.bestnuts.lod.encode.TileDisplays;
import me.bestnuts.lod.encode.TileText;
import me.bestnuts.lod.encode.TileTransform;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class LodProbe {

    private static final int SIZE = 4;
    private static final int LEVEL = 0;
    private static final long LIFETIME_TICKS = 20L * 60L;
    private static final int GRASS_TINT = 0x91BD59;
    private static final List<Key> SPRITES = List.of(
            Key.key("minecraft", "block/stone"),
            Key.key("minecraft", "block/grass_block_top"),
            Key.key("minecraft", "block/snow"),
            Key.key("minecraft", "block/sand")
    );

    private LodProbe() {
    }

    static void spawn(Plugin plugin, Player player) {
        Component text = TileText.encode(cells(), SIZE);
        Location feet = player.getLocation();

        Location rawAt = feet.clone().add(feet.getDirection().setY(0).normalize().multiply(3));
        rawAt.setYaw(0.0f);
        rawAt.setPitch(0.0f);
        Transformation upright = new Transformation(new Vector3f(0.0f, 1.0f, 0.0f), new Quaternionf(), new Vector3f(1.0f), new Quaternionf());
        TextDisplay raw = TileDisplays.spawn(rawAt, LEVEL, SIZE, upright, text);
        raw.setBillboard(Display.Billboard.CENTER);

        Location anchor = new Location(feet.getWorld(), feet.getBlockX() + 0.5, feet.getBlockY(), feet.getBlockZ() + 0.5);
        TextDisplay marked = TileDisplays.spawn(anchor, LEVEL, SIZE, TileTransform.of(LEVEL, SIZE, feet.getBlockX() + 3, feet.getBlockZ() - 2, anchor), text);

        for (TextDisplay display : List.of(raw, marked)) {
            player.showEntity(plugin, display);
            plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, LIFETIME_TICKS);
        }
        player.sendMessage(Component.text("LOD probe: raw tile in front, marked tile 3 blocks east at y+2..y+5"));
    }

    private static LodCell[] cells() {
        LodCell[] cells = new LodCell[SIZE * SIZE];
        for (int i = 0; i < cells.length; i++) {
            int material = i % SPRITES.size();
            cells[i] = new LodCell(SPRITES.get(material), 2 + i / SIZE, material == 1 ? GRASS_TINT : LodBits.NO_TINT);
        }
        return cells;
    }
}
