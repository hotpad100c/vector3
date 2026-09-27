package ml.mypals.vectorthree.shape.blast;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.vectorthree.flashback.curve.SpeedCurve;
import ml.mypals.vectorthree.flashback.curve.SpeedCurveEditor;
import ml.mypals.vectorthree.shape.area.BlastShape;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BlastEditor {
    private BlastEditor() {}

    /** Edits {@code settings[0]} and the path points ({@code points} from index 2) in place; true when anything changed. */
    public static boolean edit(BlastSettings[] settings, List<ShapePoint> points, boolean multi, boolean truncated) {
        BlastSettings.Mutable m = settings[0].mutable();
        boolean changed = false;
        if (truncated) ImGui.textColored(1f, 0.6f, 0.3f, 1f, I18n.get("vector3.blast.truncated"));

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.drive") + "##blast_drive", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radio("vector3.blast.mode.", m.mode, BlastSettings.Mode.values(), value -> m.mode = value);
            changed |= radio("vector3.blast.driver.", m.driver, BlastSettings.Driver.values(), value -> m.driver = value);
            if (m.driver == BlastSettings.Driver.AUTO) {
                int[] start = {m.startTick}, duration = {m.duration};
                if (ImGui.dragInt(I18n.get("vector3.blast.start_tick"), start, 1, 0, Integer.MAX_VALUE)) {
                    m.startTick = start[0];
                    changed = true;
                }
                ImGui.sameLine();
                if (ImGui.smallButton(I18n.get("vector3.particle.at_playhead") + "##blast_start")) {
                    m.startTick = TimelineWindow.getCursorTick();
                    changed = true;
                }
                if (ImGui.dragInt(I18n.get("vector3.blast.duration"), duration, 1, 1, 100000)) {
                    m.duration = duration[0];
                    changed = true;
                }
            } else {
                float[] progress = {m.progress};
                if (ImGui.sliderFloat(I18n.get("vector3.blast.progress"), progress, 0, 1)) {
                    m.progress = progress[0];
                    changed = true;
                }
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.trajectory") + "##blast_trajectory", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= combo("vector3.blast.trajectory.", m.trajectory, BlastSettings.Trajectory.values(), value -> m.trajectory = value);
            if (m.trajectory != BlastSettings.Trajectory.LINE && m.trajectory != BlastSettings.Trajectory.PATH) {
                float[] height = {m.arcHeight};
                if (ImGui.dragFloat(I18n.get("vector3.blast.arc_height"), height, 0.05f, -100, 100)) {
                    m.arcHeight = height[0];
                    changed = true;
                }
            }
            if (m.trajectory == BlastSettings.Trajectory.EXPLODE) {
                float[] burst = {m.burst};
                if (ImGui.dragFloat(I18n.get("vector3.blast.burst"), burst, 0.05f, 0, 100)) {
                    m.burst = burst[0];
                    changed = true;
                }
            }
            if (m.trajectory == BlastSettings.Trajectory.PATH && !multi) {
                ImGui.textDisabled(I18n.get("vector3.blast.path_hint"));
                changed |= localPoint(I18n.get("vector3.blast.path_start"), points, BlastShape.PATH_START);
                for (int i = BlastShape.FIRST_PATH_POINT; i < points.size(); i++) {
                    ShapePoint point = points.get(i);
                    float[] value = {(float) point.x(), (float) point.y(), (float) point.z()};
                    if (ImGui.dragFloat3(I18n.get("vector3.blast.path_point", i - BlastShape.FIRST_PATH_POINT + 1) + "##blast_path" + i, value, 0.05f)) {
                        points.set(i, new ShapePoint(value[0], value[1], value[2]));
                        changed = true;
                    }
                    ImGui.sameLine();
                    if (ImGui.smallButton("x##blast_remove" + i)) {
                        points.remove(i--);
                        changed = true;
                    }
                }
                if (ImGui.button(I18n.get("vector3.blast.add_path_point"))) {
                    ShapePoint last = points.size() > BlastShape.FIRST_PATH_POINT ? points.getLast() : null;
                    points.add(last != null ? new ShapePoint(last.x(), last.y() + 2, last.z())
                            : new ShapePoint(points.get(BlastShape.SCATTER).x() / 2, points.get(BlastShape.SCATTER).y() / 2 + 4,
                                    points.get(BlastShape.SCATTER).z() / 2));
                    changed = true;
                }
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.scatter") + "##blast_scatter")) {
            float[] radius = {m.scatterRadius}, flatten = {m.flatten};
            int[] seed = {m.seed};
            if (!multi) {
                changed |= localPoint(I18n.get("vector3.blast.scatter_center"), points, BlastShape.SCATTER);
                if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blast.scatter_center.tooltip"));
            }
            if (ImGui.dragFloat(I18n.get("vector3.blast.scatter_radius"), radius, 0.1f, 0, 1000)) {
                m.scatterRadius = radius[0];
                changed = true;
            }
            if (ImGui.sliderFloat(I18n.get("vector3.blast.flatten"), flatten, 0, 1)) {
                m.flatten = flatten[0];
                changed = true;
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blast.flatten.tooltip"));
            if (ImGui.dragInt(I18n.get("vector3.blast.seed"), seed)) {
                m.seed = seed[0];
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.smallButton(I18n.get("vector3.blast.reseed"))) {
                m.seed = ThreadLocalRandom.current().nextInt(100000);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.timing") + "##blast_timing")) {
            float[] stagger = {m.stagger};
            if (ImGui.sliderFloat(I18n.get("vector3.blast.stagger"), stagger, 0, 0.95f)) {
                m.stagger = stagger[0];
                changed = true;
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blast.stagger.tooltip"));
            changed |= orderMode(m);
            if (m.order == BlastSettings.Order.SWEEP) {
                ImGui.textDisabled(I18n.get("vector3.blast.sweep_hint"));
                changed |= radio("vector3.blast.sweep_shape.", m.sweepShape, BlastSettings.SweepShape.values(), value -> m.sweepShape = value);
                float[] radius = {m.sweepRadius};
                if (ImGui.dragFloat(I18n.get("vector3.blast.sweep_radius"), radius, 0.05f, 0, 1000)) {
                    m.sweepRadius = radius[0];
                    changed = true;
                }
                if (!multi) {
                    changed |= localPoint(I18n.get("vector3.blast.sweep_start"), points, BlastShape.ORIGIN);
                    changed |= localPoint(I18n.get("vector3.blast.sweep_end"), points, BlastShape.SWEEP_END);
                }
            } else if (!multi && (m.order == BlastSettings.Order.FROM_ORIGIN || m.trajectory == BlastSettings.Trajectory.EXPLODE)) {
                if (m.order == BlastSettings.Order.FROM_ORIGIN) ImGui.textDisabled(I18n.get("vector3.blast.from_origin_hint"));
                changed |= localPoint(I18n.get("vector3.blast.origin"), points, BlastShape.ORIGIN);
                if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blast.origin.tooltip"));
                if (m.order == BlastSettings.Order.FROM_ORIGIN) {
                    changed |= localPoint(I18n.get("vector3.blast.scatter_center"), points, BlastShape.SCATTER);
                }
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.over_progress") + "##blast_over")) {
            float[] spin = {m.spin}, clump = {m.clumpStart, m.clumpEnd}, alpha = {m.alphaStart, m.alphaEnd},
                    scale = {m.scaleStart, m.scaleEnd};
            if (ImGui.dragFloat(I18n.get("vector3.blast.spin"), spin, 1, -3600, 3600)) {
                m.spin = spin[0];
                changed = true;
            }
            if (ImGui.dragFloat2(I18n.get("vector3.blast.clump"), clump, 0.05f, 1, 16)) {
                m.clumpStart = clump[0];
                m.clumpEnd = clump[1];
                changed = true;
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blast.clump.tooltip"));
            if (ImGui.dragFloat2(I18n.get("vector3.blast.alpha"), alpha, 0.01f, 0, 1)) {
                m.alphaStart = alpha[0];
                m.alphaEnd = alpha[1];
                changed = true;
            }
            if (ImGui.dragFloat2(I18n.get("vector3.blast.scale"), scale, 0.01f, 0, 10)) {
                m.scaleStart = scale[0];
                m.scaleEnd = scale[1];
                changed = true;
            }
            ImGui.textDisabled(I18n.get("vector3.blast.start_end_hint"));
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.blast.curves") + "##blast_curves")) {
            changed |= curves(m);
        }

        if (changed) settings[0] = m.build();
        return changed;
    }

    private static boolean localPoint(String label, List<ShapePoint> points, int index) {
        ShapePoint point = points.get(index);
        float[] value = {(float) point.x(), (float) point.y(), (float) point.z()};
        if (!ImGui.dragFloat3(label + "##blast_point" + index, value, 0.05f)) return false;
        points.set(index, new ShapePoint(value[0], value[1], value[2]));
        return true;
    }

    private static BlastSettings.Curve editing = BlastSettings.Curve.MOTION;

    // One curve on screen at a time: SpeedCurveEditor keeps a single drag state.
    private static boolean curves(BlastSettings.Mutable m) {
        boolean changed = false;
        BlastSettings.Curve[] all = BlastSettings.Curve.values();
        for (int i = 0; i < all.length; i++) {
            if (i > 0) ImGui.sameLine();
            if (ImGui.radioButton(I18n.get("vector3.blast.curve." + all[i].name().toLowerCase()), editing == all[i])) editing = all[i];
        }
        BlastSettings current = m.build();
        SpeedCurve own = switch (editing) {
            case MOTION -> m.motionCurve;
            case SPIN -> m.spinCurve;
            case ALPHA -> m.alphaCurve;
            case SCALE -> m.scaleCurve;
            case CLUMP -> m.clumpCurve;
        };
        if (editing != BlastSettings.Curve.MOTION) {
            ImBoolean separate = new ImBoolean(own != null);
            if (ImGui.checkbox(I18n.get("vector3.blast.curve.separate"), separate)) {
                own = separate.get() ? current.motion() : null;
                setCurve(m, own);
                changed = true;
            }
            if (own == null) {
                ImGui.textDisabled(I18n.get("vector3.blast.curve.follows_motion"));
                return changed;
            }
        }
        SpeedCurve shown = editing == BlastSettings.Curve.MOTION ? current.motion() : own;
        ImGui.pushID("blast_curve_" + editing.name());
        SpeedCurveEditor.Result result = SpeedCurveEditor.render(shown, -1);
        ImGui.popID();
        if (result != null) {
            setCurve(m, result.curve());
            changed = true;
        }
        return changed;
    }

    private static void setCurve(BlastSettings.Mutable m, SpeedCurve curve) {
        switch (editing) {
            case MOTION -> m.motionCurve = curve;
            case SPIN -> m.spinCurve = curve;
            case ALPHA -> m.alphaCurve = curve;
            case SCALE -> m.scaleCurve = curve;
            case CLUMP -> m.clumpCurve = curve;
        }
    }

    private static final BlastSettings.Order[] OTHER_ORDERS = {BlastSettings.Order.RANDOM, BlastSettings.Order.BOTTOM_UP,
            BlastSettings.Order.TOP_DOWN};

    // The two ordering modes up front; the plain orders under "other".
    private static boolean orderMode(BlastSettings.Mutable m) {
        boolean changed = false;
        boolean other = m.order != BlastSettings.Order.SWEEP && m.order != BlastSettings.Order.FROM_ORIGIN;
        ImGui.text(I18n.get("vector3.blast.order.label"));
        if (ImGui.radioButton(I18n.get("vector3.blast.order.sweep"), m.order == BlastSettings.Order.SWEEP)
                && m.order != BlastSettings.Order.SWEEP) {
            m.order = BlastSettings.Order.SWEEP;
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.radioButton(I18n.get("vector3.blast.order.from_origin"), m.order == BlastSettings.Order.FROM_ORIGIN)
                && m.order != BlastSettings.Order.FROM_ORIGIN) {
            m.order = BlastSettings.Order.FROM_ORIGIN;
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.radioButton(I18n.get("vector3.blast.order.other"), other) && !other) {
            m.order = BlastSettings.Order.RANDOM;
            changed = true;
        }
        if (m.order != BlastSettings.Order.SWEEP && m.order != BlastSettings.Order.FROM_ORIGIN) {
            changed |= combo("vector3.blast.order.", m.order, OTHER_ORDERS, value -> m.order = value);
        }
        return changed;
    }

    private interface Setter<E> { void set(E value); }

    private static <E extends Enum<E>> boolean radio(String prefix, E current, E[] values, Setter<E> setter) {
        boolean changed = false;
        for (int i = 0; i < values.length; i++) {
            if (i > 0) ImGui.sameLine();
            if (ImGui.radioButton(I18n.get(prefix + values[i].name().toLowerCase()), current == values[i]) && current != values[i]) {
                setter.set(values[i]);
                changed = true;
            }
        }
        return changed;
    }

    private static <E extends Enum<E>> boolean combo(String prefix, E current, E[] values, Setter<E> setter) {
        boolean changed = false;
        ImGui.setNextItemWidth(200);
        if (ImGui.beginCombo(I18n.get(prefix + "label"), I18n.get(prefix + current.name().toLowerCase()))) {
            for (E value : values) {
                if (ImGui.selectable(I18n.get(prefix + value.name().toLowerCase()), value == current) && value != current) {
                    setter.set(value);
                    changed = true;
                }
            }
            ImGui.endCombo();
        }
        return changed;
    }
}
