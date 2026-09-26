package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.screen.select_replay.ReplaySelectionList;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import ml.mypals.vectorthree.clips.EmptyProject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// Picking the header row opens the file browser; its right half is the empty project button.
@Mixin(ReplaySelectionList.class)
public abstract class ReplayListEmptyProjectMixin {
    @Shadow @Final private SelectReplayScreen screen;

    @WrapOperation(method = "setSelected(Lcom/moulberry/flashback/screen/select_replay/ReplaySelectionEntry;)V",
            at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/Flashback;openReplayFromFileBrowser()V"))
    private void vector3$emptyProject(Operation<Void> original) {
        if (EmptyProject.hoveredInList) EmptyProject.request(screen);
        else original.call();
    }
}
