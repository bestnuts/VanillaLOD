package me.bestnuts.lod.cache;

import java.util.EnumMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

public record SurfaceMaterial(Key sprite, Tint tint, int fixedColor) {

    public enum Tint { NONE, GRASS, FOLIAGE, WATER, FIXED }

    private static final Map<Material, SurfaceMaterial> OVERRIDES = new EnumMap<>(Material.class);

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
        if (override != null) {
            return override;
        }
        String name = material.getKey().value();
        if (name.endsWith("_leaves")) {
            return new SurfaceMaterial(sprite(name), Tint.FOLIAGE, 0);
        }
        if (name.endsWith("_log") || name.endsWith("_stem")) {
            return new SurfaceMaterial(sprite(name + "_top"), Tint.NONE, 0);
        }
        return new SurfaceMaterial(sprite(name), Tint.NONE, 0);
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

    private static Key sprite(String name) {
        return Key.key("minecraft", "block/" + name);
    }
}
