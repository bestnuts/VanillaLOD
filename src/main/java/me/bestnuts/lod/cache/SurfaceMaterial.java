package me.bestnuts.lod.cache;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.bestnuts.lod.encode.SpriteNames;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

public record SurfaceMaterial(Key sprite, Tint tint, int fixedColor) {

    public enum Tint { NONE, GRASS, FOLIAGE, WATER, FIXED }

    private static final Map<Material, SurfaceMaterial> OVERRIDES = new EnumMap<>(Material.class);
    private static final Map<Material, SurfaceMaterial> RESOLVED = new ConcurrentHashMap<>();
    private static final Key MAP_COLOR_SPRITE = SpriteNames.sprite("white_concrete");
    private static final int UNKNOWN_MAP_COLOR = 0x808080;
    private static final List<String> SHAPE_SUFFIXES = List.of(
            "_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_carpet", "_trapdoor", "_door"
    );

    static {
        override(Material.GRASS_BLOCK, "grass_block_top", Tint.GRASS);
        override(Material.WATER, "water_still", Tint.WATER);
        override(Material.BUBBLE_COLUMN, "water_still", Tint.WATER);
        override(Material.LAVA, "lava_still", Tint.NONE);
        override(Material.SNOW, "snow", Tint.NONE);
        override(Material.SNOW_BLOCK, "snow", Tint.NONE);
        override(Material.POWDER_SNOW, "powder_snow", Tint.NONE);
        override(Material.PODZOL, "podzol_top", Tint.NONE);
        override(Material.MYCELIUM, "mycelium_top", Tint.NONE);
        override(Material.DIRT_PATH, "dirt_path_top", Tint.NONE);
        override(Material.FARMLAND, "farmland", Tint.NONE);
        override(Material.SANDSTONE, "sandstone_top", Tint.NONE);
        override(Material.RED_SANDSTONE, "red_sandstone_top", Tint.NONE);
        override(Material.MAGMA_BLOCK, "magma", Tint.NONE);
        override(Material.BASALT, "basalt_top", Tint.NONE);
        override(Material.CRIMSON_NYLIUM, "crimson_nylium", Tint.NONE);
        override(Material.WARPED_NYLIUM, "warped_nylium", Tint.NONE);
        override(Material.LILY_PAD, "lily_pad", Tint.FOLIAGE);
        override(Material.VINE, "vine", Tint.FOLIAGE);
        override(Material.SPRUCE_LEAVES, "spruce_leaves", 0x619961);
        override(Material.BIRCH_LEAVES, "birch_leaves", 0x80A755);
        override(Material.CHERRY_LEAVES, "cherry_leaves", Tint.NONE);
        override(Material.AZALEA_LEAVES, "azalea_leaves", Tint.NONE);
        override(Material.FLOWERING_AZALEA_LEAVES, "flowering_azalea_leaves", Tint.NONE);
        override(Material.PALE_OAK_LEAVES, "pale_oak_leaves", Tint.NONE);
    }

    public static SurfaceMaterial of(Material material) {
        SurfaceMaterial override = OVERRIDES.get(material);
        return override != null ? override : RESOLVED.computeIfAbsent(material, SurfaceMaterial::resolve);
    }

    public static boolean isSurface(Material material) {
        return !material.isAir() && (material.isSolid() || OVERRIDES.containsKey(material));
    }

    private static void override(Material material, String sprite, Tint tint) {
        OVERRIDES.put(material, new SurfaceMaterial(sprite(sprite), tint, 0));
    }

    private static void override(Material material, String sprite, int fixedColor) {
        OVERRIDES.put(material, new SurfaceMaterial(sprite(sprite), Tint.FIXED, fixedColor));
    }

    private static SurfaceMaterial resolve(Material material) {
        String name = material.getKey().value();
        String base = withoutShapeSuffix(name);
        Tint tint = name.endsWith("_leaves") ? Tint.FOLIAGE : Tint.NONE;
        return SpriteNames.find(name + "_top", name, base + "_top", base, base + "s", base + "_planks", base + "_block")
                .map(sprite -> new SurfaceMaterial(sprite, tint, 0))
                .orElseGet(() -> new SurfaceMaterial(MAP_COLOR_SPRITE, Tint.FIXED, mapColor(material)));
    }

    private static String withoutShapeSuffix(String name) {
        for (String suffix : SHAPE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private static int mapColor(Material material) {
        try {
            return material.createBlockData().getMapColor().asRGB();
        } catch (RuntimeException e) {
            return UNKNOWN_MAP_COLOR;
        }
    }

    private static Key sprite(String name) {
        return SpriteNames.sprite(name);
    }
}
