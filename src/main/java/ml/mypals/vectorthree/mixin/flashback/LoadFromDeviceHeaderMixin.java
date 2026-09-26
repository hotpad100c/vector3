package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.screen.select_replay.ReplaySelectionEntry;
import ml.mypals.vectorthree.clips.EmptyProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The list's "Load replay from file" row shares its width with the empty project button.
@Mixin(ReplaySelectionEntry.LoadFromDeviceHeader.class)
public abstract class LoadFromDeviceHeaderMixin extends ReplaySelectionEntry {
    @Shadow @Final private static Component LOAD_REPLAY_LABEL;
    @Shadow @Final private static WidgetSprites SPRITES;
    @Shadow @Final private Minecraft minecraft;

    @Unique private static final Component EMPTY_PROJECT = Component.translatable("vector3.clips.empty_project");

    @Inject(method = "extractContent", at = @At("HEAD"), cancellable = true)
    private void vector3$splitRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick,
            CallbackInfo ci) {
        ci.cancel();
        int left = getContentX() + 4, top = getContentY() + 2, height = getContentHeight() - 4;
        int half = (getContentWidth() - 8 - 4) / 2;
        int right = left + half + 4;
        boolean overRight = hovered && mouseX >= right;
        EmptyProject.hoveredInList = overRight;
        vector3$button(graphics, LOAD_REPLAY_LABEL, left, top, half, height, hovered && !overRight);
        vector3$button(graphics, EMPTY_PROJECT, right, top, half, height, overRight);
    }

    @Unique
    private void vector3$button(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, int height, boolean highlighted) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(true, highlighted), x, y, width, height);
        int textY = y + (height - minecraft.font.lineHeight) / 2 + 1;
        graphics.text(minecraft.font, label, x + (width - minecraft.font.width(label)) / 2, textY, -1, true);
    }
}
