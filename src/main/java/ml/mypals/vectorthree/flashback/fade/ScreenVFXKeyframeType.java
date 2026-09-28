package ml.mypals.vectorthree.flashback.fade;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiColorEditFlags;
import imgui.moulberry90.flag.ImGuiSliderFlags;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.flashback.FileBrowse;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingEditor;
import ml.mypals.vectorthree.flashback.fade.effects.BloomEditor;
import net.minecraft.client.resources.language.I18n;

public final class ScreenVFXKeyframeType extends CustomKeyframeType<ScreenVFX> {
    public static final ScreenVFXKeyframeType INSTANCE = new ScreenVFXKeyframeType();

    private ScreenVFXKeyframeType() {
        // Keep the old ID so existing Fade tracks load as Screen VFX tracks.
        super("vector3_fade", "vector3.keyframe_type.screen_vfx", ScreenVFX.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.FADE_TRACK, null); }
    @Override protected ScreenVFX createValue() { return ScreenVFX.defaults(); }
    @Override protected ScreenVFX sanitize(ScreenVFX value) { return value.sanitized(); }

    @Override
    public KeyframeCreatePopup<CustomKeyframe<ScreenVFX>> createPopup() {
        String[] selected = {ScreenVFX.GRADE};
        return () -> {
            if (ImGui.beginCombo(I18n.get("vector3.vfx.effect"), effectLabel(selected[0]))) {
                for (String effect : ScreenVFX.EFFECTS) {
                    if (ImGui.selectable(effectLabel(effect), selected[0].equals(effect))) selected[0] = effect;
                }
                ImGui.endCombo();
            }
            if (ImGui.button(I18n.get("vector3.keyframe_type.add")) || ReplayUI.consumeConfirm())
                return newKeyframe(ScreenVFX.defaults(selected[0]), InterpolationType.getDefault());
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.keyframe_type.cancel")) || ReplayUI.consumeCancel())
                ImGui.closeCurrentPopup();
            return null;
        };
    }

    private static String effectLabel(String effect) {
        return I18n.get("vector3.vfx.effect." + effect);
    }

    @Override
    protected ScreenVFX edit(ScreenVFX value) {
        if (ImGui.beginCombo(I18n.get("vector3.vfx.effect"), effectLabel(value.effect()))) {
            for (String effect : ScreenVFX.EFFECTS) {
                if (ImGui.selectable(effectLabel(effect), value.effect().equals(effect))) {
                    ImGui.endCombo();
                    return ScreenVFX.defaults(effect);
                }
            }
            ImGui.endCombo();
        }
        float[] colour = {value.red(), value.green(), value.blue()};
        float[] opacity = {value.opacity()};
        float[] vignette = {value.vignette()}, vignetteRadius = {value.vignetteRadius()};
        float[] dofStrength = {value.dofStrength()}, focusDistance = {value.focusDistance()};
        float[] focusRange = {value.focusRange()}, lutStrength = {value.lutStrength()};
        ImInt dofMode = new ImInt(value.dofMode());
        ImString lut = new ImString(value.lut(), 512);
        boolean changed = false;
        ScreenVFX editedSettings = value;
        if (value.applies(ScreenVFX.GRADE)) {
            var grading = ColorGradingEditor.edit(value.grading());
            if (grading != value.grading()) editedSettings = editedSettings.withGrading(grading);
        }
        if (value.effect().equals(ScreenVFX.BLOOM)) {
            var bloom = BloomEditor.edit(value.bloom());
            if (bloom != value.bloom()) editedSettings = editedSettings.withBloom(bloom);
        }
        if (value.applies(ScreenVFX.DOF)) {
            changed |= ImGui.combo(I18n.get("vector3.vfx.dof_mode"), dofMode, new String[]{
                    I18n.get("vector3.vfx.dof_mode.both"), I18n.get("vector3.vfx.dof_mode.near"),
                    I18n.get("vector3.vfx.dof_mode.far")});
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.dof_strength"), dofStrength, 0, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.focus_depth"), focusDistance, 0.1f, 512, "%.2f",
                    ImGuiSliderFlags.Logarithmic);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.focus_tip"));
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.focus_range"), focusRange, 0, 128, "%.2f",
                    ImGuiSliderFlags.Logarithmic);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.focus_range_tip"));
            ImGui.checkbox(I18n.get("vector3.vfx.focus_overlay"), FocusPlaneGizmo.OVERLAY);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.focus_overlay_tip"));
        }
        if (value.applies(ScreenVFX.VIGNETTE)) {
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.vignette_strength"), vignette, 0, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.vignette_radius"), vignetteRadius, 0, 1);
        }
        if (value.applies(ScreenVFX.LUT)) {
            if (ImGui.beginCombo(I18n.get("vector3.vfx.lut_preset"),
                    value.lut().isBlank() ? I18n.get("vector3.vfx.lut_none") : value.lut())) {
                if (ImGui.selectable(I18n.get("vector3.vfx.lut_none"), lut.get().isBlank())) {
                    lut.set(""); changed = true;
                }
                for (String preset : new String[]{"neutral", "warm", "cool", "teal_orange"}) {
                    String id = "vector3:textures/lut/" + preset + ".png";
                    if (ImGui.selectable(I18n.get("vector3.vfx.lut_" + preset), id.equals(lut.get()))) {
                        lut.set(id); changed = true;
                    }
                }
                ImGui.endCombo();
            }
            changed |= ImGui.inputText(I18n.get("vector3.vfx.lut_texture"), lut);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.lut_tip"));
            changed |= FileBrowse.button("lut", lut, I18n.get("vector3.file.image_filter"),
                    "png", "jpg", "jpeg", "bmp", "webp", "tif", "tiff", "tga");
            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.lut_strength"), lutStrength, 0, 1);
        }
        if (value.applies(ScreenVFX.FADE)) {
            changed |= ImGui.colorEdit3(I18n.get("vector3.fade.colour"), colour, ImGuiColorEditFlags.NoInputs);
            ImGui.sameLine();
            if (ImGui.smallButton(I18n.get("vector3.fade.black"))) { colour = new float[]{0, 0, 0}; changed = true; }
            ImGui.sameLine();
            if (ImGui.smallButton(I18n.get("vector3.fade.white"))) { colour = new float[]{1, 1, 1}; changed = true; }
            changed |= ImGui.sliderFloat(I18n.get("vector3.fade.opacity"), opacity, 0, 1);
        }
        return changed ? new ScreenVFX(ScreenVFX.VERSION, colour[0], colour[1], colour[2], opacity[0],
                value.exposure(), value.contrast(), value.saturation(), value.temperature(), vignette[0], vignetteRadius[0],
                dofStrength[0], Math.max(0.01f, focusDistance[0]), Math.max(0, focusRange[0]), dofMode.get(), lutStrength[0], lut.get(),
                value.effect(), editedSettings.grading(), editedSettings.bloom()) : editedSettings;
    }

    @Override protected ScreenVFX lerp(ScreenVFX from, ScreenVFX to, double amount) {
        return from.lerp(to, (float) amount);
    }

    @Override protected void apply(ScreenVFX value, KeyframeHandler handler) {
        ScreenVFXRenderer.request(value);
    }
}
