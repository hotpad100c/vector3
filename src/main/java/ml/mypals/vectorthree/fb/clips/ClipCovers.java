package ml.mypals.vectorthree.fb.clips;

import com.moulberry.flashback.editor.ui.ReplayUI;
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
        COVERS.values().forEach(cover -> cover.ifPresent(value -> value.texture().close()));
        COVERS.clear();
    }

    static @Nullable Cover of(String source) {
        return COVERS.computeIfAbsent(source, key -> Optional.ofNullable(CoverTexture.load(key)).map(Cover::new)).orElse(null);
    }
}
