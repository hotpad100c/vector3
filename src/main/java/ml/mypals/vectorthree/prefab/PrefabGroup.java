package ml.mypals.vectorthree.prefab;

import org.jetbrains.annotations.Nullable;

/**
 * Tracks moved and edited together, saved with the replay. A placed prefab keeps its prefab and
 * placement so it can be re-placed; a group made from a selection has neither.
 */
public record PrefabGroup(String id, String name, @Nullable Prefab prefab, @Nullable PrefabTransform transform,
                          float timeScale, int startTick) {
    public boolean placed() {
        return prefab != null && transform != null;
    }

    public PrefabGroup withName(String name) {
        return new PrefabGroup(id, name, prefab, transform, timeScale, startTick);
    }

    public PrefabGroup withStartTick(int startTick) {
        return new PrefabGroup(id, name, prefab, transform, timeScale, startTick);
    }

    public PrefabGroup withPlacement(PrefabTransform transform, float timeScale, int startTick) {
        return new PrefabGroup(id, name, prefab, transform, timeScale, startTick);
    }
}
