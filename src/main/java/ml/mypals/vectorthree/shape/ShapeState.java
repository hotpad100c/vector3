package ml.mypals.vectorthree.shape;

import com.google.common.collect.Maps;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import ml.mypals.vectorthree.shape.area.AreaOptions;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import ml.mypals.vectorthree.shape.text.TextSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

public record ShapeState(
        String shapeType, String shapeId,
        double x, double y, double z,
        float pitch, float yaw, float roll,
        double scaleX, double scaleY, double scaleZ,
        double sizeX, double sizeY, double sizeZ,
        int segments, float lineWidth,
        int color,
        List<ShapePoint> points,
        TextSettings text,
        String model,
        Map<String, String> blockProperties,
        String parentShapeId,
        boolean seeThrough, boolean visible,
        boolean outline, int outlineColor,
        int videoStartTick, boolean playAudio,
        boolean manualPlayback, boolean noLoop, double playbackSeconds,
        String name,
        WireframeSettings wireframe,
        AreaOptions areaOptions,
        boolean bypassShaders,
        ShapeMount mount
) {
    public static ShapeState create(String type, String id, double x, double y, double z) {
        List<ShapePoint> points = switch (type) {
            case "line", "line_strip" -> List.of(new ShapePoint(0, 0, 0), new ShapePoint(1, 1, 1));
            case "arrow" -> List.of(new ShapePoint(0, 0, 0), new ShapePoint(0, 1, 0));
            case "area" -> List.of(new ShapePoint(x - 0.5, y - 0.5, z - 0.5), new ShapePoint(x + 0.5, y + 0.5, z + 0.5));
            default -> List.of();
        };
        return new ShapeState(type, id, x, y, z, 0, 0, 0,
                1, 1, 1, 1, 1, 1, 32, 0.05f, 0xFFFFFFFF, points,
                type.equals("text") ? TextSettings.defaults() : null,
                switch (type) {
                    case "obj" -> "ryansrenderingkit:models/monkey.obj";
                    case "block" -> "minecraft:stone";
                    case "item" -> "minecraft:diamond";
                    case "entity" -> "minecraft:pig";
                    default -> "";
                }, Map.of(), "", false, true, false, 0xFFFFFFFF, 0, false, false, false, 0, null, null, null, false, null);
    }

    public ShapeState interpolate(ShapeState target, double amount) {
        return new ShapeState(shapeType, shapeId,
                lerp(x, target.x, amount), lerp(y, target.y, amount), lerp(z, target.z, amount),
                (float) lerp(pitch, target.pitch, amount), (float) lerp(yaw, target.yaw, amount),
                (float) lerp(roll, target.roll, amount),
                lerp(scaleX, target.scaleX, amount), lerp(scaleY, target.scaleY, amount),
                lerp(scaleZ, target.scaleZ, amount),
                lerp(sizeX, target.sizeX, amount), lerp(sizeY, target.sizeY, amount),
                lerp(sizeZ, target.sizeZ, amount),
                (int) Math.round(lerp(segments, target.segments, amount)),
                (float) lerp(lineWidth, target.lineWidth, amount),
                lerpColor(color, target.color, amount),
                interpolatePoints(target, amount),
                TextSettings.transition(text, target.text, amount),
                amount >= 1.0 ? target.model : model,
                amount >= 1.0 ? target.blockProperties : blockProperties,
                amount >= 1.0 ? target.parentShapeId : parentShapeId,
                amount < 0.5 ? seeThrough : target.seeThrough,
                amount < 0.5 ? visible : target.visible,
                amount < 0.5 ? outline : target.outline,
                lerpColor(outlineColor, target.outlineColor, amount),
                amount >= 1.0 ? target.videoStartTick : videoStartTick,
                amount < 0.5 ? playAudio : target.playAudio,
                amount < 0.5 ? manualPlayback : target.manualPlayback,
                amount < 0.5 ? noLoop : target.noLoop,
                lerp(playbackSeconds, target.playbackSeconds, amount),
                amount >= 1.0 ? target.name : name,
                WireframeSettings.transition(wireframe, target.wireframe, amount),
                AreaOptions.transition(areaOptions, target.areaOptions, amount),
                amount < 0.5 ? bypassShaders : target.bypassShaders,
                amount >= 1.0 ? target.mount : mount);
    }

    public static ShapeState smooth(ShapeState p0, ShapeState p1, ShapeState p2, ShapeState p3,
            float t1, float t2, float t3, float amount) {
        return new ShapeState(p1.shapeType, p1.shapeId,
                cat(p0.x, p1.x, p2.x, p3.x, t1, t2, t3, amount),
                cat(p0.y, p1.y, p2.y, p3.y, t1, t2, t3, amount),
                cat(p0.z, p1.z, p2.z, p3.z, t1, t2, t3, amount),
                (float) cat(p0.pitch, p1.pitch, p2.pitch, p3.pitch, t1, t2, t3, amount),
                (float) cat(p0.yaw, p1.yaw, p2.yaw, p3.yaw, t1, t2, t3, amount),
                (float) cat(p0.roll, p1.roll, p2.roll, p3.roll, t1, t2, t3, amount),
                cat(p0.scaleX, p1.scaleX, p2.scaleX, p3.scaleX, t1, t2, t3, amount),
                cat(p0.scaleY, p1.scaleY, p2.scaleY, p3.scaleY, t1, t2, t3, amount),
                cat(p0.scaleZ, p1.scaleZ, p2.scaleZ, p3.scaleZ, t1, t2, t3, amount),
                positive(cat(p0.sizeX, p1.sizeX, p2.sizeX, p3.sizeX, t1, t2, t3, amount)),
                positive(cat(p0.sizeY, p1.sizeY, p2.sizeY, p3.sizeY, t1, t2, t3, amount)),
                positive(cat(p0.sizeZ, p1.sizeZ, p2.sizeZ, p3.sizeZ, t1, t2, t3, amount)),
                Math.max(3, (int) Math.round(cat(p0.segments, p1.segments, p2.segments, p3.segments,
                        t1, t2, t3, amount))),
                (float) positive(cat(p0.lineWidth, p1.lineWidth, p2.lineWidth, p3.lineWidth,
                        t1, t2, t3, amount)),
                smoothColor(p0.color, p1.color, p2.color, p3.color, t1, t2, t3, amount),
                smoothPoints(p0, p1, p2, p3, t1, t2, t3, amount),
                TextSettings.transition(p1.text, p2.text, amount),
                amount >= 1.0f ? p2.model : p1.model,
                amount >= 1.0f ? p2.blockProperties : p1.blockProperties,
                amount >= 1.0f ? p2.parentShapeId : p1.parentShapeId,
                amount < 0.5f ? p1.seeThrough : p2.seeThrough,
                amount < 0.5f ? p1.visible : p2.visible,
                amount < 0.5f ? p1.outline : p2.outline,
                smoothColor(p0.outlineColor, p1.outlineColor, p2.outlineColor, p3.outlineColor, t1, t2, t3, amount),
                amount >= 1.0f ? p2.videoStartTick : p1.videoStartTick,
                amount < 0.5f ? p1.playAudio : p2.playAudio,
                amount < 0.5f ? p1.manualPlayback : p2.manualPlayback,
                amount < 0.5f ? p1.noLoop : p2.noLoop,
                cat(p0.playbackSeconds, p1.playbackSeconds, p2.playbackSeconds, p3.playbackSeconds,
                        t1, t2, t3, amount),
                amount >= 1.0f ? p2.name : p1.name,
                WireframeSettings.transition(p1.wireframe, p2.wireframe, amount),
                AreaOptions.transition(p1.areaOptions, p2.areaOptions, amount),
                amount < 0.5f ? p1.bypassShaders : p2.bypassShaders,
                amount >= 1.0f ? p2.mount : p1.mount);
    }

    public static ShapeState hermite(Map<Float, ShapeState> states, float amount) {
        TreeMap<Float, ShapeState> sorted = new TreeMap<>(states);
        Map.Entry<Float, ShapeState> floor = sorted.floorEntry(amount);
        if (floor == null) floor = sorted.firstEntry();
        ShapeState base = floor.getValue();
        return new ShapeState(base.shapeType, base.shapeId,
                hermite(states, amount, s -> s.x), hermite(states, amount, s -> s.y), hermite(states, amount, s -> s.z),
                (float) hermite(states, amount, s -> s.pitch),
                (float) hermite(states, amount, s -> s.yaw),
                (float) hermite(states, amount, s -> s.roll),
                hermite(states, amount, s -> s.scaleX),
                hermite(states, amount, s -> s.scaleY),
                hermite(states, amount, s -> s.scaleZ),
                positive(hermite(states, amount, s -> s.sizeX)),
                positive(hermite(states, amount, s -> s.sizeY)),
                positive(hermite(states, amount, s -> s.sizeZ)),
                Math.max(3, (int) Math.round(hermite(states, amount, s -> s.segments))),
                (float) positive(hermite(states, amount, s -> s.lineWidth)),
                hermiteColor(states, amount, s -> s.color), hermitePoints(states, amount, base),
                hermiteText(sorted, amount, base),
                base.model,
                base.blockProperties,
                base.parentShapeId,
                base.seeThrough, base.visible,
                base.outline, hermiteColor(states, amount, s -> s.outlineColor),
                base.videoStartTick, base.playAudio,
                base.manualPlayback, base.noLoop,
                hermite(states, amount, s -> s.playbackSeconds),
                base.name, hermiteWireframe(sorted, amount, base), base.areaOptions, base.bypassShaders, base.mount);
    }

    public ShapeState with(float[] position, float[] rotation, float[] scale, float[] size,
            int segments, float lineWidth, int color, List<ShapePoint> points,
            TextSettings text, String parentShapeId, boolean seeThrough, boolean visible,
            boolean outline, int outlineColor, boolean playAudio,
            boolean manualPlayback, boolean noLoop, double playbackSeconds) {
        return new ShapeState(shapeType, shapeId,
                position[0], position[1], position[2], rotation[0], rotation[1], rotation[2],
                scale[0], scale[1], scale[2], size[0], size[1], size[2],
                segments, lineWidth, color, List.copyOf(points), text, model,
                blockProperties == null ? Map.of() : Map.copyOf(blockProperties),
                parentShapeId, seeThrough, visible, outline, outlineColor, videoStartTick, playAudio,
                manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withIdentity(String shapeType, String shapeId) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points == null ? List.of() : points, text, model, blockProperties,
                parentShapeId, seeThrough, visible, outline, outlineColor, videoStartTick, playAudio,
                manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withModel(String model) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withBlockProperties(Map<String, String> properties) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, properties == null ? Map.of() : Map.copyOf(properties),
                parentShapeId, seeThrough, visible, outline, outlineColor, videoStartTick, playAudio,
                manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withVideoStartTick(int videoStartTick) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withName(String name) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withWireframe(WireframeSettings wireframe) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name, wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withAreaOptions(AreaOptions areaOptions) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name,
                wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withTransform(double x, double y, double z, float pitch, float yaw, float roll,
            double scaleX, double scaleY, double scaleZ) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name,
                wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withParent(String parentShapeId) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name,
                wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withMount(ShapeMount mount) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name,
                wireframe, areaOptions, bypassShaders, mount);
    }

    public ShapeState withBypassShaders(boolean bypassShaders) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points, text, model, blockProperties, parentShapeId, seeThrough, visible,
                outline, outlineColor, videoStartTick, playAudio, manualPlayback, noLoop, playbackSeconds, name,
                wireframe, areaOptions, bypassShaders, mount);
    }

    private List<ShapePoint> interpolatePoints(ShapeState target, double amount) {
        if (points == null || target.points == null || points.size() != target.points.size()) {
            return amount < 0.5 ? points : target.points;
        }
        List<ShapePoint> result = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) result.add(points.get(i).interpolate(target.points.get(i), amount));
        return result;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double cat(double p0, double p1, double p2, double p3,
            float t1, float t2, float t3, float amount) {
        return CatmullRom.value((float) p0, (float) p1, (float) p2, (float) p3,
                t1, t2, t3, amount);
    }

    private static double hermite(Map<Float, ShapeState> states, float amount,
            ToDoubleFunction<ShapeState> getter) {
        return Hermite.value(Maps.transformValues(states, getter::applyAsDouble), amount);
    }

    private static double positive(double value) { return Math.max(0.001, value); }

    private static int smoothColor(int p0, int p1, int p2, int p3,
            float t1, float t2, float t3, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (int) Math.round(cat((p0 >>> shift) & 255, (p1 >>> shift) & 255,
                    (p2 >>> shift) & 255, (p3 >>> shift) & 255, t1, t2, t3, amount));
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }

    private static int hermiteColor(Map<Float, ShapeState> states, float amount,
            java.util.function.ToIntFunction<ShapeState> colorGetter) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channelShift = shift;
            int channel = (int) Math.round(hermite(states, amount,
                    state -> (colorGetter.applyAsInt(state) >>> channelShift) & 255));
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }

    private static List<ShapePoint> smoothPoints(ShapeState p0, ShapeState p1, ShapeState p2, ShapeState p3,
            float t1, float t2, float t3, float amount) {
        if (!samePointCount(p0, p1, p2, p3)) return amount < 0.5f ? p1.points : p2.points;
        List<ShapePoint> result = new ArrayList<>(p1.points.size());
        for (int i = 0; i < p1.points.size(); i++) {
            ShapePoint a = p0.points.get(i), b = p1.points.get(i), c = p2.points.get(i), d = p3.points.get(i);
            result.add(new ShapePoint(cat(a.x(), b.x(), c.x(), d.x(), t1, t2, t3, amount),
                    cat(a.y(), b.y(), c.y(), d.y(), t1, t2, t3, amount),
                    cat(a.z(), b.z(), c.z(), d.z(), t1, t2, t3, amount)));
        }
        return result;
    }

    private static List<ShapePoint> hermitePoints(Map<Float, ShapeState> states, float amount, ShapeState base) {
        if (base.points == null || states.values().stream().anyMatch(s -> s.points == null
                || s.points.size() != base.points.size())) return base.points;
        List<ShapePoint> result = new ArrayList<>(base.points.size());
        for (int i = 0; i < base.points.size(); i++) {
            int index = i;
            result.add(new ShapePoint(hermite(states, amount, s -> s.points.get(index).x()),
                    hermite(states, amount, s -> s.points.get(index).y()),
                    hermite(states, amount, s -> s.points.get(index).z())));
        }
        return result;
    }

    private static TextSettings hermiteText(TreeMap<Float, ShapeState> states, float amount, ShapeState base) {
        Map.Entry<Float, ShapeState> ceil = states.ceilingEntry(amount);
        if (ceil == null) return base.text;
        Float floorKey = states.floorKey(amount);
        if (floorKey == null) floorKey = states.firstKey();
        float span = ceil.getKey() - floorKey;
        double local = span == 0 ? 1 : (amount - floorKey) / span;
        return TextSettings.transition(base.text, ceil.getValue().text, local);
    }

    private static WireframeSettings hermiteWireframe(TreeMap<Float, ShapeState> states, float amount, ShapeState base) {
        Map.Entry<Float, ShapeState> ceil = states.ceilingEntry(amount);
        if (ceil == null) return base.wireframe;
        Float floorKey = states.floorKey(amount);
        if (floorKey == null) floorKey = states.firstKey();
        float span = ceil.getKey() - floorKey;
        double local = span == 0 ? 1 : (amount - floorKey) / span;
        return WireframeSettings.transition(base.wireframe, ceil.getValue().wireframe, local);
    }

    private static boolean samePointCount(ShapeState... states) {
        if (states[0].points == null) return false;
        int count = states[0].points.size();
        for (ShapeState state : states) if (state.points == null || state.points.size() != count) return false;
        return true;
    }

    static int lerpColor(int from, int to, double amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (int) Math.round(lerp((from >>> shift) & 255, (to >>> shift) & 255, amount));
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }
}
