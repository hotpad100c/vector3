package ml.mypals.vectorthree.flashback.channel;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.fade.ScreenVFX;
import ml.mypals.vectorthree.flashback.fade.ScreenVFXKeyframeType;
import ml.mypals.vectorthree.flashback.pose.EntityPose;
import ml.mypals.vectorthree.flashback.pose.EntityPoseKeyframeType;
import ml.mypals.vectorthree.shape.ShapeState;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The channel tables of the keyframe types that support per-channel keyframing. */
public final class BuiltInChannels {
    private BuiltInChannels() {}

    public static void register() {
        Channels.register(ScreenVFXKeyframeType.INSTANCE, new ScreenVFXChannels());
        Channels.register(CameraKeyframeType.INSTANCE, new CameraChannels());
        Channels.register(EntityPoseKeyframeType.INSTANCE, new PoseChannels());
        Channels.register(ShapeKeyframeType.INSTANCE, new ShapeChannels());
        FlashbackCameraChannels.register();
    }

    private static final class ScreenVFXChannels extends ValueChannels<ScreenVFX> {
        ScreenVFXChannels() {
            super(ScreenVFXKeyframeType.INSTANCE, List.of("effect", "colour", "opacity", "exposure", "contrast",
                    "saturation", "temperature", "tint", "hue", "tonemap", "mixer_red", "mixer_green",
                    "mixer_blue", "lift", "gamma", "gain", "bloom_intensity", "bloom_threshold",
                    "bloom_soft_knee", "bloom_radius", "bloom_clamp", "bloom_tint", "bloom_anti_flicker",
                    "bloom_high_quality", "bloom_dirt_texture", "bloom_dirt_intensity", "vignette",
                    "vignette_radius", "dof", "focus_depth", "focus_range", "dof_mode", "lut_strength", "lut"));
        }

        @Override
        public List<String> channels(Collection<Keyframe> keyframes) {
            LinkedHashSet<String> channels = new LinkedHashSet<>();
            channels.add("effect");
            for (Keyframe keyframe : keyframes) {
                if (!(keyframe instanceof ml.mypals.vectorthree.flashback.custom.CustomKeyframe<?> custom)
                        || !(custom.value instanceof ScreenVFX value)) continue;
                switch (value.effect()) {
                    case ScreenVFX.GRADE -> channels.addAll(List.of("exposure", "contrast", "saturation", "temperature",
                            "tint", "hue", "tonemap", "mixer_red", "mixer_green", "mixer_blue", "lift", "gamma", "gain"));
                    case ScreenVFX.BLOOM -> channels.addAll(List.of("bloom_intensity", "bloom_threshold", "bloom_soft_knee",
                            "bloom_radius", "bloom_clamp", "bloom_tint", "bloom_anti_flicker", "bloom_high_quality",
                            "bloom_dirt_texture", "bloom_dirt_intensity"));
                    case ScreenVFX.DOF -> channels.addAll(List.of("dof", "focus_depth", "focus_range", "dof_mode"));
                    case ScreenVFX.VIGNETTE -> channels.addAll(List.of("vignette", "vignette_radius"));
                    case ScreenVFX.LUT -> channels.addAll(List.of("lut", "lut_strength"));
                    case ScreenVFX.FADE -> channels.addAll(List.of("colour", "opacity"));
                    default -> channels.addAll(super.channels(keyframes));
                }
            }
            return List.copyOf(channels);
        }

