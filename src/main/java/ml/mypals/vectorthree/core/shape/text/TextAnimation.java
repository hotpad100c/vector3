package ml.mypals.vectorthree.core.shape.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum TextAnimation {
    TYPEWRITER, REVERSE, CENTER, WORD, LINE, SCRAMBLE;

    private static final String NOISE = "#$%&*+=?@!<>/|~^";
    private static final int NOISE_WINDOW = 6;

    private record Unit(String format, int codePoint) {}

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static TextAnimation of(String id) {
        if (id != null) for (TextAnimation animation : values()) if (animation.id().equals(id)) return animation;
        return TYPEWRITER;
    }

    public static int length(String text) { return parse(text).size(); }

    /** {@code text} with everything past its first {@code common} visible characters only partly shown:
     *  {@code shown} of the remaining characters, picked by this animation. */
    public String frame(String text, int common, int shown) {
        List<Unit> units = parse(text);
        int n = units.size() - common;
        shown = Math.clamp(shown, 0, Math.max(n, 0));
        int[] group = new int[Math.max(n, 0)];
        int groups = 0;
        if (this == WORD || this == LINE) {
            boolean inSpace = true;
            for (int i = 0; i < n; i++) {
                int cp = units.get(common + i).codePoint;
                if (this == WORD) {
                    boolean space = Character.isWhitespace(cp);
                    if (!space && inSpace) groups++;
                    inSpace = space;
                } else if (i == 0 || units.get(common + i - 1).codePoint == '\n') groups++;
                group[i] = Math.max(groups - 1, 0);
            }
        }
        int revealedGroups = n == 0 ? 0 : (int) Math.floor(groups * (double) shown / n);
        StringBuilder out = new StringBuilder();
        String last = "";
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            int cp = unit.codePoint;
            if (i >= common) {
                int j = i - common;
                boolean show = switch (this) {
                    case TYPEWRITER, SCRAMBLE -> j < shown;
                    case REVERSE -> j >= n - shown;
                    case CENTER -> j >= (n - shown) / 2 && j < (n - shown) / 2 + shown;
                    case WORD, LINE -> group[j] < revealedGroups;
                };
                if (!show) {
                    boolean noisy = this == SCRAMBLE && j < shown + NOISE_WINDOW && !Character.isWhitespace(cp);
                    if (!noisy) continue;
                    cp = NOISE.charAt(Math.floorMod(j * 7919 + shown * 104729, NOISE.length()));
                }
            }
            if (!unit.format.equals(last)) {
                out.append("§r");
                for (int k = 0; k < unit.format.length(); k++) out.append('§').append(unit.format.charAt(k));
                last = unit.format;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    private static List<Unit> parse(String text) {
        List<Unit> units = new ArrayList<>();
        StringBuilder format = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r' || code >= '0' && code <= '9' || code >= 'a' && code <= 'f') format.setLength(0);
                if (code != 'r') format.append(code);
                i += 2;
                continue;
            }
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            units.add(new Unit(format.toString(), cp));
        }
        return units;
    }
}
