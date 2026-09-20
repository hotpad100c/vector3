package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import ml.mypals.vectorthree.shape.ShapeState;

import java.util.UUID;

public final class ShapeKeyframeType implements KeyframeType<ShapeKeyframe> {
    public static final String ID = "vector3_shape";
    public static final ShapeKeyframeType INSTANCE = new ShapeKeyframeType();

    private ShapeKeyframeType() {}

    public static void register() { KeyframeRegistry.register(INSTANCE); }
    @Override public Class<? extends KeyframeChange> keyframeChangeType() { return ShapeKeyframeChange.class; }
    @Override public boolean supportsHandler(KeyframeHandler handler) { return handler instanceof MinecraftKeyframeHandler; }
    @Override public boolean allowApplyingDuplicateKeyframeChanges() { return true; }
    @Override public String icon() { return "▣"; }
    @Override public String name() { return "RyansRenderingKit 图形"; }
    @Override public String id() { return ID; }

    @Override
    public ShapeKeyframe createDirect() {
        return new ShapeKeyframe(ShapeState.cube("vector3:timeline/" + UUID.randomUUID()));
    }

    @Override public KeyframeCreatePopup<ShapeKeyframe> createPopup() { return null; }
}
