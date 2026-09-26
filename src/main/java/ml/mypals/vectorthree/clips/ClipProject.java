package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A replay becomes a project once it has a Clips track. Its working replay (the file that is open) is rebuilt from
 * the clips whenever they change; clip sources are private copies so the originals can be moved or deleted.
 */
public final class ClipProject {
    private static @Nullable Path openReplay;
    private static volatile boolean composing;
    private static volatile double progress;

    private ClipProject() {}

    public static void opened(Path replay) {
        openReplay = replay;
    }

    public static @Nullable Path openReplay() {
        return openReplay;
    }

    /** Extra height the Clips track row gets above itself on the timeline; 0 when it isn't the top track. */
    private static float band;

    public static float band() {
        return band;
    }

    public static float updateBand(EditorScene scene, float lineHeight) {
        band = !scene.keyframeTracks.isEmpty() && scene.keyframeTracks.getFirst().keyframeType == ClipKeyframeType.INSTANCE
                ? lineHeight * 2 : 0;
        return band;
    }

    public static boolean isComposing() {
        return composing;
    }

    static Path sourcesFolder() throws IOException {
        return Files.createDirectories(Flashback.getDataDirectory().resolve("vector3_clip_sources"));
    }

    public static @Nullable KeyframeTrack clipTrack(EditorScene scene) {
        for (KeyframeTrack track : scene.keyframeTracks) {
            if (track.keyframeType == ClipKeyframeType.INSTANCE) return track;
        }
        return null;
    }

    public static List<ClipRef> clips(EditorScene scene) {
        KeyframeTrack track = clipTrack(scene);
        List<ClipRef> clips = new ArrayList<>();
        if (track != null) {
            for (Keyframe keyframe : track.keyframesByTick.values()) {
                if (keyframe instanceof ClipKeyframeType.ClipKeyframe clip) clips.add(clip.value);
            }
        }
        return clips;
    }

