package ml.mypals.vectorthree.multiedit;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The Properties page for several selected keyframes: one section per kind, edits shared across each section. */
public final class MultiPropertiesPage {
    public record Host(Supplier<EditorScene> scene, Runnable upgradeToWrite, Runnable keyframesChanged,
            Runnable useCustomCurve, Runnable gizmoControls) {}

    private static final int SILENT_FLAGS = ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoNav;

    private MultiPropertiesPage() {}

    public static void render(List<SelectedKeyframes> selection, int editingTrack, int editingTick, Host host) {
        List<MultiSelection.Entry> entries = MultiSelection.gather(host.scene().get(), selection, editingTrack, editingTick);
        ImGui.text(I18n.get("vector3.multi.selected", entries.size()));
        interpolation(entries, host);

        List<MultiSelection.Entry> shapes = new ArrayList<>();
        Map<KeyframeType<?>, List<MultiSelection.Entry>> others = new LinkedHashMap<>();
        for (MultiSelection.Entry entry : entries) {
            if (entry.working instanceof ShapeKeyframe) shapes.add(entry);
            else others.computeIfAbsent(entry.type, type -> new ArrayList<>()).add(entry);
        }
        int section = 0;
        if (!shapes.isEmpty()) {
            host.gizmoControls().run();
            section = shapeSections(shapes, section);
        }
        for (Map.Entry<KeyframeType<?>, List<MultiSelection.Entry>> group : others.entrySet()) {
            section(title(group.getKey().name(), group.getValue().size()), group.getValue(), key -> true, section++);
        }
        if (MultiSelection.commit(entries, host.upgradeToWrite(), host.scene())) host.keyframesChanged().run();
    }

    // Shapes of several types share one section for the widgets every type has, then one per type for the rest.
    private static int shapeSections(List<MultiSelection.Entry> shapes, int section) {
        Map<String, List<MultiSelection.Entry>> byType = new LinkedHashMap<>();
        for (MultiSelection.Entry entry : shapes) {
            byType.computeIfAbsent(((ShapeKeyframe) entry.working).value.shapeType(), type -> new ArrayList<>()).add(entry);
        }
        if (byType.size() == 1) {
            Map.Entry<String, List<MultiSelection.Entry>> only = byType.entrySet().iterator().next();
            section(title(shapeName(only.getKey()), only.getValue().size()), only.getValue(), key -> true, section);
            return section + 1;
        }
        Set<String> common = null;
        for (Map<String, Object> capture : captureAll(shapes, section)) {
            if (common == null) common = new HashSet<>(capture.keySet());
            else common.retainAll(capture.keySet());
        }
        Set<String> shared = common == null ? Set.of() : common;
        section(title(I18n.get("vector3.multi.shapes"), shapes.size()), shapes, shared::contains, section++);
        for (Map.Entry<String, List<MultiSelection.Entry>> type : byType.entrySet()) {
            section(title(shapeName(type.getKey()), type.getValue().size()), type.getValue(),
                    key -> !shared.contains(key), section++);
        }
        return section;
    }

    private static void section(String title, List<MultiSelection.Entry> targets, Predicate<String> shown, int section) {
        ImGui.separator();
        ImGui.text(title);
        List<Map<String, Object>> captures = captureAll(targets, section);
        MultiSelection.Entry primary = targets.getFirst();
        ImGui.pushID("vector3_multi_section_" + section);
        MultiEditSession.Change change;
        try {
            change = MultiEditSession.display("section" + section, captures, shown, true,
                    () -> primary.working.renderEditKeyframe(primary.update()));
        } finally {
            ImGui.popID();
        }
        if (change == null) return;
        silently(section, () -> {
            for (int i = 1; i < targets.size(); i++) {
                MultiSelection.Entry target = targets.get(i);
                ImGui.pushID(i);
                MultiEditSession.replay(List.of(change), null, () -> target.working.renderEditKeyframe(target.update()));
                ImGui.popID();
            }
        });
    }

