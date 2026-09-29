package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiTableFlags;
import imgui.moulberry90.flag.ImGuiTabItemFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.expression.editor.CodeEditor;
import ml.mypals.vectorthree.expression.editor.Signatures;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Value;
import ml.mypals.vectorthree.flashback.EntityPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The expression editor window: the expression being edited (with colours, suggestions, what each variable holds and
 * snippets), the scene's global variables, and a reference of the language.
 */
public final class ExpressionEditor {
    private static final int EXPRESSION = 0, GLOBALS = 1, REFERENCE = 2;
    private static final int PREVIEW_FRAMES = 10;
    private static final Pattern ENTITY = Pattern.compile("^\\s*entity\\(\\s*\"([0-9a-fA-F-]{36})\"\\s*\\)\\s*$");
    private static final String[][] SNIPPETS = {
            {"let", "let x = |;\n"}, {"a ? b : c", "| ? 1 : 0"}, {"// ", "// |"}, {"global.", "global.|"},
            {"self.local()", "self.local(|)"}, {"entity(\"\")", "entity(\"|\")"}, {"vec()", "vec(|, 0, 0)"},
            {"format()", "format(\"%.2f\", |)"}, {"lerp()", "lerp(|, 1, 0.5)"}, {"wiggle()", "wiggle(|2, 0.1)"},
    };

    private static final ImBoolean OPEN = new ImBoolean(false);
    private static @Nullable KeyframeTrack track;
    private static String widget = "", title = "";
    private static int component;
    private static boolean template;
    private static @Nullable CodeEditor.State editor;
    private static int requestedTab = -1;
    private static @Nullable Preview preview;
    private static int previewAge;
    private static final Map<Integer, CodeEditor.State> GLOBAL_EDITORS = new HashMap<>();
    private static final Map<Integer, ImString> GLOBAL_NAMES = new HashMap<>();
    private static @Nullable Preview globalPreview;
    private static int globalPreviewAge;
    private static java.lang.ref.WeakReference<EditorState> shownState = new java.lang.ref.WeakReference<>(null);

    private ExpressionEditor() {}

    public static void open(KeyframeTrack editing, ExpressionBinding binding, boolean text, String name) {
        track = editing;
        widget = binding.widget();
        component = binding.component();
        template = text;
        title = name;
        editor = new CodeEditor.State(binding.source());
        editor.insert("");
        preview = null;
        OPEN.set(true);
        requestedTab = EXPRESSION;
    }

    public static void openGlobals() {
        OPEN.set(true);
        requestedTab = GLOBALS;
    }

    public static boolean editing(KeyframeTrack candidate, ExpressionBinding binding) {
        return OPEN.get() && candidate == track && binding.targets(widget, component);
    }

    /** Drops everything tied to the replay that was open: the expression being edited and the globals' inputs. */
    public static void reset() {
        track = null;
        editor = null;
        preview = null;
        globalPreview = null;
        GLOBAL_EDITORS.clear();
        GLOBAL_NAMES.clear();
    }

