package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import net.kyori.adventure.text.format.NamedTextColor;

@Configuration
public class PaletteConfig {
    @Comment("Primary color for messages")
    public NamedTextColor primary = NamedTextColor.BLUE;

    @Comment("Secondary color for messages")
    public NamedTextColor secondary = NamedTextColor.GREEN;

    @Comment("Foreground color")
    public NamedTextColor foreground = NamedTextColor.WHITE;

    @Comment("Placeholder color")
    public NamedTextColor placeholder = NamedTextColor.GRAY;

    @Comment("Success color")
    public NamedTextColor success = NamedTextColor.GREEN;

    @Comment("Failure color")
    public NamedTextColor failure = NamedTextColor.RED;

    public PaletteConfig() {
    }
}