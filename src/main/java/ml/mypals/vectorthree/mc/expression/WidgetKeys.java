package ml.mypals.vectorthree.mc.expression;

import ml.mypals.vectorthree.mixin.minecraft.LanguageAccessor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor widgets are keyed "label#n" by their shown label, which changes with the game language. Bindings store the
 * label's translation keys instead ("t:vector3.keyframe.position|vector3.target.position#0": every key with that
 * text, since which one the editor used can't be told), or the label itself when it has none ("r:facing#0"), and
 * turn them back into this language's labels when they run.
 */
public final class WidgetKeys {
    private static final int MAX_CANDIDATES = 8;
    private static Language language;
    private static Map<String, List<String>> reverse = Map.of();

    private WidgetKeys() {}

    /** The stable key of the widget {@code key} ("label#n"); keys in {@code namespace} come first. */
    public static String stable(String key, String namespace) {
        int hash = key.lastIndexOf('#');
        String label = key.substring(0, hash), index = key.substring(hash);
        int hidden = label.indexOf("##");
        String shown = hidden >= 0 ? label.substring(0, hidden) : label;
        String suffix = hidden >= 0 ? label.substring(hidden) : "";
        List<String> translations = translationKeys(shown, namespace);
        return translations.isEmpty() ? "r:" + label + index : "t:" + String.join("|", translations) + suffix + index;
    }

    /** This language's "label#n" for a stable key, one per candidate translation key. */
    public static List<String> labels(String stable) {
        if (!stable.startsWith("t:")) return List.of(stable.startsWith("r:") ? stable.substring(2) : stable);
        int hash = stable.lastIndexOf('#');
        String body = stable.substring(2, hash);
        int hidden = body.indexOf("##");
        String suffix = (hidden >= 0 ? body.substring(hidden) : "") + stable.substring(hash);
        List<String> labels = new ArrayList<>();
        for (String translation : translations(hidden >= 0 ? body.substring(0, hidden) : body)) {
            String label = I18n.get(translation) + suffix;
            if (!labels.contains(label)) labels.add(label);
        }
        return labels;
    }

    public static String label(String stable) {
        return labels(stable).getFirst();
    }

    /** A short name for reading the widget from an expression: the translation key's last part ("position"). */
    public static String alias(String stable) {
        int hash = stable.lastIndexOf('#');
        String body = stable.substring(2, hash);
        int hidden = body.indexOf("##");
        if (hidden >= 0) body = body.substring(0, hidden);
        String name = body;
        if (stable.startsWith("t:")) {
            String first = translations(body).getFirst();
            name = first.substring(first.lastIndexOf('.') + 1);
        }
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) cleaned = "p_" + cleaned;
        return cleaned;
    }

    /** Aliases of every widget in an editor capture, in order; repeats get _2, _3, ... */
    public static Map<String, String> aliases(Iterable<String> keys, String namespace) {
        Map<String, String> byAlias = new LinkedHashMap<>();
        for (String key : keys) {
            String alias = alias(stable(key, namespace));
            String unique = alias;
            for (int n = 2; byAlias.containsKey(unique); n++) unique = alias + "_" + n;
            byAlias.put(unique, key);
        }
        return byAlias;
    }

    private static List<String> translations(String joined) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int bar = joined.indexOf('|'); bar >= 0; bar = joined.indexOf('|', start)) {
            result.add(joined.substring(start, bar));
            start = bar + 1;
        }
        result.add(joined.substring(start));
        return result;
    }

    private static List<String> translationKeys(String text, String namespace) {
        Language current = Language.getInstance();
        if (current != language) {
            language = current;
            reverse = current instanceof LanguageAccessor accessor ? build(accessor.vector3$storage()) : Map.of();
        }
        List<String> keys = reverse.get(text);
        if (keys == null) return List.of();
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingInt((String key) -> rank(key, namespace)).thenComparing(Comparator.naturalOrder()));
        return sorted.size() > MAX_CANDIDATES ? sorted.subList(0, MAX_CANDIDATES) : sorted;
    }

    private static Map<String, List<String>> build(Map<String, String> storage) {
        Map<String, List<String>> result = new HashMap<>();
        storage.forEach((key, text) -> result.computeIfAbsent(text, ignored -> new ArrayList<>()).add(key));
        return result;
    }

    private static int rank(String key, String namespace) {
        if (key.startsWith(namespace + ".")) return 0;
        if (key.startsWith("vector3.") || key.startsWith("flashback.")) return 1;
        return 2;
    }
}
