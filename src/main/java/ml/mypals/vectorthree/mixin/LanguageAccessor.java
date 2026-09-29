package ml.mypals.vectorthree.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientLanguage.class)
public interface LanguageAccessor {
    @Accessor("storage")
    Map<String, String> vector3$storage();
}
