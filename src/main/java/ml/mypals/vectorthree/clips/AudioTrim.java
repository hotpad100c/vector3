package ml.mypals.vectorthree.clips;

import java.nio.file.Path;

/** Implemented on Flashback's AudioKeyframe by AudioKeyframeTrimMixin: where in the file it starts, and how long it plays. */
public interface AudioTrim {
    int vector3$audioIn();

    /** Ticks to play, or -1 for the rest of the file. */
    int vector3$audioLength();

    void vector3$setAudioTrim(int in, int length);

    /** The whole file's length in ticks, or -1 when it failed to load. */
    int vector3$audioTicks();

    /** Points the keyframe at another file, playing all of it. */
    void vector3$setPath(Path path);
}
