package ml.mypals.vectorthree.flashback.fade;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiColorEditFlags;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import net.minecraft.client.resources.language.I18n;

/** A full-screen colour laid over the final image: dip to black / white / any colour, blended between keyframes. */
public final class FadeKeyframeType extends CustomKeyframeType<Fade> {
    public static final FadeKeyframeType INSTANCE = new FadeKeyframeType();

    private FadeKeyframeType() {
        super("vector3_fade", "vector3.keyframe_type.fade", Fade.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.FADE_TRACK, null); }

    @Override protected Fade createValue() { return new Fade(0, 0, 0, 1); }

    @Override
    protected Fade edit(Fade value) {
        float[] colour = {value.red(), value.green(), value.blue()};
        float[] opacity = {value.opacity()};
        boolean changed = ImGui.colorEdit3(I18n.get("vector3.fade.colour"), colour, ImGuiColorEditFlags.NoInputs);
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.get("vector3.fade.black"))) { colour = new float[]{0, 0, 0}; changed = true; }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.get("vector3.fade.white"))) { colour = new float[]{1, 1, 1}; changed = true; }
        changed |= ImGui.sliderFloat(I18n.get("vector3.fade.opacity"), opacity, 0, 1);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.fade.opacity.tooltip"));
        return changed ? new Fade(colour[0], colour[1], colour[2], Math.clamp(opacity[0], 0, 1)) : value;
    }

    @Override
    protected Fade lerp(Fade from, Fade to, double amount) {
        return from.lerp(to, (float) amount);
    }

    @Override
    protected void apply(Fade value, KeyframeHandler handler) {
        FadeOverlay.request(value);
    }
}
