package ml.mypals.vectorthree.fb.expression.editor;

import ml.mypals.vectorthree.core.expression.editor.Suggestion;
import ml.mypals.vectorthree.core.expression.editor.Completion;
import ml.mypals.vectorthree.core.expression.editor.Highlighter;
import ml.mypals.vectorthree.core.expression.editor.Hints;
import ml.mypals.vectorthree.core.expression.editor.Signatures;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiInputTextCallbackData;
import imgui.moulberry90.callback.ImGuiInputTextCallback;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiInputTextFlags;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiMouseCursor;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A multi-line input with syntax colours. ImGui's own input does the editing with its text made invisible; the
 * coloured text, caret and error underline are drawn over it from the same layout (lines of the font size, glyph
 * advances from calcTextSize). The box grows with its text, so it never scrolls and the two stay aligned.
 */
public final class CodeEditor {
    public static final class State {
        final ImString buffer;
        int cursor = -1;
        @Nullable Integer moveCursorTo;
        boolean focus;
        boolean active;
        @Nullable Completion.Context context;
        List<Suggestion> candidates = List.of();
        int selected;
        boolean scrollToSelected;
        private final Callback callback = new Callback(this);

        public State(String text) {
            buffer = new ImString(text, Math.max(256, text.length() * 2));
            buffer.inputData.isResizable = true;
        }

        public String text() {
            return buffer.get();
        }

        public void setText(String text) {
            buffer.set(text);
        }

        public boolean active() {
            return active;
        }

        public List<Suggestion> candidates() {
            return candidates;
        }

        public @Nullable Completion.Context context() {
            return context;
        }

        /** The suggestion Tab or Enter would insert. */
        public int selected() {
            return selected;
        }

        /** True once after the selection moved by keyboard, so the list can scroll to it. */
        public boolean consumeScroll() {
            boolean scroll = scrollToSelected;
            scrollToSelected = false;
            return scroll;
        }

        /** Replaces the word being typed with {@code completion} and puts the caret after it. */
        public void complete(String completion) {
            if (context == null) return;
            String text = text();
            int start = Math.min(context.wordStart(), text.length()), end = Math.clamp(cursor, start, text.length());
            buffer.set(text.substring(0, start) + completion + text.substring(end));
            moveCursorTo = start + completion.length();
            focus = true;
        }

        /** Inserts {@code snippet} at the caret (the end when there is none); a '|' in it marks where the caret goes. */
        public void insert(String snippet) {
            String text = text();
            int at = cursor < 0 ? text.length() : Math.min(cursor, text.length());
            int caret = snippet.indexOf('|');
            String clean = snippet.replace("|", "");
            buffer.set(text.substring(0, at) + clean + text.substring(at));
            moveCursorTo = at + (caret < 0 ? clean.length() : caret);
            focus = true;
        }
    }

    private static final class Callback extends ImGuiInputTextCallback {
        private final State state;

