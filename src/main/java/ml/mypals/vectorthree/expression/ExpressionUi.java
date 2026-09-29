package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.multiedit.MultiEditSession;
import ml.mypals.vectorthree.multiedit.MultiEditSession.Kind;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The expression controls of the properties panel: a right-click menu on any row, and under a driven row one line
 * per expression with its source, an on/off box, a remove button, and its result or error.
 */
public final class ExpressionUi {
    private record Row(Kind kind, Object value, boolean color) {}

    private static final Set<String> RESERVED = Set.of("pi", "e", "true", "false", "time", "tick", "value", "camera", "self");
    private static final String[] XYZW = {"x", "y", "z", "w"}, RGBA = {"r", "g", "b", "a"};

    private static @Nullable KeyframeTrack track;
    private static Runnable upgrade = () -> {}, changed = () -> {};
    private static boolean drawing;
    private static Map<String, List<ExpressionBinding>> byLabel = Map.of();
    private static final Map<String, Row> ROWS = new LinkedHashMap<>();
    private static final Map<String, ImString> BUFFERS = new HashMap<>();
    private static @Nullable KeyframeTrack buffersTrack;

    private ExpressionUi() {}

    /** Starts a single keyframe's panel; a null track (several keyframes, nothing selected) shows no controls. */
    public static void begin(@Nullable KeyframeTrack editing, Runnable upgradeToWrite, Runnable keyframesChanged) {
        track = editing;
        upgrade = upgradeToWrite;
        changed = keyframesChanged;
        drawing = editing != null;
        ROWS.clear();
        if (editing != buffersTrack) {
            BUFFERS.clear();
            buffersTrack = editing;
        }
        refresh();
    }

    /** Ends the editor; lists every expression of the track, including those whose widget isn't shown now. */
    public static void end() {
        drawing = false;
        if (track == null || !ExpressionBindings.any(track)) return;
        List<ExpressionBinding> all = ExpressionBindings.of(track);
        ImGui.separator();
        if (!ImGui.collapsingHeader(I18n.get("vector3.expression.list", all.size()) + "###vector3_expressions")) return;
        for (ExpressionBinding binding : all) {
            ImGui.pushID("vector3_expression_list_" + binding.widget() + "_" + binding.component());
            String label = shownLabel(WidgetKeys.label(binding.widget()));
            ImGui.text(label + component(binding.component(), false));
            ImGui.sameLine();
            ImGui.textDisabled(binding.source().replace('\n', ' '));
            ImGui.sameLine();
            if (ImGui.smallButton("x")) remove(binding);
            ImGui.popID();
        }
    }

    public static boolean offers(String key) {
        return track != null && ROWS.containsKey(key) && !ROWS.get(key).kind().equals(Kind.BUTTON);
    }

    public static boolean locks(String key, Object before) {
        List<ExpressionBinding> bound = drawing ? byLabel.get(key) : null;
        if (bound == null) return false;
        int size = before instanceof float[] floats ? floats.length : before instanceof int[] ints ? ints.length : 1;
        boolean[] driven = new boolean[size];
        for (ExpressionBinding binding : bound) {
            if (!binding.enabled()) continue;
            if (binding.component() < 0 || size == 1) return true;
            if (binding.component() < size) driven[binding.component()] = true;
        }
        for (boolean one : driven) if (!one) return false;
        return true;
    }

    public static void row(String key, Kind kind, Object value, boolean color, boolean openCombo) {
        if (!drawing || track == null || excluded(key)) return;
        ROWS.put(key, new Row(kind, value, color));
        List<ExpressionBinding> bound = byLabel.get(key);
        if (bound == null || openCombo) return;
        // These widgets are drawn inside the editor's pass but aren't part of the editor.
        MultiEditSession.suspend(() -> {
            for (ExpressionBinding binding : bound) line(key, binding, value, color);
        });
    }

    private static boolean excluded(String key) {
        String label = key.substring(0, key.lastIndexOf('#'));
        return label.equals(I18n.get("flashback.tick")) || label.equals(I18n.get("flashback.type"));
    }

