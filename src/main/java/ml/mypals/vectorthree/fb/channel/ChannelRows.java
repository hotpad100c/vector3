package ml.mypals.vectorthree.fb.channel;

import imgui.moulberry90.ImGui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each channel's first row sits in the properties panel this frame, so its keying buttons can be drawn beside
 * it. Rows are found from the widgets' labels (ChannelSpec#channelOfLabel); editors whose rows share labels (the pose
 * parts) mark them themselves with {@link #mark}.
 */
public final class ChannelRows {
    public record Row(String channel, float y0, float y1) {}

    private static final List<Row> ROWS = new ArrayList<>();
    private static boolean active;

    private ChannelRows() {}

    public static void begin() {
        ROWS.clear();
        active = true;
    }

    public static List<Row> end() {
        active = false;
        return List.copyOf(ROWS);
    }

    public static boolean active() {
        return active;
    }

    /** The last widget is {@code channel}'s row. */
    public static void mark(String channel) {
        if (active) add(channel, ImGui.getItemRectMinY(), ImGui.getItemRectMaxY());
    }

    static void add(String channel, float y0, float y1) {
        for (Row row : ROWS) if (row.channel().equals(channel)) return;
        ROWS.add(new Row(channel, y0, y1));
    }
}
