package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import ml.mypals.vectorthree.mixin.area.RenderSetupAccessor;
import ml.mypals.vectorthree.mixin.area.RenderTypeAccessor;
import ml.mypals.vectorthree.mixin.area.TextureBindingAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public final class AreaBlockEntityTranslucency {
    private static final Map<RenderType, RenderType> VARIANTS = new ConcurrentHashMap<>();
    private static final Set<RenderType> VARIANT_SET = ConcurrentHashMap.newKeySet();
    private static Vector4f currentModulator;

    private AreaBlockEntityTranslucency() {}

    static RenderType entityVariantOf(RenderType original) {
        if (VARIANT_SET.contains(original) || !DefaultVertexFormat.ENTITY.equals(original.format())) return original;
        Identifier texture = textureOf(original);
        if (texture == null) return original;
        return VARIANTS.computeIfAbsent(original, key -> register(RenderTypes.entityTranslucent(texture), texture.toString()));
    }

    static RenderType blockVariantOf(RenderType original) {
        RenderType translucent = RenderTypes.translucentMovingBlock();
        if (VARIANT_SET.contains(original) || !translucent.format().equals(original.format())) return original;
        return VARIANTS.computeIfAbsent(original, key -> register(translucent, "moving_block"));
    }

    private static RenderType register(RenderType translucent, String label) {
        RenderType variant = RenderType.create("vector3_area_translucent/" + label,
                ((RenderTypeAccessor) translucent).vector3$state());
        VARIANT_SET.add(variant);
        return variant;
    }

    private static Identifier textureOf(RenderType renderType) {
        Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) renderType).vector3$state())
                .vector3$textures();
        Object binding = textures.get("Sampler0");
        return binding == null ? null : ((TextureBindingAccessor) binding).vector3$location();
    }

    static void withModulator(Vector4f modulator, Runnable draw) {
        Vector4f previous = currentModulator;
        currentModulator = modulator;
        try {
            draw.run();
        } finally {
            currentModulator = previous;
        }
    }

    public static Vector4f modulatorFor(RenderType renderType) {
        return currentModulator != null && VARIANT_SET.contains(renderType) ? currentModulator : null;
    }
}
