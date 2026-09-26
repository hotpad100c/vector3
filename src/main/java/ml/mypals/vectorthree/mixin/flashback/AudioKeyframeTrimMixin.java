package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import com.moulberry.flashback.sound.FlashbackAudioBuffer;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiInputTextFlags;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.clips.AudioLevel;
import ml.mypals.vectorthree.clips.AudioTrim;
import ml.mypals.vectorthree.flashback.FileBrowse;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

@Mixin(value = AudioKeyframe.class, remap = false)
public abstract class AudioKeyframeTrimMixin implements AudioTrim, AudioLevel {
    @Shadow public Path path;
    @Shadow private FlashbackAudioBuffer audioBuffer;
    @Shadow private void ensureAudioBufferLoaded() {}

    @Unique private int vector3$in;
    @Unique private int vector3$length = -1;
    @Unique private float vector3$volume = 1;
    @Unique private float vector3$pitch = 1;

    @Override public int vector3$audioIn() { return vector3$in; }
    @Override public int vector3$audioLength() { return vector3$length; }
    @Override public float vector3$volume() { return vector3$volume; }
    @Override public float vector3$pitch() { return vector3$pitch; }

    @Override
    public void vector3$setAudioTrim(int in, int length) {
        vector3$in = Math.max(0, in);
        vector3$length = length;
    }

    @Override
    public void vector3$setLevel(float volume, float pitch) {
        vector3$volume = Math.clamp(volume, 0, 4);
        vector3$pitch = Math.clamp(pitch, 0.1f, 10);
    }

    @Override
    public void vector3$setPath(Path path) {
        this.path = path;
        audioBuffer = null;
        vector3$in = 0;
        vector3$length = -1;
    }

    @Override
    public int vector3$audioTicks() {
        ensureAudioBufferLoaded();
        return audioBuffer == FlashbackAudioBuffer.EMPTY ? -1 : Math.round(audioBuffer.durationInSeconds() * 20);
    }

    @Unique
    private boolean vector3$untouched() {
        return vector3$in == 0 && vector3$length < 0 && vector3$pitch == 1;
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void vector3$copyTrim(CallbackInfoReturnable<Keyframe> cir) {
        ((AudioTrim) cir.getReturnValue()).vector3$setAudioTrim(vector3$in, vector3$length);
        ((AudioLevel) cir.getReturnValue()).vector3$setLevel(vector3$volume, vector3$pitch);
    }

    @Inject(method = "renderEditKeyframe", at = @At("TAIL"))
    private void vector3$editAudio(Consumer<Consumer<Keyframe>> update, CallbackInfo ci) {
        ImString file = new ImString(path == null ? "" : path.toString(), 1024);
        ImGui.setNextItemWidth(220);
        ImGui.inputText(I18n.get("vector3.audio.file"), file, ImGuiInputTextFlags.ReadOnly);
        if (FileBrowse.button("audio", file, I18n.get("vector3.file.audio_filter"), "mp3", "ogg", "wav", "aiff", "au", "flac", "opus")) {
            try {
                Path picked = Path.of(file.get());
                update.accept(keyframe -> ((AudioTrim) keyframe).vector3$setPath(picked));
            } catch (InvalidPathException ignored) {
            }
        }
        float[] volume = {vector3$volume};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat(I18n.get("vector3.audio.volume"), volume, 0, 2, "%.2f")) {
            float value = volume[0];
            update.accept(keyframe -> ((AudioLevel) keyframe).vector3$setLevel(value, ((AudioLevel) keyframe).vector3$pitch()));
        }
        float[] pitch = {vector3$pitch};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat(I18n.get("vector3.audio.pitch"), pitch, 0.25f, 4, "%.2f")) {
            float value = pitch[0];
            update.accept(keyframe -> ((AudioLevel) keyframe).vector3$setLevel(((AudioLevel) keyframe).vector3$volume(), value));
        }
    }

    // Pitch plays the file faster, so it spans fewer timeline ticks.
    @Inject(method = "getCustomWidthInTicks", at = @At("RETURN"), cancellable = true)
    private void vector3$trimmedWidth(CallbackInfoReturnable<Float> cir) {
        float full = cir.getReturnValueF();
        if (full <= 0 || vector3$untouched()) return;
        float rest = Math.max(1, full - vector3$in);
        float media = vector3$length < 0 ? rest : Math.min(rest, vector3$length);
        cir.setReturnValue(Math.max(1, media / vector3$pitch));
    }

    // Flashback draws the whole file's waveform from the keyframe; shift and scale it to what plays, and clip it there.
    @WrapMethod(method = "drawOnTimeline")
    private void vector3$drawTrimmed(ImDrawList drawList, int size, float x, float y, int colour, float ticksPerPixel,
            float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes, Operation<Void> original) {
        if (vector3$untouched() || ticksPerPixel <= 0) {
            original.call(drawList, size, x, y, colour, ticksPerPixel, minX, maxX, tick, keyframes);
            return;
        }
        float width = ((AudioKeyframe) (Object) this).getCustomWidthInTicks() / ticksPerPixel;
        Map.Entry<Integer, Keyframe> next = keyframes.higherEntry(tick);
        if (next != null) width = Math.min(width, (next.getKey() - tick) / ticksPerPixel);
        float scaled = ticksPerPixel * vector3$pitch;
        TreeMap<Integer, Keyframe> alone = new TreeMap<>(Map.of(tick, (Keyframe) (Object) this));
        drawList.pushClipRect(x, y - size, x + width, y + size, true);
        original.call(drawList, size, x - vector3$in / scaled, y, colour, scaled, minX, maxX, tick, alone);
        drawList.popClipRect();
    }
}
