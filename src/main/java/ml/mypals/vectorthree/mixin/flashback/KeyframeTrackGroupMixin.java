package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.prefab.PrefabGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackGroupMixin implements PrefabGroupHolder.Track {
    @Unique private String vector3$prefabGroup;

    @Override public String vector3$prefabGroup() { return vector3$prefabGroup; }
    @Override public void vector3$setPrefabGroup(String id) { vector3$prefabGroup = id; }
}
