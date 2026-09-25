package ml.mypals.vectorthree.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

// TimelineWindow's private record; reached through @Coerce local capture in TimelineWindowMixin.
@Mixin(targets = "com.moulberry.flashback.editor.ui.windows.TimelineWindow$GrabMovementInfo", remap = false)
public interface GrabMovementInfoAccessor {
    @Accessor("grabbedDelta") int vector3$delta();

    @Accessor("grabbedScalePivotTick") int vector3$scalePivot();

    @Accessor("grabbedScaleFactor") float vector3$scaleFactor();

    @Mutable @Accessor("grabbedDelta") void vector3$setDelta(int delta);

    @Mutable @Accessor("grabbedScalePivotTick") void vector3$setScalePivot(int pivot);
}
