package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistory;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import ml.mypals.vectorthree.prefab.PrefabHistory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

// Undo steps position back then applies entries[position]; redo applies entries[position] then steps forward.
@Mixin(value = EditorSceneHistory.class, remap = false)
public class EditorSceneHistoryMixin {
    @Shadow @Final private List<EditorSceneHistoryEntry> entries;
    @Shadow private int position;
    @Unique private int vector3$positionBefore;

    @Inject(method = {"undo", "redo"}, at = @At("HEAD"))
    private void vector3$rememberPosition(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        vector3$positionBefore = position;
    }

    @Inject(method = "undo", at = @At("RETURN"))
    private void vector3$restoreUndoneTracks(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        if (position < vector3$positionBefore) PrefabHistory.undone(scene, entries.get(position));
    }

    @Inject(method = "redo", at = @At("RETURN"))
    private void vector3$restoreRedoneTracks(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        if (position > vector3$positionBefore) PrefabHistory.redone(scene, entries.get(position - 1));
    }
}
