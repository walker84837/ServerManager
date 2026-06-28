package org.winlogon.servermanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnsiComponentDecoder {
    private static final Pattern SGR = Pattern.compile("\u001b\\[([\\d;]*)m");

    private AnsiComponentDecoder() {
    }

    static Component decode(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        var matcher = SGR.matcher(input);
        var builder = Component.text();
        var style = Style.empty();
        int pos = 0;

        while (matcher.find()) {
            if (matcher.start() > pos) {
                builder.append(Component.text(input.substring(pos, matcher.start()), style));
            }
            style = applySequence(matcher.group(1));
            pos = matcher.end();
        }

        if (pos < input.length()) {
            builder.append(Component.text(input.substring(pos), style));
        }

        return builder.build();
    }

    private static Style applySequence(String params) {
        if (params.isEmpty()) {
            return Style.empty();
        }

        var builder = Style.style();
        for (var code : params.split(";")) {
            if (code.isEmpty()) continue;
            try {
                applyCode(builder, Integer.parseInt(code));
            } catch (NumberFormatException ignored) {
            }
        }
        return builder.build();
    }

    private static void applyCode(Style.Builder builder, int code) {
        switch (code) {
            case 0 -> {
                builder.color(NamedTextColor.WHITE);
                builder.decoration(TextDecoration.BOLD, false);
                builder.decoration(TextDecoration.ITALIC, false);
                builder.decoration(TextDecoration.UNDERLINED, false);
                builder.decoration(TextDecoration.STRIKETHROUGH, false);
            }
            case 1 -> builder.decorate(TextDecoration.BOLD);
            case 3 -> builder.decorate(TextDecoration.ITALIC);
            case 4 -> builder.decorate(TextDecoration.UNDERLINED);
            case 9 -> builder.decorate(TextDecoration.STRIKETHROUGH);
            case 22 -> builder.decoration(TextDecoration.BOLD, false);
            case 23 -> builder.decoration(TextDecoration.ITALIC, false);
            case 24 -> builder.decoration(TextDecoration.UNDERLINED, false);
            case 29 -> builder.decoration(TextDecoration.STRIKETHROUGH, false);
            case 30 -> builder.color(NamedTextColor.BLACK);
            case 31 -> builder.color(NamedTextColor.DARK_RED);
            case 32 -> builder.color(NamedTextColor.DARK_GREEN);
            case 33 -> builder.color(NamedTextColor.GOLD);
            case 34 -> builder.color(NamedTextColor.DARK_BLUE);
            case 35 -> builder.color(NamedTextColor.DARK_PURPLE);
            case 36 -> builder.color(NamedTextColor.DARK_AQUA);
            case 37, 90 -> builder.color(NamedTextColor.GRAY);
            case 38 -> { /* extended fg color - silently ignored */ }
            case 39, 97 -> builder.color(NamedTextColor.WHITE);
            case 40, 41, 42, 43, 44, 45, 46, 47, 48, 49 -> { /* bg color codes - not supported by this Adventure version */ }
            case 91 -> builder.color(NamedTextColor.RED);
            case 92 -> builder.color(NamedTextColor.GREEN);
            case 93 -> builder.color(NamedTextColor.YELLOW);
            case 94 -> builder.color(NamedTextColor.BLUE);
            case 95 -> builder.color(NamedTextColor.LIGHT_PURPLE);
            case 96 -> builder.color(NamedTextColor.AQUA);
            case 100, 101, 102, 103, 104, 105, 106, 107 -> { /* bright bg codes - not supported */ }
            default -> { }
        }
    }
}
