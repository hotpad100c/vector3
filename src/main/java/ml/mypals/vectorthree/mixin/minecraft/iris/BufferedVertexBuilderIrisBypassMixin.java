package ml.mypals.vectorthree.mixin.minecraft.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.BufferedVertexBuilder;
import ml.mypals.vectorthree.mc.render.IrisBypassTarget;
import ml.mypals.vectorthree.mc.render.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;


// The draw takes its attachments from RenderSystem's output override when one is set.
@Mixin(value = BufferedVertexBuilder.class, remap = false)
public class BufferedVertexBuilderIrisBypassMixin {
    @WrapMethod(method = "draw")
    private void vector3$drawIntoBypassTarget(Vec3 cameraPos, Operation<Void> original) {
        IrisBypassTarget.beginIrisBypass();
        try {
            RenderTargets.drawInto(IrisBypassTarget.isBypassing()
                    ? IrisBypassTarget.targetFor(Minecraft.getInstance().gameRenderer.mainRenderTarget(), "BufferedVertexBuilder")
                    : null, () -> original.call(cameraPos));
        } finally {
            IrisBypassTarget.endIrisBypass();
        }
    }
}