        @Override
        public Map<String, String> labels() {
            return Map.ofEntries(Map.entry("vector3.vfx.effect", "effect"),
                    Map.entry("vector3.fade.colour", "colour"),
                    Map.entry("vector3.fade.opacity", "opacity"),
                    Map.entry("vector3.vfx.exposure", "exposure"),
                    Map.entry("vector3.vfx.contrast", "contrast"),
                    Map.entry("vector3.vfx.saturation", "saturation"),
                    Map.entry("vector3.vfx.temperature", "temperature"),
                    Map.entry("vector3.grade.tint", "tint"),
                    Map.entry("vector3.grade.hue", "hue"),
                    Map.entry("vector3.grade.tonemap", "tonemap"),
                    Map.entry("vector3.grade.mixer_red", "mixer_red"),
                    Map.entry("vector3.grade.mixer_green", "mixer_green"),
                    Map.entry("vector3.grade.mixer_blue", "mixer_blue"),
                    Map.entry("vector3.grade.lift", "lift"),
                    Map.entry("vector3.grade.gamma", "gamma"),
                    Map.entry("vector3.grade.gain", "gain"),
                    Map.entry("vector3.bloom.intensity", "bloom_intensity"),
                    Map.entry("vector3.bloom.threshold", "bloom_threshold"),
                    Map.entry("vector3.bloom.soft_knee", "bloom_soft_knee"),
                    Map.entry("vector3.bloom.radius", "bloom_radius"),
                    Map.entry("vector3.bloom.clamp", "bloom_clamp"),
                    Map.entry("vector3.bloom.tint", "bloom_tint"),
                    Map.entry("vector3.bloom.anti_flicker", "bloom_anti_flicker"),
                    Map.entry("vector3.bloom.high_quality", "bloom_high_quality"),
                    Map.entry("vector3.bloom.dirt_texture", "bloom_dirt_texture"),
                    Map.entry("vector3.bloom.dirt_intensity", "bloom_dirt_intensity"),
                    Map.entry("vector3.vfx.vignette_strength", "vignette"),
                    Map.entry("vector3.vfx.vignette_radius", "vignette_radius"),
                    Map.entry("vector3.vfx.dof_strength", "dof"),
                    Map.entry("vector3.vfx.focus_depth", "focus_depth"),
                    Map.entry("vector3.vfx.focus_range", "focus_range"),
                    Map.entry("vector3.vfx.dof_mode", "dof_mode"),
                    Map.entry("vector3.vfx.lut_strength", "lut_strength"),
                    Map.entry("vector3.vfx.lut_texture", "lut"));
        }

        @Override
        public ScreenVFX compose(ScreenVFX base, Map<String, ScreenVFX> byChannel) {
            ScreenVFX result = base;
            for (Map.Entry<String, ScreenVFX> entry : byChannel.entrySet())
                result = result.withChannel(entry.getKey(), entry.getValue());
            return result;
        }

        @Override
        public boolean same(ScreenVFX a, ScreenVFX b, String channel) {
            return a.same(b, channel);
        }

        @Override
        public String label(String channel) {
            String key = switch (channel) {
                case "effect" -> "vector3.vfx.effect";
                case "colour" -> "vector3.fade.colour";
                case "opacity" -> "vector3.fade.opacity";
                case "exposure", "contrast", "saturation", "temperature", "tint", "hue", "tonemap",
                        "mixer_red", "mixer_green", "mixer_blue", "lift", "gamma", "gain" -> "vector3.grade." + channel;
                case "bloom_intensity", "bloom_threshold", "bloom_soft_knee", "bloom_radius", "bloom_clamp",
                        "bloom_tint", "bloom_anti_flicker", "bloom_high_quality", "bloom_dirt_texture",
                        "bloom_dirt_intensity" -> "vector3.bloom." + channel.substring(6);
                case "vignette" -> "vector3.vfx.vignette_strength";
                case "vignette_radius" -> "vector3.vfx.vignette_radius";
                case "dof" -> "vector3.vfx.dof_strength";
                case "dof_mode" -> "vector3.vfx.dof_mode";
                case "focus_depth" -> "vector3.vfx.focus_depth";
                case "focus_range" -> "vector3.vfx.focus_range";
                case "lut" -> "vector3.vfx.lut_texture";
                default -> "vector3.vfx.lut_strength";
            };
            return I18n.get(key);
        }
    }

