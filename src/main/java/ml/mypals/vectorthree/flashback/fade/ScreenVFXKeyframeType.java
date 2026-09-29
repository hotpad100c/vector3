package ml.mypals.vectorthree.flashback.fade;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiColorEditFlags;
import imgui.moulberry90.flag.ImGuiSliderFlags;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.flashback.FileBrowse;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingEditor;
import ml.mypals.vectorthree.flashback.fade.effects.BloomEditor;
import ml.mypals.vectorthree.flashback.fade.effects.DepthOfFieldEffect;
import ml.mypals.vectorthree.flashback.fade.effects.DofEditor;
import ml.mypals.vectorthree.flashback.fade.effects.DofSettings;
import ml.mypals.vectorthree.flashback.fade.effects.GrainEditor;
import ml.mypals.vectorthree.flashback.fade.effects.PixelationEditor;
import ml.mypals.vectorthree.flashback.fade.effects.LensEditor;
import ml.mypals.vectorthree.flashback.fade.effects.AdditionalEffectsEditor;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        List<String> list = new ArrayList<>(value.effects());
        int up = -1, down = -1, remove = -1;
        float[] colour = {value.red(), value.green(), value.blue()};
        float[] opacity = {value.opacity()};
        float[] vignette = {value.vignette()}, vignetteRadius = {value.vignetteRadius()};
        float[] dofStrength = {value.dofStrength()}, focusDistance = {value.focusDistance()};
        float[] focusRange = {value.focusRange()}, lutStrength = {value.lutStrength()};
        ImInt dofMode = new ImInt(value.dofMode());
        ImString lut = new ImString(value.lut(), 512);
        boolean changed = false;
        ScreenVFX editedSettings = value;
        for (int i = 0; i < list.size(); i++) {
            String kind = list.get(i);
            ImGui.pushID("vfx_" + kind);
            ImGui.beginDisabled(i == 0);
            if (ImGui.smallButton("^")) up = i;
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.beginDisabled(i == list.size() - 1);
            if (ImGui.smallButton("v")) down = i;
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.smallButton("x")) remove = i;
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.remove_effect"));
            ImGui.sameLine();
            if (ImGui.collapsingHeader(effectLabel(kind), ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                switch (kind) {
                    case ScreenVFX.GRADE -> {
                        var grading = ColorGradingEditor.edit(editedSettings.grading());
                        if (grading != editedSettings.grading()) editedSettings = editedSettings.withGrading(grading);
                    }
                    case ScreenVFX.BLOOM -> {
                        var bloom = BloomEditor.edit(editedSettings.bloom());
                        if (bloom != editedSettings.bloom()) editedSettings = editedSettings.withBloom(bloom);
                    }
                    case ScreenVFX.GRAIN -> {
                        var grain = GrainEditor.edit(editedSettings.grain());
                        if (grain != editedSettings.grain()) editedSettings = editedSettings.withGrain(grain);
                    }
                    case ScreenVFX.PIXELATION -> {
                        var pixelation = PixelationEditor.edit(editedSettings.pixelation());
                        if (pixelation != editedSettings.pixelation())
                            editedSettings = editedSettings.withPixelation(pixelation);
                    }
                    case ScreenVFX.LENS -> {
                        var lens = LensEditor.edit(editedSettings.lens());
                        if (lens != editedSettings.lens()) editedSettings = editedSettings.withLens(lens);
                    }
                    case ScreenVFX.DOF -> {
                        changed |= ImGui.combo(I18n.get("vector3.vfx.dof_mode"), dofMode, new String[]{
                                I18n.get("vector3.vfx.dof_mode.both"), I18n.get("vector3.vfx.dof_mode.near"),
                                I18n.get("vector3.vfx.dof_mode.far"), I18n.get("vector3.vfx.dof_mode.distance")});
                        boolean distanceBlur = dofMode.get() == ScreenVFX.DOF_DISTANCE;
                        DofSettings dof = distanceBlur ? editedSettings.dof()
                                : DofEditor.autofocus(editedSettings.dof());
                        if (distanceBlur || !dof.autofocus()) {
                            changed |= ImGui.sliderFloat(I18n.get(distanceBlur ? "vector3.vfx.blur_distance"
                                    : "vector3.vfx.focus_depth"), focusDistance, 0.1f, 512, "%.2f",
                                    ImGuiSliderFlags.Logarithmic);
                            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get(distanceBlur
                                    ? "vector3.vfx.blur_distance_tip" : "vector3.vfx.focus_tip"));
                        }
                        if (!distanceBlur) {
                            changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.focus_range"), focusRange, 0, 128,
                                    "%.2f", ImGuiSliderFlags.Logarithmic);
                            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.focus_range_tip"));
                        }
                        changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.dof_strength"), dofStrength, 0,
                                DepthOfFieldEffect.MAX_STRENGTH, "%.2f");
                        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.dof_strength_tip"));
                        dof = DofEditor.lens(dof);
                        if (dof != editedSettings.dof()) editedSettings = editedSettings.withDof(dof);
                        ImGui.checkbox(I18n.get("vector3.vfx.focus_overlay"), FocusPlaneGizmo.OVERLAY);
                        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.vfx.focus_overlay_tip"));
                    }
                    case ScreenVFX.VIGNETTE -> {
                        changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.vignette_strength"), vignette, 0, 1);
                        changed |= ImGui.sliderFloat(I18n.get("vector3.vfx.vignette_radius"), vignetteRadius, 0, 1);
                    }
                    case ScreenVFX.LUT -> {
                        if (ImGui.beginCombo(I18n.get("vector3.vfx.lut_preset"),
                                value.lut().isBlank() ? I18n.get("vector3.vfx.lut_none") : value.lut())) {
                            if (ImGui.selectable(I18n.get("vector3.vfx.lut_none"), lut.get().isBlank())) {
                                lut.set("");
                                changed = true;
                            }
                            for (String preset : new String[]{"neutral", "warm", "cool", "teal_orange"}) {
                                String id = "vector3:textures/lut/" + preset + ".png";
                                if (ImGui.selectable(I18n.get("vector3.vfx.lut_" + preset), id.equals(lut.get()))) {
                                    lut.set(id);
                                    changed = true;
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
                    case ScreenVFX.FADE -> {
                        changed |= ImGui.colorEdit3(I18n.get("vector3.fade.colour"), colour,
                                ImGuiColorEditFlags.NoInputs);
                        ImGui.sameLine();
                        if (ImGui.smallButton(I18n.get("vector3.fade.black"))) {
                            colour = new float[]{0, 0, 0};
                            changed = true;
                        }
                        ImGui.sameLine();
                        if (ImGui.smallButton(I18n.get("vector3.fade.white"))) {
                            colour = new float[]{1, 1, 1};
                            changed = true;
                        }
                        changed |= ImGui.sliderFloat(I18n.get("vector3.fade.opacity"), opacity, 0, 1);
                    }
                    default -> {
                        var additional = AdditionalEffectsEditor.edit(kind, editedSettings.additional());
                        if (additional != editedSettings.additional())
                            editedSettings = editedSettings.withAdditional(additional);
                    }
                }
                ImGui.unindent();
            }
            ImGui.popID();
        }

        String added = null;
        List<String> available = new ArrayList<>();
        for (String kind : ScreenVFX.EFFECTS) if (!list.contains(kind)) available.add(kind);
        if (!available.isEmpty()) {
            ImGui.separator();
            if (ImGui.beginCombo(I18n.get("vector3.vfx.add_effect"), I18n.get("vector3.vfx.add_effect.hint"))) {
                for (String kind : available) if (ImGui.selectable(effectLabel(kind))) added = kind;
                ImGui.endCombo();
            }
        }

        ScreenVFX result = editedSettings;
        if (changed) {
            ScreenVFX.Builder builder = editedSettings.toBuilder();
            builder.red = colour[0];
            builder.green = colour[1];
            builder.blue = colour[2];
            builder.opacity = opacity[0];
            builder.vignette = vignette[0];
            builder.vignetteRadius = vignetteRadius[0];
            builder.dofStrength = dofStrength[0];
            builder.focusDistance = Math.max(0.01f, focusDistance[0]);
            builder.focusRange = Math.max(0, focusRange[0]);
            builder.dofMode = dofMode.get();
            builder.lutStrength = lutStrength[0];
            builder.lut = lut.get();
            result = builder.build();
        }
        boolean reordered = false;
        if (up > 0) {
            Collections.swap(list, up, up - 1);
            reordered = true;
        } else if (down >= 0 && down < list.size() - 1) {
            Collections.swap(list, down, down + 1);
            reordered = true;
        } else if (remove >= 0) {
            list.remove(remove);
            reordered = true;
        }
        if (reordered) result = result.withEffects(list);
        if (added != null) result = result.withEffect(added);
        return result;
    }

    @Override protected ScreenVFX lerp(ScreenVFX from, ScreenVFX to, double amount) {
        return from.lerp(to, (float) amount);
    }

    @Override protected void apply(ScreenVFX value, KeyframeHandler handler) {
        ScreenVFXRenderer.request(value);
    }
}
