package ml.mypals.vectorthree.camera.lookto;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import ml.mypals.vectorthree.camera.target.Target;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One LookTo keyframe. From a keyframe that doesn't end the scope until the next keyframe, the camera
 * looks at a point blended from this keyframe's target toward the next one's; an ending keyframe
 * only supplies that final target. The target's fields stay flat so older saves still load.
 */
public record LookTo(boolean endsScope, Target.Kind kind, @Nullable UUID entity, TrackingBodyPart bodyPart,
                     double x, double y, double z, @Nullable String shapeId) {

    public static LookTo of(boolean endsScope, Target target) {
        return new LookTo(endsScope, target.kind(), target.entity(), target.bodyPart(), target.x(), target.y(),
                target.z(), target.shapeId());
    }

    public Target target() {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withTarget(Target target) {
        return of(endsScope, target);
    }

    public LookTo withEndsScope(boolean endsScope) {
        return of(endsScope, target());
    }
}