    /** Pastes copied rows onto every selected keyframe that has them; returns how many of the rows found a home. */
    public static int paste(List<PropertyClipboard.Clip> clips, List<SelectedKeyframes> selection, int editingTrack,
            int editingTick, Host host) {
        List<MultiSelection.Entry> entries = MultiSelection.gather(host.scene().get(), selection, editingTrack, editingTick);
        List<MultiEditSession.Change> changes = clips.stream().map(PropertyClipboard.Clip::change).toList();
        Set<String> applied = new HashSet<>();
        silently(-1, () -> {
            for (int i = 0; i < entries.size(); i++) {
                MultiSelection.Entry target = entries.get(i);
                ImGui.pushID(i);
                MultiEditSession.replay(changes, applied, () -> target.working.renderEditKeyframe(target.update()));
                ImGui.popID();
            }
        });
        if (MultiSelection.commit(entries, host.upgradeToWrite(), host.scene())) host.keyframesChanged().run();
        return applied.size();
    }

    private static List<Map<String, Object>> captureAll(List<MultiSelection.Entry> targets, int section) {
        List<Map<String, Object>> captures = new ArrayList<>(targets.size());
        silently(section, () -> {
            for (int i = 0; i < targets.size(); i++) {
                MultiSelection.Entry target = targets.get(i);
                ImGui.pushID(i);
                captures.add(MultiEditSession.capture(() -> target.working.renderEditKeyframe(edit -> {})));
                ImGui.popID();
            }
        });
        return captures;
    }

    // Silent passes still submit text and layout, so they run in a 1px child the cursor then steps back over.
    private static void silently(int section, Runnable passes) {
        float x = ImGui.getCursorPosX(), y = ImGui.getCursorPosY();
        ImGui.beginChild("##vector3_multi_silent_" + section, 1, 1, false, SILENT_FLAGS);
        try {
            passes.run();
        } finally {
            // Cancelled widgets never consume SetNextItemWidth and the like; an empty item does.
            ImGui.dummy(0, 0);
            ImGui.endChild();
            ImGui.setCursorPos(x, y);
        }
    }

    private static void interpolation(List<MultiSelection.Entry> entries, Host host) {
        List<MultiSelection.Entry> targets = entries.stream().filter(entry -> entry.type.allowChangingInterpolationType()).toList();
        if (targets.isEmpty()) return;
        String[] names = InterpolationType.getNames();
        String custom = I18n.get("vector3.curve.custom");
        int first = choice(targets.getFirst().working, names.length);
        boolean mixed = targets.stream().anyMatch(entry -> choice(entry.working, names.length) != first);
        boolean locked = mixed && !InputHelper.isCtrlDownRaw();
        String preview = mixed ? "-" : first == names.length ? custom : names[first];
        if (locked) ImGui.beginDisabled();
        ImGui.setNextItemWidth(160);
        if (ImGui.beginCombo(I18n.get("flashback.type"), preview)) {
            for (int i = 0; i < names.length; i++) {
                if (ImGui.selectable(names[i], !mixed && first == i)) {
                    InterpolationType type = InterpolationType.INTERPOLATION_TYPES[i];
                    for (MultiSelection.Entry entry : targets) {
                        entry.working.interpolationType(type);
                        SpeedCurves.set(entry.working, null);
                        entry.touch();
                    }
                }
            }
            if (ImGui.selectable(custom, !mixed && first == names.length)) host.useCustomCurve().run();
            ImGui.endCombo();
        }
        if (locked) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip(I18n.get("vector3.multi.locked"));
        }
    }

    private static int choice(Keyframe keyframe, int customIndex) {
        return SpeedCurves.of(keyframe) != null ? customIndex : keyframe.interpolationType().ordinal();
    }

    private static String shapeName(String type) {
        ShapeTrackRegistry.Definition definition = ShapeTrackRegistry.definition(type);
        return definition == null ? type : I18n.get(definition.name());
    }

    private static String title(String name, int count) {
        return name + " ×" + count;
    }
}
