package ml.mypals.vectorthree;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import ml.mypals.vectorthree.camera.EditorCameraController;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;

public class Vector3 implements ClientModInitializer {
	public static final String MOD_ID = "vector3";
	public static final ShapeGizmoEditor GIZMO_EDITOR = new ShapeGizmoEditor();
	public static final EditorCameraController EDITOR_CAMERA = new EditorCameraController();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ShapeTrackRegistry.registerDefaults();
		ShapeKeyframeType.register();
		ShapeKeyframe.setEditor(GIZMO_EDITOR);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GIZMO_EDITOR.clear();
			EDITOR_CAMERA.reset();
			ShapeTrackRegistry.clear();
		});
		LOGGER.info("Registered RyansRenderingKit shape tracks with Flashback");
	}
	public static float[] skyOverrideColor() {
		EditorState editorState = EditorStateManager.getCurrent();
		if (editorState == null) {
			return null;
		}
		ReplayVisuals visuals = editorState.replayVisuals;
		if (visuals.renderSky) {
			return null;
		}
		return isTransparentExport() ? new float[]{0.0F, 0.0F, 0.0F} : visuals.skyColour;
	}

	@Unique
	public static boolean isTransparentExport() {
		return Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
