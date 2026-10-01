package ml.mypals.vectorthree.mixin.minecraft.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.ryansrenderingkit.builderManager.BuilderManager;
import ml.mypals.vectorthree.mc.render.IrisBypassTarget;
import ml.mypals.vectorthree.mc.render.RenderTargets;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


// Each render-type draw takes its attachments from RenderSystem's output override when one is set.
@Mixin(value = BuilderManager.class, remap = false)
public class BuilderManagerIrisBypassMixin {
    @Shadow public String id;

    @WrapMethod(method = "flushDraws")
    private void vector3$drawIntoBypassTarget(Operation<Void> original) {
        IrisBypassTarget.beginIrisBypass();
        try {
            RenderTargets.drawInto(IrisBypassTarget.isBypassing()
                    ? IrisBypassTarget.targetFor(Minecraft.getInstance().gameRenderer.mainRenderTarget(), "BuilderManager:" + id)
                    : null, original::call);
        } finally {
            IrisBypassTarget.endIrisBypass();
        }
    }
}
