package ml.mypals.vectorthree.fb.shape.particle;

import ml.mypals.vectorthree.core.shape.particle.ParticleSettings;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.client.resources.language.I18n;

/** The particle emitter's panel, one collapsible section per module like Unity's inspector. */
public final class ParticleEditor {
    private ParticleEditor() {}

    /** Edits {@code settings[0]}, the emission {@code rate} and the box {@code size} in place; true when anything changed. */
    public static boolean edit(String[] particleId, ParticleSettings[] settings, float[] rate, float[] size) {
        boolean changed = false;
        ParticleSettings s = settings[0];

        ImGui.setNextItemWidth(360);
        if (ImGui.beginCombo(I18n.get("vector3.keyframe.particle"), particleId[0])) {
            for (String id : ShapeTrackRegistry.particleIds()) {
                if (ImGui.selectable(id, id.equals(particleId[0]))) {
                    particleId[0] = id;
                    changed = true;
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.main") + "##particle_main", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] lifetime = {s.lifetimeMin(), s.lifetimeMax()};
            float[] speed = {s.speedMin(), s.speedMax()};
            float[] scale = {s.sizeMin(), s.sizeMax()};
            ImBoolean nativeMotion = new ImBoolean(s.nativeMotion());
            ImBoolean tint = new ImBoolean(s.tint());
            ImBoolean localSpace = new ImBoolean(s.localSpace());
            int[] max = {s.maxParticles()};
            boolean edited = ImGui.dragFloat2(I18n.get("vector3.particle.lifetime"), lifetime, 0.05f, 0, 600);
            tooltip("vector3.particle.lifetime.tooltip");
            edited |= ImGui.dragFloat2(I18n.get("vector3.particle.speed"), speed, 0.05f, -1000, 1000);
            edited |= ImGui.dragFloat2(I18n.get("vector3.particle.size"), scale, 0.02f, 0.01f, 100);
            tooltip("vector3.particle.range.tooltip");
            edited |= ImGui.checkbox(I18n.get("vector3.particle.tint"), tint);
            tooltip("vector3.particle.tint.tooltip");
            edited |= ImGui.checkbox(I18n.get("vector3.particle.native_motion"), nativeMotion);
            tooltip("vector3.particle.native_motion.tooltip");
            edited |= ImGui.checkbox(I18n.get("vector3.particle.local_space"), localSpace);
            tooltip("vector3.particle.local_space.tooltip");
            edited |= ImGui.dragInt(I18n.get("vector3.particle.max_particles"), max, 1, 0, 100000);
            tooltip("vector3.particle.zero_unlimited");
            if (edited) {
                s = s.withMain(Math.max(0, lifetime[0]), Math.max(lifetime[0], lifetime[1]), speed[0], Math.max(speed[0], speed[1]),
                        Math.max(0.01f, scale[0]), Math.max(scale[0], scale[1]), nativeMotion.get(), tint.get(),
                        Math.max(0, max[0]), localSpace.get());
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.emission") + "##particle_emission", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.emission_rate"), rate, 0.5f, 0, 10000);
            float[] distance = {s.rateOverDistance()};
            int[] burstCount = {s.burstCount()}, burstTick = {s.burstTick()}, interval = {s.burstInterval()}, cycles = {s.burstCycles()};
            boolean edited = ImGui.dragFloat(I18n.get("vector3.particle.rate_over_distance"), distance, 0.1f, 0, 10000);
            tooltip("vector3.particle.rate_over_distance.tooltip");
            edited |= ImGui.dragInt(I18n.get("vector3.particle.burst_count"), burstCount, 1, 0, 10000);
            tooltip("vector3.particle.burst.tooltip");
            if (burstCount[0] > 0) {
                edited |= ImGui.dragInt(I18n.get("vector3.particle.burst_tick"), burstTick, 1, 0, Integer.MAX_VALUE);
                ImGui.sameLine();
                if (ImGui.smallButton(I18n.get("vector3.particle.at_playhead"))) {
                    burstTick[0] = TimelineWindow.getCursorTick();
                    edited = true;
                }
                edited |= ImGui.dragInt(I18n.get("vector3.particle.burst_interval"), interval, 1, 1, 100000);
                edited |= ImGui.dragInt(I18n.get("vector3.particle.burst_cycles"), cycles, 0.1f, 0, 100000);
                tooltip("vector3.particle.zero_unlimited");
            }
            if (edited) {
                s = s.withEmission(Math.max(0, distance[0]), Math.max(0, burstTick[0]), Math.max(0, burstCount[0]),
                        Math.max(1, interval[0]), Math.max(0, cycles[0]));
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.shape") + "##particle_shape", ImGuiTreeNodeFlags.DefaultOpen)) {
            ParticleSettings.Kind kind = s.shape();
            float[] radius = {s.radius()}, thickness = {s.thickness()}, angle = {s.angle()}, arc = {s.arc()};
            float[] randomDirection = {s.randomDirection()};
            boolean edited = false;
            ImGui.setNextItemWidth(200);
            if (ImGui.beginCombo(I18n.get("vector3.particle.shape_type"), I18n.get(kindKey(kind)))) {
                for (ParticleSettings.Kind option : ParticleSettings.Kind.values()) {
                    if (ImGui.selectable(I18n.get(kindKey(option)), option == kind)) {
                        kind = option;
                        edited = true;
                    }
                }
                ImGui.endCombo();
            }
            switch (kind) {
                case BOX -> changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.spread"), size, 0.05f, 0, 1000);
                case EDGE -> edited |= ImGui.dragFloat(I18n.get("vector3.particle.radius"), radius, 0.02f, 0, 1000);
                default -> {
                    edited |= ImGui.dragFloat(I18n.get("vector3.particle.radius"), radius, 0.02f, 0, 1000);
                    edited |= ImGui.sliderFloat(I18n.get("vector3.particle.thickness"), thickness, 0, 1);
                    tooltip("vector3.particle.thickness.tooltip");
                }
            }
            if (kind == ParticleSettings.Kind.CONE) edited |= ImGui.sliderFloat(I18n.get("vector3.particle.angle"), angle, 0, 90);
            if (kind == ParticleSettings.Kind.CONE || kind == ParticleSettings.Kind.CIRCLE) {
                edited |= ImGui.sliderFloat(I18n.get("vector3.particle.arc"), arc, 0, 360);
            }
            edited |= ImGui.sliderFloat(I18n.get("vector3.particle.random_direction"), randomDirection, 0, 1);
            ImGui.textDisabled(I18n.get("vector3.particle.direction_hint"));
            if (edited) {
                s = s.withShape(kind, Math.max(0, radius[0]), Math.clamp(thickness[0], 0, 1), Math.clamp(angle[0], 0, 90),
                        Math.clamp(arc[0], 0, 360), Math.clamp(randomDirection[0], 0, 1));
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.force_module") + "##particle_force")) {
            float[] force = {s.forceX(), s.forceY(), s.forceZ()};
            float[] noise = {s.noise()};
            boolean edited = ImGui.dragFloat3(I18n.get("vector3.particle.force"), force, 0.05f);
            tooltip("vector3.particle.force.tooltip");
            edited |= ImGui.dragFloat(I18n.get("vector3.particle.noise"), noise, 0.05f, 0, 1000);
            tooltip("vector3.particle.noise.tooltip");
            if (edited) {
                s = s.withForce(force[0], force[1], force[2], Math.max(0, noise[0]));
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.over_lifetime") + "##particle_lifetime")) {
            ImBoolean colorOverLifetime = new ImBoolean(s.colorOverLifetime());
            int end = s.endColor();
            float[] endColor = {((end >> 16) & 255) / 255f, ((end >> 8) & 255) / 255f, (end & 255) / 255f, ((end >>> 24) & 255) / 255f};
            float[] endSize = {s.endSize()};
            boolean edited = ImGui.checkbox(I18n.get("vector3.particle.color_over_lifetime"), colorOverLifetime);
            tooltip("vector3.particle.color_over_lifetime.tooltip");
            if (colorOverLifetime.get()) edited |= ImGui.colorEdit4(I18n.get("vector3.particle.end_color"), endColor);
            edited |= ImGui.dragFloat(I18n.get("vector3.particle.end_size"), endSize, 0.02f, 0, 100);
            tooltip("vector3.particle.end_size.tooltip");
            if (edited) {
                int argb = (Math.round(endColor[3] * 255) << 24) | (Math.round(endColor[0] * 255) << 16)
                        | (Math.round(endColor[1] * 255) << 8) | Math.round(endColor[2] * 255);
                s = s.withLifetime(colorOverLifetime.get(), argb, Math.max(0, endSize[0]));
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(I18n.get("vector3.particle.physics") + "##particle_physics")) {
            ImBoolean physics = new ImBoolean(s.physics());
            ImBoolean collision = new ImBoolean(s.collision());
            float[] gravity = {s.gravity()}, drag = {s.drag()};
            boolean edited = ImGui.checkbox(I18n.get("vector3.particle.override_physics"), physics);
            tooltip("vector3.particle.override_physics.tooltip");
            if (physics.get()) {
                edited |= ImGui.dragFloat(I18n.get("vector3.particle.gravity"), gravity, 0.01f, -100, 100);
                edited |= ImGui.dragFloat(I18n.get("vector3.particle.drag"), drag, 0.05f, 0, 20);
                tooltip("vector3.particle.drag.tooltip");
                edited |= ImGui.checkbox(I18n.get("vector3.particle.collision"), collision);
            }
            if (edited) {
                s = s.withPhysics(physics.get(), gravity[0], Math.clamp(drag[0], 0, 20), collision.get());
                changed = true;
            }
        }

        settings[0] = s;
        return changed;
    }

    private static String kindKey(ParticleSettings.Kind kind) {
        return "vector3.particle.shape." + kind.name().toLowerCase();
    }

    private static void tooltip(String key) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get(key));
    }
}
