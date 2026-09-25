package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.prefab.PrefabGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Keyframe.class, remap = false)
public class KeyframeGroupMixin implements PrefabGroupHolder.Keyframe {
    @Unique private transient String vector3$group;

    @Override public String vector3$group() { return vector3$group; }
    @Override public void vector3$setGroup(String id) { vector3$group = id; }
}
