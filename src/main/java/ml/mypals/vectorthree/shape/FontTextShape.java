package ml.mypals.vectorthree.shape;

import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

public final class FontTextShape extends TextShape {
    public String font;

    public FontTextShape(TextSettings settings, Color color, boolean seeThrough) {
        super(Shape.RenderingType.BATCH, transformer -> {}, Vec3.ZERO,
                Arrays.asList(settings.value().split("\\R", -1)), List.of(color),
                BillBoardMode.valueOf(settings.billboard()), seeThrough, settings.shadow(), settings.outline());
        font = settings.fontOrDefault();
    }
}
