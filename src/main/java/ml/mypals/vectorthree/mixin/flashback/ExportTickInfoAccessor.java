package ml.mypals.vectorthree.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob$TickInfo", remap = false)
public interface ExportTickInfoAccessor {
    @Accessor("serverTick") double vector3$serverTick();
    @Accessor("clientTick") double vector3$clientTick();
    @Mutable @Accessor("serverTick") void vector3$setServerTick(double tick);
    @Mutable @Accessor("clientTick") void vector3$setClientTick(double tick);
}
