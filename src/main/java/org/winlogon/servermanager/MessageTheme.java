package org.winlogon.servermanager;

import lombok.Getter;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import org.winlogon.servermanager.config.PaletteConfig;

public class MessageTheme {
    @Getter
    private final MiniMessage miniMessage;
    @Getter
    private final TagResolver paletteResolver;

    public MessageTheme(PaletteConfig palette) {
        this.paletteResolver = TagResolver.builder()
            .tag("primary", Tag.styling(palette.primary))
            .tag("secondary", Tag.styling(palette.secondary))
            .tag("foreground", Tag.styling(palette.foreground))
            .tag("placeholder", Tag.styling(palette.placeholder))
            .tag("success", Tag.styling(palette.success))
            .tag("failure", Tag.styling(palette.failure))
            .tag("warning", Tag.styling(palette.warning))
            .tag("details", Tag.styling(builder -> builder.color(palette.details).decorate(TextDecoration.BOLD)))
            .build();

        var resolver = TagResolver.builder()
            .resolver(paletteResolver)
            .resolver(StandardTags.defaults())
            .build();

        this.miniMessage = MiniMessage.builder()
            .tags(resolver)
            .build();
    }
}
