package ml.mypals.vectorthree;

import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.fb.FlashbackBootstrap;
import ml.mypals.vectorthree.fb.timeline.Timeline;
import ml.mypals.vectorthree.mc.MinecraftBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class Vector3 implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MinecraftBootstrap.init();
		FlashbackBootstrap.init();
		// The event fires on a network thread, but cleanup frees GPU resources, which only the render thread may.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			guard("Flashback cleanup", FlashbackBootstrap::onDisconnect);
			guard("Minecraft cleanup", MinecraftBootstrap::onDisconnect);
		}));
		if (Boolean.getBoolean("vector3.mixinAudit")) {
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
				MixinEnvironment.getCurrentEnvironment().audit();
				Mod.LOGGER.info("Mixin audit passed; timeline accessors read editingTrack={} mouseX={} scene={}",
						Timeline.editingTrack(), Timeline.mouseX(), Timeline.scene());
			});
		}
	}

	private static void guard(String what, Runnable cleanup) {
		try {
			cleanup.run();
		} catch (RuntimeException exception) {
			Mod.LOGGER.warn("{} failed on disconnect", what, exception);
		}
	}
}
