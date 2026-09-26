package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.Flashback;
import ml.mypals.vectorthree.clips.ClipProject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

// Flashback keeps only the open archive's file system, but a project has to rewrite the archive file itself.
@Mixin(value = Flashback.class, remap = false)
public class FlashbackOpenReplayMixin {
    @Inject(method = "openReplayWorld", at = @At("HEAD"))
    private static void vector3$rememberReplay(Path path, CallbackInfo ci) {
        ClipProject.opened(path);
    }
}
