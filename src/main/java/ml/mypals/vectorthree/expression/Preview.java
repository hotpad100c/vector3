package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.expression.editor.CodeEditor;
import ml.mypals.vectorthree.expression.editor.Hints;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Scope;
import ml.mypals.vectorthree.expression.lang.Value;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers the editor's questions by evaluating code fragments at the playhead, the way the expression itself runs:
 * the locals it last had, its parameter's keyframed value, and (on a track) self. Results are kept for its lifetime.
 */
final class Preview implements Hints {
    private static final String[] COMPONENTS = {"x", "y", "z", "w"};

    private final @Nullable KeyframeTrack track;
    private final float tick;
    private final Map<String, Value> lets;
    private final @Nullable Value own;
    private References.@Nullable SelfRef self;
    private final Map<String, Object> results = new HashMap<>();

    Preview(@Nullable KeyframeTrack track, float tick, Map<String, Value> lets, @Nullable Value own) {
        this.track = track;
        this.tick = tick;
        this.lets = lets;
        this.own = own;
    }

    private Value eval(String source) {
        Object cached = results.computeIfAbsent(source, key -> {
            try {
                return ExpressionRuntime.program(key, false).eval(new PreviewScope());
            } catch (ExprError error) {
                return error;
            } catch (RuntimeException | StackOverflowError error) {
                return new ExprError(String.valueOf(error.getMessage()));
            }
        });
        if (cached instanceof ExprError error) throw error;
        return (Value) cached;
    }

    @Override
    public List<String> members(String target) {
        try {
            return switch (eval(target)) {
                case Value.Obj obj -> obj.members();
                case Value.Vec vec -> List.of(COMPONENTS).subList(0, Math.min(4, vec.values().length));
                default -> List.of();
            };
        } catch (ExprError error) {
            return List.of();
        }
    }

    @Override
    public @Nullable String describe(String expression) {
        try {
            Value value = eval(expression);
            String text = value.text().replace('\n', ' ');
            if (text.length() > 120) text = text.substring(0, 120) + "...";
            return value instanceof Value.Obj ? value.type() : value.type() + " = " + text;
        } catch (ExprError error) {
            return net.minecraft.client.resources.language.I18n.get("vector3.expression.error", error.getMessage());
        }
    }

    @Override
    public List<String> names() {
        List<String> names = new ArrayList<>();
        EditorScene scene = EvalContext.currentScene();
        if (scene == null) return names;
        for (KeyframeTrack candidate : scene.keyframeTracks) {
            String name = candidate.customName;
            Keyframe first = candidate.keyframesByTick.isEmpty() ? null : candidate.keyframesByTick.firstEntry().getValue();
            if ((name == null || name.isBlank()) && first instanceof ShapeKeyframe shape) {
                name = shape.value.name() != null ? shape.value.name() : ShapeTrackRegistry.displayName(shape.value.shapeId());
            }
            if (name != null && Globals.validName(name) && !names.contains(name)) names.add(name);
        }
        return names;
    }

    @Override
    public List<CodeEditor.Suggestion> arguments(String function) {
        List<CodeEditor.Suggestion> arguments = new ArrayList<>();
        if (function.equals("track")) {
            EditorScene scene = EvalContext.currentScene();
            if (scene == null) return arguments;
            for (KeyframeTrack candidate : scene.keyframeTracks) {
                String name = ExpressionUi.trackName(candidate);
                arguments.add(new CodeEditor.Suggestion(name, name, candidate.keyframeType.name()));
            }
        } else if (function.equals("entity")) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return arguments;
            Vec3 camera = minecraft.gameRenderer.mainCamera().position();
            List<Entity> entities = new ArrayList<>();
            for (Entity entity : minecraft.level.entitiesForRendering()) entities.add(entity);
            entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(camera)));
            for (Entity entity : entities.subList(0, Math.min(100, entities.size()))) {
                String detail = EntityType.getKey(entity.getType()).getPath() + " · "
                        + Math.round(Math.sqrt(entity.distanceToSqr(camera))) + " m";
                arguments.add(new CodeEditor.Suggestion(entity.getUUID().toString(), entity.getName().getString(), detail));
            }
        }
        return arguments;
    }

    private References.SelfRef self() {
        if (track == null) throw new ExprError("self is not available in a global");
        if (self == null) {
            KeyframeChange change = ExpressionRuntime.withoutExpressions(track, tick);
            Keyframe keyframe = change == null ? null : KeyframeRebuild.of(track, change);
            if (keyframe == null && !track.keyframesByTick.isEmpty()) {
                var nearest = track.keyframesByTick.floorEntry((int) Math.floor(tick));
                keyframe = (nearest != null ? nearest : track.keyframesByTick.firstEntry()).getValue().copy();
            }
            if (keyframe == null) throw new ExprError("the track has no keyframes");
            self = new References.SelfRef(keyframe, track.keyframeType);
        }
        return self;
    }

    private final class PreviewScope implements Scope {
        @Override public double tick() { return tick; }

        @Override
        public Value value() {
            if (own == null) throw new ExprError(track == null ? "value is not available in a global" : "value isn't known yet");
            return own;
        }

        // A let is compiled on its own as a bare name, which reads as a track; the last run's value answers it.
        @Override
        public Value track(String name) {
            Value local = lets.get(name);
            return local != null ? local : References.track(name, tick);
        }

        @Override public Value entity(String id) { return References.entity(id); }
        @Override public Value camera() { return References.camera(); }
        @Override public Value self() { return Preview.this.self(); }
        @Override public Value global(String name) { return Globals.get(name, tick); }
        @Override public List<String> globalNames() { return Globals.names(); }
    }
}