    /** Flashback's camera keyframes: position, look direction and roll. */
    private static final class CameraChannels implements ChannelSpec<KeyframeChangeCameraPosition> {
        private static final List<String> CHANNELS = List.of("position", "rotation", "roll");

        @Override public List<String> channels(Collection<Keyframe> keyframes) { return CHANNELS; }

        @Override
        public Map<String, String> labels() {
            return Map.of("flashback.position", "position", "flashback.yaw", "rotation", "flashback.pitch", "rotation",
                    "flashback.roll", "roll");
        }

        @Override
        public @Nullable KeyframeChangeCameraPosition result(KeyframeChange change) {
            return change instanceof KeyframeChangeCameraPosition camera ? camera : null;
        }

        @Override public KeyframeChange change(KeyframeChangeCameraPosition value) { return value; }

        @Override
        public Keyframe keyframe(KeyframeChangeCameraPosition value, Keyframe template) {
            CameraKeyframe keyframe = (CameraKeyframe) template.copy();
            keyframe.position.set(value.position());
            keyframe.yaw = (float) value.yaw();
            keyframe.pitch = (float) value.pitch();
            keyframe.roll = (float) value.roll();
            return keyframe;
        }

        @Override
        public KeyframeChangeCameraPosition compose(KeyframeChangeCameraPosition base, Map<String, KeyframeChangeCameraPosition> byChannel) {
            KeyframeChangeCameraPosition position = byChannel.getOrDefault("position", base);
            KeyframeChangeCameraPosition rotation = byChannel.getOrDefault("rotation", base);
            KeyframeChangeCameraPosition roll = byChannel.getOrDefault("roll", base);
            return new KeyframeChangeCameraPosition(new Vector3d(position.position()), rotation.yaw(), rotation.pitch(), roll.roll());
        }

        @Override
        public boolean same(KeyframeChangeCameraPosition a, KeyframeChangeCameraPosition b, String channel) {
            return switch (channel) {
                case "position" -> a.position().equals(b.position());
                case "rotation" -> a.yaw() == b.yaw() && a.pitch() == b.pitch();
                default -> a.roll() == b.roll();
            };
        }

        @Override public String label(String channel) { return I18n.get("vector3.channel." + channel); }
    }

    /** One rotation and one offset channel per model part, named "part:rotate" / "part:move". */
    private static final class PoseChannels extends ValueChannels<EntityPose> {
        PoseChannels() {
            super(EntityPoseKeyframeType.INSTANCE, List.of());
        }

        @Override
        public List<String> channels(Collection<Keyframe> keyframes) {
            Set<String> parts = new LinkedHashSet<>();
            for (Keyframe keyframe : keyframes) {
                EntityPose pose = result(keyframe);
                if (pose != null) parts.addAll(pose.parts().keySet());
            }
            List<String> channels = new ArrayList<>();
            for (String part : parts) {
                channels.add(part + ":rotate");
                channels.add(part + ":move");
            }
            return channels;
        }

        @Override
        public EntityPose compose(EntityPose base, Map<String, EntityPose> byChannel) {
            Map<String, EntityPose.Limb> parts = new LinkedHashMap<>(base.parts());
            byChannel.forEach((channel, pose) -> {
                String part = part(channel);
                EntityPose.Limb from = pose.parts().getOrDefault(part, EntityPose.Limb.NONE);
                EntityPose.Limb to = parts.getOrDefault(part, EntityPose.Limb.NONE);
                parts.put(part, rotates(channel)
                        ? new EntityPose.Limb(from.rotate(), to.move(), from.xRot(), from.yRot(), from.zRot(), to.x(), to.y(), to.z())
                        : new EntityPose.Limb(to.rotate(), from.move(), to.xRot(), to.yRot(), to.zRot(), from.x(), from.y(), from.z()));
            });
            return base.withParts(parts);
        }

