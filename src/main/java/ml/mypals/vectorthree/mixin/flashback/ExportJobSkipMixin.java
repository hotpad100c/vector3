package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.NavigableMap;

@Mixin(value = ExportJob.class, remap = false)
public class ExportJobSkipMixin {
    @WrapOperation(method = "doExport", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/exporting/ExportJob;calculateTicks(Lcom/moulberry/flashback/state/EditorState;IID)Ljava/util/List;"))
    private List<?> vector3$skipFrames(EditorState editorState, int startTick, int endTick, double fps,
            Operation<List<?>> original) {
        List<?> ticks = original.call(editorState, startTick, endTick, fps);
        NavigableMap<Integer, Integer> scopes = SkipKeyframeType.scopes(editorState);
        if (scopes.isEmpty()) return ticks;
        ticks.removeIf(info -> {
            int tick = startTick + (int) ((ExportTickInfoAccessor) info).vector3$serverTick();
            return SkipKeyframeType.resolve(scopes, tick) != tick;
        });
        return ticks;
    }
}
