package ml.mypals.vectorthree.mixin.area;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x") double vector3$x();
    @Accessor("y") double vector3$y();
    @Accessor("z") double vector3$z();
    @Accessor("xo") double vector3$xo();
    @Accessor("yo") double vector3$yo();
    @Accessor("zo") double vector3$zo();
    @Accessor("x") void vector3$setX(double value);
    @Accessor("y") void vector3$setY(double value);
    @Accessor("z") void vector3$setZ(double value);
    @Accessor("xo") void vector3$setXo(double value);
    @Accessor("yo") void vector3$setYo(double value);
    @Accessor("zo") void vector3$setZo(double value);
    @Accessor("xd") double vector3$xd();
    @Accessor("yd") double vector3$yd();
    @Accessor("zd") double vector3$zd();
    @Accessor("xd") void vector3$setXd(double value);
    @Accessor("yd") void vector3$setYd(double value);
    @Accessor("zd") void vector3$setZd(double value);
    @Accessor("age") int vector3$age();
    @Accessor("gravity") void vector3$setGravity(float value);
    @Accessor("friction") void vector3$setFriction(float value);
    @Accessor("hasPhysics") void vector3$setHasPhysics(boolean value);
}
