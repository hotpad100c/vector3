package ml.mypals.vectorthree.prefab;

import org.jetbrains.annotations.Nullable;

/** Implemented on Flashback's KeyframeTrack (the group it belongs to) and EditorScene (the groups). */
public interface PrefabGroupHolder {
    interface Track {
        @Nullable String vector3$prefabGroup();

        void vector3$setPrefabGroup(@Nullable String id);
    }

    interface Scene {
        PrefabGroupStore vector3$prefabGroups();
    }
}
