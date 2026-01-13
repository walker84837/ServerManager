package org.winlogon.servermanager;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.winlogon.servermanager.config.PaletteConfig;

public class MessageTheme {
    private final MiniMessage miniMessage;
    private final TagResolver paletteResolver;

    public MessageTheme(PaletteConfig palette) {
        this.paletteResolver = TagResolver.builder()
                .tag("primary", Tag.styling(palette.primary))
                .tag("secondary", Tag.styling(palette.secondary))
                .tag("foreground", Tag.styling(palette.foreground))
                .tag("placeholder", Tag.styling(palette.placeholder))
                .tag("success", Tag.styling(palette.success))
                .tag("failure", Tag.styling(palette.failure))
                .build();

        this.miniMessage = MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolver(paletteResolver)
                        .resolvers(MiniMessage.miniMessage().tags()) // include standard tags
                        .build())
                .build();
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public TagResolver getPaletteResolver() {
        return paletteResolver;
    }
}