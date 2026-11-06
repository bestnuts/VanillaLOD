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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import me.bestnuts.lod.encode.LodBits;
import me.bestnuts.lod.encode.LodCell;

/**
 * Surface columns of one world, kept per 32 x 32 chunk region at three strides. The 16 block summary of every region
 * stays in memory; the 4 block summary and full resolution are read from disk on demand and dropped when unused,
 * so distant terrain costs a sliver of what full resolution would.
 */
public final class LodWorldCache {

    private static final int REGION_SHIFT = 5;
    private static final int REGION_CHUNKS = 1 << REGION_SHIFT;
    private static final int FORMAT_VERSION = 7;
    private static final int SUMMARY_VERSION = 1;
    private static final int COARSE_STRIDE = 16;
    private static final int MEDIUM_STRIDE = 4;
    private static final int FINE_STRIDE = 1;
    private static final int MEDIUM_REGIONS = 256;
    private static final int FINE_REGIONS = 32;
    private static final int MAX_SAMPLES_PER_AXIS = 4;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.lod");

    private static final class Region {
        private final int x;
        private final int z;
        private final ColumnGrid coarse;
        private ColumnGrid medium;
        private ColumnGrid fine;
        private boolean dirty;
        private volatile int chunks;

        private Region(int x, int z, ColumnGrid coarse) {
            this.x = x;
            this.z = z;
            this.coarse = coarse;
        }
    }

