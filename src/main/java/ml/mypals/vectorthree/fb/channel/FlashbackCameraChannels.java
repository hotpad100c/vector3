package ml.mypals.vectorthree.fb.channel;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPositionOrbit;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraShake;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import com.moulberry.flashback.keyframe.types.CameraOrbitKeyframeType;
import com.moulberry.flashback.keyframe.types.CameraShakeKeyframeType;
import com.moulberry.flashback.keyframe.types.TrackEntityKeyframeType;
import ml.mypals.vectorthree.core.camera.orbit.OrbitTilt;
import ml.mypals.vectorthree.core.camera.shake.ShakeHolder;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Channel tables of Flashback's other camera keyframes, with vector3's additions (orbit tilt, shake parameters). */
final class FlashbackCameraChannels {
    private FlashbackCameraChannels() {}

    static void register() {
        Channels.register(CameraOrbitKeyframeType.INSTANCE, new Orbit());
        Channels.register(CameraShakeKeyframeType.INSTANCE, new Shake());
        Channels.register(TrackEntityKeyframeType.INSTANCE, new TrackEntity());
    }

    private abstract static class Fixed<R extends KeyframeChange> implements ChannelSpec<R> {
        private final Class<R> type;
        private final List<String> channels;

        Fixed(Class<R> type, List<String> channels) {
            this.type = type;
            this.channels = channels;
        }

        @Override public List<String> channels(Collection<Keyframe> keyframes) { return channels; }
        @Override public @Nullable R result(KeyframeChange change) { return type.isInstance(change) ? type.cast(change) : null; }
        @Override public KeyframeChange change(R value) { return value; }
        @Override public String label(String channel) { return I18n.get("vector3.channel." + channel); }
    }

    /** Centre, distance, angles (yaw / pitch) and vector3's tilt. */
    private static final class Orbit extends Fixed<KeyframeChangeCameraPositionOrbit> {
        Orbit() {
            super(KeyframeChangeCameraPositionOrbit.class, List.of("center", "distance", "angles", "tilt"));
        }

        @Override
        public Map<String, String> labels() {
            return Map.of("flashback.position", "center", "flashback.distance", "distance", "flashback.yaw", "angles",
                    "flashback.pitch", "angles", "vector3.orbit.tilt", "tilt");
        }

        @Override
        public KeyframeChangeCameraPositionOrbit compose(KeyframeChangeCameraPositionOrbit base,
                Map<String, KeyframeChangeCameraPositionOrbit> byChannel) {
            KeyframeChangeCameraPositionOrbit center = byChannel.getOrDefault("center", base),
                    distance = byChannel.getOrDefault("distance", base), angles = byChannel.getOrDefault("angles", base),
                    tilt = byChannel.getOrDefault("tilt", base);
            KeyframeChangeCameraPositionOrbit result = new KeyframeChangeCameraPositionOrbit(new Vector3d(center.center()),
                    distance.distance(), angles.yaw(), angles.pitch());
            ((OrbitTilt) (Object) result).vector3$setTilt(((OrbitTilt) (Object) tilt).vector3$tiltX(),
                    ((OrbitTilt) (Object) tilt).vector3$tiltZ());
            return result;
        }

        @Override
        public Keyframe keyframe(KeyframeChangeCameraPositionOrbit value, Keyframe template) {
            CameraOrbitKeyframe keyframe = (CameraOrbitKeyframe) template.copy();
            keyframe.center = new Vector3d(value.center());
            keyframe.distance = (float) value.distance();
            keyframe.yaw = (float) value.yaw();
            keyframe.pitch = (float) value.pitch();
            ((OrbitTilt) keyframe).vector3$setTilt(((OrbitTilt) (Object) value).vector3$tiltX(),
                    ((OrbitTilt) (Object) value).vector3$tiltZ());
            return keyframe;
        }

        @Override
        public boolean same(KeyframeChangeCameraPositionOrbit a, KeyframeChangeCameraPositionOrbit b, String channel) {
            return switch (channel) {
                case "center" -> a.center().equals(b.center());
                case "distance" -> a.distance() == b.distance();
                case "angles" -> a.yaw() == b.yaw() && a.pitch() == b.pitch();
                default -> ((OrbitTilt) (Object) a).vector3$tiltX() == ((OrbitTilt) (Object) b).vector3$tiltX()
                        && ((OrbitTilt) (Object) a).vector3$tiltZ() == ((OrbitTilt) (Object) b).vector3$tiltZ();
            };
        }
    }

