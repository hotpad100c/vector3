package ml.mypals.vectorthree.mixin.flashback;

import com.google.common.collect.Maps;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.camera.shake.ShakeHolder;
import ml.mypals.vectorthree.camera.shake.ShakeKeyframe;
import ml.mypals.vectorthree.camera.shake.ShakeParams;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Mixin(value = CameraShakeKeyframe.class, remap = false)
public abstract class CameraShakeKeyframeMixin implements ShakeHolder, ShakeKeyframe {
    @Shadow private float frequencyX;
    @Shadow private float amplitudeX;
    @Shadow private float frequencyY;
    @Shadow private float amplitudeY;
    @Shadow private boolean splitParams;

    @Unique private ShakeParams vector3$shake;

    @Override public ShakeParams vector3$shake() { return vector3$shake; }
    @Override public void vector3$setShake(ShakeParams params) { vector3$shake = params; }
    @Override public float vector3$frequencyX() { return frequencyX; }
    @Override public float vector3$frequencyY() { return frequencyY; }

    @Override
    public void vector3$setBase(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY) {
        this.frequencyX = frequencyX;
        this.amplitudeX = amplitudeX;
        this.frequencyY = frequencyY;
        this.amplitudeY = amplitudeY;
        this.splitParams = frequencyX != frequencyY || amplitudeX != amplitudeY;
    }

    @Unique
    private ShakeParams vector3$params() {
        return ShakeParams.orDefault(vector3$shake);
    }

