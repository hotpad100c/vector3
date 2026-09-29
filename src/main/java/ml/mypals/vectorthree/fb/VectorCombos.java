package ml.mypals.vectorthree.fb;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import ml.mypals.vectorthree.core.camera.target.Target;
import ml.mypals.vectorthree.core.entity.BodyPart;

import java.util.function.Function;

/** Combo boxes for core enums, which can't implement Flashback's combo interface themselves. */
public final class VectorCombos {
    private VectorCombos() {}

    public static BodyPart bodyPart(String label, BodyPart current) {
        return FlashbackPorts.fromFlashback(ImGuiHelper.enumCombo(label, FlashbackPorts.toFlashback(current)));
    }

    public static Target.Kind targetKind(String label, Target.Kind current) {
        return labeled(label, current, Target.Kind.values(), Target.Kind::label);
    }

    private static <T> T labeled(String label, T current, T[] values, Function<T, String> text) {
        String[] labels = new String[values.length];
        int[] index = {0};
        for (int i = 0; i < values.length; i++) {
            labels[i] = text.apply(values[i]);
            if (values[i] == current) index[0] = i;
        }
        return ImGuiHelper.combo(label, index, labels) ? values[index[0]] : current;
    }
}
