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

    private ClipProject() {}

    public static void opened(Path replay) {
        openReplay = replay;
    }

    public static @Nullable Path openReplay() {
        return openReplay;
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
            if (clip.in() < clip.spanStart() || clip.out() > clip.spanStart() + clip.spanLength()) return true;
            lastPlaced = clip.placedAt();
        }
        return false;
    }

    /** The parts of each composed clip's chunk span it doesn't show; merged into the Skip scopes. */
    public static NavigableMap<Integer, Integer> hiddenRanges(EditorScene scene) {
        NavigableMap<Integer, Integer> hidden = new TreeMap<>();
        for (ClipRef clip : clips(scene)) {
            if (!clip.composed()) continue;
            int spanEnd = clip.placedAt() + clip.spanLength();
            if (clip.visibleStart() > clip.placedAt()) hidden.put(clip.placedAt(), clip.visibleStart());
            if (clip.visibleEnd() < spanEnd) hidden.put(clip.visibleEnd(), spanEnd);
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
        int at = Math.max(0, tick);
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

    /**
     * Lays the clips out in timeline order, carries every other keyframe along with the clip it sat in, then
     * leaves the replay, rebuilds the working replay and opens it again.
     */
    public static void apply(EditorState editorState, EditorScene scene) throws IOException {
        Path working = openReplay;
        KeyframeTrack track = clipTrack(scene);
        if (working == null || track == null || composing) return;
        List<ClipRef> before = clips(scene);
        if (before.isEmpty()) return;
        List<ClipRef> after = ClipComposer.layout(before);
        for (KeyframeTrack other : scene.keyframeTracks) {
            if (other != track) other.keyframesByTick = shifted(other.keyframesByTick, before, after);
        }
        TreeMap<Integer, Keyframe> placed = new TreeMap<>();
        for (ClipRef clip : after) {
            placed.put(clip.visibleStart(), new ClipKeyframeType.ClipKeyframe(clip,
                    com.moulberry.flashback.keyframe.interpolation.InterpolationType.LINEAR));
        }
        track.keyframesByTick = placed;
        ((ClearableHistory) scene).vector3$clearHistory();
        editorState.markDirty();

        ReplayArchive.Info info = ReplayArchive.read(working);
        if (info == null) throw new IOException("Cannot read the open replay");
        UUID id = info.meta().replayIdentifier;
        String name = info.meta().name;
        Minecraft minecraft = Minecraft.getInstance();
        RegistryAccess registries = minecraft.level.registryAccess();
        composing = true;
        minecraft.execute(() -> {
            if (minecraft.level != null) minecraft.level.disconnect(Component.empty());
            minecraft.disconnect(new GenericMessageScreen(Component.translatable("vector3.clips.composing")), false);
            Thread worker = new Thread(() -> rebuild(after, id, name, working, registries), "vector3-clip-compose");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void rebuild(List<ClipRef> clips, UUID id, String name, Path working, RegistryAccess registries) {
        Minecraft minecraft = Minecraft.getInstance();
        Exception failure = null;
        try {
            // The replay server still holds the working archive open until it has fully stopped.
            while (minecraft.getSingleplayerServer() != null) Thread.sleep(50);
            ClipComposer.compose(clips, id, name, working, registries);
        } catch (Exception exception) {
            failure = exception;
            Vector3.LOGGER.error("Could not compose the clips into {}", working, exception);
        }
        Exception error = failure;
        minecraft.execute(() -> {
            composing = false;
            if (error != null) {
                SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("vector3.clips.compose_failed"), Component.literal(String.valueOf(error.getMessage())));
            }
            Flashback.openReplayWorld(working);
        });
    }

    // Keyframes follow the clip whose composed span held them; ones outside every span stay put.
    private static TreeMap<Integer, Keyframe> shifted(TreeMap<Integer, Keyframe> keyframes, List<ClipRef> before,
            List<ClipRef> after) {
        TreeMap<Integer, Keyframe> result = new TreeMap<>();
        for (Map.Entry<Integer, Keyframe> entry : keyframes.entrySet()) {
            int tick = entry.getKey(), moved = tick;
            for (int i = 0; i < before.size(); i++) {
                ClipRef old = before.get(i);
                if (old.composed() && tick >= old.placedAt() && tick < old.placedAt() + old.spanLength()) {
                    moved = tick - old.placedAt() + old.spanStart() - after.get(i).spanStart() + after.get(i).placedAt();
                    break;
                }
            }
            result.put(Math.max(0, moved), entry.getValue());
        }
        return result;
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