        Callback(State state) {
            this.state = state;
        }

        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            String text = data.getBuf();
            if (state.moveCursorTo != null) {
                int at = byteIndex(text, state.moveCursorTo);
                data.setCursorPos(at);
                data.setSelectionStart(at);
                data.setSelectionEnd(at);
                state.moveCursorTo = null;
            }
            boolean listed = !state.candidates.isEmpty() && state.context != null;
            if (data.getEventFlag() == ImGuiInputTextFlags.CallbackCompletion) {
                if (listed) complete(data);
            } else if (listed && !ImGui.getIO().getKeyShift()) {
                // The input has already handled these keys this frame (moved the caret a line, typed a newline);
                // with suggestions showing they are undone and used on the list instead.
                boolean up = ImGui.isKeyPressed(ImGuiKey.UpArrow), down = ImGui.isKeyPressed(ImGuiKey.DownArrow);
                if ((up || down) && state.cursor >= 0) {
                    int back = byteIndex(data.getBuf(), state.cursor);
                    data.setCursorPos(back);
                    data.setSelectionStart(back);
                    data.setSelectionEnd(back);
                    state.selected = Math.floorMod(state.selected + (down ? 1 : -1), state.candidates.size());
                    state.scrollToSelected = true;
                } else if (ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter)) {
                    String buffer = data.getBuf();
                    int at = charIndex(buffer, data.getCursorPos());
                    if (at > 0 && buffer.charAt(at - 1) == '\n') {
                        data.deleteChars(data.getCursorPos() - 1, 1);
                        complete(data);
                    }
                }
            }
            state.cursor = charIndex(data.getBuf(), data.getCursorPos());
        }

        private void complete(ImGuiInputTextCallbackData data) {
            int start = byteIndex(data.getBuf(), state.context.wordStart()), end = data.getCursorPos();
            if (end < start) return;
            Suggestion pick = state.candidates.get(Math.min(state.selected, state.candidates.size() - 1));
            data.deleteChars(start, end - start);
            data.insertChars(start, pick.insert());
        }
    }

    private static final int FLAGS = ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.CallbackCompletion
            | ImGuiInputTextFlags.NoHorizontalScroll;

    private CodeEditor() {}

    /** Draws the editor; true when the text changed. {@code errorAt} is a character to underline, or -1. */
    public static boolean render(String id, State state, boolean template, float width, int minLines,
            @Nullable Hints hints, int errorAt) {
        String before = state.text();
        float fontSize = ImGui.getTextLineHeight();
        float padX = ImGui.getStyle().getFramePaddingX(), padY = ImGui.getStyle().getFramePaddingY();
        int lines = 1;
        for (int i = 0; i < before.length(); i++) if (before.charAt(i) == '\n') lines++;
        float height = Math.max(minLines, lines) * fontSize + padY * 2 + fontSize * 0.5f;
        if (state.focus) {
            ImGui.setKeyboardFocusHere();
            state.focus = false;
        }
        ImGui.pushStyleColor(ImGuiCol.Text, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0, 0, 0, 0);
        boolean changed = ImGui.inputTextMultiline(id, state.buffer, width, height, FLAGS, state.callback);
        ImGui.popStyleColor(2);
        state.active = ImGui.isItemActive();
        boolean hovered = ImGui.isItemHovered();
        float minX = ImGui.getItemRectMinX(), minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX(), maxY = ImGui.getItemRectMaxY();
        String text = state.text();
        if (!state.active && state.cursor > text.length()) state.cursor = text.length();

        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(minX, minY, maxX, maxY, ImGui.getColorU32(state.active ? ImGuiCol.FrameBgActive
                : hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg), ImGui.getStyle().getFrameRounding());
        draw.pushClipRect(minX, minY, maxX, maxY, true);
        Layout layout = new Layout(text, minX + padX, minY + padY, fontSize);
        drawText(draw, layout, Highlighter.spans(text, template));
        if (errorAt >= 0 && errorAt <= text.length()) underline(draw, layout, errorAt);
        if (state.active && state.cursor >= 0 && (ImGui.getTime() % 1.2) < 0.8) {
            float cx = layout.x(state.cursor), cy = layout.y(state.cursor);
            draw.addLine(cx, cy, cx, cy + fontSize, ImGui.getColorU32(ImGuiCol.Text), 1.5f);
        }
        draw.popClipRect();

        state.context = state.active && state.cursor >= 0 ? Completion.at(text, state.cursor, template) : null;
        List<Suggestion> candidates = state.context == null ? List.of() : candidates(state.context, text, state.cursor, hints);
        if (!candidates.equals(state.candidates)) state.selected = 0;
        state.candidates = candidates;
        if (hovered && hints != null) hover(layout, text, template, hints);
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
        return changed;
    }

    /** One line of highlighted code as a clickable box, for showing an expression where there is no room to edit it. */
    public static boolean preview(String id, String text, boolean template, float width) {
        float fontSize = ImGui.getTextLineHeight();
        float padX = ImGui.getStyle().getFramePaddingX(), padY = ImGui.getStyle().getFramePaddingY();
        float height = fontSize + padY * 2;
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        float w = width > 0 ? width : Math.max(40, ImGui.getContentRegionAvailX() + width);
        boolean clicked = ImGui.invisibleButton(id, w, height);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x, y, x + w, y + height, ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg),
                ImGui.getStyle().getFrameRounding());
        int newline = text.indexOf('\n');
        String shown = newline < 0 ? text : text.substring(0, newline);
        draw.pushClipRect(x, y, x + w, y + height, true);
        drawText(draw, new Layout(shown, x + padX, y + padY, fontSize), Highlighter.spans(shown, template));
        if (newline >= 0) {
            float end = x + padX + ImGui.calcTextSizeX(shown, false) + fontSize * 0.5f;
            draw.addText(end, y + padY, ImGui.getColorU32(ImGuiCol.TextDisabled), "...");
        }
        draw.popClipRect();
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return clicked;
    }

    private static List<Suggestion> candidates(Completion.Context context, String text, int cursor, @Nullable Hints hints) {
        String prefix = context.prefix().toLowerCase(Locale.ROOT);
        List<Suggestion> all = new ArrayList<>();
        if (context.argumentOf() != null) {
            if (hints == null) return List.of();
            String after = text.substring(Math.min(cursor, text.length()));
            char quote = context.quote() != 0 ? context.quote() : '"';
            boolean closed = context.quote() != 0 && after.startsWith(String.valueOf(quote));
            String rest = closed ? after.substring(1) : after;
            String close = (closed ? "" : String.valueOf(quote)) + (rest.stripLeading().startsWith(")") ? "" : ")");
            for (Suggestion argument : hints.arguments(context.argumentOf())) {
                String value = argument.insert().replace("\\", "\\\\").replace(String.valueOf(quote), "\\" + quote);
                String insert = (context.quote() != 0 ? "" : String.valueOf(quote)) + value + close;
                all.add(new Suggestion(insert, argument.label(), argument.detail()));
            }
            return filter(all, prefix, true);
        }
        if (context.target() != null) {
            if (hints != null) {
                for (String member : hints.members(context.target())) {
                    all.add(new Suggestion(member.replace("()", "("), member, ""));
                }
            }
            return filter(all, prefix, false);
        }
        if (prefix.isEmpty()) return List.of();
        for (String local : Highlighter.locals(text)) all.add(new Suggestion(local, local, "let"));
        for (String builtin : Signatures.BUILTINS) all.add(new Suggestion(builtin, builtin, ""));
        for (String function : Signatures.FUNCTIONS.keySet()) {
            all.add(new Suggestion(function + "(", function + Signatures.of(function), I18n.get(Signatures.descriptionKey(function))));
        }
        if (hints != null) for (String name : hints.names()) all.add(new Suggestion(name, name, I18n.get("vector3.expression.editor.track")));
        return filter(all, prefix, false);
    }

    /** Starts-with matches first, then contains; an argument also matches on its value (a UUID being pasted). */
    private static List<Suggestion> filter(List<Suggestion> all, String prefix, boolean matchValue) {
        List<Suggestion> starts = new ArrayList<>(), contains = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Suggestion candidate : all) {
            String label = candidate.label().toLowerCase(Locale.ROOT);
            String value = matchValue ? candidate.insert().toLowerCase(Locale.ROOT) : "";
            if (!matchValue && (label.equals(prefix) || candidate.insert().equalsIgnoreCase(prefix))) continue;
            if (!seen.add(candidate.insert())) continue;
            if (prefix.isEmpty() || label.startsWith(prefix)) starts.add(candidate);
            else if (label.contains(prefix) || matchValue && value.contains(prefix)) contains.add(candidate);
        }
        starts.addAll(contains);
        return starts.size() > 60 ? starts.subList(0, 60) : starts;
    }

    private static void hover(Layout layout, String text, boolean template, Hints hints) {
        int at = layout.index(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (at < 0) return;
        for (Highlighter.Span span : Highlighter.spans(text, template)) {
            if (at < span.start() || at >= span.end()) continue;
            switch (span.style()) {
                case FUNCTION -> {
                    String name = text.substring(span.start(), span.end());
                    if (Signatures.FUNCTIONS.containsKey(name)) {
                        ImGui.setTooltip(name + Signatures.of(name) + "\n" + I18n.get(Signatures.descriptionKey(name)));
                    }
                }
                case TRACK, LOCAL, BUILTIN, MEMBER -> {
                    String chain = Completion.chainEndingAt(text, span.end());
                    String description = chain == null ? null : hints.describe(chain);
                    if (description != null) ImGui.setTooltip(chain + "\n" + description);
                }
                default -> {}
            }
            return;
        }
    }

    private static int color(Highlighter.Style style) {
        return switch (style) {
            case PLAIN, TRACK -> ImGui.getColorU32(0.95f, 0.72f, 0.80f, 1);
            case LITERAL -> ImGui.getColorU32(ImGuiCol.Text);
            case COMMENT -> ImGui.getColorU32(0.48f, 0.65f, 0.48f, 1);
            case STRING -> ImGui.getColorU32(0.92f, 0.64f, 0.46f, 1);
            case NUMBER -> ImGui.getColorU32(0.72f, 0.86f, 0.56f, 1);
            case KEYWORD -> ImGui.getColorU32(0.80f, 0.58f, 0.95f, 1);
            case BUILTIN -> ImGui.getColorU32(0.42f, 0.76f, 0.98f, 1);
            case FUNCTION -> ImGui.getColorU32(0.96f, 0.86f, 0.48f, 1);
            case MEMBER -> ImGui.getColorU32(0.64f, 0.82f, 0.96f, 1);
            case LOCAL -> ImGui.getColorU32(0.52f, 0.92f, 0.84f, 1);
            case OPERATOR -> ImGui.getColorU32(0.78f, 0.78f, 0.78f, 1);
            case BRACE -> ImGui.getColorU32(0.98f, 0.48f, 0.72f, 1);
        };
    }

    private static void drawText(ImDrawList draw, Layout layout, List<Highlighter.Span> spans) {
        String text = layout.text;
        int drawn = 0;
        for (Highlighter.Span span : spans) {
            if (drawn < span.start()) segment(draw, layout, drawn, span.start(), ImGui.getColorU32(ImGuiCol.Text));
            segment(draw, layout, span.start(), span.end(), color(span.style()));
            drawn = span.end();
        }
        if (drawn < text.length()) segment(draw, layout, drawn, text.length(), ImGui.getColorU32(ImGuiCol.Text));
    }

    private static void segment(ImDrawList draw, Layout layout, int start, int end, int color) {
        int from = start;
        while (from < end) {
            int newline = layout.text.indexOf('\n', from);
            int stop = newline < 0 || newline >= end ? end : newline;
            if (stop > from) draw.addText(layout.x(from), layout.y(from), color, layout.text.substring(from, stop));
            from = stop + 1;
        }
    }

    private static void underline(ImDrawList draw, Layout layout, int at) {
        String text = layout.text;
        int end = at;
        while (end < text.length() && Highlighter.identifierPart(text.charAt(end))) end++;
        if (end == at) end = Math.min(text.length(), at + 1);
        float x0 = layout.x(at), x1 = end > at && text.charAt(end - 1) != '\n' ? layout.x(end) : x0 + layout.fontSize * 0.5f;
        float y = layout.y(at) + layout.fontSize;
        int red = ImGui.getColorU32(1, 0.35f, 0.35f, 1);
        for (float x = x0; x < x1; x += 4) {
            draw.addLine(x, y, Math.min(x + 2, x1), y - 2, red, 1);
            draw.addLine(Math.min(x + 2, x1), y - 2, Math.min(x + 4, x1), y, red, 1);
        }
    }

    /** Where each character of the text is drawn. */
    private record Layout(String text, float originX, float originY, float fontSize, int[] lineStarts) {
        Layout(String text, float originX, float originY, float fontSize) {
            this(text, originX, originY, fontSize, starts(text));
        }

        private static int[] starts(String text) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') starts.add(i + 1);
            return starts.stream().mapToInt(Integer::intValue).toArray();
        }

        int line(int index) {
            int line = 0;
            while (line + 1 < lineStarts.length && lineStarts[line + 1] <= index) line++;
            return line;
        }

        float x(int index) {
            int start = lineStarts[line(index)];
            return originX + ImGui.calcTextSizeX(text.substring(start, Math.min(index, text.length())), false);
        }

        float y(int index) {
            return originY + line(index) * fontSize;
        }

        /** The character under a point, or -1. */
        int index(float px, float py) {
            int line = (int) Math.floor((py - originY) / fontSize);
            if (line < 0 || line >= lineStarts.length) return -1;
            int start = lineStarts[line];
            int end = line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
            for (int i = start; i < end; i++) {
                if (originX + ImGui.calcTextSizeX(text.substring(start, i + 1), false) > px) return i;
            }
            return -1;
        }
    }

    static int byteIndex(String text, int charIndex) {
        return text.substring(0, Math.clamp(charIndex, 0, text.length())).getBytes(StandardCharsets.UTF_8).length;
    }

    static int charIndex(String text, int byteIndex) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, 0, Math.clamp(byteIndex, 0, bytes.length), StandardCharsets.UTF_8).length();
    }
}
