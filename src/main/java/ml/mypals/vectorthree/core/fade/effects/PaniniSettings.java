package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

public record PaniniSettings(float distance, float crop) {
    public static final List<String> CHANNELS = List.of("panini_distance", "panini_crop");
    public static PaniniSettings defaults() { return new PaniniSettings(0.5f, 0.2f); }
    public PaniniSettings sanitized() { return new PaniniSettings(Math.clamp(distance, 0, 1), Math.clamp(crop, 0, 1)); }
    public PaniniSettings lerp(PaniniSettings to, float t) {
        return new PaniniSettings(distance + (to.distance - distance) * t, crop + (to.crop - crop) * t);
    }
    public PaniniSettings withChannel(String channel, PaniniSettings source) {
        return new PaniniSettings(channel.equals("panini_distance") ? source.distance : distance,
                channel.equals("panini_crop") ? source.crop : crop);
    }
    public boolean same(PaniniSettings other, String channel) {
        return switch (channel) {
            case "panini_distance" -> distance == other.distance;
            case "panini_crop" -> crop == other.crop;
            default -> true;
        };
    }
}
