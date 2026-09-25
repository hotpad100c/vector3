package ml.mypals.vectorthree.prefab;

import org.jetbrains.annotations.Nullable;

/** Implemented on Flashback's Keyframe (the group it belongs to), KeyframeTrack (pre-keyframe saves) and EditorScene (the groups). */
public interface PrefabGroupHolder {
    interface Track {
        @Nullable String vector3$prefabGroup();

        void vector3$setPrefabGroup(@Nullable String id);
    }

    interface Keyframe {
        @Nullable String vector3$group();

        void vector3$setGroup(@Nullable String id);
    }

    interface Scene {
        PrefabGroupStore vector3$prefabGroups();
    }
}
