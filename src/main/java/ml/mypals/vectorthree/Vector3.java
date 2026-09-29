package ml.mypals.vectorthree;

import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.fb.FlashbackBootstrap;
import ml.mypals.vectorthree.fb.timeline.Timeline;
import ml.mypals.vectorthree.mc.MinecraftBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** The mod entry: plugs the Minecraft side and the Flashback side in. Code shared by both lives in {@code core}. */
public class Vector3 implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MinecraftBootstrap.init();
		FlashbackBootstrap.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FlashbackBootstrap.onDisconnect();
			MinecraftBootstrap.onDisconnect();
		});
		// Mixins into Flashback normally apply only when their target loads; this loads them all so a broken one shows at startup.
		if (Boolean.getBoolean("vector3.mixinAudit")) {
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
				MixinEnvironment.getCurrentEnvironment().audit();
				Mod.LOGGER.info("Mixin audit passed; timeline accessors read editingTrack={} mouseX={} scene={}",
						Timeline.editingTrack(), Timeline.mouseX(), Timeline.scene());
			});
		}
		Mod.LOGGER.info("Registered RyansRenderingKit shape tracks with Flashback");
	}
}
