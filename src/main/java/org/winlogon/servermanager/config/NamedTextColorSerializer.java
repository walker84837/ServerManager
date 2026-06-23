package org.winlogon.servermanager.config;

import de.exlll.configlib.Serializer;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;

public class NamedTextColorSerializer implements Serializer<NamedTextColor, String> {
    private static final Map<String, NamedTextColor> COLORS = Map.ofEntries(
        Map.entry("black", NamedTextColor.BLACK),
        Map.entry("dark_blue", NamedTextColor.DARK_BLUE),
        Map.entry("dark_green", NamedTextColor.DARK_GREEN),
        Map.entry("dark_aqua", NamedTextColor.DARK_AQUA),
        Map.entry("dark_red", NamedTextColor.DARK_RED),
        Map.entry("dark_purple", NamedTextColor.DARK_PURPLE),
        Map.entry("gold", NamedTextColor.GOLD),
        Map.entry("gray", NamedTextColor.GRAY),
        Map.entry("dark_gray", NamedTextColor.DARK_GRAY),
        Map.entry("blue", NamedTextColor.BLUE),
        Map.entry("green", NamedTextColor.GREEN),
        Map.entry("aqua", NamedTextColor.AQUA),
        Map.entry("red", NamedTextColor.RED),
        Map.entry("light_purple", NamedTextColor.LIGHT_PURPLE),
        Map.entry("yellow", NamedTextColor.YELLOW),
        Map.entry("white", NamedTextColor.WHITE)
    );

    @Override
    public String serialize(NamedTextColor element) {
        return element.toString();
    }

    @Override
    public NamedTextColor deserialize(String s) {
        var color = COLORS.get(s.toLowerCase());
        if (color == null) {
            throw new IllegalArgumentException("Unknown color name: " + s);
        }
        return color;
    }
}
