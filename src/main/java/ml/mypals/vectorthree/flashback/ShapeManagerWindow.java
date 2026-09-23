package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiTableColumnFlags;
import imgui.moulberry90.flag.ImGuiTableFlags;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;

public final class ShapeManagerWindow {
    private static final ImString search = new ImString("", 128);

    private ShapeManagerWindow() {}

    public static void render() {
        ImGui.begin("Shape Manager");
        ImGui.setNextItemWidth(-1);
        ImGui.inputText("##search", search);
        if (ImGui.beginChild("##shapeManagerList", 0, 0, true)) {
            if (ImGui.beginTable("##shapeManagerTable", 4,
                    ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable)) {
                ImGui.tableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("Type", ImGuiTableColumnFlags.WidthFixed, 90);
                ImGui.tableSetupColumn("Parent", ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 60);
                ImGui.tableHeadersRow();
                String filter = search.get().toLowerCase();
                for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                    ShapeState state = ShapeTrackRegistry.state(shapeId);
                    String name = ShapeTrackRegistry.displayName(shapeId);
                    if (!filter.isBlank() && !name.toLowerCase().contains(filter)) continue;
                    ImGui.pushID(shapeId);
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.textUnformatted(name);
                    ImGui.tableNextColumn();
                    ImGui.textUnformatted(state != null ? state.shapeType() : ShapeTrackRegistry.typeOf(shapeId));
                    ImGui.tableNextColumn();
                    String parentId = state != null ? state.parentShapeId() : null;
                    ImGui.textUnformatted(parentId == null || parentId.isEmpty()
                            ? "-" : ShapeTrackRegistry.displayName(parentId));
                    ImGui.tableNextColumn();
                    if (ImGui.smallButton("Select")) ShapeTimelineSelection.request(shapeId);
                    ImGui.popID();
                }
                ImGui.endTable();
            }
            ImGui.endChild();
        }
        ImGui.end();
    }
}