        @Override
        public boolean same(EntityPose a, EntityPose b, String channel) {
            EntityPose.Limb x = a.parts().getOrDefault(part(channel), EntityPose.Limb.NONE);
            EntityPose.Limb y = b.parts().getOrDefault(part(channel), EntityPose.Limb.NONE);
            return rotates(channel)
                    ? x.rotate() == y.rotate() && x.xRot() == y.xRot() && x.yRot() == y.yRot() && x.zRot() == y.zRot()
                    : x.move() == y.move() && x.x() == y.x() && x.y() == y.y() && x.z() == y.z();
        }

        @Override
        public String label(String channel) {
            return EntityPoseKeyframeType.label(part(channel)) + " · "
                    + I18n.get(rotates(channel) ? "vector3.entity_pose.rotation" : "vector3.entity_pose.offset");
        }

        private static String part(String channel) {
            return channel.substring(0, channel.lastIndexOf(':'));
        }

        private static boolean rotates(String channel) {
            return channel.endsWith(":rotate");
        }
    }

    /**
     * Shapes: the continuous properties split into channels. Identity, model, parent, mount, name and the other
     * switches aren't channels; they follow the nearest keyframe as before.
     */
    private static final class ShapeChannels extends ValueChannels<ShapeState> {
        ShapeChannels() {
            super(ShapeKeyframeType.INSTANCE, List.of("position", "rotation", "scale", "size", "line_width", "color",
                    "outline", "points", "text", "wireframe", "visible", "video", "particle", "blast"));
        }

        private static final Map<String, String> LABELS = Map.ofEntries(
                Map.entry("vector3.keyframe.position", "position"), Map.entry("vector3.keyframe.rotation", "rotation"),
                Map.entry("vector3.keyframe.scale", "scale"), Map.entry("vector3.keyframe.color", "color"),
                Map.entry("vector3.keyframe.dimensions", "size"), Map.entry("vector3.keyframe.radius", "size"),
                Map.entry("vector3.keyframe.height", "size"), Map.entry("vector3.keyframe.segments", "size"),
                Map.entry("vector3.keyframe.head_size", "size"), Map.entry("vector3.keyframe.line_width", "line_width"),
                Map.entry("vector3.keyframe.start", "points"), Map.entry("vector3.keyframe.end", "points"),
                Map.entry("vector3.keyframe.text", "text"), Map.entry("vector3.keyframe.shadow", "text"),
                Map.entry("vector3.keyframe.outline_width", "text"), Map.entry("vector3.keyframe.glow", "text"),
                Map.entry("vector3.keyframe.outline", "outline"), Map.entry("vector3.keyframe.outline_color", "outline"),
                Map.entry("vector3.keyframe.wireframe", "wireframe"), Map.entry("vector3.keyframe.show_faces", "wireframe"),
                Map.entry("vector3.keyframe.visible", "visible"), Map.entry("vector3.keyframe.start_tick", "video"),
                Map.entry("vector3.keyframe.playback_position", "video"),
                Map.entry("vector3.keyframe.emission_rate", "line_width"), Map.entry("vector3.keyframe.spread", "size"),
                Map.entry("vector3.particle.lifetime", "particle"), Map.entry("vector3.particle.speed", "particle"),
                Map.entry("vector3.particle.size", "particle"), Map.entry("vector3.particle.shape", "particle"),
                Map.entry("vector3.particle.radius", "particle"), Map.entry("vector3.particle.thickness", "particle"),
                Map.entry("vector3.particle.angle", "particle"), Map.entry("vector3.particle.arc", "particle"),
                Map.entry("vector3.particle.random_direction", "particle"), Map.entry("vector3.particle.rate_over_distance", "particle"),
                Map.entry("vector3.particle.force", "particle"), Map.entry("vector3.particle.noise", "particle"),
                Map.entry("vector3.particle.end_color", "particle"), Map.entry("vector3.particle.end_size", "particle"),
                Map.entry("vector3.particle.gravity", "particle"), Map.entry("vector3.particle.drag", "particle"),
                Map.entry("vector3.blast.progress", "blast"), Map.entry("vector3.blast.start_tick", "blast"),
                Map.entry("vector3.blast.duration", "blast"), Map.entry("vector3.blast.arc_height", "blast"),
                Map.entry("vector3.blast.burst", "blast"), Map.entry("vector3.blast.scatter_center", "points"),
                Map.entry("vector3.blast.scatter_radius", "blast"), Map.entry("vector3.blast.flatten", "blast"),
                Map.entry("vector3.blast.stagger", "blast"), Map.entry("vector3.blast.spin", "blast"),
                Map.entry("vector3.blast.clump", "blast"), Map.entry("vector3.blast.alpha", "blast"),
                Map.entry("vector3.blast.scale", "blast"));

