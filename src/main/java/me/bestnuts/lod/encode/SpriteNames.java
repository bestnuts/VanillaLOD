package me.bestnuts.lod.encode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;

/**
 * Block atlas sprite names shipped with vanilla 26.3, from assets/minecraft/textures/block.
 */
public final class SpriteNames {

    private static final String RESOURCE = "/block_sprites.txt";
    private static final String BLOCK_PREFIX = "block/";
    private static final Set<String> NAMES = load();

    private SpriteNames() {
    }

    public static Optional<Key> find(String... candidates) {
        for (String candidate : candidates) {
            if (NAMES.contains(candidate)) {
                return Optional.of(sprite(candidate));
            }
        }
        return Optional.empty();
    }

    public static boolean exists(Key sprite) {
        return sprite.value().startsWith(BLOCK_PREFIX) && NAMES.contains(sprite.value().substring(BLOCK_PREFIX.length()));
    }

    public static Key sprite(String name) {
        return Key.key("minecraft", BLOCK_PREFIX + name);
    }

    private static Set<String> load() {
        try (InputStream in = SpriteNames.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
