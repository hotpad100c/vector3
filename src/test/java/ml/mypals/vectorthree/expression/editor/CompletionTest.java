package ml.mypals.vectorthree.expression.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompletionTest {
    @Test
    void chains() {
        assertEquals("self", Completion.chainEndingAt("self.", 4));
        assertEquals("entity(\"a.b\").nbt", Completion.chainEndingAt("1 + entity(\"a.b\").nbt.", 21));
        assertEquals("(a + b)", Completion.chainEndingAt("(a + b).", 7));
        assertEquals("vec(1, 2)[0]", Completion.chainEndingAt("vec(1, 2)[0].", 12));
        assertNull(Completion.chainEndingAt(" .", 1));
    }

    @Test
    void contexts() {
        Completion.Context member = Completion.at("global.sp", 9, false);
        assertEquals("sp", member.prefix());
        assertEquals("global", member.target());
        Completion.Context word = Completion.at("1 + wig", 7, false);
        assertEquals("wig", word.prefix());
        assertNull(word.target());
        assertNull(Completion.at("\"abc.de", 7, false));
        assertNull(Completion.at("1 // self.", 10, false));
        assertNull(Completion.at("Speed: sel", 10, true));
        Completion.Context inside = Completion.at("Speed: {self.po", 15, true);
        assertEquals("self", inside.target());
        assertNull(Completion.at("{{self.po", 9, true));
    }

    @Test
    void arguments() {
        Completion.Context quoted = Completion.at("1 + track(\"Cu", 13, false);
        assertEquals("track", quoted.argumentOf());
        assertEquals("Cu", quoted.prefix());
        assertEquals('"', quoted.quote());
        assertEquals(11, quoted.wordStart());
        Completion.Context bare = Completion.at("entity( ", 8, false);
        assertEquals("entity", bare.argumentOf());
        assertEquals(0, bare.quote());
        Completion.Context single = Completion.at("{entity('St", 11, true);
        assertEquals("entity", single.argumentOf());
        assertEquals('\'', single.quote());
        assertNull(Completion.at("format(\"%.2", 11, false));
        assertNull(Completion.at("self.track(\"a", 13, false));
        assertNull(Completion.at("sin(", 4, false).argumentOf());
    }

    @Test
    void highlighting() {
        List<Highlighter.Span> spans = Highlighter.spans("let v = self.position; // c\nsin(v.x)", false);
        assertEquals(Highlighter.Style.KEYWORD, spans.get(0).style());
        assertEquals(Highlighter.Style.LOCAL, spans.get(1).style());
        assertTrue(spans.stream().anyMatch(s -> s.style() == Highlighter.Style.COMMENT));
        assertTrue(spans.stream().anyMatch(s -> s.style() == Highlighter.Style.FUNCTION));
        assertTrue(spans.stream().anyMatch(s -> s.style() == Highlighter.Style.MEMBER));
        assertEquals(Set.of("v", "w"), Highlighter.locals("let v = 1; let  w = 2; outlet x"));
        List<Highlighter.Span> template = Highlighter.spans("a {time} b", true);
        assertEquals(Highlighter.Style.LITERAL, template.getFirst().style());
        assertEquals(Highlighter.Style.BRACE, template.get(1).style());
        assertEquals(Highlighter.Style.BUILTIN, template.get(2).style());
    }
}
