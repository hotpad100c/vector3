package ml.mypals.vectorthree.camera.dolly;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class DollyZoomCamera {
    private static DollyZoom from;
    private static DollyZoom to;
    private static float amount;
    private static boolean pending;

    private DollyZoomCamera() {}

    public static void begin(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) pending = false;
    }

    public static void request(DollyZoom from, DollyZoom to, float amount) {
        DollyZoomCamera.from = from;
        DollyZoomCamera.to = to;
        DollyZoomCamera.amount = amount;
        pending = true;
    }

    public static void finish(KeyframeHandler handler) {
        if (!(handler instanceof MinecraftKeyframeHandler) || !pending) return;
        pending = false;
        Minecraft minecraft = handler.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getCameraEntity() != player) return;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 start = from.target().resolve(partialTick);
        Vec3 end = to.target().resolve(partialTick);
        if (start == null) start = end;
        if (end == null) end = start;
        if (start == null) return;
        DollyZoom.Pose pose = DollyZoom.pose(from, to, amount, start.lerp(end, amount));
        handler.applyCameraPosition(new Vector3d(pose.eye().x, pose.eye().y - player.getEyeHeight(), pose.eye().z),
                pose.yaw(), pose.pitch(), 0);
        handler.applyFov(pose.fov());
    }
}
