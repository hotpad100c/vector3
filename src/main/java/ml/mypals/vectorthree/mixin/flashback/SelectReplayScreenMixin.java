package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import ml.mypals.vectorthree.clips.EmptyProject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectReplayScreen.class)
public abstract class SelectReplayScreenMixin extends Screen {
    protected SelectReplayScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void vector3$addEmptyProjectButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.translatable("vector3.clips.empty_project"),
                button -> EmptyProject.request(this)).bounds(width - 108, 6, 100, 20).build());
    }
}
