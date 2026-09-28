package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.flashback.FileBrowse;
import net.minecraft.client.resources.language.I18n;

import static ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;

public final class BloomEditor {
    private BloomEditor() {}

    public static BloomSettings edit(BloomSettings value) {
        float[] intensity = {value.intensity()}, threshold = {value.threshold()};
        float[] softKnee = {value.softKnee()}, radius = {value.radius()}, clamp = {value.clamp()};
        float[] tint = {value.tint().r(), value.tint().g(), value.tint().b()};
        float[] dirtIntensity = {value.dirtIntensity()};
        ImBoolean antiFlicker = new ImBoolean(value.antiFlicker());
        ImBoolean highQuality = new ImBoolean(value.highQuality());
        ImString dirtTexture = new ImString(value.dirtTexture(), 512);
        boolean changed = false;
        if (ImGui.collapsingHeader(I18n.get("vector3.bloom.main"))) {
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.intensity"), intensity, 0, 5);
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.threshold"), threshold, 0, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.soft_knee"), softKnee, 0, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.radius"), radius, 0, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.clamp"), clamp, 0.01f, 4);
            changed |= ImGui.colorEdit3(I18n.get("vector3.bloom.tint"), tint);
            changed |= ImGui.checkbox(I18n.get("vector3.bloom.anti_flicker"), antiFlicker);
            changed |= ImGui.checkbox(I18n.get("vector3.bloom.high_quality"), highQuality);
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.bloom.lens_dirt"))) {
            changed |= ImGui.inputText(I18n.get("vector3.bloom.dirt_texture"), dirtTexture);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.bloom.dirt_tip"));
            changed |= FileBrowse.button("bloom_dirt", dirtTexture, I18n.get("vector3.file.image_filter"),
                    "png", "jpg", "jpeg", "bmp", "webp", "tif", "tiff", "tga");
            changed |= ImGui.sliderFloat(I18n.get("vector3.bloom.dirt_intensity"), dirtIntensity, 0, 5);
        }
        return changed ? new BloomSettings(intensity[0], threshold[0], softKnee[0], radius[0],
                clamp[0], antiFlicker.get(), highQuality.get(),
                new Rgb(tint[0], tint[1], tint[2]), dirtTexture.get(), dirtIntensity[0]) : value;
    }
}
