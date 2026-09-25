package ml.mypals.vectorthree.prefab;

import ml.mypals.vectorthree.flashback.PersistentWindow;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PrefabBasketWindow {
    public static final String PAYLOAD = "vector3_prefab";
    private static final String TEMPLATE = "template:";
    private static final String SAVED = "saved:";
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_prefab_basket");

    private static @Nullable List<PrefabStore.Saved> saved;
    private static @Nullable String placeAtCursor;
    private static boolean saveRequested;

    private PrefabBasketWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.prefab.basket"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static void reload() {
        saved = null;
    }

    public static @Nullable String consumePlaceAtCursor() {
        String id = placeAtCursor;
        placeAtCursor = null;
        return id;
    }

    public static boolean consumeSaveRequest() {
        boolean requested = saveRequested;
        saveRequested = false;
        return requested;
    }

    public static void place(String id, int tick) {
        if (id.startsWith(TEMPLATE)) {
            PrefabTemplates.Template template = PrefabTemplates.get(id.substring(TEMPLATE.length()));
            if (template != null) Vector3.PREFABS.beginPlace(template.build(), template, tick);
        } else if (id.startsWith(SAVED)) {
            int index = Integer.parseInt(id.substring(SAVED.length()));
            List<PrefabStore.Saved> prefabs = saved();
            if (index < prefabs.size()) Vector3.PREFABS.beginPlace(prefabs.get(index).prefab(), null, tick);
        }
    }

    public static void render(boolean hasSelection) {
        if (!WINDOW.isOpen()) return;
        ImGui.setNextWindowSize(280, 320, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.prefab.basket")), WINDOW.open())) {
            ImGui.textDisabled(I18n.get("vector3.prefab.drag_hint"));
            ImGui.separator();
            for (PrefabTemplates.Template template : PrefabTemplates.all()) {
                item(I18n.get(template.nameKey()), TEMPLATE + template.id());
            }
            ImGui.separator();
            List<PrefabStore.Saved> prefabs = saved();
            for (int i = 0; i < prefabs.size(); i++) {
                PrefabStore.Saved prefab = prefabs.get(i);
                item(prefab.prefab().name(), SAVED + i);
                if (ImGui.beginPopupContextItem("##prefab_menu_" + i)) {
                    if (ImGui.menuItem(I18n.get("vector3.prefab.delete"))) {
                        try {
                            PrefabStore.delete(prefab.file());
                        } catch (Exception exception) {
                            Vector3.LOGGER.warn("Could not delete prefab {}", prefab.file(), exception);
                        }
                        reload();
                    }
                    ImGui.endPopup();
                }
            }
            if (prefabs.isEmpty()) ImGui.textDisabled(I18n.get("vector3.prefab.none_saved"));
            ImGui.separator();
            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.button(I18n.get("vector3.prefab.save_selection"))) saveRequested = true;
            if (!hasSelection) ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.prefab.reload"))) reload();
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static void item(String name, String id) {
        if (ImGui.selectable(name + "###" + id) && ImGui.isMouseDoubleClicked(0)) placeAtCursor = id;
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD, id);
            ImGui.textUnformatted(name);
            ImGui.endDragDropSource();
        }
    }

    private static List<PrefabStore.Saved> saved() {
        if (saved == null) saved = PrefabStore.list();
        return saved;
    }
}
