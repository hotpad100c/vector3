package ml.mypals.vectorthree.flashback.custom;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Base for vector3's own Flashback keyframe types. Each keyframe holds one immutable value (usually a
 * record); a subclass says how to create, edit, blend and apply it, and this class supplies the
 * Keyframe, KeyframeChange, add popup and save/load plumbing.
 * <p>
 * Flashback registers types by class and silently drops a second instance of one, so every type needs
 * its own subclass. Register the singleton with {@link CustomKeyframes#register}. Values are saved with
 * Gson, so they must be Gson-serializable; fields added later load as null/0 (see {@link #sanitize}).
 */
public abstract class CustomKeyframeType<T> implements KeyframeType<CustomKeyframe<T>> {
    private final String id;
    private final String nameKey;
    private final Class<T> valueType;

    protected CustomKeyframeType(String id, String nameKey, Class<T> valueType) {
        this.id = id;
        this.nameKey = nameKey;
        this.valueType = valueType;
    }

    /** Value of a newly added keyframe; called each time the add popup opens. */
    protected abstract T createValue();

    /** Draws the value's editor and returns the edited value, or the same instance when unchanged. */
    protected abstract T edit(T value);

    /** Applies a value, either a keyframe's own or one blended by {@link #lerp}. */
    protected abstract void apply(T value, KeyframeHandler handler);

    /** Blends two keyframe values; the default switches halfway, for values that can't be blended. */
    protected T lerp(T from, T to, double amount) {
        return amount < 0.5 ? from : to;
    }

    /**
     * Smooth (Catmull-Rom) interpolation between {@code p1} and {@code p2}; {@code p0} and {@code p3} only
     * shape the curve, and the times are relative to {@code p0}. Defaults to {@link #lerp}.
     */
    protected T smooth(T p0, T p1, T p2, T p3, float t1, float t2, float t3, float amount) {
        return lerp(p1, p2, amount);
    }

    /** Hermite interpolation at a real-time tick; defaults to {@link #lerp} between the surrounding values. */
    protected T hermite(Map<Float, T> values, float tick) {
        TreeMap<Float, T> sorted = new TreeMap<>(values);
        Map.Entry<Float, T> before = sorted.floorEntry(tick);
        Map.Entry<Float, T> after = sorted.higherEntry(tick);
        if (before == null) before = sorted.firstEntry();
        if (after == null) return before.getValue();
        double amount = (tick - before.getKey()) / (after.getKey() - before.getKey());
        return lerp(before.getValue(), after.getValue(), Math.clamp(amount, 0, 1));
    }

    /** Repairs a loaded value, e.g. fields that older saves lack and Gson left null. */
    protected T sanitize(T value) {
        return value;
    }

    /** Creates this type's keyframes; override to use a CustomKeyframe subclass. */
    protected CustomKeyframe<T> newKeyframe(T value, InterpolationType interpolation) {
        return new CustomKeyframe<>(this, value, interpolation);
    }

    /** The JSON field the value is saved under. */
    protected String valueField() {
        return "value";
    }

    /** Draws a keyframe on the timeline; return false to use Flashback's interpolation-type shapes. */
    protected boolean drawOnTimeline(CustomKeyframe<T> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        return false;
    }

    /** A change that just runs {@code action}, for types that override {@link #customKeyframeChange}. */
    protected static KeyframeChange action(Consumer<KeyframeHandler> action) {
        return new CustomKeyframeChange(null, null, action);
    }

    @SuppressWarnings("unchecked")
    protected static <T> T valueOf(Keyframe keyframe) {
        return ((CustomKeyframe<T>) keyframe).value;
    }

    public Class<T> valueType() {
        return valueType;
    }

    @Override public String id() { return id; }
    @Override public String name() { return I18n.get(nameKey); }
    @Override public Class<? extends KeyframeChange> keyframeChangeType() { return CustomKeyframeChange.class; }

    // Every custom type shares CustomKeyframeChange, so Flashback's one-change-per-class rule has to be off.
    @Override public final boolean allowApplyingDuplicateKeyframeChanges() { return true; }

    // Other handlers (camera path previews, tickrate capture) sample keyframes at other ticks.
    @Override public boolean supportsHandler(KeyframeHandler handler) { return handler instanceof MinecraftKeyframeHandler; }

    @Override public @Nullable CustomKeyframe<T> createDirect() { return null; }

    @Override
    public KeyframeCreatePopup<CustomKeyframe<T>> createPopup() {
        @SuppressWarnings("unchecked") T[] draft = (T[]) new Object[]{createValue()};
        return () -> {
            draft[0] = edit(draft[0]);
            if (ImGui.button(I18n.get("vector3.keyframe_type.add")) || ReplayUI.consumeConfirm()) {
                return newKeyframe(draft[0], InterpolationType.getDefault());
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.keyframe_type.cancel")) || ReplayUI.consumeCancel()) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
