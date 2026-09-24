package ml.mypals.vectorthree.camera.lookto;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;


public final class LookToCamera {
    private static LookTo from;
    private static LookTo to;
    private static float amount;
    private static boolean pending;

    private LookToCamera() {}

    // The replay server also applies keyframes, on its own thread; only the render thread's pass is ours.
    public static void begin(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) pending = false;
    }

    public static void request(LookTo from, LookTo to, float amount) {
        LookToCamera.from = from;
        LookToCamera.to = to;
        LookToCamera.amount = amount;
        pending = true;
    }

    public static void finish(KeyframeHandler handler) {
        if (!(handler instanceof MinecraftKeyframeHandler) || !pending) return;
        pending = false;
        Minecraft minecraft = handler.getMinecraft();
        LocalPlayer player = minecraft.player;
        // While spectating an entity the camera isn't the player, same as Flashback's camera keyframes.
        if (player == null || minecraft.getCameraEntity() != player) return;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 start = from.target().resolve(partialTick);
        Vec3 end = to.target().resolve(partialTick);
        if (start == null) start = end;
        if (end == null) end = start;
        if (start == null) return;
        Vec3 delta = start.lerp(end, amount).subtract(player.getEyePosition(partialTick));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-6 && Math.abs(delta.y) < 1.0E-6) return;

        // Entity#lookAt's convention; keep yaw continuous with the current one.
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        yaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot());
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }
}
