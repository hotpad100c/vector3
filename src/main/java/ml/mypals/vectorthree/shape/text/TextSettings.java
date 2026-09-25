package ml.mypals.vectorthree.shape.text;

public record TextSettings(String value, boolean holdText, boolean shadow,
        boolean outline, String billboard, String font,
        boolean glow, boolean outlineGlow, Float glowStrength, Integer glowColor, Float glowSpread) {
    public static final float DEFAULT_GLOW_STRENGTH = 1.5f;
    public static final float MAX_GLOW_STRENGTH = 63 / 8f;
    public static final float DEFAULT_GLOW_SPREAD = 1.5f;
    public static final float MIN_GLOW_SPREAD = 0.25f;
    public static final float MAX_GLOW_SPREAD = 8f;

    public TextSettings(String value, boolean holdText, boolean shadow, boolean outline, String billboard, String font) {
        this(value, holdText, shadow, outline, billboard, font, false, false, null, null, null);
    }

    public static TextSettings defaults() {
        return new TextSettings("Text", false, true, false, "ALL", "minecraft:default");
    }

    public TextSettings withValue(String value) {
        return new TextSettings(value, holdText, shadow, outline, billboard, fontOrDefault(),
                glow, outlineGlow, glowStrength, glowColor, glowSpread);
    }

    private TextSettings withGlow(boolean glow, boolean outlineGlow, Float glowStrength, Integer glowColor, Float glowSpread) {
        return new TextSettings(value, holdText, shadow, outline, billboard, font, glow, outlineGlow, glowStrength,
                glowColor, glowSpread);
    }

    public String fontOrDefault() { return font == null || font.isBlank() ? "minecraft:default" : font; }

    public float glowStrengthOrDefault() {
        return glowStrength == null ? DEFAULT_GLOW_STRENGTH : Math.clamp(glowStrength, 0, MAX_GLOW_STRENGTH);
    }

    public float glowSpreadOrDefault() {
        return glowSpread == null ? DEFAULT_GLOW_SPREAD : Math.clamp(glowSpread, MIN_GLOW_SPREAD, MAX_GLOW_SPREAD);
    }

    public static TextSettings transition(TextSettings from, TextSettings to, double amount) {
        if (from == null && to == null) return null;
        from = from == null ? defaults() : from;
        to = to == null ? defaults() : to;
        float strength = (float) (from.glowStrengthOrDefault() + (to.glowStrengthOrDefault() - from.glowStrengthOrDefault()) * amount);
        float spread = (float) (from.glowSpreadOrDefault() + (to.glowSpreadOrDefault() - from.glowSpreadOrDefault()) * amount);
        Integer glowColor = from.glowColor != null && to.glowColor != null
                ? lerpArgb(from.glowColor, to.glowColor, amount) : amount < 0.5 ? from.glowColor : to.glowColor;
        if (to.holdText) return (amount < 1.0 ? from : to).withValue(amount < 1.0 ? from.value : to.value)
                .withGlow(to.glow, to.outlineGlow, strength, glowColor, spread);

        String value;
        if (amount < 0.5) {
            int keep = (int) Math.floor(visibleLength(from.value) * (1.0 - amount * 2.0));
            value = visiblePrefix(from.value, keep);
        } else {
            int show = (int) Math.floor(visibleLength(to.value) * ((amount - 0.5) * 2.0));
            value = visiblePrefix(to.value, show);
        }
        return new TextSettings(value, to.holdText, to.shadow, to.outline, to.billboard,
                amount >= 1.0 ? to.fontOrDefault() : from.fontOrDefault(), to.glow, to.outlineGlow, strength, glowColor, spread);
    }

    private static int lerpArgb(int from, int to, double amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF, b = (to >>> shift) & 0xFF;
            result |= ((int) Math.round(a + (b - a) * amount) & 0xFF) << shift;
        }
        return result;
    }

    private static int visibleLength(String text) {
        int count = 0;
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) { i += 2; continue; }
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            count++;
        }
        return count;
    }

    private static String visiblePrefix(String text, int count) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < text.length() && visible < count;) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                result.append(text, i, i + 2);
                i += 2;
                continue;
            }
            int codePoint = text.codePointAt(i);
            result.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
            visible++;
        }
        return result.toString();
    }
}
