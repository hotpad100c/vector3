package ml.mypals.vectorthree.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.text.VanillaText;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.network.chat.TextColor;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.shape.text.FontTextShape;
import ml.mypals.vectorthree.shape.text.TextFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextShape.class, remap = false)
public class TextShapeMixin {
    @Inject(method = "getRenderMessages", at = @At("HEAD"), cancellable = true)
    private void vector3$parseStyledText(CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        TextShape shape = (TextShape) (Object) this;
        cir.setReturnValue(TextFormatting.format(shape.contents,
                shape instanceof FontTextShape fontShape ? fontShape.font : "minecraft:default"));
    }

    @Inject(method = "enabled", at = @At("HEAD"), cancellable = true)
    private void vector3$honorVisibility(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(((TextShape) (Object) this).enabled);
    }

    /** RRK's vanilla outline uses 0.8× the text color; use the shape's configured outline color instead. */
    @Redirect(method = "drawInternal", at = @At(value = "INVOKE",
            target = "Lml/mypals/ryansrenderingkit/utils/Helpers;multiplyRGB(IF)I"))
    private int vector3$configuredOutlineColor(int color, float shade) {
        return (Object) this instanceof FontTextShape fontShape ? fontShape.outlineColor : Helpers.multiplyRGB(color, shade);
    }

    @Redirect(method = "drawInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int vector3$measureStyledText(Font font, String text) {
        TextShape shape = (TextShape) (Object) this;
        return font.width(TextFormatting.formatLine(text,
                shape instanceof FontTextShape fontShape ? fontShape.font : "minecraft:default"));
    }

    // The glow pass: fill in its glow colour, or black to cut it out of the outline's glow; strength rides in the light.
    @WrapOperation(method = "drawInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeStorage;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"))
    private void vector3$glowText(SubmitNodeStorage submits, PoseStack pose, float x, float y, FormattedCharSequence text,
            boolean dropShadow, Font.DisplayMode mode, int light, int color, int background, int outline, Operation<Void> original) {
        VanillaText.Glow glow = VanillaText.glow;
        if (glow == null) {
            original.call(submits, pose, x, y, text, dropShadow, mode, light, color, background, outline);
            return;
        }
        FormattedCharSequence recolored = glow.fill() && glow.fillColor() == null ? text
                : sink -> text.accept((index, style, codepoint) -> sink.accept(index, style.withColor((TextColor) null), codepoint));
        original.call(submits, pose, x, y, recolored, false, mode, glow.strength(), VanillaText.glowFill(glow, color), 0, outline);
    }

    @Inject(method = "beforeDraw(Lcom/mojang/blaze3d/vertex/PoseStack;FZ)V", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lorg/joml/Quaternionfc;)V"))
    private void vector3$faceCameraOnly(PoseStack poseStack, float deltaTime, boolean camSpace, CallbackInfo ci) {
        Matrix4f pose = poseStack.last().pose();
        Vector3f translation = pose.getTranslation(new Vector3f());
        Vector3f scale = pose.getScale(new Vector3f());
        pose.translation(translation).scale(scale);
        poseStack.last().normal().identity();
    }
}
