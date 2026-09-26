package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayExporter;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * "Empty project": a copy of a template replay of nothing but void. Making a valid replay from scratch would mean
 * faking its registry and login packets, so the template is recorded for real the first time: a throwaway void
 * world is created, recorded for a moment and deleted again.
 */
public final class EmptyProject {
    private enum Stage { IDLE, WAITING_FOR_WORLD, RECORDING, LEAVING }

    private static final String WORLD = "vector3_empty_project_template";
    private static final int SETTLE_TICKS = 40;
    private static final int RECORD_TICKS = 40;

    public static boolean hoveredInList;
    private static Stage stage = Stage.IDLE;
    private static int ticks;

    private EmptyProject() {}

    private static Path template() {
        return Flashback.getDataDirectory().resolve("vector3_empty_template.zip");
    }

    public static void request(Screen parent) {
        if (stage != Stage.IDLE) return;
        if (Files.isRegularFile(template())) {
            openCopy();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings(WORLD, GameType.SPECTATOR,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        stage = Stage.WAITING_FOR_WORLD;
        ticks = 0;
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                registries -> WorldPresets.createNormalWorldDimensions(registries).replaceOverworldGenerator(registries,
                        new FlatLevelSource(registries.lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                                .getOrThrow(FlatLevelGeneratorPresets.THE_VOID).value().settings())),
                parent);
    }

    public static void tick(Minecraft minecraft) {
        switch (stage) {
            case IDLE -> {}
            case WAITING_FOR_WORLD -> {
                if (minecraft.level == null || minecraft.player == null) return;
                if (++ticks < SETTLE_TICKS) return;
                Flashback.startRecordingReplay();
                stage = Stage.RECORDING;
                ticks = 0;
            }
            case RECORDING -> {
                if (++ticks < RECORD_TICKS) return;
                saveTemplate();
                stage = Stage.LEAVING;
                minecraft.disconnect(new GenericMessageScreen(Component.translatable("vector3.clips.empty_project_preparing")), false);
            }
            case LEAVING -> {
                if (minecraft.level != null || minecraft.getSingleplayerServer() != null) return;
                stage = Stage.IDLE;
                deleteWorld(minecraft);
                if (Files.isRegularFile(template())) openCopy();
            }
        }
    }

    private static void saveTemplate() {
        Recorder recorder = Flashback.RECORDER;
        if (recorder == null) return;
        Flashback.RECORDER = null;
        try {
            recorder.endTickWithContext(true);
            ReplayExporter.export(recorder.finish(), template(), "Empty project");
        } catch (RuntimeException exception) {
            Vector3.LOGGER.error("Could not record the empty project template", exception);
        }
    }

    private static void deleteWorld(Minecraft minecraft) {
        Path world = minecraft.getLevelSource().getBaseDir().resolve(WORLD);
        if (!Files.exists(world)) return;
        try (Stream<Path> files = Files.walk(world)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException exception) {
            Vector3.LOGGER.warn("Could not delete the template world {}", world, exception);
        }
    }

    private static void openCopy() {
        try {
            Path folder = Files.createDirectories(Flashback.getReplayFolder());
            String name = "Empty project " + LocalDateTime.now().withNano(0).toString().replace(':', '-');
            Path copy = folder.resolve(name + ".zip");
            Files.copy(template(), copy, StandardCopyOption.REPLACE_EXISTING);
            ReplayArchive.editMeta(copy, meta -> {
                meta.replayIdentifier = UUID.randomUUID();
                meta.name = name;
            });
            Flashback.openReplayWorld(copy);
        } catch (IOException exception) {
            Vector3.LOGGER.error("Could not create an empty project", exception);
        }
    }
}
