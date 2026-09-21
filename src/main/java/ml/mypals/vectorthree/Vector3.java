package ml.mypals.vectorthree;

import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Vector3 implements ClientModInitializer {
	public static final String MOD_ID = "vector3";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ShapeGizmoEditor gizmoEditor = new ShapeGizmoEditor();
		ShapeTrackRegistry.registerDefaults();
		ShapeKeyframeType.register();
		ShapeKeyframe.setEditor(gizmoEditor);
		ClientTickEvents.END_CLIENT_TICK.register(client -> gizmoEditor.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			gizmoEditor.clear();
			ShapeTrackRegistry.clear();
		});
		LOGGER.info("Registered RyansRenderingKit shape tracks with Flashback");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
