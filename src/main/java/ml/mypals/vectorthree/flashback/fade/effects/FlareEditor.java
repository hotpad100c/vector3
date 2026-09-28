package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class FlareEditor {
    private FlareEditor() {}
    public static FlareSettings edit(FlareSettings value) {
        float[] intensity = {value.intensity()}, threshold = {value.threshold()}, halo = {value.halo()};
        float[] chromatic = {value.chromatic()};
        int[] ghosts = {value.ghosts()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.flare.intensity"), intensity, 0, 3);
        changed |= ImGui.sliderFloat(I18n.get("vector3.flare.threshold"), threshold, 0, 1);
        changed |= ImGui.sliderInt(I18n.get("vector3.flare.ghosts"), ghosts, 1, 8);
        changed |= ImGui.sliderFloat(I18n.get("vector3.flare.halo"), halo, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.flare.chromatic"), chromatic, 0, 1);
        return changed ? new FlareSettings(intensity[0], threshold[0], ghosts[0], halo[0], chromatic[0]) : value;
    }
}
