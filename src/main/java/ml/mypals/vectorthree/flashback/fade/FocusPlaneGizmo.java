package ml.mypals.vectorthree.flashback.fade;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.CameraPreview;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.UUID;

/**
 * The focus plane of the selected depth-of-field keyframe. Seen through the shot camera it is drawn by the
 * DoF composite as an overlay; with the main view detached it is a plane in the world in front of the shot camera.
 */
public final class FocusPlaneGizmo {
    public static final ImBoolean OVERLAY = new ImBoolean(true);

    private static final Color FRAME = new Color(70, 225, 255, 230);
    private static final Color GRID = new Color(255, 210, 60, 235);
    private static final Color RANGE = new Color(70, 225, 255, 110);
    private static final Color RAY = new Color(255, 255, 255, 90);
    private static final int DIVISIONS = 8;
    private static final int FRAME_START = 0, GRID_START = 4, RANGE_START = GRID_START + (DIVISIONS - 1) * 2,
            RAY_START = RANGE_START + 8, COUNT = RAY_START + 4;

    private final String session = UUID.randomUUID().toString();
    private final LineShape[] lines = new LineShape[COUNT];
    private CustomKeyframe<ScreenVFX> selected;

    public void select(CustomKeyframe<ScreenVFX> keyframe) {
        selected = keyframe;
    }

    public void clearSelection() {
        if (selected != null) clear();
    }

    public void clear() {
        selected = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null) lines[i].discard();
            lines[i] = null;
        }
        ShapeManagers.removeShapes(Vector3.id("focus_plane_gizmo/" + session));
    }

    public boolean overlayActive() {
        return OVERLAY.get() && editing() != null;
    }

    public void beforeFrame() {
        if (editing() == null) hide();
    }

    public void frame() {
        ScreenVFX value = editing();
        CameraPreview.Shot shot = CameraPreview.shot();
        if (value == null || shot == null) {
            hide();
            return;
        }
        Vec3 forward = shot.forward().normalize();
        Vec3 up = shot.up().normalize();
        Vec3 right = forward.cross(up).normalize();
        double tan = Math.tan(Math.toRadians(shot.fov()) * 0.5);
        double distance = value.focusDistance(), half = value.focusRange() * 0.5;
        ensureLines();

        Vec3[] plane = rectangle(shot.eye(), forward, right, up, distance, tan, shot.aspect());
        for (int i = 0; i < 4; i++) segment(FRAME_START + i, plane[i], plane[(i + 1) % 4]);
        for (int i = 1; i < DIVISIONS; i++) {
            double t = (double) i / DIVISIONS;
            segment(GRID_START + (i - 1) * 2, lerp(plane[0], plane[1], t), lerp(plane[3], plane[2], t));
            segment(GRID_START + (i - 1) * 2 + 1, lerp(plane[0], plane[3], t), lerp(plane[1], plane[2], t));
        }

        boolean showRange = half > 0.01;
        Vec3[] near = rectangle(shot.eye(), forward, right, up, Math.max(0.05, distance - half), tan, shot.aspect());
        Vec3[] far = rectangle(shot.eye(), forward, right, up, distance + half, tan, shot.aspect());
        for (int i = 0; i < 4; i++) {
            segment(RANGE_START + i, near[i], near[(i + 1) % 4]);
            segment(RANGE_START + 4 + i, far[i], far[(i + 1) % 4]);
            segment(RAY_START + i, shot.eye(), far[i]);
        }
        for (int i = RANGE_START; i < RAY_START; i++) {
            if (showRange) lines[i].enable();
            else lines[i].disable();
        }
    }

    private ScreenVFX editing() {
        if (selected == null || !ReplayUI.isActive() || Flashback.isExporting()) return null;
        ScreenVFX value = selected.value;
        return value != null && value.applies(ScreenVFX.DOF) ? value : null;
    }

    private static Vec3[] rectangle(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double distance,
                                    double tan, double aspect) {
        Vec3 center = eye.add(forward.scale(distance));
        Vec3 y = up.scale(distance * tan), x = right.scale(distance * tan * aspect);
        return new Vec3[]{center.subtract(x).subtract(y), center.add(x).subtract(y),
                center.add(x).add(y), center.subtract(x).add(y)};
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    private void ensureLines() {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null) {
                boolean grid = i >= GRID_START && i < RANGE_START;
                Color color = i < GRID_START ? FRAME : grid ? GRID : i < RAY_START ? RANGE : RAY;
                lines[i] = (LineShape) ShapeGenerator.generateLine()
                        .start(Vec3.ZERO).end(new Vec3(0, 1, 0))
                        .lineWidth(i < GRID_START ? 2.5f : grid ? 1.5f : 1f)
                        .color(color).seeThrough(!grid)
                        .build(Shape.RenderingType.BATCH);
                ShapeManagers.addShape(Vector3.id("focus_plane_gizmo/" + session + "/" + i), lines[i]);
            }
            lines[i].enable();
        }
    }

    private void segment(int index, Vec3 start, Vec3 end) {
        lines[index].forceSetStart(start);
        lines[index].forceSetEnd(end);
    }

    private void hide() {
        for (LineShape line : lines) if (line != null) line.disable();
    }
}
