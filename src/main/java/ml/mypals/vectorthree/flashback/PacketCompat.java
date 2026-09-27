package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.Packet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Force-compatibility mode: packets Flashback can't replay (e.g. minecraft:transfer) are skipped instead of aborting
 * the replay. On by default; the choice is kept in Flashback's config through the opened-windows set, where the
 * marker means "strict" (off).
 */
public final class PacketCompat {
    private static final PersistentWindow STRICT = new PersistentWindow("vector3_strict_packets");
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private PacketCompat() {}

    public static boolean enabled() {
        return !STRICT.isOpen();
    }

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.packet_compat"), "", enabled())) STRICT.toggle();
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.packet_compat.tooltip"));
    }

    /** Logged once per kind: the action's name, with what went wrong. */
    public static void skippedAction(String action, @org.jetbrains.annotations.Nullable RuntimeException exception) {
        String kind = action.replaceAll(" \\(\\d+ unread bytes\\)", " (unread bytes)");
        if (!reported.add(kind)) return;
        if (exception == null) Vector3.LOGGER.warn("Force compatibility: skipped the rest of replay action {}", action);
        else Vector3.LOGGER.warn("Force compatibility: skipped failing replay action {}", action, exception);
    }

    public static void skipped(Packet<?> packet, RuntimeException exception) {
        String type = packet.type().toString();
        if (reported.add(type)) Vector3.LOGGER.warn("Force compatibility: skipped unsupported packet {}", type, exception);
    }
}
