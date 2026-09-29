package ml.mypals.vectorthree.core.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;

import java.util.List;

public record AdvancedGradingSettings(Rgb splitShadows, Rgb splitHighlights, float splitBalance,
                                      Rgb shadows, Rgb midtones, Rgb highlights,
                                      float shadowEnd, float highlightStart) {
    public static final List<String> CHANNELS = List.of("split_shadows", "split_highlights", "split_balance",
            "smh_shadows", "smh_midtones", "smh_highlights", "smh_shadow_end", "smh_highlight_start");

    public static AdvancedGradingSettings defaults() {
        return new AdvancedGradingSettings(Rgb.ZERO, Rgb.ZERO, 0, Rgb.ONE, Rgb.ONE, Rgb.ONE, 0.33f, 0.66f);
    }

    public AdvancedGradingSettings sanitized() {
        return new AdvancedGradingSettings(splitShadows == null ? Rgb.ZERO : splitShadows,
                splitHighlights == null ? Rgb.ZERO : splitHighlights, Math.clamp(splitBalance, -1, 1),
                shadows == null ? Rgb.ONE : shadows, midtones == null ? Rgb.ONE : midtones,
                highlights == null ? Rgb.ONE : highlights, Math.clamp(shadowEnd, 0.01f, 0.49f),
                Math.clamp(highlightStart, 0.51f, 0.99f));
    }

    public AdvancedGradingSettings lerp(AdvancedGradingSettings to, float t) {
        return new AdvancedGradingSettings(splitShadows.lerp(to.splitShadows, t),
                splitHighlights.lerp(to.splitHighlights, t), mix(splitBalance, to.splitBalance, t),
                shadows.lerp(to.shadows, t), midtones.lerp(to.midtones, t),
                highlights.lerp(to.highlights, t), mix(shadowEnd, to.shadowEnd, t),
                mix(highlightStart, to.highlightStart, t));
    }

    public AdvancedGradingSettings withChannel(String channel, AdvancedGradingSettings source) {
        return new AdvancedGradingSettings(channel.equals("split_shadows") ? source.splitShadows : splitShadows,
                channel.equals("split_highlights") ? source.splitHighlights : splitHighlights,
                channel.equals("split_balance") ? source.splitBalance : splitBalance,
                channel.equals("smh_shadows") ? source.shadows : shadows,
                channel.equals("smh_midtones") ? source.midtones : midtones,
                channel.equals("smh_highlights") ? source.highlights : highlights,
                channel.equals("smh_shadow_end") ? source.shadowEnd : shadowEnd,
                channel.equals("smh_highlight_start") ? source.highlightStart : highlightStart);
    }

    public boolean same(AdvancedGradingSettings other, String channel) {
        return switch (channel) {
            case "split_shadows" -> splitShadows.equals(other.splitShadows);
            case "split_highlights" -> splitHighlights.equals(other.splitHighlights);
            case "split_balance" -> splitBalance == other.splitBalance;
            case "smh_shadows" -> shadows.equals(other.shadows);
            case "smh_midtones" -> midtones.equals(other.midtones);
            case "smh_highlights" -> highlights.equals(other.highlights);
            case "smh_shadow_end" -> shadowEnd == other.shadowEnd;
            case "smh_highlight_start" -> highlightStart == other.highlightStart;
            default -> true;
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
