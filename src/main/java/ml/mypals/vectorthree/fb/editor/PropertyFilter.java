package ml.mypals.vectorthree.fb.editor;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/** The search box at the top of the Properties window: rows whose label doesn't contain the text are not drawn. */
public final class PropertyFilter {
    private static final ImString TEXT = new ImString(64);

    private PropertyFilter() {}

    public static void draw() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##vector3_property_filter", I18n.get("vector3.properties.search"), TEXT);
    }

    public static boolean active() {
        return !TEXT.get().isBlank();
    }

    /** {@code key} is a widget key ("label#n"); widgets with hidden labels ("##id") always stay. */
    public static boolean matches(String key) {
        if (!active()) return true;
        int end = key.lastIndexOf('#');
        String label = end < 0 ? key : key.substring(0, end);
        int hidden = label.indexOf("##");
        if (hidden >= 0) label = label.substring(0, hidden);
        return label.isEmpty() || label.toLowerCase(Locale.ROOT).contains(TEXT.get().trim().toLowerCase(Locale.ROOT));
    }
}