    /** Clips that are new, reordered, re-ranged or dragged away from where they were composed. */
    public static boolean dirty(EditorScene scene) {
        KeyframeTrack track = clipTrack(scene);
        if (track == null) return false;
        int lastPlaced = -1;
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            if (!(entry.getValue() instanceof ClipKeyframeType.ClipKeyframe keyframe)) continue;
            ClipRef clip = keyframe.value;
            if (!clip.composed() || clip.placedAt() < lastPlaced || entry.getKey() != clip.visibleStart()) return true;
            // Trimmed clips leave a skipped gap until they are composed again, cut exactly to their range.
            if (clip.in() != clip.spanStart() || clip.out() != clip.spanStart() + clip.spanLength()) return true;
            lastPlaced = clip.placedAt();
        }
        return false;
    }

    /** Where the last clip ends, so the timeline can show clips placed past the end of the open replay. */
    public static int end(EditorScene scene) {
        KeyframeTrack track = clipTrack(scene);
        int end = 0;
        if (track != null) {
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (entry.getValue() instanceof ClipKeyframeType.ClipKeyframe clip) end = Math.max(end, entry.getKey() + clip.value.length());
            }
        }
        return end;
    }

    /** The clip boundary (a start, an end, or 0) nearest {@code tick}, ignoring the clip at {@code ignore}. */
    public static int snap(EditorScene scene, int tick, int ignore) {
        KeyframeTrack track = clipTrack(scene);
        int best = 0;
        if (track == null) return best;
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            if (entry.getKey() == ignore || !(entry.getValue() instanceof ClipKeyframeType.ClipKeyframe clip)) continue;
            for (int boundary : new int[]{entry.getKey(), entry.getKey() + clip.value.length()}) {
                if (Math.abs(boundary - tick) < Math.abs(best - tick)) best = boundary;
            }
        }
        return best;
    }

    /**
     * The parts of each composed chunk span no clip shows; merged into the Skip scopes. A split clip leaves two
     * clips on one span, so the span is cut against all of them together.
     */
    public static NavigableMap<Integer, Integer> hiddenRanges(EditorScene scene) {
        Map<Integer, List<ClipRef>> bySpan = new TreeMap<>();
        for (ClipRef clip : clips(scene)) {
            if (clip.composed()) bySpan.computeIfAbsent(clip.placedAt(), start -> new ArrayList<>()).add(clip);
        }
        NavigableMap<Integer, Integer> hidden = new TreeMap<>();
        for (Map.Entry<Integer, List<ClipRef>> span : bySpan.entrySet()) {
            List<ClipRef> clips = span.getValue();
            clips.sort(java.util.Comparator.comparingInt(ClipRef::visibleStart));
            int cursor = span.getKey(), end = cursor + clips.getFirst().spanLength();
            for (ClipRef clip : clips) {
                int from = Math.clamp(clip.visibleStart(), span.getKey(), end), to = Math.clamp(clip.visibleEnd(), from, end);
                if (from > cursor) hidden.put(cursor, from);
                cursor = Math.max(cursor, to);
            }
            if (cursor < end) hidden.put(cursor, end);
        }
        return hidden;
    }

    /** Adds a clip at {@code tick}; the first clip added to a plain replay turns the replay itself into clip one. */
    public static void addClip(EditorScene scene, Path replay, int tick) throws IOException {
        KeyframeTrack track = clipTrack(scene);
        if (track == null) {
            track = new KeyframeTrack(ClipKeyframeType.INSTANCE);
            scene.keyframeTracks.addFirst(track);
            Path current = openReplay;
            if (current != null) {
                ReplayArchive.Info info = ReplayArchive.read(current);
                if (info != null) {
                    Path copy = keepSource(current);
                    track.keyframesByTick.put(0, new ClipKeyframeType.ClipKeyframe(
                            new ClipRef(copy.toString(), label(info, current), 0, info.totalTicks(), 0, 0, info.totalTicks()),
                            com.moulberry.flashback.keyframe.interpolation.InterpolationType.LINEAR));
                }
            }
        }
        ReplayArchive.Info info = ReplayArchive.read(replay);
        if (info == null) throw new IOException("Not a readable replay: " + replay);
        // ReplayCombiner refuses to join recordings from different game versions, so refuse them up front.
        ReplayArchive.Info open = openReplay == null ? null : ReplayArchive.read(openReplay);
        if (open != null && open.meta().dataVersion != info.meta().dataVersion) {
            throw new IncompatibleClipException(info.meta().versionString, open.meta().versionString);
        }
        Path copy = keepSource(replay);
        int at = snap(scene, Math.max(0, tick), -1);
        while (track.keyframesByTick.containsKey(at)) at++;
        track.keyframesByTick.put(at, new ClipKeyframeType.ClipKeyframe(
                new ClipRef(copy.toString(), label(info, replay), 0, info.totalTicks(), -1, 0, info.totalTicks()),
                com.moulberry.flashback.keyframe.interpolation.InterpolationType.LINEAR));
    }

    private static String label(ReplayArchive.Info info, Path replay) {
        String name = info.meta().name;
        return name == null || name.isBlank() ? replay.getFileName().toString() : name;
    }

    private static Path keepSource(Path replay) throws IOException {
        Path copy = sourcesFolder().resolve(UUID.randomUUID() + ".zip");
        Files.copy(replay, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    public static double progress() {
        return progress;
    }

    /**
     * Composes the clips in timeline order into a pending archive in the background, while the replay stays open.
     * Once that is written the replay is left, swapped for the new archive and opened again. Other keyframes keep
     * their ticks, so reordering clips never reshuffles them.
     */
    public static void apply(EditorState editorState, EditorScene scene) throws IOException {
        Path working = openReplay;
        if (working == null || clipTrack(scene) == null || composing) return;
        List<ClipRef> before = clips(scene);
        if (before.isEmpty()) return;
        List<ClipRef> after = ClipComposer.layout(before);
        ReplayArchive.Info info = ReplayArchive.read(working);
        if (info == null) throw new IOException("Cannot read the open replay");
        UUID id = info.meta().replayIdentifier;
        String name = info.meta().name;
        RegistryAccess registries = Minecraft.getInstance().level.registryAccess();
        Path pending = sourcesFolder().resolve("pending-" + id + ".zip");
        composing = true;
        progress = 0;
        Thread worker = new Thread(() -> {
            Exception failure = null;
            try {
                ClipComposer.compose(after, id, name, pending, registries, value -> progress = value);
            } catch (Exception exception) {
                failure = exception;
                Vector3.LOGGER.error("Could not compose the clips into {}", working, exception);
            }
            Exception error = failure;
            Minecraft.getInstance().execute(() -> {
                if (error == null) swapIn(editorState, after, pending, working);
                else failed(error, pending);
            });
        }, "vector3-clip-compose");
        worker.setDaemon(true);
        worker.start();
    }

    private static void failed(Exception error, Path pending) {
        composing = false;
        try {
            Files.deleteIfExists(pending);
        } catch (IOException ignored) {
            // Left for the next compose to overwrite.
        }
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("vector3.clips.compose_failed"), Component.literal(String.valueOf(error.getMessage())));
    }

    private static void swapIn(EditorState editorState, List<ClipRef> after, Path pending, Path working) {
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            KeyframeTrack track = clipTrack(scene);
            if (track != null) {
                TreeMap<Integer, Keyframe> placed = new TreeMap<>();
                for (ClipRef clip : after) {
                    placed.put(clip.visibleStart(), new ClipKeyframeType.ClipKeyframe(clip,
                            com.moulberry.flashback.keyframe.interpolation.InterpolationType.LINEAR));
                }
                track.keyframesByTick = placed;
            }
            ((ClearableHistory) scene).vector3$clearHistory();
        } finally {
            editorState.release(stamp);
        }
        editorState.markDirty();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.disconnect(new GenericMessageScreen(Component.translatable("vector3.clips.reopening")), false);
        Thread mover = new Thread(() -> {
            Exception failure = null;
            try {
                // The replay server holds the working archive open until it has fully stopped.
                while (minecraft.getSingleplayerServer() != null) Thread.sleep(50);
                Files.move(pending, working, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception exception) {
                failure = exception;
                Vector3.LOGGER.error("Could not replace {} with the composed clips", working, exception);
            }
            Exception error = failure;
            minecraft.execute(() -> {
                composing = false;
                if (error != null) failed(error, pending);
                Flashback.openReplayWorld(working);
            });
        }, "vector3-clip-swap");
        mover.setDaemon(true);
        mover.start();
    }

    public static final class IncompatibleClipException extends IOException {
        public final String clipVersion, projectVersion;

        IncompatibleClipException(String clipVersion, String projectVersion) {
            super("Clip recorded on " + clipVersion + ", project on " + projectVersion);
            this.clipVersion = clipVersion;
            this.projectVersion = projectVersion;
        }
    }

    /** Implemented on EditorScene by EditorSceneMixin: undo steps are meaningless once the timeline is rebuilt. */
    public interface ClearableHistory {
        void vector3$clearHistory();
    }

    /** Implemented on EditorSceneHistory by EditorSceneHistoryMixin. */
    public interface ResettableHistory {
        void vector3$reset();
    }
}
