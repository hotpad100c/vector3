package ml.mypals.vectorthree.fb.clips;

import com.moulberry.flashback.editor.ui.ReplayUI;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.mc.clips.CoverTexture;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The icon.png Flashback saves in every replay, as ImGui textures for the clip bars. */
public final class ClipCovers {
    record Cover(CoverTexture texture) {
        // Asked every frame: the renderer drops ids of textures a frame didn't draw.
        long textureId() {
            return ReplayUI.imguiRenderer.getTextureId(texture.view());
        }

        float aspect() {
            return texture.aspect();
        }
    }

    private static final Map<String, Optional<Cover>> COVERS = new HashMap<>();

    private ClipCovers() {}

    /** Replay covers are only shown inside the editor, so they go when it closes. */
    public static void clear() {
        try {
            for (Optional<Cover> cover : COVERS.values()) {
                if (cover.isEmpty()) continue;
                try {
                    cover.get().texture().close();
                } catch (RuntimeException exception) {
                    Mod.LOGGER.warn("Could not free a clip cover", exception);
                }
            }
        } finally {
            COVERS.clear();
        }
    }

    static @Nullable Cover of(String source) {
        Optional<Cover> cover = COVERS.get(source);
        // Never hand out a texture that was freed under us; load it again.
        if (cover != null && cover.isPresent() && !cover.get().texture().alive()) cover = null;
        if (cover == null) {
            cover = Optional.ofNullable(CoverTexture.load(source)).map(Cover::new);
            COVERS.put(source, cover);
        }
        return cover.orElse(null);
    }
}