    /** Horizontal and vertical shake, and vector3's extra parameters; the noise phases follow the whole track. */
    private static final class Shake extends Fixed<KeyframeChangeCameraShake> {
        Shake() {
            super(KeyframeChangeCameraShake.class, List.of("shake_x", "shake_y", "shake_params"));
        }

        @Override
        public Map<String, String> labels() {
            return Map.of("flashback.frequency_x", "shake_x", "flashback.amplitude_x", "shake_x",
                    "flashback.frequency", "shake_x", "flashback.amplitude", "shake_x",
                    "flashback.frequency_y", "shake_y", "flashback.amplitude_y", "shake_y");
        }

        @Override
        public KeyframeChangeCameraShake compose(KeyframeChangeCameraShake base, Map<String, KeyframeChangeCameraShake> byChannel) {
            KeyframeChangeCameraShake x = byChannel.getOrDefault("shake_x", base), y = byChannel.getOrDefault("shake_y", base),
                    params = byChannel.getOrDefault("shake_params", base);
            KeyframeChangeCameraShake result = new KeyframeChangeCameraShake(x.frequencyX(), x.amplitudeX(),
                    y.frequencyY(), y.amplitudeY());
            ((ShakeHolder) (Object) result).vector3$setShake(((ShakeHolder) (Object) params).vector3$shake());
            ((ShakeHolder) (Object) result).vector3$setPhases(((ShakeHolder) (Object) base).vector3$phases());
            return result;
        }

        @Override
        public boolean same(KeyframeChangeCameraShake a, KeyframeChangeCameraShake b, String channel) {
            return switch (channel) {
                case "shake_x" -> a.frequencyX() == b.frequencyX() && a.amplitudeX() == b.amplitudeX();
                case "shake_y" -> a.frequencyY() == b.frequencyY() && a.amplitudeY() == b.amplitudeY();
                default -> Objects.equals(((ShakeHolder) (Object) a).vector3$shake(), ((ShakeHolder) (Object) b).vector3$shake());
            };
        }
    }

    /** The tracked entity and body part aren't a channel (they follow the nearest keyframe); the offsets are. */
    private static final class TrackEntity extends Fixed<KeyframeChangeTrackEntity> {
        TrackEntity() {
            super(KeyframeChangeTrackEntity.class, List.of("angles", "position_offset", "view_offset", "roll"));
        }

        @Override
        public Map<String, String> labels() {
            return Map.of("flashback.yaw_offset", "angles", "flashback.pitch_offset", "angles",
                    "flashback.position_offset", "position_offset", "flashback.view_offset", "view_offset", "flashback.roll", "roll");
        }

        @Override
        public KeyframeChangeTrackEntity compose(KeyframeChangeTrackEntity base, Map<String, KeyframeChangeTrackEntity> byChannel) {
            KeyframeChangeTrackEntity angles = byChannel.getOrDefault("angles", base),
                    position = byChannel.getOrDefault("position_offset", base),
                    view = byChannel.getOrDefault("view_offset", base), roll = byChannel.getOrDefault("roll", base);
            KeyframeChangeTrackEntity result = new KeyframeChangeTrackEntity(base.target(), base.trackingBodyPart(),
                    angles.yawOffset(), angles.pitchOffset(), new Vector3d(position.positionOffset()),
                    new Vector3d(view.viewOffset()), roll.roll());
            ml.mypals.vectorthree.core.pose.ModelPartHolder.copy(base, result);
            return result;
        }

        @Override
        public Keyframe keyframe(KeyframeChangeTrackEntity value, Keyframe template) {
            TrackEntityKeyframe keyframe = (TrackEntityKeyframe) template.copy();
            keyframe.yawOffset = value.yawOffset();
            keyframe.pitchOffset = value.pitchOffset();
            keyframe.positionOffset.set(value.positionOffset());
            keyframe.viewOffset.set(value.viewOffset());
            keyframe.roll = value.roll();
            return keyframe;
        }

        @Override
        public boolean same(KeyframeChangeTrackEntity a, KeyframeChangeTrackEntity b, String channel) {
            return switch (channel) {
                case "angles" -> a.yawOffset() == b.yawOffset() && a.pitchOffset() == b.pitchOffset();
                case "position_offset" -> Objects.equals(a.positionOffset(), b.positionOffset());
                case "view_offset" -> Objects.equals(a.viewOffset(), b.viewOffset());
                default -> a.roll() == b.roll();
            };
        }
    }
}
