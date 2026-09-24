package ml.mypals.vectorthree.shape.text;

public record TextSettings(String value, boolean holdText, boolean shadow,
        boolean outline, String billboard, String font) {
    public static TextSettings defaults() {
        return new TextSettings("Text", false, true, false, "ALL", "minecraft:default");
    }

    public TextSettings withValue(String value) {
        return new TextSettings(value, holdText, shadow, outline, billboard, fontOrDefault());
    }

    public String fontOrDefault() { return font == null || font.isBlank() ? "minecraft:default" : font; }

    public static TextSettings transition(TextSettings from, TextSettings to, double amount) {
        if (from == null && to == null) return null;
        from = from == null ? defaults() : from;
        to = to == null ? defaults() : to;
        if (to.holdText) return (amount < 1.0 ? from : to).withValue(amount < 1.0 ? from.value : to.value);

        String value;
        if (amount < 0.5) {
            int keep = (int) Math.floor(visibleLength(from.value) * (1.0 - amount * 2.0));
            value = visiblePrefix(from.value, keep);
        } else {
            int show = (int) Math.floor(visibleLength(to.value) * ((amount - 0.5) * 2.0));
            value = visiblePrefix(to.value, show);
        }
        return new TextSettings(value, to.holdText, to.shadow, to.outline, to.billboard,
                amount >= 1.0 ? to.fontOrDefault() : from.fontOrDefault());
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
