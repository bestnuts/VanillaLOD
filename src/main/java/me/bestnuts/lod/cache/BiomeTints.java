package me.bestnuts.lod.cache;

import java.util.HashMap;
import java.util.Map;
import me.bestnuts.lod.encode.LodBits;
import org.bukkit.block.Biome;

public final class BiomeTints {

    private record Colors(int grass, int foliage, int water) {
    }

    private static final int DEFAULT_WATER = 0x3F76E4;
    private static final Colors DEFAULT = new Colors(0x91BD59, 0x77AB2F, DEFAULT_WATER);
    private static final Colors SNOWY = new Colors(0x80B497, 0x60A17B, 0x3D57D6);
    private static final Colors ARID = new Colors(0xBFB755, 0xAEA42A, DEFAULT_WATER);
    private static final Map<String, Colors> COLORS = new HashMap<>();

    static {
        put(DEFAULT, "plains", "sunflower_plains", "river", "beach", "mushroom_fields");
        put(new Colors(0x79C05A, 0x59AE30, DEFAULT_WATER), "forest", "flower_forest");
        put(new Colors(0x88BB67, 0x6BA941, DEFAULT_WATER), "birch_forest", "old_growth_birch_forest");
        put(new Colors(0x507A32, 0x59AE30, DEFAULT_WATER), "dark_forest");
        put(new Colors(0x778272, 0x878D76, 0x76889D), "pale_garden");
        put(new Colors(0x86B783, 0x68A464, DEFAULT_WATER), "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
        put(SNOWY, "snowy_plains", "snowy_taiga", "snowy_beach", "ice_spikes", "grove", "snowy_slopes", "jagged_peaks", "frozen_peaks");
        put(new Colors(0x59C93C, 0x30BB0B, DEFAULT_WATER), "jungle", "bamboo_jungle");
        put(new Colors(0x64C73F, 0x3EB80F, DEFAULT_WATER), "sparse_jungle");
        put(new Colors(0x6A7039, 0x6A7039, 0x617B64), "swamp");
        put(new Colors(0x6A7039, 0x8DB127, 0x3A7A6A), "mangrove_swamp");
        put(ARID, "desert", "savanna", "savanna_plateau", "windswept_savanna");
        put(new Colors(0x90814D, 0x9E814D, DEFAULT_WATER), "badlands", "eroded_badlands", "wooded_badlands");
        put(new Colors(0x83BB6D, 0x63A948, 0x0E4ECF), "meadow");
        put(new Colors(0xB6DB61, 0xB6DB61, 0x5DB7EF), "cherry_grove");
        put(new Colors(0x8AB689, 0x6DA36B, DEFAULT_WATER), "windswept_hills", "windswept_gravelly_hills", "windswept_forest");
        put(new Colors(0x9ABE4B, 0x82AC1E, DEFAULT_WATER), "stony_peaks", "stony_shore");
        put(new Colors(DEFAULT.grass, DEFAULT.foliage, 0x43D5EE), "warm_ocean");
        put(new Colors(DEFAULT.grass, DEFAULT.foliage, 0x45ADF2), "lukewarm_ocean", "deep_lukewarm_ocean");
        put(new Colors(DEFAULT.grass, DEFAULT.foliage, 0x3D57D6), "cold_ocean", "deep_cold_ocean");
        put(new Colors(SNOWY.grass, SNOWY.foliage, 0x3938C9), "frozen_ocean", "deep_frozen_ocean", "frozen_river");
    }

    private BiomeTints() {
    }

    public static int color(SurfaceMaterial material, Biome biome) {
        Colors colors = COLORS.getOrDefault(biome.getKey().value(), DEFAULT);
        return switch (material.tint()) {
            case NONE -> LodBits.NO_TINT;
            case GRASS -> colors.grass;
            case FOLIAGE -> colors.foliage;
            case WATER -> colors.water;
            case FIXED -> material.fixedColor();
        };
    }

    private static void put(Colors colors, String... biomes) {
        for (String biome : biomes) {
            COLORS.put(biome, colors);
        }
    }
}
