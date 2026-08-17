package org.winlogon.servermanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AnsiComponentDecoderTest {

    private static Component decode(String input) {
        return AnsiComponentDecoder.decode(input);
    }

    private static String textContent(Component component) {
        return ((TextComponent) component).content();
    }

    private static String childText(Component parent, int index) {
        return textContent(parent.children().get(index));
    }

    private static Style childStyle(Component parent, int index) {
        return parent.children().get(index).style();
    }

    private static Component expectedChild(String text, Style style) {
        return Component.text().append(Component.text(text, style)).build();
    }

    private static Component expectedChild(String text) {
        return Component.text().append(Component.text(text)).build();
    }

    // --- Edge cases ---

    @Test
    void nullInput() {
        assertEquals(Component.empty(), decode(null));
    }

    @Test
    void emptyInput() {
        assertEquals(Component.empty(), decode(""));
    }

    @Test
    void plainText() {
        assertEquals(expectedChild("hello"), decode("hello"));
    }

    @Test
    void bareResetCode() {
        var result = decode("\u001b[0m");
        assertTrue(result.children().isEmpty());
    }

    // --- Color codes ---

    @ParameterizedTest
    @MethodSource("colorCodeCases")
    void colorCodes(int ansiCode, NamedTextColor expectedColor) {
        var result = decode("\u001b[" + ansiCode + "mhello");
        assertEquals(expectedChild("hello", Style.style().color(expectedColor).build()), result);
    }

    static Stream<Arguments> colorCodeCases() {
        return Stream.of(
            Arguments.of(31, NamedTextColor.DARK_RED),
            Arguments.of(32, NamedTextColor.DARK_GREEN),
            Arguments.of(33, NamedTextColor.GOLD),
            Arguments.of(34, NamedTextColor.DARK_BLUE),
            Arguments.of(35, NamedTextColor.DARK_PURPLE),
            Arguments.of(36, NamedTextColor.DARK_AQUA),
            Arguments.of(37, NamedTextColor.GRAY),
            Arguments.of(90, NamedTextColor.GRAY),
            Arguments.of(91, NamedTextColor.RED),
            Arguments.of(92, NamedTextColor.GREEN),
            Arguments.of(93, NamedTextColor.YELLOW),
            Arguments.of(94, NamedTextColor.BLUE),
            Arguments.of(95, NamedTextColor.LIGHT_PURPLE),
            Arguments.of(96, NamedTextColor.AQUA),
            Arguments.of(39, NamedTextColor.WHITE),
            Arguments.of(97, NamedTextColor.WHITE)
        );
    }

    // --- Decoration codes ---

    @Test
    void boldCode() {
        var result = decode("\u001b[1mbold");
        assertEquals(expectedChild("bold", Style.style().decorate(TextDecoration.BOLD).build()), result);
    }

    @Test
    void italicCode() {
        var result = decode("\u001b[3mitalic");
        assertEquals(expectedChild("italic", Style.style().decorate(TextDecoration.ITALIC).build()), result);
    }

    @Test
    void underlineCode() {
        var result = decode("\u001b[4munderline");
        assertEquals(expectedChild("underline", Style.style().decorate(TextDecoration.UNDERLINED).build()), result);
    }

    @Test
    void strikethroughCode() {
        var result = decode("\u001b[9mstrike");
        assertEquals(expectedChild("strike", Style.style().decorate(TextDecoration.STRIKETHROUGH).build()), result);
    }

    @Test
    void multipleCodesWithinSequence() {
        var result = decode("\u001b[1;31mbold red");
        var expected = Style.style()
            .decorate(TextDecoration.BOLD)
            .color(NamedTextColor.DARK_RED)
            .build();
        assertEquals(expectedChild("bold red", expected), result);
    }

    @Test
    void separateSequencesDoNotAccumulate() {
        var result = decode("\u001b[1m\u001b[31mbold red");
        var children = result.children();
        assertEquals(1, children.size());
        assertEquals("bold red", textContent(children.get(0)));
        assertEquals(NamedTextColor.DARK_RED, children.get(0).style().color());
        assertFalse(children.get(0).style().hasDecoration(TextDecoration.BOLD));
    }

    @Test
    void unsetBold() {
        var result = decode("\u001b[1mtext\u001b[22mno bold");
        var children = result.children();
        assertEquals(2, children.size());
        assertEquals(expectedChild("text", Style.style().decorate(TextDecoration.BOLD).build()),
            Component.text().append(children.get(0)).build());
        assertEquals(expectedChild("no bold", Style.style().decoration(TextDecoration.BOLD, false).build()),
            Component.text().append(children.get(1)).build());
    }

    // --- Reset code ---

    @Test
    void resetCodeResetsAll() {
        var result = decode("\u001b[1;31mbold red\u001b[0mplain");
        var children = result.children();
        assertEquals(2, children.size());

        var resetStyle = Style.style()
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)
            .build();

        var styled = Style.style()
            .decorate(TextDecoration.BOLD)
            .color(NamedTextColor.DARK_RED)
            .build();

        assertEquals(expectedChild("bold red", styled), Component.text().append(children.get(0)).build());
        assertEquals(expectedChild("plain", resetStyle), Component.text().append(children.get(1)).build());
    }

    // --- Ignored codes ---

    @ParameterizedTest
    @MethodSource("ignoredCodeCases")
    void ignoredCodes(int ansiCode) {
        var result = decode("\u001b[" + ansiCode + "mtext");
        assertEquals(expectedChild("text"), result);
    }

    static Stream<Arguments> ignoredCodeCases() {
        return Stream.of(
            Arguments.of(38),   // extended fg color
            Arguments.of(40),   // bg color
            Arguments.of(48),   // bg color
            Arguments.of(100),  // bright bg
            Arguments.of(999)   // unknown code
        );
    }

    // --- Malformed input ---

    @Test
    void nonNumericParam() {
        var result = decode("\u001b[12a34mtext");
        var children = result.children();
        assertEquals(1, children.size());
        assertEquals("\u001b[12a34mtext", textContent(children.get(0)));
    }

    @Test
    void emptyParamSegments() {
        var result = decode("\u001b[;;mtext");
        assertEquals(expectedChild("text"), result);
    }

    @Test
    void unmatchedEscape() {
        var result = decode("\u001b[31mhello\u001b[world");
        var children = result.children();
        assertEquals(1, children.size());
        assertEquals("hello\u001b[world", textContent(children.get(0)));
        assertEquals(NamedTextColor.DARK_RED, children.get(0).style().color());
    }

    // --- Trailing/multiple segments ---

    @Test
    void multipleSegments() {
        var result = decode("\u001b[31mred\u001b[32mgreen");
        var children = result.children();
        assertEquals(2, children.size());
        assertEquals("red", childText(result, 0));
        assertEquals(NamedTextColor.DARK_RED, childStyle(result, 0).color());
        assertEquals("green", childText(result, 1));
        assertEquals(NamedTextColor.DARK_GREEN, childStyle(result, 1).color());
    }

    @Test
    void textBeforeFirstCode() {
        var result = decode("plain\u001b[31mred");
        var children = result.children();
        assertEquals(2, children.size());
        assertEquals("plain", childText(result, 0));
        assertEquals(Style.empty(), childStyle(result, 0));
        assertEquals("red", childText(result, 1));
        assertEquals(NamedTextColor.DARK_RED, childStyle(result, 1).color());
    }
}
