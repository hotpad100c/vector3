package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

/** Unity-style YRGB and versus curves, in stable UI/GPU order. */
public record GradingCurves(ColorCurve master, ColorCurve red, ColorCurve green, ColorCurve blue,
                            ColorCurve hueHue, ColorCurve hueSat, ColorCurve satSat, ColorCurve lumSat) {
    public static final List<String> CHANNELS = List.of("curve_master", "curve_red", "curve_green", "curve_blue",
            "curve_hue_hue", "curve_hue_sat", "curve_sat_sat", "curve_lum_sat");

    public static GradingCurves defaults() {
        return new GradingCurves(ColorCurve.identity(), ColorCurve.identity(), ColorCurve.identity(),
                ColorCurve.identity(), ColorCurve.neutral(), ColorCurve.neutral(),
                ColorCurve.neutral(), ColorCurve.neutral());
    }

    public GradingCurves sanitized() {
        GradingCurves d = defaults();
        return new GradingCurves(safe(master, d.master, 0), safe(red, d.red, 0), safe(green, d.green, 0),
                safe(blue, d.blue, 0), safe(hueHue, d.hueHue, 0.5f), safe(hueSat, d.hueSat, 0.5f),
                safe(satSat, d.satSat, 0.5f), safe(lumSat, d.lumSat, 0.5f));
    }

    private static ColorCurve safe(ColorCurve curve, ColorCurve fallback, float endpoint) {
        return curve == null ? fallback : curve.sanitized(endpoint);
    }

    public ColorCurve get(int index) {
        return switch (index) {
            case 0 -> master; case 1 -> red; case 2 -> green; case 3 -> blue;
            case 4 -> hueHue; case 5 -> hueSat; case 6 -> satSat; default -> lumSat;
        };
    }

    public GradingCurves with(int index, ColorCurve value) {
        return new GradingCurves(index == 0 ? value : master, index == 1 ? value : red,
                index == 2 ? value : green, index == 3 ? value : blue,
                index == 4 ? value : hueHue, index == 5 ? value : hueSat,
                index == 6 ? value : satSat, index == 7 ? value : lumSat);
    }

    public GradingCurves lerp(GradingCurves to, float t) {
        return new GradingCurves(master.lerp(to.master, t), red.lerp(to.red, t), green.lerp(to.green, t),
                blue.lerp(to.blue, t), hueHue.lerp(to.hueHue, t), hueSat.lerp(to.hueSat, t),
                satSat.lerp(to.satSat, t), lumSat.lerp(to.lumSat, t));
    }

    public GradingCurves withChannel(String channel, GradingCurves source) {
        int index = CHANNELS.indexOf(channel);
        return index < 0 ? this : with(index, source.get(index));
    }

    public boolean same(GradingCurves other, String channel) {
        int index = CHANNELS.indexOf(channel);
        return index < 0 || get(index).equals(other.get(index));
    }
}
