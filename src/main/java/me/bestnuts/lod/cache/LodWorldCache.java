package me.bestnuts.lod.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.LodCell;

public final class LodWorldCache {

    private static final int REGION_SHIFT = 5;
    private static final int FORMAT_VERSION = 7;
    private static final int MAX_SAMPLES_PER_AXIS = 4;
    private static final int FLOOR_SAMPLES_PER_AXIS = 8;

    private final Path directory;
    private final int minY;
    private final FloatingRule floatingRule;
    private final SpritePalette palette = new SpritePalette();
    private final Map<Long, ChunkColumns> chunks = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> regionChunks = new ConcurrentHashMap<>();
    private final Set<Long> dirtyRegions = ConcurrentHashMap.newKeySet();

    public LodWorldCache(Path directory, int minY, FloatingRule floatingRule) {
        this.directory = directory;
        this.minY = minY;
        this.floatingRule = floatingRule;
    }

    public int minY() {
        return minY;
    }

    public FloatingRule floatingRule() {
        return floatingRule;
    }

    public SpritePalette palette() {
        return palette;
    }

    public int size() {
        return chunks.size();
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunks.containsKey(key(chunkX, chunkZ));
    }

    public void put(int chunkX, int chunkZ, ChunkColumns columns) {
        store(chunkX, chunkZ, columns);
        dirtyRegions.add(regionKey(chunkX, chunkZ));
    }

    public LodCell sample(int blockX, int blockZ, int cellBlocks) {
        int stride = Math.max(1, cellBlocks / MAX_SAMPLES_PER_AXIS);
        Map<Integer, Integer> spriteVotes = new HashMap<>();
        Map<Integer, Integer> spriteTopY = new HashMap<>();
        Map<Integer, Integer> spriteTints = new HashMap<>();
        Map<Integer, Long> spriteBottomSum = new HashMap<>();
        Map<Integer, Integer> spriteFloating = new HashMap<>();
        Set<Integer> spriteGrounded = new HashSet<>();
        boolean cached = false;

        for (int dz = 0; dz < cellBlocks; dz += stride) {
            for (int dx = 0; dx < cellBlocks; dx += stride) {
                int x = blockX + dx;
                int z = blockZ + dz;
                ChunkColumns columns = chunks.get(key(x >> 4, z >> 4));
                if (columns == null) {
                    continue;
                }
                cached = true;
                int localX = x & 15;
                int localZ = z & 15;
                int sprite = columns.sprite(localX, localZ);
                if (sprite == ChunkColumns.NO_SURFACE) {
                    continue;
                }
                spriteVotes.merge(sprite, 1, Integer::sum);
                spriteTopY.merge(sprite, columns.topY(localX, localZ), Math::max);
                spriteTints.putIfAbsent(sprite, columns.tint(localX, localZ));
                int bottomY = columns.bottomY(localX, localZ);
                if (bottomY == ChunkColumns.GROUNDED) {
                    spriteGrounded.add(sprite);
                } else {
                    spriteBottomSum.merge(sprite, (long) bottomY, Long::sum);
                    spriteFloating.merge(sprite, 1, Integer::sum);
                }
            }
        }

        if (spriteVotes.isEmpty()) {
            return cached ? LodCell.VOID : null;
        }
        int sprite = spriteVotes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        // Island undersides deepen towards the middle; the mean keeps edge cells from taking the deepest column's thickness.
        int bottom = spriteGrounded.contains(sprite)
                ? LodCell.GROUNDED
                : (int) Math.round((double) spriteBottomSum.get(sprite) / spriteFloating.get(sprite)) - minY;
        return new LodCell(
                palette.sprite(sprite),
                spriteTopY.get(sprite) + 1 - minY,
                spriteTints.getOrDefault(sprite, LodBits.NO_TINT),
                bottom
        );
    }

    // Lowest surface under a cell, sampled finer than the cell itself so it also covers what a finer neighbour tile draws.
    public int lowestSurface(int blockX, int blockZ, int cellBlocks) {
        int stride = Math.max(1, cellBlocks / FLOOR_SAMPLES_PER_AXIS);
        int lowest = Integer.MAX_VALUE;
        for (int dz = 0; dz < cellBlocks; dz += stride) {
            for (int dx = 0; dx < cellBlocks; dx += stride) {
                int x = blockX + dx;
                int z = blockZ + dz;
                ChunkColumns columns = chunks.get(key(x >> 4, z >> 4));
                if (columns != null && columns.sprite(x & 15, z & 15) != ChunkColumns.NO_SURFACE) {
                    lowest = Math.min(lowest, columns.topY(x & 15, z & 15) + 1 - minY);
                }
            }
        }
        return lowest == Integer.MAX_VALUE ? LodCell.NO_FLOOR : lowest;
    }

    public void load() throws IOException {
        palette.load(directory.resolve("palette.txt"));
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> regions = Files.newDirectoryStream(directory, "r.*.lod")) {
            for (Path region : regions) {
                readRegion(region);
            }
        }
    }

    public void saveDirty() throws IOException {
        List<Long> regions = new ArrayList<>(dirtyRegions);
        if (regions.isEmpty()) {
            return;
        }
        Files.createDirectories(directory);
        palette.save(directory.resolve("palette.txt"));
        for (long region : regions) {
            dirtyRegions.remove(region);
            writeRegion(unpackX(region), unpackZ(region));
        }
    }

    private void readRegion(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != FORMAT_VERSION || in.readInt() != floatingRule.airGap() || in.readInt() != floatingRule.maxThickness()) {
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int chunkX = in.readInt();
                int chunkZ = in.readInt();
                store(chunkX, chunkZ, ChunkColumns.read(in));
            }
        }
    }

    private void writeRegion(int regionX, int regionZ) throws IOException {
        List<Long> keys = List.copyOf(regionChunks.getOrDefault(key(regionX, regionZ), Set.of()));

        Path file = directory.resolve("r." + regionX + "." + regionZ + ".lod");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(floatingRule.airGap());
            out.writeInt(floatingRule.maxThickness());
            out.writeInt(keys.size());
            for (long key : keys) {
                out.writeInt(unpackX(key));
                out.writeInt(unpackZ(key));
                chunks.get(key).write(out);
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private void store(int chunkX, int chunkZ, ChunkColumns columns) {
        long key = key(chunkX, chunkZ);
        chunks.put(key, columns);
        regionChunks.computeIfAbsent(regionKey(chunkX, chunkZ), region -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private static long regionKey(int chunkX, int chunkZ) {
        return key(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
