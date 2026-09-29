package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

public record PixelationSettings(float blockSize, int colorLevels) {
    public static final List<String> CHANNELS = List.of("pixel_block", "pixel_levels");
    public static PixelationSettings defaults() { return new PixelationSettings(6, 256); }
    public PixelationSettings sanitized() {
        return new PixelationSettings(Math.clamp(blockSize, 1, 128), Math.clamp(colorLevels, 2, 256));
    }
    public PixelationSettings lerp(PixelationSettings to, float t) {
        return new PixelationSettings(blockSize + (to.blockSize - blockSize) * t,
                Math.round(colorLevels + (to.colorLevels - colorLevels) * t));
    }
    public PixelationSettings withChannel(String channel, PixelationSettings source) {
        return new PixelationSettings(channel.equals("pixel_block") ? source.blockSize : blockSize,
                channel.equals("pixel_levels") ? source.colorLevels : colorLevels);
    }
    public boolean same(PixelationSettings other, String channel) {
        return switch (channel) {
            case "pixel_block" -> blockSize == other.blockSize;
            case "pixel_levels" -> colorLevels == other.colorLevels;
            default -> true;
        };
    }
}
