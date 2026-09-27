package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.flashback.TrackManagement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackManagementMixin implements TrackManagement.Holder {
    @Unique private boolean vector3$locked;
    @Unique private boolean vector3$solo;
    @Unique private boolean vector3$collapsed;
    @Unique private String vector3$folder;

    @Override public boolean vector3$locked() { return vector3$locked; }
    @Override public void vector3$setLocked(boolean value) { vector3$locked = value; }
    @Override public boolean vector3$solo() { return vector3$solo; }
    @Override public void vector3$setSolo(boolean value) { vector3$solo = value; }
    @Override public boolean vector3$collapsed() { return vector3$collapsed; }
    @Override public void vector3$setCollapsed(boolean value) { vector3$collapsed = value; }
    @Override public String vector3$folder() { return vector3$folder; }
    @Override public void vector3$setFolder(String value) { vector3$folder = value; }

    @Inject(method = "createKeyframeChange", at = @At("HEAD"), cancellable = true)
    private void vector3$applySolo(float tick, RealTimeMapping mapping, CallbackInfoReturnable<KeyframeChange> cir) {
        if (!TrackManagement.audible((KeyframeTrack) (Object) this)) cir.setReturnValue(null);
    }
}
