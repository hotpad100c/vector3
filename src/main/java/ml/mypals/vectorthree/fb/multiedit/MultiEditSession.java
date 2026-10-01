package ml.mypals.vectorthree.fb.multiedit;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.fb.expression.ExpressionUi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Drives ImGuiMultiEditMixin. A keyframe's own editor UI is run once per target without drawing (CAPTURE) to learn
 * every widget's value, then drawn for the primary target (DISPLAY) with mixed widgets locked behind Ctrl, and the
 * edit the user made is replayed into every other target's editor (REPLAY) so each target applies it through its
 * own UI code. Widgets are keyed by label plus how many times that label came before. DISPLAY also reports every
 * widget to PropertySelection, which is how rows get selected, copied and pasted.
 */
public final class MultiEditSession {
    private enum Mode { OFF, CAPTURE, DISPLAY, REPLAY }

    public enum Kind { VALUE, CHECK, RADIO, BUTTON, COMBO }

    public record Change(String key, @Nullable Object value, boolean @Nullable [] components, @Nullable String selection) {}

    private record Pending(String key, Object before, boolean locked, boolean[] mixed, Kind kind, String label, boolean color) {}

    private static final Pending PASS = new Pending("", "", false, new boolean[0], Kind.BUTTON, "", false);
    private static final Pending COMBO_ITEM = new Pending("", "", false, new boolean[0], Kind.BUTTON, "", false);

    // One frame per running editor pass: evaluating expressions can run a pass inside another one.
    private static final class State {
        Mode mode = Mode.OFF;
        boolean shared;
        String scope = "";
        final Map<String, Integer> counts = new HashMap<>();
        Map<String, Object> capture;
        List<Map<String, Object>> captures;
        Predicate<String> visible = key -> true;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        final ArrayDeque<String> openCombos = new ArrayDeque<>();
        int nesting;
        @Nullable Change change;
        Map<String, Change> injects = Map.of();
        @Nullable Map<String, UnaryOperator<Object>> computed;
        @Nullable Set<String> hits;
        @Nullable Change replayCombo;
        String lastSelectable;
        boolean color;
    }

    private static final Set<String> HELD = new java.util.HashSet<>();
    private static State s = new State();
    private static final ArrayDeque<State> OUTER = new ArrayDeque<>();

    private MultiEditSession() {}

    /** True while one editor stands for several keyframes (or runs silently for one of them). */
    public static boolean active() {
        return s.shared;
    }

    /** True while an editor runs without being drawn, so it must not touch anything outside its keyframe. */
    public static boolean silent() {
        return s.mode == Mode.CAPTURE || s.mode == Mode.REPLAY;
    }

    public static Map<String, Object> capture(Runnable render) {
        return capture(render, true);
    }

    /** {@code severalTargets} false: the editor reads one keyframe on its own (see {@link #active}). */
    public static Map<String, Object> capture(Runnable render, boolean severalTargets) {
        begin(Mode.CAPTURE, severalTargets);
        s.capture = new LinkedHashMap<>();
        try {
            render.run();
            return s.capture;
        } finally {
            end();
        }
    }

    /** Draws the primary's editor; {@code targets} are the other targets' captures, empty when it edits one keyframe. */
    public static @Nullable Change display(String rowScope, List<Map<String, Object>> targets, Predicate<String> shown,
            boolean severalTargets, Runnable render) {
        begin(Mode.DISPLAY, severalTargets);
        s.scope = rowScope;
        s.captures = targets;
        s.visible = shown;
        s.change = null;
        try {
            render.run();
            return s.change;
        } finally {
            while (!s.pending.isEmpty()) {
                if (s.pending.pop().locked()) ImGui.endDisabled();
            }
            end();
        }
    }

    /** Feeds these edits into one target's editor; the keys it actually had are added to {@code applied}. */
    public static void replay(List<Change> edits, @Nullable Set<String> applied, Runnable render) {
        begin(Mode.REPLAY, true);
        Map<String, Change> byKey = new HashMap<>();
        for (Change edit : edits) byKey.put(edit.key(), edit);
        s.injects = byKey;
        s.hits = applied;
        try {
            render.run();
        } finally {
            end();
        }
    }

    /**
     * Like {@link #replay}, but each widget's new value is computed from its current one (the snapshot, e.g. a
     * float[] or a String; a combo gets its preview and answers the item to pick). A null answer leaves it alone.
     * It edits one keyframe, so the editor shows everything it would for that keyframe alone.
     */
    public static void replayComputed(Map<String, UnaryOperator<Object>> edits, @Nullable Set<String> applied, Runnable render) {
        begin(Mode.REPLAY, false);
        s.computed = edits;
        s.hits = applied;
        try {
            render.run();
        } finally {
            end();
        }
    }

    /** Runs {@code body} with no editor pass active, for widgets drawn around an editor that aren't part of it. */
    public static void suspend(Runnable body) {
        begin(Mode.OFF, false);
        try {
            body.run();
        } finally {
            end();
        }
    }

    /** The next widget is a colour editor. */
    public static void markColor() {
        s.color = true;
    }

    private static void begin(Mode next, boolean severalTargets) {
        OUTER.push(s);
        s = new State();
        s.mode = next;
        s.shared = severalTargets;
    }

