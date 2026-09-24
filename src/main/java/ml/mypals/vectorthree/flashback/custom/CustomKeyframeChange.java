package ml.mypals.vectorthree.flashback.custom;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** The change of every {@link CustomKeyframeType}: either a value to apply, or a plain action. */
public final class CustomKeyframeChange implements KeyframeChange {
    private final @Nullable CustomKeyframeType<?> type;
    private final @Nullable Object value;
    private final Consumer<KeyframeHandler> action;

    CustomKeyframeChange(@Nullable CustomKeyframeType<?> type, @Nullable Object value, Consumer<KeyframeHandler> action) {
        this.type = type;
        this.value = value;
        this.action = action;
    }

    static <T> CustomKeyframeChange of(CustomKeyframeType<T> type, T value) {
        return new CustomKeyframeChange(type, value, handler -> type.apply(value, handler));
    }

    @Override
    public void apply(KeyframeHandler handler) {
        action.accept(handler);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange other, double amount) {
        if (type != null && other instanceof CustomKeyframeChange target && target.type == type) {
            return lerp(type, value, target.value, amount);
        }
        return amount < 0.5 ? this : other;
    }

    @SuppressWarnings("unchecked")
    private static <T> KeyframeChange lerp(CustomKeyframeType<T> type, Object from, Object to, double amount) {
        return of(type, type.lerp((T) from, (T) to, amount));
    }
}