    private static void line(String key, ExpressionBinding binding, Object value, boolean color) {
        KeyframeTrack current = track;
        boolean text = value instanceof String;
        ImGui.pushID("vector3_expression_" + key + "_" + binding.component());
        ImGui.indent();
        ImGui.textDisabled("fx" + component(binding.component(), color));
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get(text ? "vector3.expression.help_text" : "vector3.expression.help"));
        ImGui.sameLine();
        ImString buffer = BUFFERS.computeIfAbsent(binding.widget() + "|" + binding.component(), ignored -> {
            ImString created = new ImString(binding.source(), Math.max(256, binding.source().length() * 2));
            created.inputData.isResizable = true;
            return created;
        });
        float buttons = ImGui.getFrameHeight() * 2 + ImGui.getStyle().getItemSpacingX() * 2;
        boolean edited;
        if (text) {
            edited = ImGui.inputTextMultiline("##source", buffer, -buttons, ImGui.getTextLineHeight() * 3 + 8);
        } else {
            ImGui.setNextItemWidth(-buttons);
            edited = ImGui.inputText("##source", buffer);
        }
        if (edited) replace(binding, binding.withSource(buffer.get()));
        ImGui.sameLine();
        ImBoolean enabled = new ImBoolean(binding.enabled());
        if (ImGui.checkbox("##enabled", enabled)) replace(binding, binding.withEnabled(enabled.get()));
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.expression.enabled.tooltip"));
        ImGui.sameLine();
        if (ImGui.button("x", ImGui.getFrameHeight(), ImGui.getFrameHeight())) remove(binding);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.expression.remove"));
        status(current, ExpressionBindings.find(current, binding.widget(), binding.component()), text);
        ImGui.unindent();
        ImGui.popID();
    }

    private static void status(KeyframeTrack current, @Nullable ExpressionBinding binding, boolean text) {
        if (binding == null) return;
        if (!binding.enabled()) {
            ImGui.textDisabled(I18n.get("vector3.expression.disabled"));
            return;
        }
        ExprError compile = ExpressionRuntime.check(binding.source(), text);
        if (compile != null) {
            error(compile.getMessage(), compile.position());
            return;
        }
        ExpressionBindings.Status status = ExpressionBindings.status(current, binding);
        if (status == null) return;
        if (status.missing()) {
            ImGui.textDisabled(I18n.get("vector3.expression.missing"));
        } else if (status.error() != null) {
            error(status.error(), status.position());
        } else if (status.result() != null) {
            String result = status.result().replace('\n', ' ');
            ImGui.textDisabled("= " + (result.length() > 80 ? result.substring(0, 80) + "..." : result));
        }
    }

    private static void error(String message, int position) {
        String text = position >= 0 ? I18n.get("vector3.expression.error_at", position + 1, message)
                : I18n.get("vector3.expression.error", message);
        ImGui.pushTextWrapPos(0);
        ImGui.textColored(0.95f, 0.42f, 0.42f, 1f, text);
        ImGui.popTextWrapPos();
    }

    /** Called from the properties right-click menu; true if it added anything. */
    public static boolean menu(String key) {
        Row row = ROWS.get(key);
        if (track == null || row == null || row.kind() == Kind.BUTTON) return false;
        String widget = WidgetKeys.stable(key, References.namespace(track.keyframeType));
        int size = row.value() instanceof float[] floats ? floats.length : row.value() instanceof int[] ints ? ints.length : 1;
        String initial = row.value() instanceof String ? "{value}" : "value";
        if (size > 1) {
            if (ImGui.beginMenu(I18n.get("vector3.expression.add"))) {
                if (ImGui.menuItem(I18n.get("vector3.expression.add_all"), "", false,
                        ExpressionBindings.find(track, widget, ExpressionBinding.ALL) == null)) {
                    add(widget, ExpressionBinding.ALL, initial);
                }
                for (int i = 0; i < size; i++) {
                    if (ImGui.menuItem(component(i, row.color()).substring(1), "", false,
                            ExpressionBindings.find(track, widget, i) == null)) {
                        add(widget, i, initial);
                    }
                }
                ImGui.endMenu();
            }
        } else if (ImGui.menuItem(I18n.get("vector3.expression.add"), "", false,
                ExpressionBindings.find(track, widget, ExpressionBinding.ALL) == null)) {
            add(widget, ExpressionBinding.ALL, initial);
        }
        for (ExpressionBinding binding : ExpressionBindings.of(track)) {
            if (!binding.widget().equals(widget)) continue;
            if (ImGui.menuItem(I18n.get("vector3.expression.remove") + component(binding.component(), row.color()))) remove(binding);
        }
        if (size > 1 && ImGui.beginMenu(I18n.get("vector3.expression.copy_reference"))) {
            if (ImGui.menuItem(I18n.get("vector3.expression.add_all"))) copyReference(key, -1, row.color());
            for (int i = 0; i < size; i++) {
                if (ImGui.menuItem(component(i, row.color()).substring(1))) copyReference(key, i, row.color());
            }
            ImGui.endMenu();
        } else if (size == 1 && ImGui.menuItem(I18n.get("vector3.expression.copy_reference"))) {
            copyReference(key, -1, row.color());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.expression.copy_reference.tooltip"));
        return true;
    }

    private static void copyReference(String key, int component, boolean color) {
        KeyframeTrack current = track;
        if (current == null) return;
        List<String> keys = new ArrayList<>(ROWS.keySet());
        Map<String, String> aliases = WidgetKeys.aliases(keys, References.namespace(current.keyframeType));
        String alias = null;
        for (Map.Entry<String, String> entry : aliases.entrySet()) if (entry.getValue().equals(key)) alias = entry.getKey();
        if (alias == null) return;
        String name = trackName(current);
        String head = name.matches("[A-Za-z_][A-Za-z0-9_]*") && !RESERVED.contains(name)
                ? name : "track(\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\")";
        String reference = head + "." + alias + component(component, color);
        Minecraft.getInstance().keyboardHandler.setClipboard(reference);
    }

    static String trackName(KeyframeTrack track) {
        if (track.customName != null && !track.customName.isBlank()) return track.customName;
        Keyframe first = track.keyframesByTick.isEmpty() ? null : track.keyframesByTick.firstEntry().getValue();
        if (first instanceof ShapeKeyframe shape) {
            return shape.value.name() != null && !shape.value.name().isBlank()
                    ? shape.value.name() : ShapeTrackRegistry.displayName(shape.value.shapeId());
        }
        return track.keyframeType.name();
    }

    private static void add(String widget, int component, String source) {
        KeyframeTrack current = track;
        if (current == null) return;
        upgrade.run();
        ExpressionBindings.put(current, widget, component, new ExpressionBinding(widget, component, source, true));
        BUFFERS.remove(widget + "|" + component);
        refresh();
        changed.run();
    }

    private static void replace(ExpressionBinding before, ExpressionBinding after) {
        KeyframeTrack current = track;
        if (current == null) return;
        upgrade.run();
        ExpressionBindings.put(current, before.widget(), before.component(), after);
        refresh();
        changed.run();
    }

    private static void remove(ExpressionBinding binding) {
        KeyframeTrack current = track;
        if (current == null) return;
        upgrade.run();
        ExpressionBindings.put(current, binding.widget(), binding.component(), null);
        BUFFERS.remove(binding.widget() + "|" + binding.component());
        refresh();
        changed.run();
    }

    private static void refresh() {
        if (track == null) {
            byLabel = Map.of();
            return;
        }
        Map<String, List<ExpressionBinding>> next = new HashMap<>();
        for (ExpressionBinding binding : ExpressionBindings.of(track)) {
            for (String label : WidgetKeys.labels(binding.widget())) {
                next.computeIfAbsent(label, key -> new ArrayList<>()).add(binding);
            }
        }
        byLabel = next;
    }

    private static String component(int component, boolean color) {
        if (component < 0) return "";
        String[] names = color ? RGBA : XYZW;
        return "." + (component < names.length ? names[component] : Integer.toString(component));
    }

    private static String shownLabel(String key) {
        String label = key.substring(0, key.lastIndexOf('#'));
        int hidden = label.indexOf("##");
        return hidden >= 0 ? label.substring(0, hidden) : label;
    }
}