    private static void end() {
        s = OUTER.isEmpty() ? new State() : OUTER.pop();
    }

    private static String key(String label) {
        int index = s.counts.merge(label, 1, Integer::sum) - 1;
        return label + "#" + index;
    }

    /** The widget's result when it should not run for real, or null to let it run. */
    public static @Nullable Boolean head(String label, @Nullable Object container, Kind kind) {
        boolean color = s.color;
        s.color = false;
        if (s.mode == Mode.OFF) return null;
        if (s.mode == Mode.DISPLAY) {
            if (s.nesting > 0) {
                s.nesting++;
                return null;
            }
            if (!s.openCombos.isEmpty()) {
                s.pending.push(PASS);
                s.nesting = 1;
                return null;
            }
            String key = key(label);
            if (!s.visible.test(key)) return false;
            Object before = WidgetValues.snapshot(container);
            boolean[] mixed = WidgetValues.mixed(key, before, s.captures);
            // Ctrl unlocks a mixed widget, and it stays unlocked while it is being edited, so Ctrl can be let go to type.
            boolean locked = WidgetValues.any(mixed) && !EditorInput.isCtrlDown() && !HELD.contains(s.scope + "|" + key)
                    || !s.shared && ExpressionUi.locks(key, before);
            if (locked) ImGui.beginDisabled();
            s.pending.push(new Pending(key, before, locked, mixed, kind, label, color));
            s.nesting = 1;
            return null;
        }
        String key = key(label);
        if (s.mode == Mode.CAPTURE) {
            s.capture.put(key, WidgetValues.snapshot(container));
            return false;
        }
        if (s.computed != null) return computed(s.computed, key, container, kind);
        Change edit = s.injects.get(key);
        if (edit == null) return false;
        if (s.hits != null) s.hits.add(key);
        if (kind == Kind.COMBO) s.replayCombo = edit;
        return WidgetValues.inject(edit, container, kind);
    }

    private static Boolean computed(Map<String, UnaryOperator<Object>> operations, String key, @Nullable Object container, Kind kind) {
        UnaryOperator<Object> op = kind == Kind.BUTTON ? null : operations.get(key);
        Object after = op == null ? null : op.apply(WidgetValues.snapshot(container));
        if (op != null && s.hits != null) s.hits.add(key);
        if (after == null) return false;
        Change edit = kind == Kind.COMBO ? new Change(key, null, null, after.toString()) : new Change(key, after, null, null);
        if (kind == Kind.COMBO) s.replayCombo = edit;
        return WidgetValues.inject(edit, container, kind);
    }

    public static void tail(@Nullable Object container, boolean result) {
        if (s.mode != Mode.DISPLAY || s.nesting == 0) return;
        if (--s.nesting > 0) return;
        Pending pending = s.pending.pop();
        if (pending == PASS) return;
        if (pending == COMBO_ITEM) {
            if (result && s.change == null && !s.openCombos.isEmpty()) {
                s.change = new Change(s.openCombos.peek(), null, null, s.lastSelectable);
            }
            return;
        }
        if (pending.locked()) ImGui.endDisabled();
        else if (WidgetValues.any(pending.mixed())) {
            String held = s.scope + "|" + pending.key();
            if (pending.kind() == Kind.COMBO ? result : ImGui.isItemActive()) HELD.add(held);
            else HELD.remove(held);
        }
        if (WidgetValues.any(pending.mixed())) MixedOverlay.draw(pending.label(), pending.mixed(), pending.kind(), pending.locked());
        Object value = pending.kind() == Kind.VALUE ? WidgetValues.snapshot(container) : pending.before();
        if (pending.kind() != Kind.BUTTON) PropertySelection.record(s.scope, pending.key(), pending.kind(), value);
        if (!s.shared) ExpressionUi.row(pending.key(), pending.kind(), value, pending.color(), pending.kind() == Kind.COMBO && result);
        if (pending.kind() == Kind.COMBO) {
            if (result) s.openCombos.push(pending.key());
            return;
        }
        if (!result || s.change != null) return;
        s.change = switch (pending.kind()) {
            case CHECK -> new Change(pending.key(), !(Boolean) pending.before(), null, null);
            case RADIO -> new Change(pending.key(), true, null, null);
            case BUTTON -> new Change(pending.key(), null, null, null);
            default -> new Change(pending.key(), value, WidgetValues.changed(pending.before(), value), null);
        };
    }

    public static @Nullable Boolean selectableHead(String label) {
        if (s.mode == Mode.OFF) return null;
        if (s.mode == Mode.DISPLAY && s.nesting == 0 && !s.openCombos.isEmpty()) {
            s.lastSelectable = label;
            s.pending.push(COMBO_ITEM);
            s.nesting = 1;
            return null;
        }
        if (s.mode == Mode.REPLAY && s.replayCombo != null) return label.equals(s.replayCombo.selection());
        return head(label, null, Kind.BUTTON);
    }

    /** True when the end-combo call should be skipped. */
    public static boolean endComboHead() {
        if (s.mode == Mode.DISPLAY && s.nesting == 0 && !s.openCombos.isEmpty()) s.openCombos.pop();
        if (s.mode == Mode.REPLAY && s.replayCombo != null) {
            s.replayCombo = null;
            return true;
        }
        return false;
    }
}
