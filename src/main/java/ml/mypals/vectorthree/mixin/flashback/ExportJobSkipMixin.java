package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.flashback.loop.Loop;
import ml.mypals.vectorthree.flashback.loop.LoopKeyframeType;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;

@Mixin(value = ExportJob.class, remap = false)
public class ExportJobSkipMixin {
    @WrapOperation(method = "doExport", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/exporting/ExportJob;calculateTicks(Lcom/moulberry/flashback/state/EditorState;IID)Ljava/util/List;"))
    private List<?> vector3$skipFrames(EditorState editorState, int startTick, int endTick, double fps,
            Operation<List<?>> original) {
        List<?> ticks = original.call(editorState, startTick, endTick, fps);
        List<Loop.Scope> loops = LoopKeyframeType.scopes(editorState);
        if (!loops.isEmpty()) ticks = vector3$loop(ticks, loops, editorState, startTick, endTick, fps, original);
        NavigableMap<Integer, Integer> scopes = SkipKeyframeType.scopes(editorState);
        if (scopes.isEmpty()) return ticks;
        ticks.removeIf(info -> {
            int tick = startTick + (int) ((ExportTickInfoAccessor) info).vector3$serverTick();
            return SkipKeyframeType.resolve(scopes, tick) != tick;
        });
        return ticks;
    }

    // Each repeat is a fresh run of calculateTicks over the scope. Server ticks go back to the scope's start, while
    // client ticks keep counting up so the client world keeps ticking through the repeat.
    @Unique
    private static List<?> vector3$loop(List<?> ticks, List<Loop.Scope> loops, EditorState editorState, int startTick,
            int endTick, double fps, Operation<List<?>> original) {
        if (ticks.isEmpty()) return ticks;
        double step = ticks.size() > 1 ? vector3$client(ticks.get(1)) - vector3$client(ticks.getFirst()) : 1;
        List<Object> result = new ArrayList<>();
        double offset = 0;
        int index = 0;
        for (Loop.Scope scope : loops) {
            if (scope.start() < startTick || scope.end() > endTick) continue;
            while (index < ticks.size() && startTick + (int) vector3$server(ticks.get(index)) < scope.end()) {
                vector3$add(result, ticks.get(index++), 0, offset);
            }
            if (result.isEmpty()) continue;
            for (int repeat = 0; repeat < scope.count(); repeat++) {
                List<?> copy = original.call(editorState, scope.start(), scope.end(), fps);
                copy.removeIf(info -> vector3$server(info) >= scope.end() - scope.start());
                if (copy.isEmpty()) break;
                double shift = vector3$client(result.getLast()) + step - vector3$client(copy.getFirst());
                for (Object info : copy) vector3$add(result, info, scope.start() - startTick, shift);
            }
            if (index < ticks.size()) offset = vector3$client(result.getLast()) + step - vector3$client(ticks.get(index));
        }
        while (index < ticks.size()) vector3$add(result, ticks.get(index++), 0, offset);
        return result;
    }

    @Unique
    private static void vector3$add(List<Object> result, Object info, double serverShift, double clientShift) {
        ExportTickInfoAccessor accessor = (ExportTickInfoAccessor) info;
        if (serverShift != 0) accessor.vector3$setServerTick(accessor.vector3$serverTick() + serverShift);
        if (clientShift != 0) accessor.vector3$setClientTick(accessor.vector3$clientTick() + clientShift);
        result.add(info);
    }

    @Unique
    private static double vector3$server(Object info) {
        return ((ExportTickInfoAccessor) info).vector3$serverTick();
    }

    @Unique
    private static double vector3$client(Object info) {
        return ((ExportTickInfoAccessor) info).vector3$clientTick();
    }
}
