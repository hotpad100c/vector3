package ml.mypals.vectorthree.mixin.minecraft.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface QuadParticleAccessor {
    @Accessor("quadSize") float vector3$quadSize();
    @Accessor("quadSize") void vector3$setQuadSize(float value);
    @Accessor("rCol") float vector3$rCol();
    @Accessor("gCol") float vector3$gCol();
    @Accessor("bCol") float vector3$bCol();
    @Accessor("alpha") float vector3$alpha();
    @Accessor("alpha") void vector3$setAlpha(float value);
}
