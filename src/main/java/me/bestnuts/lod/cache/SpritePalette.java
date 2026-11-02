package me.bestnuts.lod.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;

public final class SpritePalette {

    private final List<Key> sprites = new ArrayList<>();
    private final Map<Key, Integer> ids = new HashMap<>();

    public synchronized int id(Key sprite) {
        Integer id = ids.get(sprite);
        if (id != null) {
            return id;
        }
        sprites.add(sprite);
        ids.put(sprite, sprites.size() - 1);
        return sprites.size() - 1;
    }

    public synchronized Key sprite(int id) {
        return sprites.get(id);
    }

    public synchronized void load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                id(Key.key(line.strip()));
            }
        }
    }

    public synchronized void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, sprites.stream().map(Key::asString).toList(), StandardCharsets.UTF_8);
    }
}
