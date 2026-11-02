package me.bestnuts.lod.view;

import java.util.List;
import org.bukkit.entity.TextDisplay;

record TileEntities(TextDisplay top, TextDisplay walls) {

    List<TextDisplay> displays() {
        return List.of(top, walls);
    }

    boolean isValid() {
        return top.isValid() && walls.isValid();
    }

    void remove() {
        top.remove();
        walls.remove();
    }
}
