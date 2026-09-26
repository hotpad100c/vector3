package ml.mypals.vectorthree.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// Sodium and Iris are optional: their mixins only apply when the mod is installed.
public final class Vector3MixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "ml.mypals.vectorthree.mixin.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName;
        if (name.startsWith("area.sodium.")) return FabricLoader.getInstance().isModLoaded("sodium");
        if (name.startsWith("iris.skyOverride.")) return FabricLoader.getInstance().isModLoaded("iris");
        if (name.startsWith("iris.pack.")) return FabricLoader.getInstance().isModLoaded("iris");
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
