package ml.mypals.vectorthree.mixin.flashback;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Flashback first binds the marker texture inside its open render pass, where uploading it throws; load it before.
@Mixin(value = WorldRenderHook.class, remap = false)
public class MarkerTexturePreloadMixin {
    @Unique private static final Identifier MARKER_TEXTURE = Identifier.parse("flashback:world_marker_circle.png");

    @Inject(method = "renderHook", at = @At("HEAD"))
    private static void vector3$preloadMarkerTexture(PoseStack poseStack, CameraRenderState camera, CallbackInfo ci) {
        Minecraft.getInstance().getTextureManager().getTexture(MARKER_TEXTURE);
    }
}
