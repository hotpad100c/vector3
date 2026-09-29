package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.ArrayList;
import java.util.List;

public record AdditionalEffects(FlareSettings flare, ExposureSettings exposure, MotionBlurSettings motion,
                                PaniniSettings panini, OcclusionSettings ao, ReflectionSettings ssr,
                                GodRaysSettings rays, BlurSettings blur) {
    public static final List<String> CHANNELS;
    static {
        List<String> channels = new ArrayList<>();
        channels.addAll(FlareSettings.CHANNELS);
        channels.addAll(ExposureSettings.CHANNELS);
        channels.addAll(MotionBlurSettings.CHANNELS);
        channels.addAll(PaniniSettings.CHANNELS);
        channels.addAll(OcclusionSettings.CHANNELS);
        channels.addAll(ReflectionSettings.CHANNELS);
        channels.addAll(GodRaysSettings.CHANNELS);
        channels.addAll(BlurSettings.CHANNELS);
        CHANNELS = List.copyOf(channels);
    }

    public static AdditionalEffects defaults() {
        return new AdditionalEffects(FlareSettings.defaults(), ExposureSettings.defaults(),
                MotionBlurSettings.defaults(), PaniniSettings.defaults(), OcclusionSettings.defaults(),
                ReflectionSettings.defaults(), GodRaysSettings.defaults(), BlurSettings.defaults());
    }

    public AdditionalEffects sanitized() {
        return new AdditionalEffects(flare == null ? FlareSettings.defaults() : flare.sanitized(),
                exposure == null ? ExposureSettings.defaults() : exposure.sanitized(),
                motion == null ? MotionBlurSettings.defaults() : motion.sanitized(),
                panini == null ? PaniniSettings.defaults() : panini.sanitized(),
                ao == null ? OcclusionSettings.defaults() : ao.sanitized(),
                ssr == null ? ReflectionSettings.defaults() : ssr.sanitized(),
                rays == null ? GodRaysSettings.defaults() : rays.sanitized(),
                blur == null ? BlurSettings.defaults() : blur.sanitized());
    }

    public AdditionalEffects lerp(AdditionalEffects to, float t) {
        return new AdditionalEffects(flare.lerp(to.flare, t), exposure.lerp(to.exposure, t),
                motion.lerp(to.motion, t), panini.lerp(to.panini, t), ao.lerp(to.ao, t), ssr.lerp(to.ssr, t),
                rays.lerp(to.rays, t), blur.lerp(to.blur, t));
    }

    public AdditionalEffects withChannel(String channel, AdditionalEffects source) {
        return new AdditionalEffects(flare.withChannel(channel, source.flare),
                exposure.withChannel(channel, source.exposure), motion.withChannel(channel, source.motion),
                panini.withChannel(channel, source.panini), ao.withChannel(channel, source.ao),
                ssr.withChannel(channel, source.ssr), rays.withChannel(channel, source.rays),
                blur.withChannel(channel, source.blur));
    }

    public boolean same(AdditionalEffects other, String channel) {
        return flare.same(other.flare, channel) && exposure.same(other.exposure, channel)
                && motion.same(other.motion, channel) && panini.same(other.panini, channel)
                && ao.same(other.ao, channel) && ssr.same(other.ssr, channel) && rays.same(other.rays, channel)
                && blur.same(other.blur, channel);
    }

    public AdditionalEffects withFlare(FlareSettings value) {
        return new AdditionalEffects(value, exposure, motion, panini, ao, ssr, rays, blur);
    }
    public AdditionalEffects withExposure(ExposureSettings value) {
        return new AdditionalEffects(flare, value, motion, panini, ao, ssr, rays, blur);
    }
    public AdditionalEffects withMotion(MotionBlurSettings value) {
        return new AdditionalEffects(flare, exposure, value, panini, ao, ssr, rays, blur);
    }
    public AdditionalEffects withPanini(PaniniSettings value) {
        return new AdditionalEffects(flare, exposure, motion, value, ao, ssr, rays, blur);
    }
    public AdditionalEffects withAo(OcclusionSettings value) {
        return new AdditionalEffects(flare, exposure, motion, panini, value, ssr, rays, blur);
    }
    public AdditionalEffects withSsr(ReflectionSettings value) {
        return new AdditionalEffects(flare, exposure, motion, panini, ao, value, rays, blur);
    }
    public AdditionalEffects withRays(GodRaysSettings value) {
        return new AdditionalEffects(flare, exposure, motion, panini, ao, ssr, value, blur);
    }
    public AdditionalEffects withBlur(BlurSettings value) {
        return new AdditionalEffects(flare, exposure, motion, panini, ao, ssr, rays, value);
    }
}
