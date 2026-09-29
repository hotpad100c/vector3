package ml.mypals.vectorthree.mc;

import ml.mypals.vectorthree.mc.compat.IrisCompat;
import ml.mypals.vectorthree.mc.fade.ScreenVFXRenderer;
import ml.mypals.vectorthree.mc.pose.EntityPoses;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.mc.text.FontOptions;
import ml.mypals.vectorthree.mc.text.SdfFont;

/** The Minecraft side's startup and teardown. */
public final class MinecraftBootstrap {
    private MinecraftBootstrap() {}

    public static void init() {
        FontOptions.ensureFontFolder();
        ShapeTrackRegistry.registerDefaults();
        IrisCompat.registerObjPbr();
    }

    public static void onDisconnect() {
        ShapeTrackRegistry.clear();
        ScreenVFXRenderer.clear();
        EntityPoses.clear();
        SdfFont.clearCache();
    }
}