        @Override
        public Map<String, String> labels() {
            return LABELS;
        }

        @Override
        public ShapeState compose(ShapeState b, Map<String, ShapeState> byChannel) {
            ShapeState position = byChannel.getOrDefault("position", b), rotation = byChannel.getOrDefault("rotation", b),
                    scale = byChannel.getOrDefault("scale", b), size = byChannel.getOrDefault("size", b),
                    line = byChannel.getOrDefault("line_width", b), color = byChannel.getOrDefault("color", b),
                    outline = byChannel.getOrDefault("outline", b), points = byChannel.getOrDefault("points", b),
                    text = byChannel.getOrDefault("text", b), wireframe = byChannel.getOrDefault("wireframe", b),
                    visible = byChannel.getOrDefault("visible", b), video = byChannel.getOrDefault("video", b),
                    particle = byChannel.getOrDefault("particle", b), blast = byChannel.getOrDefault("blast", b);
            return new ShapeState(b.shapeType(), b.shapeId(),
                    position.x(), position.y(), position.z(),
                    rotation.pitch(), rotation.yaw(), rotation.roll(),
                    scale.scaleX(), scale.scaleY(), scale.scaleZ(),
                    size.sizeX(), size.sizeY(), size.sizeZ(), size.segments(),
                    line.lineWidth(), color.color(), points.points(), text.text(),
                    b.model(), b.blockProperties(), b.parentShapeId(), b.seeThrough(), visible.visible(),
                    outline.outline(), outline.outlineColor(),
                    video.videoStartTick(), video.playAudio(), video.manualPlayback(), video.noLoop(), video.playbackSeconds(),
                    b.name(), wireframe.wireframe(), b.areaOptions(), b.bypassShaders(), b.mount(), particle.particle(), b.screen(), blast.blast());
        }

        @Override
        public boolean same(ShapeState a, ShapeState b, String channel) {
            return switch (channel) {
                case "position" -> a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
                case "rotation" -> a.pitch() == b.pitch() && a.yaw() == b.yaw() && a.roll() == b.roll();
                case "scale" -> a.scaleX() == b.scaleX() && a.scaleY() == b.scaleY() && a.scaleZ() == b.scaleZ();
                case "size" -> a.sizeX() == b.sizeX() && a.sizeY() == b.sizeY() && a.sizeZ() == b.sizeZ()
                        && a.segments() == b.segments();
                case "line_width" -> a.lineWidth() == b.lineWidth();
                case "color" -> a.color() == b.color();
                case "outline" -> a.outline() == b.outline() && a.outlineColor() == b.outlineColor();
                case "points" -> Objects.equals(a.points(), b.points());
                case "text" -> Objects.equals(a.text(), b.text());
                case "wireframe" -> Objects.equals(a.wireframe(), b.wireframe());
                case "visible" -> a.visible() == b.visible();
                case "particle" -> Objects.equals(a.particle(), b.particle());
                case "blast" -> Objects.equals(a.blast(), b.blast());
                default -> a.videoStartTick() == b.videoStartTick() && a.playAudio() == b.playAudio()
                        && a.manualPlayback() == b.manualPlayback() && a.noLoop() == b.noLoop()
                        && a.playbackSeconds() == b.playbackSeconds();
            };
        }
    }
}