    public static void render() {
        EditorState state = EditorStateManager.getCurrent();
        if (state != shownState.get()) {
            reset();
            Globals.reset();
            shownState = new java.lang.ref.WeakReference<>(state);
        }
        if (!OPEN.get()) return;
        ImGui.setNextWindowSize(760, 600, ImGuiCond.FirstUseEver);
        if (ImGui.begin(I18n.get("vector3.expression.editor.title") + "###vector3_expression_editor", OPEN)) {
            if (ImGui.beginTabBar("##vector3_expression_tabs")) {
                if (track != null && ImGui.beginTabItem(I18n.get("vector3.expression.editor.tab.expression"), tab(EXPRESSION))) {
                    expressionTab();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(I18n.get("vector3.expression.editor.tab.globals"), tab(GLOBALS))) {
                    globalsTab();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(I18n.get("vector3.expression.editor.tab.reference"), tab(REFERENCE))) {
                    referenceTab();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
            requestedTab = -1;
        }
        ImGui.end();
    }

    private static int tab(int tab) {
        return requestedTab == tab ? ImGuiTabItemFlags.SetSelected : 0;
    }

    private static void expressionTab() {
        KeyframeTrack current = track;
        ExpressionBinding binding = current == null ? null : ExpressionBindings.find(current, widget, component);
        if (current == null || binding == null || editor == null) {
            ImGui.textDisabled(I18n.get("vector3.expression.editor.removed"));
            return;
        }
        ImGui.text(ExpressionUi.trackName(current) + "  ›  " + title);
        ImGui.sameLine();
        ImBoolean enabled = new ImBoolean(binding.enabled());
        if (ImGui.checkbox(I18n.get("vector3.expression.editor.enabled"), enabled)) {
            ExpressionBinding next = binding.withEnabled(enabled.get());
            edit(scene -> ExpressionBindings.put(current, widget, component, next));
        }
        if (template) ImGui.textDisabled(I18n.get("vector3.expression.editor.template_hint"));

        ExpressionBindings.Status status = ExpressionBindings.status(current, binding);
        if (preview == null || ++previewAge > PREVIEW_FRAMES) {
            preview = new Preview(current, TimelineWindow.getCursorTick(), status == null ? Map.of() : status.lets(),
                    status == null ? null : status.own());
            previewAge = 0;
        }
        ExprError compile = ExpressionRuntime.check(editor.text(), template);
        int errorAt = compile != null ? compile.position()
                : status != null && status.error() != null ? status.position() : -1;
        if (CodeEditor.render("##vector3_expression_source", editor, template, ImGui.getContentRegionAvailX(), 6, preview, errorAt)) {
            ExpressionBinding next = binding.withSource(editor.text());
            edit(scene -> ExpressionBindings.put(current, widget, component, next));
            preview = null;
        }
        result(compile, status);
        suggestions(editor);

        if (ImGui.beginTable("##vector3_expression_info", 2, ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV)) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            variables(status);
            ImGui.tableNextColumn();
            snippets(editor);
            ImGui.endTable();
        }
    }

    private static void result(@Nullable ExprError compile, ExpressionBindings.@Nullable Status status) {
        if (compile != null) {
            error(compile.getMessage(), compile.position());
        } else if (status == null) {
            ImGui.textDisabled(I18n.get("vector3.expression.editor.not_run"));
        } else if (status.missing()) {
            ImGui.textDisabled(I18n.get("vector3.expression.missing"));
        } else if (status.error() != null) {
            error(status.error(), status.position());
        } else if (status.result() != null) {
            ImGui.textDisabled(I18n.get("vector3.expression.editor.result") + ": " + status.result().type() + " = "
                    + shorten(status.result().text()));
        }
    }

    private static void error(String message, int position) {
        String text = position >= 0 ? I18n.get("vector3.expression.error_at", position + 1, message)
                : I18n.get("vector3.expression.error", message);
        ImGui.pushTextWrapPos(0);
        ImGui.textColored(0.95f, 0.42f, 0.42f, 1f, text);
        ImGui.popTextWrapPos();
    }

    private static void suggestions(CodeEditor.State state) {
        ImGui.separator();
        ImGui.textDisabled(I18n.get("vector3.expression.editor.suggestions"));
        float height = ImGui.getTextLineHeightWithSpacing() * 5;
        if (ImGui.beginChild("##vector3_expression_suggestions", 0, height, false)) {
            List<CodeEditor.Suggestion> candidates = state.candidates();
            if (candidates.isEmpty()) ImGui.textDisabled(I18n.get("vector3.expression.editor.no_suggestions"));
            for (int i = 0; i < candidates.size(); i++) {
                CodeEditor.Suggestion candidate = candidates.get(i);
                boolean selected = i == state.selected();
                if (selected && state.consumeScroll()) ImGui.setScrollHereY(0.5f);
                if (ImGui.selectable(candidate.label() + "##suggestion" + i, selected)) state.complete(candidate.insert());
                if (!candidate.detail().isEmpty()) {
                    ImGui.sameLine();
                    ImGui.textDisabled(candidate.detail());
                }
            }
        }
        ImGui.endChild();
    }

    private static void variables(ExpressionBindings.@Nullable Status status) {
        ImGui.textDisabled(I18n.get("vector3.expression.editor.variables"));
        if (status != null && status.own() != null) row("value", status.own());
        if (status != null) status.lets().forEach(ExpressionEditor::row);
        for (GlobalVariable global : Globals.of(EvalContext.currentScene())) {
            Globals.Status globalStatus = Globals.status(global.name());
            if (globalStatus != null && globalStatus.value() != null) row("global." + global.name(), globalStatus.value());
        }
    }

    private static void row(String name, Value value) {
        ImGui.text(name);
        ImGui.sameLine();
        ImGui.textDisabled(value instanceof Value.Obj ? value.type() : value.type() + " = " + shorten(value.text()));
    }

    private static void snippets(CodeEditor.State state) {
        ImGui.textDisabled(I18n.get("vector3.expression.editor.snippets"));
        for (int i = 0; i < SNIPPETS.length; i++) {
            if (i % 2 == 1) ImGui.sameLine(ImGui.getContentRegionAvailX() / 2 + ImGui.getCursorPosX() - ImGui.getStyle().getItemSpacingX());
            if (ImGui.smallButton(SNIPPETS[i][0] + "##snippet" + i)) {
                String snippet = SNIPPETS[i][1];
                state.insert(template && !ml.mypals.vectorthree.expression.editor.Completion.insideBraces(state.text(),
                        Math.max(0, cursor(state))) ? "{" + snippet + "}" : snippet);
            }
        }
    }

    private static int cursor(CodeEditor.State state) {
        return state.context() != null ? state.context().wordStart() + state.context().prefix().length() : state.text().length();
    }

    private static void globalsTab() {
        EditorScene scene = EvalContext.currentScene();
        if (scene == null) {
            ImGui.textDisabled(I18n.get("vector3.expression.editor.no_scene"));
            return;
        }
        ImGui.pushTextWrapPos(0);
        ImGui.textDisabled(I18n.get("vector3.expression.editor.globals_hint"));
        ImGui.popTextWrapPos();
        List<GlobalVariable> globals = Globals.of(scene);
        float tick = TimelineWindow.getCursorTick();
        if (globalPreview == null || ++globalPreviewAge > PREVIEW_FRAMES) {
            for (GlobalVariable global : globals) Globals.preview(global.name(), tick);
            globalPreview = new Preview(null, tick, Map.of(), null);
            globalPreviewAge = 0;
        }
        for (int i = 0; i < globals.size(); i++) {
            GlobalVariable global = globals.get(i);
            int index = i;
            ImGui.pushID("vector3_global_" + i);
            ImGui.separator();
            ImString name = GLOBAL_NAMES.computeIfAbsent(i, key -> new ImString(global.name(), 64));
            // A valid, unused name that differs was changed elsewhere (undo, another row removed); an invalid one is
            // still being typed.
            if (!name.get().equals(global.name()) && Globals.validName(name.get())
                    && globals.stream().noneMatch(other -> other.name().equals(name.get()))) {
                name.set(global.name());
            }
            ImGui.textDisabled("global.");
            ImGui.sameLine(0, 0);
            ImGui.setNextItemWidth(160);
            if (ImGui.inputText("##name", name) && Globals.validName(name.get()) && globals.stream().noneMatch(
                    other -> other != global && other.name().equals(name.get()))) {
                String renamed = name.get();
                edit(s -> replaceGlobal(s, index, global.withName(renamed)));
            }
            if (!Globals.validName(name.get())) {
                ImGui.sameLine();
                ImGui.textColored(0.95f, 0.42f, 0.42f, 1f, I18n.get("vector3.expression.editor.invalid_name"));
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(220);
            UUID current = entityOf(global.source());
            UUID picked = EntityPicker.combo(I18n.get("vector3.expression.editor.pick_entity") + "##pick", current);
            if (picked != null && !picked.equals(current)) {
                GlobalVariable next = global.withSource("entity(\"" + picked + "\")");
                edit(s -> replaceGlobal(s, index, next));
                GLOBAL_EDITORS.remove(i);
            }
            ImGui.sameLine();
            if (ImGui.button("x")) {
                edit(s -> {
                    List<GlobalVariable> next = new ArrayList<>(Globals.of(s));
                    if (index < next.size()) next.remove(index);
                    Globals.set(s, next);
                });
                GLOBAL_EDITORS.clear();
                GLOBAL_NAMES.clear();
                ImGui.popID();
                break;
            }
            CodeEditor.State state = GLOBAL_EDITORS.computeIfAbsent(i, key -> new CodeEditor.State(global.source()));
            if (!state.active() && !state.text().equals(global.source())) state.setText(global.source());
            Globals.Status status = Globals.status(global.name());
            ExprError compile = ExpressionRuntime.check(state.text(), false);
            int errorAt = compile != null ? compile.position() : status != null && status.error() != null ? status.position() : -1;
            if (CodeEditor.render("##source", state, false, ImGui.getContentRegionAvailX(), 1, globalPreview, errorAt)) {
                GlobalVariable next = global.withSource(state.text());
                edit(s -> replaceGlobal(s, index, next));
                globalPreview = null;
            }
            if (compile != null) {
                error(compile.getMessage(), compile.position());
            } else if (status != null && status.error() != null) {
                error(status.error(), status.position());
            } else if (status != null && status.value() != null) {
                row("=", status.value());
            }
            if (state.active() && !state.candidates().isEmpty()) {
                List<String> shown = new ArrayList<>();
                int first = Math.max(0, Math.min(state.selected() - 3, state.candidates().size() - 8));
                for (int c = first; c < Math.min(first + 8, state.candidates().size()); c++) {
                    String candidate = state.candidates().get(c).label();
                    shown.add(c == state.selected() ? "[" + candidate + "]" : candidate);
                }
                ImGui.textDisabled(String.join("  ", shown));
            }
            ImGui.popID();
        }
        ImGui.separator();
        if (ImGui.button(I18n.get("vector3.expression.editor.add_global"))) {
            int number = globals.size() + 1;
            while (taken(globals, "g" + number)) number++;
            String added = "g" + number;
            edit(s -> {
                List<GlobalVariable> next = new ArrayList<>(Globals.of(s));
                next.add(new GlobalVariable(added, "0"));
                Globals.set(s, next);
            });
        }
    }

    private static boolean taken(List<GlobalVariable> globals, String name) {
        return globals.stream().anyMatch(global -> global.name().equals(name));
    }

    private static void replaceGlobal(EditorScene scene, int index, GlobalVariable global) {
        List<GlobalVariable> next = new ArrayList<>(Globals.of(scene));
        if (index < next.size()) next.set(index, global);
        Globals.set(scene, next);
    }

    private static @Nullable UUID entityOf(String source) {
        Matcher matcher = ENTITY.matcher(source);
        if (!matcher.matches()) return null;
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void referenceTab() {
        ImGui.pushTextWrapPos(0);
        ImGui.textDisabled(I18n.get("vector3.expression.editor.reference_hint"));
        ImGui.popTextWrapPos();
        if (ImGui.collapsingHeader(I18n.get("vector3.expression.editor.reference.operators"))) {
            ImGui.pushTextWrapPos(0);
            ImGui.text(I18n.get("vector3.expression.editor.reference.operators_text"));
            ImGui.popTextWrapPos();
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.expression.editor.reference.variables"))) {
            for (String name : List.of("time", "tick", "value", "self", "camera", "global", "pi", "e", "true", "false")) {
                ImGui.text(name);
                ImGui.sameLine(120);
                ImGui.textDisabled(I18n.get("vector3.expression.var." + name));
            }
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.expression.editor.reference.functions"))) {
            for (String function : Signatures.FUNCTIONS.keySet()) {
                ImGui.text(function + Signatures.of(function));
                ImGui.sameLine(260);
                ImGui.textDisabled(I18n.get(Signatures.descriptionKey(function)));
            }
        }
    }

    /** Edits the scene from outside the timeline's own lock, then re-applies the keyframes. */
    private static void edit(Consumer<EditorScene> body) {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) return;
        long stamp = state.acquireWrite();
        try {
            body.accept(state.getCurrentScene(stamp));
        } finally {
            state.release(stamp);
        }
        state.markDirty();
        ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
    }

    private static String shorten(String text) {
        String line = text.replace('\n', ' ');
        return line.length() > 100 ? line.substring(0, 100) + "..." : line;
    }
}
