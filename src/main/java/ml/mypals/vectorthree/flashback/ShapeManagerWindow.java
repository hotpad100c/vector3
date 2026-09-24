package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShapeManagerWindow {
    private static final ImString search = new ImString("", 128);
    private static final ImBoolean editorMode = new ImBoolean(false);
    private static final ImBoolean debugBypassOnly = new ImBoolean(false);
    private static final ImBoolean open = new ImBoolean(false);

    private ShapeManagerWindow() {}

    public static boolean isEditorMode() { return editorMode.get(); }

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.shape_manager.title"))) open.set(!open.get());
    }

    public static void render() {
        if (!open.get()) return;
        ImGui.setNextWindowSize(360, 320, ImGuiCond.FirstUseEver);
        if (ImGui.begin(I18n.get("vector3.shape_manager.title"), open)) {
            ImGui.checkbox(I18n.get("vector3.shape_manager.editor_mode"), editorMode);
            ImGui.checkbox(I18n.get("vector3.shape_manager.debug_bypass_only"), debugBypassOnly);
            IrisBypassTarget.debugShowOnly = debugBypassOnly.get();
            ImGui.separator();

            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##search", search);

            if (ImGui.beginChild("##shapeManagerTree", 0, 0, true)) {
                renderTree();
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private static void renderTree() {
        Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
        List<String> roots = new ArrayList<>();
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            ShapeState state = ShapeTrackRegistry.state(shapeId);
            String parentId = state != null ? state.parentShapeId() : null;if (parentId != null && !parentId.isEmpty() && ShapeTrackRegistry.shape(parentId) != null) {
                childrenByParent.computeIfAbsent(parentId, id -> new ArrayList<>()).add(shapeId);
            } else {
                roots.add(shapeId);
            }
        }
        String filter = search.get().toLowerCase();
        for (String shapeId : roots) renderNode(shapeId, childrenByParent, filter, new HashSet<>());
    }

    /** {@code visited} guards against a parent cycle slipping through for one render frame; real cycles
     *  are already rejected by ShapeTrackRegistry.applyParent() before a state is ever applied. */
    private static void renderNode(String shapeId, Map<String, List<String>> childrenByParent,
            String filter, Set<String> visited) {
        if (!visited.add(shapeId)) return;
        List<String> children = childrenByParent.getOrDefault(shapeId, List.of());
        if (!filter.isBlank() && !subtreeMatches(shapeId, childrenByParent, filter, new HashSet<>())) return;

        String label = treeLabel(shapeId, ShapeTrackRegistry.state(shapeId));
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.OpenOnDoubleClick
                | ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.DefaultOpen;
        if (children.isEmpty()) flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;

        ImGui.pushID(shapeId);
        boolean open = ImGui.treeNodeEx("##node", flags, label);
        if (ImGui.isItemClicked(0) && !ImGui.isItemToggledOpen()) ShapeTimelineSelection.request(shapeId);
        if (ImGui.isItemClicked(1)) ImGui.setClipboardText(shapeId);
        if (ImGui.isItemHovered()) ImGui.setTooltip(shapeId + "\n" + I18n.get("vector3.shape_manager.copy_hint"));
        if (!children.isEmpty() && open) {
            for (String child : children) renderNode(child, childrenByParent, filter, visited);
            ImGui.treePop();
        }
        ImGui.popID();
    }

    private static boolean subtreeMatches(String shapeId, Map<String, List<String>> childrenByParent,
            String filter, Set<String> visited) {
        if (!visited.add(shapeId)) return false;
        if (treeLabel(shapeId, ShapeTrackRegistry.state(shapeId)).toLowerCase().contains(filter)) return true;
        for (String child : childrenByParent.getOrDefault(shapeId, List.of())) {
            if (subtreeMatches(child, childrenByParent, filter, visited)) return true;
        }
        return false;
    }

    private static String treeLabel(String shapeId, ShapeState state) {
        String type = state != null ? state.shapeType() : ShapeTrackRegistry.typeOf(shapeId);
        ShapeTrackRegistry.Definition definition = type != null ? ShapeTrackRegistry.definition(type) : null;
        String typeLabel = definition != null ? I18n.get(definition.name()) : type;
        String name = state != null ? state.name() : null;
        return VectorIcons.withShapeIcon(type, name != null && !name.isBlank()
                ? name + "  (" + typeLabel + ")"
                : ShapeTrackRegistry.displayName(shapeId));
    }
}
