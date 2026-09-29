package ml.mypals.vectorthree.fb.pose;

import ml.mypals.vectorthree.mc.pose.EntityParts;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Picks one of an entity's model parts, or none (null). */
public final class ModelPartCombo {
    private ModelPartCombo() {}

    public static @Nullable String render(String label, @Nullable Entity entity, @Nullable String current) {
        String preview = current == null ? I18n.get("vector3.mount.model_part.none") : EntityPoseKeyframeType.label(current);
        String result = current;
        if (ImGui.beginCombo(label, preview)) {
            if (ImGui.selectable(I18n.get("vector3.mount.model_part.none"), current == null)) result = null;
            List<String> names = EntityParts.names(entity);
            if (names.isEmpty()) ImGui.textDisabled(I18n.get("vector3.mount.model_part.unavailable"));
            for (String name : names) {
                if (ImGui.selectable(EntityPoseKeyframeType.label(name) + "##" + name, name.equals(current))) result = name;
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.mount.model_part.tooltip"));
        return result;
    }
}