    private final Path directory;
    private final int minY;
    private final FloatingRule floatingRule;
    private final SpritePalette palette = new SpritePalette();
    private final Map<Long, Region> regions = new ConcurrentHashMap<>();
    private final LinkedHashMap<Long, Region> mediumLoaded = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<Long, Region> fineLoaded = new LinkedHashMap<>(16, 0.75f, true);

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
        return regions.values().stream().mapToInt(region -> region.chunks).sum();
    }

    public boolean contains(int chunkX, int chunkZ) {
        Region region = regions.get(key(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
        return region != null && region.coarse.cached(region.coarse.index(localBlock(chunkX << 4), localBlock(chunkZ << 4)));
    }

    public int cachedChunks(int regionX, int regionZ) {
        Region region = regions.get(key(regionX, regionZ));
        return region == null ? 0 : region.chunks;
    }

    public void put(int chunkX, int chunkZ, ChunkColumns columns) {
        int regionX = chunkX >> REGION_SHIFT;
        int regionZ = chunkZ >> REGION_SHIFT;
        Region region = regions.computeIfAbsent(key(regionX, regionZ), key -> new Region(regionX, regionZ, new ColumnGrid(COARSE_STRIDE)));
        int localX = chunkX & (REGION_CHUNKS - 1);
        int localZ = chunkZ & (REGION_CHUNKS - 1);
        synchronized (region) {
            ColumnGrid fine = fine(region);
            ColumnGrid medium = medium(region);
            boolean added = !region.coarse.cached(region.coarse.index(localX << 4, localZ << 4));
            fine.setChunk(localX, localZ, columns);
            medium.refreshChunk(fine, localX, localZ);
            region.coarse.refreshChunk(fine, localX, localZ);
            region.dirty = true;
            if (added) {
                region.chunks++;
            }
        }
        touch(fineLoaded, FINE_REGIONS, region);
        touch(mediumLoaded, MEDIUM_REGIONS, region);
    }

    public LodCell sample(int blockX, int blockZ, int cellBlocks) {
        int stride = Math.max(1, cellBlocks / MAX_SAMPLES_PER_AXIS);
        int gridStride = gridStride(stride);
        Map<Integer, Integer> spriteVotes = new HashMap<>();
        Map<Integer, Integer> spriteTopY = new HashMap<>();
        Map<Integer, Integer> spriteTints = new HashMap<>();
        Map<Integer, Long> spriteBottomSum = new HashMap<>();
        Map<Integer, Integer> spriteFloating = new HashMap<>();
        Set<Integer> spriteGrounded = new HashSet<>();
        boolean cached = false;
        ColumnGrid grid = grid(blockX, blockZ, gridStride);
        if (grid == null) {
            return null;
        }

        for (int dz = 0; dz < cellBlocks; dz += stride) {
            for (int dx = 0; dx < cellBlocks; dx += stride) {
                int index = grid.index(localBlock(blockX + dx), localBlock(blockZ + dz));
                if (!grid.cached(index)) {
                    continue;
                }
                cached = true;
                int sprite = grid.sprite(index);
                if (sprite == ChunkColumns.NO_SURFACE) {
                    continue;
                }
                spriteVotes.merge(sprite, 1, Integer::sum);
                spriteTopY.merge(sprite, grid.top(index), Math::max);
                spriteTints.putIfAbsent(sprite, grid.tint(index));
                int bottomY = grid.bottom(index);
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

    // Lowest surface anywhere under a cell, so a skirt also covers what a finer neighbour tile draws.
    public int lowestSurface(int blockX, int blockZ, int cellBlocks) {
        int gridStride = gridStride(cellBlocks);
        ColumnGrid grid = grid(blockX, blockZ, gridStride);
        if (grid == null) {
            return LodCell.NO_FLOOR;
        }
        int lowest = ColumnGrid.NO_FLOOR;
        for (int dz = 0; dz < cellBlocks; dz += gridStride) {
            for (int dx = 0; dx < cellBlocks; dx += gridStride) {
                lowest = Math.min(lowest, grid.lowest(grid.index(localBlock(blockX + dx), localBlock(blockZ + dz))));
            }
        }
        return lowest == ColumnGrid.NO_FLOOR ? LodCell.NO_FLOOR : lowest + 1 - minY;
    }

    public void load() throws IOException {
        palette.load(directory.resolve("palette.txt"));
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "r.*.lod")) {
            for (Path file : files) {
                Matcher matcher = REGION_FILE.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    loadRegion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                }
            }
        }
    }

    public void saveDirty() throws IOException {
        List<Region> dirty = regions.values().stream().filter(region -> region.dirty).toList();
        for (Region region : dirty) {
            synchronized (region) {
                save(region);
            }
        }
    }

    private void loadRegion(int regionX, int regionZ) throws IOException {
        ColumnGrid coarse = readSummary(regionX, regionZ, COARSE_STRIDE);
        if (coarse != null) {
            Region region = new Region(regionX, regionZ, coarse);
            region.chunks = countChunks(coarse);
            regions.put(key(regionX, regionZ), region);
            return;
        }
        // Region files written before the summaries existed get them derived once.
        ColumnGrid fine = readFine(regionX, regionZ);
        if (fine == null) {
            return;
        }
        Region region = new Region(regionX, regionZ, new ColumnGrid(COARSE_STRIDE));
        region.fine = fine;
        region.medium = derive(fine, MEDIUM_STRIDE);
        fillCoarse(region);
        region.chunks = countChunks(region.coarse);
        writeSummary(region, region.medium);
        writeSummary(region, region.coarse);
        region.medium = null;
        region.fine = null;
        regions.put(key(regionX, regionZ), region);
    }

    // Cells are at most 128 blocks and aligned to their size, so one cell never spans two regions.
    private ColumnGrid grid(int blockX, int blockZ, int stride) {
        Region region = regions.get(key(blockX >> 9, blockZ >> 9));
        if (region == null) {
            return null;
        }
        if (stride == COARSE_STRIDE) {
            return region.coarse;
        }
        ColumnGrid grid;
        synchronized (region) {
            grid = stride == MEDIUM_STRIDE ? medium(region) : fine(region);
        }
        if (stride == MEDIUM_STRIDE) {
            touch(mediumLoaded, MEDIUM_REGIONS, region);
        } else {
            touch(fineLoaded, FINE_REGIONS, region);
        }
        return grid;
    }

    // Caller holds the region lock.
    private ColumnGrid fine(Region region) {
        if (region.fine == null) {
            try {
                ColumnGrid fine = readFine(region.x, region.z);
                region.fine = fine == null ? new ColumnGrid(FINE_STRIDE) : fine;
            } catch (IOException e) {
                region.fine = new ColumnGrid(FINE_STRIDE);
            }
        }
        return region.fine;
    }

    // Caller holds the region lock. A dirty region's summary on disk is stale, so it is derived from full resolution instead.
    private ColumnGrid medium(Region region) {
        if (region.medium == null) {
            ColumnGrid medium = null;
            if (!region.dirty) {
                try {
                    medium = readSummary(region.x, region.z, MEDIUM_STRIDE);
                } catch (IOException e) {
                    medium = null;
                }
            }
            region.medium = medium != null ? medium : derive(fine(region), MEDIUM_STRIDE);
        }
        return region.medium;
    }

    private void touch(LinkedHashMap<Long, Region> loaded, int capacity, Region region) {
        List<Region> evicted = new ArrayList<>();
        synchronized (loaded) {
            loaded.put(key(region.x, region.z), region);
            Iterator<Region> eldest = loaded.values().iterator();
            while (loaded.size() > capacity && eldest.hasNext()) {
                evicted.add(eldest.next());
                eldest.remove();
            }
        }
        for (Region victim : evicted) {
            synchronized (victim) {
                if (loaded == fineLoaded) {
                    if (victim.dirty) {
                        try {
                            save(victim);
                        } catch (IOException e) {
                            continue;
                        }
                    }
                    victim.fine = null;
                } else {
                    victim.medium = null;
                }
            }
        }
    }

    // Caller holds the region lock; a dirty region always has its full resolution loaded.
    private void save(Region region) throws IOException {
        if (!region.dirty) {
            return;
        }
        Files.createDirectories(directory);
        palette.save(directory.resolve("palette.txt"));
        writeFine(region);
        writeSummary(region, medium(region));
        writeSummary(region, region.coarse);
        region.dirty = false;
    }

    private void fillCoarse(Region region) {
        for (int chunkZ = 0; chunkZ < REGION_CHUNKS; chunkZ++) {
            for (int chunkX = 0; chunkX < REGION_CHUNKS; chunkX++) {
                if (region.fine.cached(region.fine.index(chunkX << 4, chunkZ << 4))) {
                    region.coarse.refreshChunk(region.fine, chunkX, chunkZ);
                }
            }
        }
    }

    private static ColumnGrid derive(ColumnGrid fine, int stride) {
        ColumnGrid grid = new ColumnGrid(stride);
        for (int chunkZ = 0; chunkZ < REGION_CHUNKS; chunkZ++) {
            for (int chunkX = 0; chunkX < REGION_CHUNKS; chunkX++) {
                if (fine.cached(fine.index(chunkX << 4, chunkZ << 4))) {
                    grid.refreshChunk(fine, chunkX, chunkZ);
                }
            }
        }
        return grid;
    }

    private static int countChunks(ColumnGrid coarse) {
        int count = 0;
        for (int chunkZ = 0; chunkZ < REGION_CHUNKS; chunkZ++) {
            for (int chunkX = 0; chunkX < REGION_CHUNKS; chunkX++) {
                if (coarse.cached(coarse.index(chunkX << 4, chunkZ << 4))) {
                    count++;
                }
            }
        }
        return count;
    }

    private ColumnGrid readFine(int regionX, int regionZ) throws IOException {
        Path file = regionFile(regionX, regionZ, "lod");
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != FORMAT_VERSION || in.readInt() != floatingRule.airGap() || in.readInt() != floatingRule.maxThickness()) {
                return null;
            }
            ColumnGrid fine = new ColumnGrid(FINE_STRIDE);
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int chunkX = in.readInt();
                int chunkZ = in.readInt();
                fine.setChunk(chunkX & (REGION_CHUNKS - 1), chunkZ & (REGION_CHUNKS - 1), ChunkColumns.read(in));
            }
            return fine;
        }
    }

    private void writeFine(Region region) throws IOException {
        List<int[]> cached = new ArrayList<>();
        for (int chunkZ = 0; chunkZ < REGION_CHUNKS; chunkZ++) {
            for (int chunkX = 0; chunkX < REGION_CHUNKS; chunkX++) {
                if (region.coarse.cached(region.coarse.index(chunkX << 4, chunkZ << 4))) {
                    cached.add(new int[]{chunkX, chunkZ});
                }
            }
        }
        write(regionFile(region.x, region.z, "lod"), out -> {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(floatingRule.airGap());
            out.writeInt(floatingRule.maxThickness());
            out.writeInt(cached.size());
            for (int[] chunk : cached) {
                out.writeInt((region.x << REGION_SHIFT) + chunk[0]);
                out.writeInt((region.z << REGION_SHIFT) + chunk[1]);
                region.fine.chunk(chunk[0], chunk[1]).write(out);
            }
        });
    }

    private ColumnGrid readSummary(int regionX, int regionZ, int stride) throws IOException {
        Path file = regionFile(regionX, regionZ, "s" + stride);
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != SUMMARY_VERSION || in.readInt() != floatingRule.airGap() || in.readInt() != floatingRule.maxThickness()
                    || in.readInt() != stride) {
                return null;
            }
            return ColumnGrid.read(in, stride);
        }
    }

    private void writeSummary(Region region, ColumnGrid grid) throws IOException {
        write(regionFile(region.x, region.z, "s" + grid.stride()), out -> {
            out.writeInt(SUMMARY_VERSION);
            out.writeInt(floatingRule.airGap());
            out.writeInt(floatingRule.maxThickness());
            out.writeInt(grid.stride());
            grid.write(out);
        });
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static void write(Path file, Writer writer) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
            writer.write(out);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path regionFile(int regionX, int regionZ, String extension) {
        return directory.resolve("r." + regionX + "." + regionZ + "." + extension);
    }

    private static int gridStride(int stride) {
        if (stride >= COARSE_STRIDE) {
            return COARSE_STRIDE;
        }
        return stride >= MEDIUM_STRIDE ? MEDIUM_STRIDE : FINE_STRIDE;
    }

    private static int localBlock(int block) {
        return block & (ColumnGrid.REGION_BLOCKS - 1);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
