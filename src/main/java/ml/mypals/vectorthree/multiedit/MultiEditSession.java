package ml.mypals.vectorthree.multiedit;

import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    private record Pending(String key, Object before, boolean locked, boolean[] mixed, Kind kind, String label) {}

    private static final Pending PASS = new Pending("", "", false, new boolean[0], Kind.BUTTON, "");
    private static final Pending COMBO_ITEM = new Pending("", "", false, new boolean[0], Kind.BUTTON, "");

    private static Mode mode = Mode.OFF;
    private static boolean shared;
    private static String scope = "";
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static Map<String, Object> capture;
    private static List<Map<String, Object>> captures;
    private static Predicate<String> visible = key -> true;
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<String> OPEN_COMBOS = new ArrayDeque<>();
    private static int nesting;
    private static @Nullable Change change;
    private static Map<String, Change> injects = Map.of();
    private static @Nullable Set<String> hits;
    private static @Nullable Change replayCombo;
    private static String lastSelectable;

    private MultiEditSession() {}

    /** True while one editor stands for several keyframes (or runs silently for one of them). */
    public static boolean active() {
        return shared;
    }

    /** True while an editor runs without being drawn, so it must not touch anything outside its keyframe. */
    public static boolean silent() {
        return mode == Mode.CAPTURE || mode == Mode.REPLAY;
    }

    public static Map<String, Object> capture(Runnable render) {
        begin(Mode.CAPTURE, true);
        capture = new LinkedHashMap<>();
        try {
            render.run();
            return capture;
        } finally {
            end();
        }
    }

    /** Draws the primary's editor; {@code targets} are the other targets' captures, empty when it edits one keyframe. */
    public static @Nullable Change display(String rowScope, List<Map<String, Object>> targets, Predicate<String> shown,
            boolean severalTargets, Runnable render) {
        begin(Mode.DISPLAY, severalTargets);
        scope = rowScope;
        captures = targets;
        visible = shown;
        change = null;
        try {
            render.run();
            return change;
        } finally {
            while (!PENDING.isEmpty()) {
                if (PENDING.pop().locked()) ImGui.endDisabled();
            }
            end();
        }
    }

    /** Feeds these edits into one target's editor; the keys it actually had are added to {@code applied}. */
    public static void replay(List<Change> edits, @Nullable Set<String> applied, Runnable render) {
        begin(Mode.REPLAY, true);
        Map<String, Change> byKey = new HashMap<>();
        for (Change edit : edits) byKey.put(edit.key(), edit);
        injects = byKey;
        hits = applied;
        try {
            render.run();
        } finally {
            end();
        }
    }

    private static void begin(Mode next, boolean severalTargets) {
        mode = next;
        shared = severalTargets;
        COUNTS.clear();
        PENDING.clear();
        OPEN_COMBOS.clear();
        nesting = 0;
        replayCombo = null;
    }

    private static void end() {
        mode = Mode.OFF;
        shared = false;
        scope = "";
        capture = null;
        captures = null;
        visible = key -> true;
        injects = Map.of();
        hits = null;
    }

    private static String key(String label) {
        int index = COUNTS.merge(label, 1, Integer::sum) - 1;
        return label + "#" + index;
    }

    public static void head(String label, @Nullable Object container, Kind kind, CallbackInfoReturnable<Boolean> cir) {
        if (mode == Mode.OFF) return;
        if (mode == Mode.DISPLAY) {
            if (nesting > 0) {
                nesting++;
                return;
            }
            if (!OPEN_COMBOS.isEmpty()) {
                PENDING.push(PASS);
                nesting = 1;
                return;
            }
            String key = key(label);
            if (!visible.test(key)) {
                cir.setReturnValue(false);
                return;
            }
            Object before = WidgetValues.snapshot(container);
            boolean[] mixed = WidgetValues.mixed(key, before, captures);
            boolean locked = WidgetValues.any(mixed) && !InputHelper.isCtrlDownRaw();
            if (locked) ImGui.beginDisabled();
            PENDING.push(new Pending(key, before, locked, mixed, kind, label));
            nesting = 1;
            return;
        }
        String key = key(label);
        if (mode == Mode.CAPTURE) {
            capture.put(key, WidgetValues.snapshot(container));
            cir.setReturnValue(false);
            return;
        }
        Change edit = injects.get(key);
        if (edit == null) {
            cir.setReturnValue(false);
            return;
        }
        if (hits != null) hits.add(key);
        if (kind == Kind.COMBO) replayCombo = edit;
        cir.setReturnValue(WidgetValues.inject(edit, container, kind));
    }

    public static void tail(@Nullable Object container, boolean result) {
        if (mode != Mode.DISPLAY || nesting == 0) return;
        if (--nesting > 0) return;
        Pending pending = PENDING.pop();
        if (pending == PASS) return;
        if (pending == COMBO_ITEM) {
            if (result && change == null && !OPEN_COMBOS.isEmpty()) {
                change = new Change(OPEN_COMBOS.peek(), null, null, lastSelectable);
            }
            return;
        }
        if (pending.locked()) ImGui.endDisabled();
        if (WidgetValues.any(pending.mixed())) MixedOverlay.draw(pending.label(), pending.mixed(), pending.kind(), pending.locked());
        Object value = pending.kind() == Kind.VALUE ? WidgetValues.snapshot(container) : pending.before();
        if (pending.kind() != Kind.BUTTON) PropertySelection.record(scope, pending.key(), pending.kind(), value);
        if (pending.kind() == Kind.COMBO) {
            if (result) OPEN_COMBOS.push(pending.key());
            return;
        }
        if (!result || change != null) return;
        change = switch (pending.kind()) {
            case CHECK -> new Change(pending.key(), !(Boolean) pending.before(), null, null);
            case RADIO -> new Change(pending.key(), true, null, null);
            case BUTTON -> new Change(pending.key(), null, null, null);
            default -> new Change(pending.key(), value, WidgetValues.changed(pending.before(), value), null);
        };
    }

    public static void selectableHead(String label, CallbackInfoReturnable<Boolean> cir) {
        if (mode == Mode.OFF) return;
        if (mode == Mode.DISPLAY && nesting == 0 && !OPEN_COMBOS.isEmpty()) {
            lastSelectable = label;
            PENDING.push(COMBO_ITEM);
            nesting = 1;
            return;
        }
        if (mode == Mode.REPLAY && replayCombo != null) {
            cir.setReturnValue(label.equals(replayCombo.selection()));
            return;
        }
        head(label, null, Kind.BUTTON, cir);
    }

    public static void endComboHead(CallbackInfo ci) {
        if (mode == Mode.DISPLAY && nesting == 0 && !OPEN_COMBOS.isEmpty()) OPEN_COMBOS.pop();
        if (mode == Mode.REPLAY && replayCombo != null) {
            replayCombo = null;
            ci.cancel();
        }
    }
}
