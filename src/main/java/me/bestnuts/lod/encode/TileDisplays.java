package me.bestnuts.lod.encode;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

public final class TileDisplays {

    private static final Display.Brightness SKY_ONLY = new Display.Brightness(0, 15);

    private TileDisplays() {
    }

    public static TextDisplay spawn(Location anchor, int level, int size, Transformation transformation, Component text) {
        return anchor.getWorld().spawn(anchor, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(SKY_ONLY);
            display.setDisplayWidth(0.0f);
            display.setDisplayHeight(0.0f);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0));
            display.setLineWidth(TileText.lineWidth(size));
            display.setTextOpacity(LodBits.opacity(level));
            display.setTransformation(transformation);
            display.text(text);
        });
    }
}
