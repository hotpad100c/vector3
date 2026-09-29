package ml.mypals.vectorthree.fb;

import ml.mypals.vectorthree.fb.camera.dolly.DollyZoomKeyframeType;
import ml.mypals.vectorthree.fb.camera.lookto.LookToKeyframeType;
import ml.mypals.vectorthree.fb.channel.BuiltInChannels;
import ml.mypals.vectorthree.fb.clips.ClipCovers;
import ml.mypals.vectorthree.fb.clips.ClipKeyframeType;
import ml.mypals.vectorthree.fb.clips.EmptyProject;
import ml.mypals.vectorthree.fb.custom.CustomKeyframes;
import ml.mypals.vectorthree.fb.fade.ScreenVFXKeyframeType;
import ml.mypals.vectorthree.fb.loop.LoopKeyframeType;
import ml.mypals.vectorthree.fb.pose.EntityPoseKeyframeType;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframeType;
import ml.mypals.vectorthree.fb.skip.SkipKeyframeType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Everything vector3 plugs into Flashback: keyframe types, channels, and the ports core code calls back through. */
public final class FlashbackBootstrap {
    private FlashbackBootstrap() {}

    public static void init() {
        FlashbackPorts.install();
        ShapeKeyframeType.register();
        CustomKeyframes.register(LookToKeyframeType.INSTANCE);
        CustomKeyframes.register(SkipKeyframeType.INSTANCE);
        CustomKeyframes.register(LoopKeyframeType.INSTANCE);
        CustomKeyframes.register(ScreenVFXKeyframeType.INSTANCE);
        CustomKeyframes.register(EntityPoseKeyframeType.INSTANCE);
        CustomKeyframes.register(DollyZoomKeyframeType.INSTANCE);
        CustomKeyframes.register(ClipKeyframeType.INSTANCE);
        BuiltInChannels.register();
        ClientTickEvents.END_CLIENT_TICK.register(EmptyProject::tick);
        ShapeKeyframe.setEditor(Editors.GIZMO_EDITOR);
    }

    public static void onDisconnect() {
        Editors.clearAll();
        ClipCovers.clear();
    }
}
