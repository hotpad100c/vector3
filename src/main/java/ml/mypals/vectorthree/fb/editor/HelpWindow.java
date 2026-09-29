package ml.mypals.vectorthree.fb.editor;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiTabItemFlags;
import imgui.moulberry90.flag.ImGuiTableFlags;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import ml.mypals.vectorthree.mixin.flashback.KeybindAccessor;
import ml.mypals.vectorthree.mixin.flashback.KeybindsAccessor;
import java.util.Locale;

/** Help window: vector3's shortcuts and Flashback's tips, one tab each. ? opens the shortcuts. */
public final class HelpWindow {
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_help");
    private static final ImString filter = new ImString("", 64);

    private record Section(String title, List<String> entries) {}

    // Each entry is a translation key; its value is "keys|what it does".
    private static final List<Section> SECTIONS = List.of(
            new Section("vector3.shortcuts.viewport", List.of("move", "rotate", "scale", "geometry", "gizmo_drag",
                    "gizmo_snap", "select_shape", "place", "place_face", "duplicate", "delete_shape")),
            new Section("vector3.shortcuts.editor_camera", List.of("orbit", "pan", "dolly", "focus")),
            new Section("vector3.shortcuts.timeline", List.of("split", "blade", "trim", "alt_copy", "ripple",
                    "track_select", "track_box", "track_copy", "track_delete")),
            new Section("vector3.shortcuts.properties", List.of("row_copy", "row_paste", "row_clear")),
            new Section("vector3.shortcuts.general", List.of("sheet")));

    private static final int SHORTCUTS = 0, TIPS = 1;
    private static int requestedTab = -1;

    private HelpWindow() {}

    public static void renderMenu() {
        if (!ImGui.beginMenu(I18n.get("vector3.help.menu"))) return;
        if (ImGui.menuItem(I18n.get("vector3.shortcuts.title"), "?")) open(SHORTCUTS);
        if (ImGui.menuItem(I18n.get("vector3.help.tips"))) open(TIPS);
        ImGui.endMenu();
    }

    private static void open(int tab) {
        if (!WINDOW.isOpen()) WINDOW.toggle();
        requestedTab = tab;
    }

    public static void render() {
        if (VectorKeybinds.pressed(VectorKeybinds.HELP)) {
            if (WINDOW.isOpen()) WINDOW.toggle();
            else open(SHORTCUTS);
        }
        if (!WINDOW.isOpen()) return;
        ImGui.setNextWindowSize(520, 480, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.help.title")), WINDOW.open())) {
            if (ImGui.beginTabBar("##vector3Help")) {
                if (ImGui.beginTabItem(I18n.get("vector3.shortcuts.title"), tabFlags(SHORTCUTS))) {
                    renderShortcuts();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(I18n.get("vector3.help.tips"), tabFlags(TIPS))) {
                    renderTips();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
            requestedTab = -1;
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static int tabFlags(int tab) {
        return requestedTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
    }

    private static void renderShortcuts() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##filter", I18n.get("vector3.shortcuts.search"), filter);
        String query = filter.get().trim().toLowerCase(Locale.ROOT);
        if (ImGui.beginChild("##shortcuts")) {
            for (Section section : SECTIONS) {
                List<String[]> rows = section.entries().stream()
                        .map(key -> I18n.get("vector3.shortcuts." + key).split("\\|", 2))
                        .filter(row -> row.length == 2 && matches(query, row[0], row[1])).toList();
                table(I18n.get(section.title()), rows);
            }
            table(I18n.get("vector3.shortcuts.flashback"), flashbackRows(query));
        }
        ImGui.endChild();
    }

    // Flashback's own keybinds as currently bound; unbound ones aren't in keybindsForKey.
    private static List<String[]> flashbackRows(String query) {
        Set<Object> keybinds = Collections.newSetFromMap(new IdentityHashMap<>());
        KeybindsAccessor.vector3$keybindsForKey().values().forEach(keybinds::addAll);
        return keybinds.stream()
                .map(keybind -> (KeybindAccessor) keybind)
                .map(keybind -> new String[]{keybind.vector3$keys(), I18n.get("flashback.keybinds." + keybind.vector3$description())})
                .filter(row -> matches(query, row[0], row[1]))
                .sorted(Comparator.comparing(row -> row[1]))
                .toList();
    }

    // Flashback's tip-of-the-day lines, flashback.tip1 upward, in the current language.
    private static void renderTips() {
        if (!ImGui.beginChild("##tips")) {
            ImGui.endChild();
            return;
        }
        for (int i = 1; Language.getInstance().has("flashback.tip" + i); i++) {
            ImGui.bullet();
            ImGui.sameLine();
            ImGui.textWrapped(I18n.get("flashback.tip" + i));
            ImGui.spacing();
        }
        ImGui.endChild();
    }

    private static boolean matches(String query, String keys, String description) {
        return query.isEmpty() || keys.toLowerCase(Locale.ROOT).contains(query)
                || description.toLowerCase(Locale.ROOT).contains(query);
    }

    private static void table(String title, List<String[]> rows) {
        if (rows.isEmpty()) return;
        ImGui.separatorText(title);
        if (!ImGui.beginTable("##" + title, 2, ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp)) return;
        ImGui.tableSetupColumn("keys", 0, 0.35f);
        ImGui.tableSetupColumn("action", 0, 0.65f);
        for (String[] row : rows) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.textColored(0xFF50C8FF, row[0]);
            ImGui.tableNextColumn();
            ImGui.textWrapped(row[1]);
        }
        ImGui.endTable();
    }
}
