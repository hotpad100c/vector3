package ml.mypals.vectorthree.mixin.multiedit;

import com.llamalad7.mixinextras.sugar.Local;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImDouble;
import imgui.moulberry90.type.ImFloat;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.multiedit.MultiEditSession;
import ml.mypals.vectorthree.multiedit.MultiEditSession.Kind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every overload of a widget family starts with the label and its value holder, so one handler covers them all.
@Mixin(value = ImGui.class, remap = false)
public class ImGuiMultiEditMixin {
    @Inject(method = {"dragFloat", "dragFloat2", "dragFloat3", "dragFloat4", "sliderFloat", "sliderFloat2", "sliderFloat3",
            "sliderFloat4", "sliderAngle", "inputFloat2", "inputFloat3", "inputFloat4", "colorEdit3", "colorEdit4"},
            at = @At("HEAD"), cancellable = true)
    private static void vector3$floats(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true, ordinal = 0) float[] value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = {"dragFloat", "dragFloat2", "dragFloat3", "dragFloat4", "sliderFloat", "sliderFloat2", "sliderFloat3",
            "sliderFloat4", "sliderAngle", "inputFloat2", "inputFloat3", "inputFloat4", "colorEdit3", "colorEdit4"},
            at = @At("RETURN"))
    private static void vector3$floatsEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true, ordinal = 0) float[] value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = {"dragInt", "dragInt2", "dragInt3", "dragInt4", "sliderInt", "sliderInt2", "sliderInt3", "sliderInt4",
            "inputInt2", "inputInt3", "inputInt4"}, at = @At("HEAD"), cancellable = true)
    private static void vector3$ints(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true, ordinal = 0) int[] value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = {"dragInt", "dragInt2", "dragInt3", "dragInt4", "sliderInt", "sliderInt2", "sliderInt3", "sliderInt4",
            "inputInt2", "inputInt3", "inputInt4"}, at = @At("RETURN"))
    private static void vector3$intsEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true, ordinal = 0) int[] value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = {"inputInt", "combo"}, at = @At("HEAD"), cancellable = true)
    private static void vector3$imInt(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true) ImInt value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = {"inputInt", "combo"}, at = @At("RETURN"))
    private static void vector3$imIntEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) ImInt value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = "inputFloat", at = @At("HEAD"), cancellable = true)
    private static void vector3$imFloat(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true) ImFloat value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = "inputFloat", at = @At("RETURN"))
    private static void vector3$imFloatEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) ImFloat value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = "inputDouble", at = @At("HEAD"), cancellable = true)
    private static void vector3$imDouble(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true) ImDouble value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = "inputDouble", at = @At("RETURN"))
    private static void vector3$imDoubleEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) ImDouble value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = {"inputText", "inputTextMultiline", "inputTextWithHint"}, at = @At("HEAD"), cancellable = true)
    private static void vector3$text(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true) ImString value) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = {"inputText", "inputTextMultiline", "inputTextWithHint"}, at = @At("RETURN"))
    private static void vector3$textEnd(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) ImString value) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = "checkbox(Ljava/lang/String;Limgui/moulberry90/type/ImBoolean;)Z", at = @At("HEAD"), cancellable = true)
    private static void vector3$checkboxHolder(String label, ImBoolean value, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = "checkbox(Ljava/lang/String;Limgui/moulberry90/type/ImBoolean;)Z", at = @At("RETURN"))
    private static void vector3$checkboxHolderEnd(String label, ImBoolean value, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = "checkbox(Ljava/lang/String;Z)Z", at = @At("HEAD"), cancellable = true)
    private static void vector3$checkbox(String label, boolean value, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.head(label, value, Kind.CHECK, cir);
    }

    @Inject(method = "checkbox(Ljava/lang/String;Z)Z", at = @At("RETURN"))
    private static void vector3$checkboxEnd(String label, boolean value, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = "radioButton(Ljava/lang/String;Z)Z", at = @At("HEAD"), cancellable = true)
    private static void vector3$radio(String label, boolean active, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.head(label, active, Kind.RADIO, cir);
    }

    @Inject(method = "radioButton(Ljava/lang/String;Z)Z", at = @At("RETURN"))
    private static void vector3$radioEnd(String label, boolean active, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(active, cir.getReturnValueZ());
    }

    @Inject(method = "radioButton(Ljava/lang/String;Limgui/moulberry90/type/ImInt;I)Z", at = @At("HEAD"), cancellable = true)
    private static void vector3$radioHolder(String label, ImInt value, int button, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.head(label, value, Kind.VALUE, cir);
    }

    @Inject(method = "radioButton(Ljava/lang/String;Limgui/moulberry90/type/ImInt;I)Z", at = @At("RETURN"))
    private static void vector3$radioHolderEnd(String label, ImInt value, int button, CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(value, cir.getReturnValueZ());
    }

    @Inject(method = {"button", "smallButton"}, at = @At("HEAD"), cancellable = true)
    private static void vector3$button(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true, ordinal = 0) String label) {
        MultiEditSession.head(label, null, Kind.BUTTON, cir);
    }

    @Inject(method = {"button", "smallButton"}, at = @At("RETURN"))
    private static void vector3$buttonEnd(CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(null, cir.getReturnValueZ());
    }

    @Inject(method = "beginCombo", at = @At("HEAD"), cancellable = true)
    private static void vector3$combo(CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) String label, @Local(argsOnly = true, ordinal = 1) String preview) {
        MultiEditSession.head(label, preview, Kind.COMBO, cir);
    }

    @Inject(method = "beginCombo", at = @At("RETURN"))
    private static void vector3$comboEnd(CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(null, cir.getReturnValueZ());
    }

    @Inject(method = "endCombo", at = @At("HEAD"), cancellable = true)
    private static void vector3$endCombo(CallbackInfo ci) {
        MultiEditSession.endComboHead(ci);
    }

    @Inject(method = "selectable", at = @At("HEAD"), cancellable = true)
    private static void vector3$selectable(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true, ordinal = 0) String label) {
        MultiEditSession.selectableHead(label, cir);
    }

    @Inject(method = "selectable", at = @At("RETURN"))
    private static void vector3$selectableEnd(CallbackInfoReturnable<Boolean> cir) {
        MultiEditSession.tail(null, cir.getReturnValueZ());
    }
}