    @Unique
    private static void vector3$toChange(KeyframeChange change, ShakeParams params) {
        if (change instanceof ShakeHolder holder) holder.vector3$setShake(params);
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void vector3$copyShake(CallbackInfoReturnable<Keyframe> cir) {
        ((ShakeHolder) cir.getReturnValue()).vector3$setShake(vector3$shake);
    }

    @Inject(method = "createChange", at = @At("RETURN"))
    private void vector3$changeShake(CallbackInfoReturnable<KeyframeChange> cir) {
        vector3$toChange(cir.getReturnValue(), vector3$params());
    }

    @Inject(method = "createSmoothInterpolatedChange", at = @At("RETURN"))
    private void vector3$smoothShake(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3,
            float amount, CallbackInfoReturnable<KeyframeChange> cir) {
        ShakeParams self = vector3$params();
        float[] a = self.floats(), b = vector3$floats(p1), c = vector3$floats(p2), d = vector3$floats(p3);
        float[] result = new float[a.length];
        for (int i = 0; i < a.length; i++) result[i] = CatmullRom.value(a[i], b[i], c[i], d[i], t1 - t0, t2 - t0, t3 - t0, amount);
        vector3$toChange(cir.getReturnValue(), ShakeParams.of(result, self.octaves(), self.seed()));
    }

    @Inject(method = "createHermiteInterpolatedChange", at = @At("RETURN"))
    private void vector3$hermiteShake(Map<Float, Keyframe> keyframes, float amount,
            CallbackInfoReturnable<KeyframeChange> cir) {
        ShakeParams self = vector3$params();
        float[] result = new float[self.floats().length];
        for (int i = 0; i < result.length; i++) {
            int index = i;
            result[i] = (float) Hermite.value(Maps.transformValues(keyframes, k -> (double) vector3$floats(k)[index]), amount);
        }
        vector3$toChange(cir.getReturnValue(), ShakeParams.of(result, self.octaves(), self.seed()));
    }

    @Unique
    private static float[] vector3$floats(Keyframe keyframe) {
        return ShakeParams.orDefault(((ShakeHolder) keyframe).vector3$shake()).floats();
    }

    @Inject(method = "renderEditKeyframe", at = @At("TAIL"))
    private void vector3$editShake(Consumer<Consumer<Keyframe>> update, CallbackInfo ci) {
        ShakeParams params = vector3$params();
        ImGui.separator();
        vector3$slider(update, "roll_frequency", params.rollFrequency(), 0.1f, 10, "%.1f",
                (p, v) -> new ShakeParams(v, p.rollAmplitude(), p.positionFrequency(), p.positionX(), p.positionY(), p.positionZ(), p.octaves(), p.roughness(), p.seed()));
        vector3$slider(update, "roll_amplitude", params.rollAmplitude(), 0, 10, "%.1f",
                (p, v) -> new ShakeParams(p.rollFrequency(), v, p.positionFrequency(), p.positionX(), p.positionY(), p.positionZ(), p.octaves(), p.roughness(), p.seed()));
        vector3$slider(update, "position_frequency", params.positionFrequency(), 0.1f, 10, "%.1f",
                (p, v) -> new ShakeParams(p.rollFrequency(), p.rollAmplitude(), v, p.positionX(), p.positionY(), p.positionZ(), p.octaves(), p.roughness(), p.seed()));
        float[] position = {params.positionX(), params.positionY(), params.positionZ()};
        ImGui.setNextItemWidth(160);
        if (ImGui.dragFloat3(I18n.get("vector3.shake.position_amplitude"), position, 0.002f, 0, 1, "%.3f")) {
            float x = position[0], y = position[1], z = position[2];
            vector3$update(update, p -> new ShakeParams(p.rollFrequency(), p.rollAmplitude(), p.positionFrequency(), x, y, z, p.octaves(), p.roughness(), p.seed()));
        }
        int[] octaves = {params.octaves()};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt(I18n.get("vector3.shake.octaves"), octaves, 1, ShakeParams.MAX_OCTAVES)) {
            int value = octaves[0];
            vector3$update(update, p -> new ShakeParams(p.rollFrequency(), p.rollAmplitude(), p.positionFrequency(), p.positionX(), p.positionY(), p.positionZ(), value, p.roughness(), p.seed()));
        }
        vector3$slider(update, "roughness", params.roughness(), 0, 1, "%.2f",
                (p, v) -> new ShakeParams(p.rollFrequency(), p.rollAmplitude(), p.positionFrequency(), p.positionX(), p.positionY(), p.positionZ(), p.octaves(), v, p.seed()));
        int[] seed = {params.seed()};
        ImGui.setNextItemWidth(160);
        if (ImGui.dragInt(I18n.get("vector3.shake.seed"), seed)) {
            int value = seed[0];
            vector3$update(update, p -> new ShakeParams(p.rollFrequency(), p.rollAmplitude(), p.positionFrequency(), p.positionX(), p.positionY(), p.positionZ(), p.octaves(), p.roughness(), value));
        }
        ImGui.textDisabled(I18n.get("vector3.shake.presets"));
        for (ShakeParams.Preset preset : ShakeParams.Preset.values()) {
            if (preset.ordinal() > 0) ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.shake.preset." + preset.name().toLowerCase(Locale.ROOT)))) {
                int seedValue = params.seed();
                update.accept(keyframe -> {
                    ((ShakeKeyframe) keyframe).vector3$setBase(preset.frequencyX, preset.amplitudeX, preset.frequencyY, preset.amplitudeY);
                    ShakeParams p = preset.params;
                    ((ShakeHolder) keyframe).vector3$setShake(new ShakeParams(p.rollFrequency(), p.rollAmplitude(), p.positionFrequency(),
                            p.positionX(), p.positionY(), p.positionZ(), p.octaves(), p.roughness(), seedValue));
                });
            }
        }
    }

    @Unique
    private static void vector3$slider(Consumer<Consumer<Keyframe>> update, String key, float value, float min, float max,
            String format, BiFunction<ShakeParams, Float, ShakeParams> field) {
        float[] edit = {value};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat(I18n.get("vector3.shake." + key), edit, min, max, format) && edit[0] != value) {
            float result = edit[0];
            vector3$update(update, p -> field.apply(p, result));
        }
    }

    @Unique
    private static void vector3$update(Consumer<Consumer<Keyframe>> update, UnaryOperator<ShakeParams> edit) {
        update.accept(keyframe -> {
            ShakeHolder holder = (ShakeHolder) keyframe;
            holder.vector3$setShake(edit.apply(ShakeParams.orDefault(holder.vector3$shake())));
        });
    }
}
