package ml.mypals.vectorthree.fb.clips;

import ml.mypals.vectorthree.core.clips.ClipRef;

import ml.mypals.vectorthree.core.Mod;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
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
import java.util.*;

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

    /** Clips that are new: only they need composing, since composed clips are cut, moved and copied as views. */
    public static boolean dirty(EditorScene scene) {
        for (ClipRef clip : clips(scene)) if (!clip.composed()) return true;
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
        ClipOverlap.apply(track, java.util.Set.of(at));
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
     * Appends the sources of the clips that are not in the working replay yet, in the background while the replay
     * stays open. Once the grown archive is written the replay is left, swapped for it and opened again. Clips
     * already in the replay are never rebuilt (they are only edited on the timeline), and they keep their places.
     */
    public static void apply(EditorState editorState, EditorScene scene) throws IOException {
        Path working = openReplay;
        if (working == null || clipTrack(scene) == null || composing) return;
        List<String> sources = new ArrayList<>();
        for (ClipRef clip : clips(scene)) if (!clip.composed()) sources.add(clip.source());
        if (sources.isEmpty()) return;
        ReplayArchive.Info info = ReplayArchive.read(working);
        if (info == null) throw new IOException("Cannot read the open replay");
        Map<String, Integer> starts = ClipComposer.layout(info.totalTicks(), sources);
        Map<String, Integer> lengths = new HashMap<>();
        for (String source : starts.keySet()) lengths.put(source, Math.max(1, ReplayArchive.read(Path.of(source)).totalTicks()));
        UUID id = info.meta().replayIdentifier;
        String name = info.meta().name;
        RegistryAccess registries = Minecraft.getInstance().level.registryAccess();
        Path pending = sourcesFolder().resolve("pending-" + id + ".zip");
        composing = true;
        progress = 0;
        Thread worker = new Thread(() -> {
            Exception failure = null;
            try {
                ClipComposer.compose(working, sources, id, name, pending, registries, value -> progress = value);
            } catch (Throwable throwable) {
                failure = throwable instanceof Exception exception ? exception : new IOException(throwable);
                Mod.LOGGER.error("Could not compose the clips into {}", working, throwable);
            }
            Exception error = failure;
            Minecraft.getInstance().execute(() -> {
                if (error == null) swapIn(editorState, starts, lengths, pending, working);
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

    private static void swapIn(EditorState editorState, Map<String, Integer> starts, Map<String, Integer> lengths,
            Path pending, Path working) {
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            KeyframeTrack track = clipTrack(scene);
            if (track != null) {
                // The new sources now sit whole at the end of the archive; their clips keep their timeline places.
                for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                    if (!(entry.getValue() instanceof ClipKeyframeType.ClipKeyframe keyframe) || keyframe.value.composed()) continue;
                    Integer start = starts.get(keyframe.value.source());
                    if (start == null) continue;
                    entry.setValue(new ClipKeyframeType.ClipKeyframe(
                            keyframe.value.placed(start, 0, lengths.get(keyframe.value.source())),
                            com.moulberry.flashback.keyframe.interpolation.InterpolationType.LINEAR));
                }
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
                Mod.LOGGER.error("Could not replace {} with the composed clips", working, exception);
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
