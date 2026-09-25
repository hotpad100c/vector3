package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiViewport;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.ViewportPick;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.List;
import java.util.UUID;

/**
 * An eyedropper button for entity and shape slots. Picking runs in a transparent popup opened from the
 * slot's own UI: a click on a child popup doesn't close its parent, so a keyframe popup stays open and the
 * pick comes back to the slot that asked for it, in the same frame. The target under the cursor is boxed.
 */
public final class Eyedropper {
    private static final String OVERLAY = "##vector3_eyedropper";
    private static final int OVERLAY_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoBackground
            | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoNav;
    private static final Identifier BOX_ID = Vector3.id("eyedropper_highlight");
    private static final Color BOX_COLOR = new Color(255, 210, 40, 255);
    private static final double BOX_INSET = 0.02;

    private static @Nullable BoxWireframeShape box;
    private static int boxFrame = -1;

    private enum Kind { ENTITY, SHAPE }

    private Eyedropper() {}

    /** Draws the button on the current line; returns the entity picked this frame, if any. */
    public static @Nullable UUID entity(String id) {
        return button(id, Kind.ENTITY) instanceof Entity entity ? entity.getUUID() : null;
    }

    /** Draws the button on the current line; returns the shape picked this frame, if any. */
    public static @Nullable String shape(String id) {
        return button(id, Kind.SHAPE) instanceof String shapeId ? shapeId : null;
    }

    /** Called once per frame after the UI: drops the highlight box once no picker drew it. */
    public static void endFrame() {
        if (box != null && boxFrame != ImGui.getFrameCount()) {
            box.discard();
            ShapeManagers.removeShapes(BOX_ID);
            box = null;
        }
    }

    private static @Nullable Object button(String id, Kind kind) {
        Object picked = null;
        ImGui.sameLine();
        ImGui.pushID("vector3_eyedropper_" + id);
        if (ImGui.button(I18n.get("vector3.eyedropper"))) ImGui.openPopup(OVERLAY);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get(kind == Kind.ENTITY
                ? "vector3.eyedropper.entity_tooltip" : "vector3.eyedropper.shape_tooltip"));
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        if (ImGui.beginPopup(OVERLAY, OVERLAY_FLAGS)) {
            Object hovered = hovered(kind);
            highlight(hovered);
            ImGui.setTooltip(hovered == null ? I18n.get("vector3.eyedropper.hint") : describe(hovered));
            if (ImGui.isMouseClicked(0)) {
                picked = hovered;
                ImGui.closeCurrentPopup();
            } else if (ImGui.isMouseClicked(1) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        ImGui.popID();
        return picked;
    }

    // ReplayUI.getMouseLookVector needs the viewport itself hovered, which the overlay covers.
    private static @Nullable Object hovered(Kind kind) {
        Vec2 mouse = ReplayUI.getMouseViewportFraction();
        if (mouse == null || mouse.x < 0 || mouse.x > 1 || mouse.y < 0 || mouse.y > 1) return null;
        Vec3 direction = ShapeGizmoEditor.unboundedMouseLookVector();
        if (direction == null) return null;
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (kind == Kind.SHAPE) return ShapeTrackRegistry.pickShape(new RayModelIntersection.Ray(eye, direction));
        ViewportPick.Hit hit = ViewportPick.pick(eye, direction);
        return hit == null ? null : hit.entity();
    }

    private static void highlight(@Nullable Object target) {
        AABB bounds = target instanceof Entity entity ? entityBounds(entity)
                : target instanceof String shapeId ? shapeBounds(shapeId) : null;
        if (target instanceof String shapeId) ShapeTrackRegistry.previewHighlight(shapeId);
        if (bounds == null) return;
        if (box == null) {
            box = ShapeGenerator.generateBoxWireframe()
                    .aabb(Vec3.ZERO, new Vec3(1, 1, 1))
                    .edgeWidth(1.5f)
                    .color(BOX_COLOR)
                    .seeThrough(true)
                    .build(Shape.RenderingType.BATCH);
            ShapeManagers.addShape(BOX_ID, box);
        }
        box.forceSetCorners(new Vec3(bounds.minX, bounds.minY, bounds.minZ), new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ));
        boxFrame = ImGui.getFrameCount();
    }

    private static AABB entityBounds(Entity entity) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position())).inflate(BOX_INSET);
    }

    private static @Nullable AABB shapeBounds(String shapeId) {
        Shape shape = ShapeTrackRegistry.shape(shapeId);
        ShapeState state = ShapeTrackRegistry.state(shapeId);
        if (shape == null || state == null) return null;
        List<Vec3> model = shape.getModel(false);
        if (model == null || model.isEmpty()) {
            Vector3f center = ShapeTrackRegistry.worldTransformOrIdentity(shapeId).getTranslation(new Vector3f());
            return new AABB(center.x, center.y, center.z, center.x, center.y, center.z).inflate(0.25);
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 point : model) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(BOX_INSET);
    }

    private static String describe(Object target) {
        if (target instanceof Entity entity) return entity.getName().getString() + "\n" + entity.getStringUUID();
        return ShapeTrackRegistry.displayName((String) target);
    }
}
