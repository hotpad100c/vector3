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
import ml.mypals.vectorthree.flashback.fade.effects.DofSettings;
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
        DofSettings dof = value.dof();
        if (dof.autofocus() || value.dofMode() == ScreenVFX.DOF_DISTANCE) {
            hide();
            return;
        }
        Frustum frustum = new Frustum(shot);
        double half = value.focusRange() * 0.5;
        ensureLines();

        Vec3[] plane = frustum.rectangle(dof, value.focusDistance(), 0);
        for (int i = 0; i < 4; i++) segment(FRAME_START + i, plane[i], plane[(i + 1) % 4]);
        for (int i = 1; i < DIVISIONS; i++) {
            double t = (double) i / DIVISIONS;
            segment(GRID_START + (i - 1) * 2, frustum.at(dof, value.focusDistance(), 0, t, 0),
                    frustum.at(dof, value.focusDistance(), 0, t, 1));
            segment(GRID_START + (i - 1) * 2 + 1, frustum.at(dof, value.focusDistance(), 0, 0, t),
                    frustum.at(dof, value.focusDistance(), 0, 1, t));
        }

        boolean showRange = half > 0.01;
        Vec3[] near = frustum.rectangle(dof, value.focusDistance(), -half);
        Vec3[] far = frustum.rectangle(dof, value.focusDistance(), half);
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

    /** Points on the (possibly tilted) focus surface, by screen position u, v in [0, 1]. */
    private record Frustum(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double tan, double aspect) {
        Frustum(CameraPreview.Shot shot) {
            this(shot.eye(), shot.forward().normalize(), shot.forward().cross(shot.up()).normalize(),
                    shot.up().normalize(), Math.tan(Math.toRadians(shot.fov()) * 0.5), shot.aspect());
        }

        Vec3 at(DofSettings dof, double focus, double offset, double u, double v) {
            double distance = Math.max(0.05, dof.focusAt(focus, u, v) + offset);
            Vec3 ray = forward.add(right.scale((u * 2 - 1) * tan * aspect)).add(up.scale((v * 2 - 1) * tan));
            return eye.add(ray.scale(distance));
        }

        Vec3[] rectangle(DofSettings dof, double focus, double offset) {
            return new Vec3[]{at(dof, focus, offset, 0, 0), at(dof, focus, offset, 1, 0),
                    at(dof, focus, offset, 1, 1), at(dof, focus, offset, 0, 1)};
        }
    }

    private ScreenVFX editing() {
        if (selected == null || !ReplayUI.isActive() || Flashback.isExporting()) return null;
        ScreenVFX value = selected.value;
        return value != null && value.applies(ScreenVFX.DOF) ? value : null;
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
