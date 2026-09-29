package ml.mypals.vectorthree.expression.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTest {
    private static double num(String source) {
        return Program.expression(source).eval(Scope.ofTick(40)).number();
    }

    private static String text(String source) {
        return Program.template(source).eval(Scope.ofTick(40)).text();
    }

    @Test
    void precedence() {
        assertEquals(7, num("1 + 2 * 3"));
        assertEquals(9, num("(1 + 2) * 3"));
        assertEquals(-4, num("-2^2"));
        assertEquals(512, num("2^3^2"));
        assertEquals(1, num("7 % 3"));
        assertEquals(2, num("-7 % 3"));
        assertEquals(1, num("1 < 2 && 2 <= 2 || 0"));
        assertEquals(5, num("0 ? 3 : 1 ? 5 : 6"));
    }

    @Test
    void timeAndFunctions() {
        assertEquals(2, num("time"));
        assertEquals(40, num("tick"));
        assertEquals(1, num("clamp(5, 0, 1)"));
        assertEquals(3.14, num("round(pi, 2)"), 1e-9);
        assertEquals(0.5, num("smoothstep(0, 1, 0.5)"), 1e-9);
        assertEquals(15, num("remap(0.5, 0, 1, 10, 20)"), 1e-9);
        assertEquals(3, num("max(1, 3, 2)"));
    }

    @Test
    void vectors() {
        assertEquals(5, num("length(vec(3, 4))"), 1e-9);
        assertEquals(6, num("(vec(1, 2, 3) * 2).z"));
        assertEquals(4, num("(vec(1, 2) + vec(0, 2))[1]"));
        assertEquals(1, num("vec(1, 2, 3).r"));
    }

    @Test
    void strings() {
        assertEquals("a1", Program.expression("'a' + 1").eval(Scope.ofTick(0)).text());
        assertEquals("Time 2s", text("Time {time}s"));
        assertEquals("{literal}", text("{{literal}}"));
        assertEquals("007", text("{pad(7, 3, \"0\")}"));
        assertEquals("1.50", text("{format(\"%.2f\", 1.5)}"));
        assertEquals("3", text("{format(\"%d\", 3)}"));
        assertEquals("x}y", text("{\"x}y\"}"));
        assertEquals("plain", text("plain"));
    }

    @Test
    void noiseIsDeterministic() {
        assertEquals(num("noise(1.37, 4)"), num("noise(1.37, 4)"));
        assertEquals(num("rand(9)"), num("rand(9)"));
        assertNotEquals(num("rand(9)"), num("rand(10)"));
        for (int i = 0; i < 200; i++) {
            double n = num("noise(" + i * 0.173 + ")");
            assertTrue(n >= -1.01 && n <= 1.01, "noise out of range: " + n);
        }
    }

    @Test
    void errors() {
        ExprError unknown = assertThrows(ExprError.class, () -> Program.expression("1 + foo(2)"));
        assertEquals(4, unknown.position());
        ExprError arity = assertThrows(ExprError.class, () -> Program.expression("sin(1, 2)"));
        assertTrue(arity.getMessage().contains("sin()"));
        assertThrows(ExprError.class, () -> Program.expression("(1 + 2"));
        assertThrows(ExprError.class, () -> Program.expression(""));
        ExprError templated = assertThrows(ExprError.class, () -> Program.template("ab {1 +} c"));
        assertEquals(7, templated.position());
        ExprError track = assertThrows(ExprError.class, () -> Program.expression("2 * Cube.position").eval(Scope.ofTick(0)));
        assertEquals(4, track.position());
        assertThrows(ExprError.class, () -> Program.expression("self.position").eval(Scope.ofTick(0)));
        assertTrue(Program.expression("value * 2").usesValue());
        assertFalse(Program.expression("time").usesValue());
    }
}
