package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.DofSettings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiSliderFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resources.language.I18n;

public final class DofEditor {
    private static final int[] SHAPES = {0, 3, 4, 5, 6, 7, 8};

    private DofEditor() {}

    public static DofSettings autofocus(DofSettings value) {
        ImBoolean autofocus = new ImBoolean(value.autofocus());
        float[] smoothing = {value.autofocusSmoothing()};
        boolean changed = ImGui.checkbox(I18n.get("vector3.dof.autofocus"), autofocus);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.autofocus_tip"));
        if (autofocus.get()) {
            changed |= ImGui.sliderFloat(I18n.get("vector3.dof.autofocus_smoothing"), smoothing, 0.01f, 5, "%.2f s",
                    ImGuiSliderFlags.Logarithmic);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.autofocus_smoothing_tip"));
        }
        return changed ? new DofSettings(value.aperture(), autofocus.get(), Math.max(0.01f, smoothing[0]),
                value.shape(), value.samples(), value.rings(), value.rotation(), value.chromatic(),
                value.chromaticStrength(), value.anamorphic(), value.fovScaled(), value.tiltX(), value.tiltY()) : value;
    }

    public static DofSettings lens(DofSettings value) {
        float[] aperture = {value.aperture()}, samples = {value.samples()}, rotation = {value.rotation()};
        float[] tiltX = {value.tiltX()}, tiltY = {value.tiltY()}, chromaticStrength = {value.chromaticStrength()};
        int[] rings = {value.rings()};
        int shapeIndex = 0;
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i] == value.shape()) shapeIndex = i;
        ImInt shape = new ImInt(shapeIndex);
        ImBoolean chromatic = new ImBoolean(value.chromatic()), anamorphic = new ImBoolean(value.anamorphic());
        ImBoolean fovScaled = new ImBoolean(value.fovScaled());
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.dof.aperture"), aperture, 0.05f, 16, "%.2f",
                ImGuiSliderFlags.Logarithmic);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.aperture_tip"));
        if (ImGui.collapsingHeader(I18n.get("vector3.dof.shape_settings"))) {
            String[] names = new String[SHAPES.length];
            for (int i = 0; i < SHAPES.length; i++) names[i] = I18n.get("vector3.dof.shape." + SHAPES[i]);
            changed |= ImGui.combo(I18n.get("vector3.dof.shape"), shape, names);
            changed |= ImGui.sliderFloat(I18n.get("vector3.dof.samples"), samples, 1, 5, "%.2f");
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.samples_tip"));
            changed |= ImGui.sliderInt(I18n.get("vector3.dof.rings"), rings, 1, 8);
            changed |= ImGui.sliderFloat(I18n.get("vector3.dof.rotation"), rotation, 0, 360, "%.0f");
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.dof.lens_settings"))) {
            changed |= ImGui.checkbox(I18n.get("vector3.dof.chromatic"), chromatic);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.chromatic_tip"));
            if (chromatic.get()) {
                changed |= ImGui.sliderFloat(I18n.get("vector3.dof.chromatic_strength"), chromaticStrength, 0.05f, 8,
                        "%.2f", ImGuiSliderFlags.Logarithmic);
            }
            changed |= ImGui.checkbox(I18n.get("vector3.dof.anamorphic"), anamorphic);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.anamorphic_tip"));
            changed |= ImGui.checkbox(I18n.get("vector3.dof.fov_scaled"), fovScaled);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.fov_scaled_tip"));
            changed |= ImGui.sliderFloat(I18n.get("vector3.dof.tilt_x"), tiltX, -20, 20, "%.1f");
            changed |= ImGui.sliderFloat(I18n.get("vector3.dof.tilt_y"), tiltY, -20, 20, "%.1f");
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dof.tilt_tip"));
        }
        return changed ? new DofSettings(aperture[0], value.autofocus(), value.autofocusSmoothing(),
                SHAPES[shape.get()], samples[0],
                rings[0], rotation[0], chromatic.get(), Math.max(0.05f, chromaticStrength[0]), anamorphic.get(),
                fovScaled.get(), tiltX[0], tiltY[0])
                : value;
    }
}
