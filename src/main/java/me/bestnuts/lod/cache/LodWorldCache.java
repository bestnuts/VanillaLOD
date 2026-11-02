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
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SAMPLES_PER_AXIS = 4;

    private final Path directory;
    private final int minY;
    private final SpritePalette palette = new SpritePalette();
    private final Map<Long, ChunkColumns> chunks = new ConcurrentHashMap<>();
    private final Set<Long> dirtyRegions = ConcurrentHashMap.newKeySet();

    public LodWorldCache(Path directory, int minY) {
        this.directory = directory;
        this.minY = minY;
    }

    public int minY() {
        return minY;
    }

    public SpritePalette palette() {
        return palette;
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunks.containsKey(key(chunkX, chunkZ));
    }

    public void put(int chunkX, int chunkZ, ChunkColumns columns) {
        chunks.put(key(chunkX, chunkZ), columns);
        dirtyRegions.add(key(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    public LodCell sample(int blockX, int blockZ, int cellBlocks) {
        int stride = Math.max(1, cellBlocks / MAX_SAMPLES_PER_AXIS);
        Map<Integer, Integer> spriteVotes = new HashMap<>();
        Map<Integer, Integer> spriteTopY = new HashMap<>();
        Map<Integer, Integer> spriteTints = new HashMap<>();

        for (int dz = 0; dz < cellBlocks; dz += stride) {
            for (int dx = 0; dx < cellBlocks; dx += stride) {
                int x = blockX + dx;
                int z = blockZ + dz;
                ChunkColumns columns = chunks.get(key(x >> 4, z >> 4));
                if (columns == null) {
                    continue;
                }
                int localX = x & 15;
                int localZ = z & 15;
                int sprite = columns.sprite(localX, localZ);
                spriteVotes.merge(sprite, 1, Integer::sum);
                spriteTopY.merge(sprite, columns.topY(localX, localZ), Math::max);
                spriteTints.putIfAbsent(sprite, columns.tint(localX, localZ));
            }
        }

        if (spriteVotes.isEmpty()) {
            return null;
        }
        int sprite = spriteVotes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return new LodCell(palette.sprite(sprite), spriteTopY.get(sprite) + 1 - minY, spriteTints.getOrDefault(sprite, LodBits.NO_TINT));
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
            if (in.readInt() != FORMAT_VERSION) {
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int chunkX = in.readInt();
                int chunkZ = in.readInt();
                chunks.put(key(chunkX, chunkZ), ChunkColumns.read(in));
            }
        }
    }

    private void writeRegion(int regionX, int regionZ) throws IOException {
        Map<Long, ChunkColumns> regionChunks = new HashMap<>();
        chunks.forEach((key, columns) -> {
            if (unpackX(key) >> REGION_SHIFT == regionX && unpackZ(key) >> REGION_SHIFT == regionZ) {
                regionChunks.put(key, columns);
            }
        });

        Path file = directory.resolve("r." + regionX + "." + regionZ + ".lod");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(regionChunks.size());
            for (Map.Entry<Long, ChunkColumns> entry : regionChunks.entrySet()) {
                out.writeInt(unpackX(entry.getKey()));
                out.writeInt(unpackZ(entry.getKey()));
                entry.getValue().write(out);
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
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
