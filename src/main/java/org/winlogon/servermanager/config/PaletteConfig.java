package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.SerializeWith;

import net.kyori.adventure.text.format.NamedTextColor;

@Configuration
public class PaletteConfig {
    @Comment("Primary color for messages")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor primary = NamedTextColor.BLUE;

    @Comment("Secondary color for messages")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor secondary = NamedTextColor.GREEN;

    @Comment("Foreground color")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor foreground = NamedTextColor.WHITE;

    @Comment("Placeholder color")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor placeholder = NamedTextColor.GRAY;

    @Comment("Success color")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor success = NamedTextColor.GREEN;

    @Comment("Failure color")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor failure = NamedTextColor.RED;

    @Comment("Warning color for alerts and warnings")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor warning = NamedTextColor.YELLOW;

    @Comment("Details color for data values")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor details = NamedTextColor.GRAY;

    @Comment("Color for section headers (e.g., === System Health ===)")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor header = NamedTextColor.AQUA;

    @Comment("Color for inline labels (e.g., OS:, CPU:, Total:)")
    @SerializeWith(serializer = NamedTextColorSerializer.class)
    public NamedTextColor label = NamedTextColor.DARK_AQUA;

    public PaletteConfig() {
    }
}
