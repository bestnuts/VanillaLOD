package me.bestnuts.lod.command;

import java.util.List;
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.LodCell;
import me.bestnuts.lod.encode.TileDisplays;
import me.bestnuts.lod.encode.TileGlyphs;
import me.bestnuts.lod.encode.TileText;
import me.bestnuts.lod.encode.TileTransform;
import me.bestnuts.lod.encode.WallView;
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
    private static final int HORIZON_STEPS = 1;
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
        LodCell[] grid = grid();
        Component tops = TileText.encode(TileGlyphs.tops(grid, SIZE), SIZE);
        Component walls = TileText.encode(TileGlyphs.walls(grid, TileGlyphs.noFloors(SIZE), SIZE, LodBits.cellBlocks(LEVEL), WallView.ALL), TileGlyphs.wallColumns(SIZE));
        Location feet = player.getLocation();

        Location rawAt = feet.clone().add(feet.getDirection().setY(0).normalize().multiply(3));
        rawAt.setYaw(0.0f);
        rawAt.setPitch(0.0f);
        Transformation upright = new Transformation(new Vector3f(0.0f, 1.0f, 0.0f), new Quaternionf(), new Vector3f(1.0f), new Quaternionf());
        TextDisplay raw = TileDisplays.spawn(rawAt, LodBits.opacity(LodBits.KIND_TOP, LEVEL), SIZE, upright, tops);
        raw.setBillboard(Display.Billboard.CENTER);

        Location anchor = new Location(feet.getWorld(), feet.getBlockX() + 0.5, feet.getBlockY(), feet.getBlockZ() + 0.5);
        int originX = feet.getBlockX() + 3;
        int originZ = feet.getBlockZ() - 2;
        TextDisplay markedTops = TileDisplays.spawn(anchor, LodBits.opacity(LodBits.KIND_TOP, LEVEL), SIZE,
                TileTransform.top(LEVEL, SIZE, originX, originZ, anchor, feet.getBlockY(), HORIZON_STEPS), tops);
        TextDisplay markedWalls = TileDisplays.spawn(anchor, LodBits.opacity(LodBits.KIND_WALL, LEVEL), TileGlyphs.wallColumns(SIZE),
                TileTransform.walls(LEVEL, SIZE, originX, originZ, anchor, feet.getBlockY(), HORIZON_STEPS), walls);

        for (TextDisplay display : List.of(raw, markedTops, markedWalls)) {
            player.showEntity(plugin, display);
            plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, LIFETIME_TICKS);
        }
        player.sendMessage(Component.text("LOD probe: raw tile in front, marked tile 3 blocks east at y+2..y+5"));
    }

    private static LodCell[] grid() {
        int gridSize = TileGlyphs.gridSize(SIZE);
        LodCell[] grid = new LodCell[gridSize * gridSize];
        for (int i = 0; i < grid.length; i++) {
            int material = i % SPRITES.size();
            grid[i] = new LodCell(SPRITES.get(material), 2 + i / gridSize + i % 2, material == 1 ? GRASS_TINT : LodBits.NO_TINT);
        }
        return grid;
    }
}
