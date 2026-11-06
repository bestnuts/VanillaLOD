package me.bestnuts.lod.view;

import me.bestnuts.lod.encode.LodBits;

public record TileKey(int level, int tileX, int tileZ) {

    public static final int CELLS = 16;

    public int cellBlocks() {
        return LodBits.cellBlocks(level);
    }

    public int blocks() {
        return CELLS * cellBlocks();
    }

    public int originX() {
        return tileX * blocks();
    }

    public int originZ() {
        return tileZ * blocks();
    }

    public double nearestDistance(double x, double z) {
        return nearestDistance(x, z, 0.0);
    }

    public double nearestDistance(double x, double z, double margin) {
        double dx = Math.max(Math.max(originX() - margin - x, 0.0), x - (originX() + blocks() + margin));
        double dz = Math.max(Math.max(originZ() - margin - z, 0.0), z - (originZ() + blocks() + margin));
        return Math.hypot(dx, dz);
    }

    public double farthestDistance(double x, double z) {
        double dx = Math.max(Math.abs(originX() - x), Math.abs(originX() + blocks() - x));
        double dz = Math.max(Math.abs(originZ() - z), Math.abs(originZ() + blocks() - z));
        return Math.hypot(dx, dz);
    }

    public boolean overlaps(TileKey other) {
        return originX() < other.originX() + other.blocks() && other.originX() < originX() + blocks()
                && originZ() < other.originZ() + other.blocks() && other.originZ() < originZ() + blocks();
    }

    public boolean overlapsChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return minX < originX() + blocks() && minX + 16 > originX()
                && minZ < originZ() + blocks() && minZ + 16 > originZ();
    }
}
