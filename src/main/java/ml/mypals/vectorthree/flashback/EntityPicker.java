package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A combo of the loaded entities, nearest first, searchable by name, type or UUID. */
public final class EntityPicker {
    private static final int MAX_LISTED = 200;
    private static final ImString FILTER = new ImString(64);

    private EntityPicker() {}

    /** Returns the picked entity's UUID, or {@code current} when nothing was picked this frame. */
    public static @Nullable UUID combo(String label, @Nullable UUID current) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Entity selected = level == null || current == null ? null : level.getEntity(current);
        String preview = selected != null ? describe(selected, camera)
                : current != null ? current.toString() : I18n.get("vector3.entity_picker.none");

        UUID result = current;
        if (!ImGui.beginCombo(label, preview)) {
            UUID dropped = Eyedropper.entity(label);
            return dropped != null ? dropped : result;
        }
        if (ImGui.isWindowAppearing()) FILTER.set("");
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##entity_filter", I18n.get("vector3.entity_picker.search"), FILTER);
        String filter = FILTER.get().trim().toLowerCase(Locale.ROOT);

        UUID typed = parseUuid(filter);
        if (typed != null && ImGui.selectable(I18n.get("vector3.entity_picker.use_uuid", typed))) result = typed;

        if (level != null) {
            List<Entity> entities = new ArrayList<>();
            // The local player is Flashback's replay camera, not a recorded entity.
            for (Entity entity : level.entitiesForRendering()) {
                if (entity != minecraft.player) entities.add(entity);
            }
            entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(camera)));
            int listed = 0;
            for (Entity entity : entities) {
                String text = describe(entity, camera);
                if (!filter.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(filter)
                        && !entity.getStringUUID().startsWith(filter)) continue;
                if (listed++ >= MAX_LISTED) break;
                // The distance changes every frame, so the ID comes from the UUID alone.
                if (ImGui.selectable(text + "###" + entity.getStringUUID(), entity.getUUID().equals(current))) {
                    result = entity.getUUID();
                }
            }
            if (listed == 0 && typed == null) ImGui.textDisabled(I18n.get("vector3.entity_picker.empty"));
        }
        ImGui.endCombo();
        UUID dropped = Eyedropper.entity(label);
        return dropped != null ? dropped : result;
    }

    /** Draws the picker in place of a UUID text field; writes the pick into {@code uuidText}. */
    public static boolean pickInto(String label, ImString uuidText) {
        UUID current = parseUuid(uuidText.get().trim());
        UUID picked = combo(label, current);
        if (picked == null || Objects.equals(picked, current)) return false;
        uuidText.set(picked.toString());
        return true;
    }

    private static String describe(Entity entity, Vec3 camera) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return String.format(Locale.ROOT, "%s [%s] %.1fm", entity.getName().getString(),
                type.getPath(), Math.sqrt(entity.distanceToSqr(camera)));
    }

    private static @Nullable UUID parseUuid(String text) {
        if (text.length() != 36) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
