package ml.mypals.vectorthree.flashback.fade.effects;

public record ColorGradingSettings(float exposure, float temperature, float tint, float hue,
                                   float saturation, float contrast, int toneMap,
                                   Rgb mixerRed, Rgb mixerGreen, Rgb mixerBlue,
                                   Rgb lift, Rgb gamma, Rgb gain, GradingCurves curves,
                                   AdvancedGradingSettings advanced) {
    public record Rgb(float r, float g, float b) {
        public static final Rgb ZERO = new Rgb(0, 0, 0), ONE = new Rgb(1, 1, 1);
        public Rgb lerp(Rgb to, float t) {
            return new Rgb(mix(r, to.r, t), mix(g, to.g, t), mix(b, to.b, t));
        }
    }

    public static ColorGradingSettings defaults() {
        return new ColorGradingSettings(0.25f, 0, 0, 0, 0, 0, 0,
                new Rgb(1, 0, 0), new Rgb(0, 1, 0), new Rgb(0, 0, 1),
                Rgb.ZERO, Rgb.ONE, Rgb.ONE, GradingCurves.defaults(), AdvancedGradingSettings.defaults());
    }

    public static ColorGradingSettings fromLegacy(float exposure, float contrast, float saturation, float temperature) {
        ColorGradingSettings d = defaults();
        return new ColorGradingSettings(exposure, temperature * 100, 0, 0,
                (saturation - 1) * 100, (contrast - 1) * 100, 0,
                d.mixerRed, d.mixerGreen, d.mixerBlue, d.lift, d.gamma, d.gain, d.curves, d.advanced);
    }

    public ColorGradingSettings sanitized() {
        ColorGradingSettings d = defaults();
        return new ColorGradingSettings(exposure, temperature, tint, hue, saturation, contrast,
                Math.clamp(toneMap, 0, 2), mixerRed == null ? d.mixerRed : mixerRed,
                mixerGreen == null ? d.mixerGreen : mixerGreen, mixerBlue == null ? d.mixerBlue : mixerBlue,
                lift == null ? Rgb.ZERO : lift, gamma == null ? Rgb.ONE : gamma,
                gain == null ? Rgb.ONE : gain, curves == null ? GradingCurves.defaults() : curves.sanitized(),
                advanced == null ? AdvancedGradingSettings.defaults() : advanced.sanitized());
    }

    public ColorGradingSettings lerp(ColorGradingSettings to, float t) {
        return new ColorGradingSettings(mix(exposure, to.exposure, t),
                mix(temperature, to.temperature, t), mix(tint, to.tint, t), mix(hue, to.hue, t),
                mix(saturation, to.saturation, t), mix(contrast, to.contrast, t),
                t < 0.5f ? toneMap : to.toneMap,
                mixerRed.lerp(to.mixerRed, t), mixerGreen.lerp(to.mixerGreen, t),
                mixerBlue.lerp(to.mixerBlue, t), lift.lerp(to.lift, t),
                gamma.lerp(to.gamma, t), gain.lerp(to.gain, t), curves.lerp(to.curves, t),
                advanced.lerp(to.advanced, t));
    }

    public ColorGradingSettings withChannel(String channel, ColorGradingSettings source) {
        return new ColorGradingSettings(
                channel.equals("exposure") ? source.exposure : exposure,
                channel.equals("temperature") ? source.temperature : temperature,
                channel.equals("tint") ? source.tint : tint,
                channel.equals("hue") ? source.hue : hue,
                channel.equals("saturation") ? source.saturation : saturation,
                channel.equals("contrast") ? source.contrast : contrast,
                channel.equals("tonemap") ? source.toneMap : toneMap,
                channel.equals("mixer_red") ? source.mixerRed : mixerRed,
                channel.equals("mixer_green") ? source.mixerGreen : mixerGreen,
                channel.equals("mixer_blue") ? source.mixerBlue : mixerBlue,
                channel.equals("lift") ? source.lift : lift,
                channel.equals("gamma") ? source.gamma : gamma,
                channel.equals("gain") ? source.gain : gain,
                curves.withChannel(channel, source.curves), advanced.withChannel(channel, source.advanced));
    }

    public boolean same(ColorGradingSettings other, String channel) {
        return switch (channel) {
            case "exposure" -> exposure == other.exposure;
            case "temperature" -> temperature == other.temperature;
            case "tint" -> tint == other.tint;
            case "hue" -> hue == other.hue;
            case "saturation" -> saturation == other.saturation;
            case "contrast" -> contrast == other.contrast;
            case "tonemap" -> toneMap == other.toneMap;
            case "mixer_red" -> mixerRed.equals(other.mixerRed);
            case "mixer_green" -> mixerGreen.equals(other.mixerGreen);
            case "mixer_blue" -> mixerBlue.equals(other.mixerBlue);
            case "lift" -> lift.equals(other.lift);
            case "gamma" -> gamma.equals(other.gamma);
            case "gain" -> gain.equals(other.gain);
            default -> curves.same(other.curves, channel) && advanced.same(other.advanced, channel);
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
