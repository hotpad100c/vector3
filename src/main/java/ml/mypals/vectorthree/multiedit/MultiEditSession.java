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
import java.util.function.Predicate;

/**
 * Drives ImGuiMultiEditMixin. A keyframe's own editor UI is run once per target without drawing (CAPTURE) to learn
 * every widget's value, then drawn for the primary target (DISPLAY) with mixed widgets locked behind Ctrl, and the
 * edit the user made is replayed into every other target's editor (REPLAY) so each target applies it through its
 * own UI code. Widgets are keyed by label plus how many times that label came before.
 */
public final class MultiEditSession {
    private enum Mode { OFF, CAPTURE, DISPLAY, REPLAY }

    public enum Kind { VALUE, CHECK, RADIO, BUTTON, COMBO }

    public record Change(String key, @Nullable Object value, boolean @Nullable [] components, @Nullable String selection) {}

    private record Pending(String key, Object before, boolean locked, boolean[] mixed, Kind kind, String label) {}

    private static final Pending PASS = new Pending("", "", false, new boolean[0], Kind.BUTTON, "");
    private static final Pending COMBO_ITEM = new Pending("", "", false, new boolean[0], Kind.BUTTON, "");

    private static Mode mode = Mode.OFF;
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static Map<String, Object> capture;
    private static List<Map<String, Object>> captures;
    private static Predicate<String> visible = key -> true;
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<String> OPEN_COMBOS = new ArrayDeque<>();
    private static int nesting;
    private static @Nullable Change change;
    private static @Nullable Change inject;
    private static boolean replayComboOpen;

    private MultiEditSession() {}

    public static boolean active() {
        return mode != Mode.OFF;
    }

    /** True while an editor runs without being drawn, so it must not touch anything outside its keyframe. */
    public static boolean silent() {
        return mode == Mode.CAPTURE || mode == Mode.REPLAY;
    }

    public static Map<String, Object> capture(Runnable render) {
        begin(Mode.CAPTURE);
        capture = new LinkedHashMap<>();
        try {
            render.run();
            return capture;
        } finally {
            end();
        }
    }

    public static @Nullable Change display(List<Map<String, Object>> targets, Predicate<String> shown, Runnable render) {
        begin(Mode.DISPLAY);
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

    public static void replay(Change edit, Runnable render) {
        begin(Mode.REPLAY);
        inject = edit;
        try {
            render.run();
        } finally {
            end();
        }
    }

    private static void begin(Mode next) {
        mode = next;
        COUNTS.clear();
        PENDING.clear();
        OPEN_COMBOS.clear();
        nesting = 0;
        replayComboOpen = false;
    }

    private static void end() {
        mode = Mode.OFF;
        capture = null;
        captures = null;
        visible = key -> true;
        inject = null;
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
        if (inject != null && key.equals(inject.key())) {
            if (kind == Kind.COMBO) replayComboOpen = true;
            cir.setReturnValue(WidgetValues.inject(inject, container, kind));
        } else {
            cir.setReturnValue(false);
        }
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
        if (pending.kind() == Kind.COMBO) {
            if (result) OPEN_COMBOS.push(pending.key());
            return;
        }
        if (!result || change != null) return;
        change = switch (pending.kind()) {
            case CHECK -> new Change(pending.key(), !(Boolean) pending.before(), null, null);
            case RADIO -> new Change(pending.key(), true, null, null);
            case BUTTON -> new Change(pending.key(), null, null, null);
            default -> {
                Object after = WidgetValues.snapshot(container);
                yield new Change(pending.key(), after, WidgetValues.changed(pending.before(), after), null);
            }
        };
    }

    private static String lastSelectable;

    public static void selectableHead(String label, CallbackInfoReturnable<Boolean> cir) {
        if (mode == Mode.OFF) return;
        if (mode == Mode.DISPLAY && nesting == 0 && !OPEN_COMBOS.isEmpty()) {
            lastSelectable = label;
            PENDING.push(COMBO_ITEM);
            nesting = 1;
            return;
        }
        if (mode == Mode.REPLAY && replayComboOpen) {
            cir.setReturnValue(inject != null && label.equals(inject.selection()));
            return;
        }
        head(label, null, Kind.BUTTON, cir);
    }

    public static void endComboHead(CallbackInfo ci) {
        if (mode == Mode.DISPLAY && nesting == 0 && !OPEN_COMBOS.isEmpty()) OPEN_COMBOS.pop();
        if (mode == Mode.REPLAY && replayComboOpen) {
            replayComboOpen = false;
            ci.cancel();
        }
    }
}
