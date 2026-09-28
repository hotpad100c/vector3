package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;

public final class ColorWheelEditor {
    private ColorWheelEditor() {}

    public static Rgb edit(String label, Rgb value, float min, float max) {
        ImGui.textUnformatted(label);
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        float size = 104, cx = left + size / 2, cy = top + size / 2, radius = 45;
        ImGui.invisibleButton("##wheel_" + label, size, size);
        ImDrawList draw = ImGui.getWindowDrawList();
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48, b = (i + 1) * Math.PI * 2 / 48;
            float x = (float) Math.cos((a + b) * 0.5) * 0.5f;
            float y = (float) Math.sin((a + b) * 0.5) * 0.5f;
            int rgb = 0xff000000 | (Math.round(Math.clamp(0.5f + x - y / 2, 0, 1) * 255) << 16)
                    | (Math.round(Math.clamp(0.5f - x - y / 2, 0, 1) * 255) << 8)
                    | Math.round(Math.clamp(0.5f + y, 0, 1) * 255);
            draw.addTriangleFilled(cx, cy, cx + (float) Math.cos(a) * radius,
                    cy + (float) Math.sin(a) * radius, cx + (float) Math.cos(b) * radius,
                    cy + (float) Math.sin(b) * radius, rgb);
        }
        float master = (value.r() + value.g() + value.b()) / 3;
        float px = (value.r() - value.g()) * 0.5f;
        float py = value.b() - master;
        draw.addCircleFilled(cx + px * radius / 0.5f, cy + py * radius / 0.5f, 5, 0xffffffff);
        Rgb result = value;
        if (ImGui.isItemActive() && ImGui.isMouseDown(0)) {
            float x = Math.clamp((ImGui.getIO().getMousePosX() - cx) / radius, -1, 1) * 0.5f;
            float y = Math.clamp((ImGui.getIO().getMousePosY() - cy) / radius, -1, 1) * 0.5f;
            result = new Rgb(Math.clamp(master + x - y / 2, min, max),
                    Math.clamp(master - x - y / 2, min, max), Math.clamp(master + y, min, max));
        }
        float[] brightness = {master};
        if (ImGui.dragFloat(label + "##master", brightness, 0.005f, min, max)) {
            float delta = brightness[0] - master;
            result = new Rgb(Math.clamp(result.r() + delta, min, max),
                    Math.clamp(result.g() + delta, min, max), Math.clamp(result.b() + delta, min, max));
        }
        float[] channels = {result.r(), result.g(), result.b()};
        if (ImGui.dragFloat3(label + " RGB##channels", channels, 0.005f, min, max))
            result = new Rgb(channels[0], channels[1], channels[2]);
        return result;
    }
}
