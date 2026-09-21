package ml.mypals.vectorthree.shape;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.util.List;

public final class TextFormatting {
    private TextFormatting() {}

    public static FormattedCharSequence[] format(List<String> lines) {
        return lines.stream().map(TextFormatting::format)
                .toArray(FormattedCharSequence[]::new);
    }

    private static FormattedCharSequence format(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text))
                        .result().map(Component::getVisualOrderText).orElseGet(() -> legacy(text));
            } catch (RuntimeException ignored) {
                // Incomplete JSON while editing falls back to visible literal text.
            }
        }
        return legacy(text);
    }

    private static FormattedCharSequence legacy(String text) {
        return sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink);
    }
}
