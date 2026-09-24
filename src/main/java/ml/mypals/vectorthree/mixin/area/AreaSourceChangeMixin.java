package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientLevel.class)
public class AreaSourceChangeMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void vector3$markSourceDirty(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (!oldState.equals(newState)) AreaSuppression.markDirtyIfInside(pos);
    }
}
