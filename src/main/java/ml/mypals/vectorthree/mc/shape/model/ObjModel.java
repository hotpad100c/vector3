package ml.mypals.vectorthree.mc.shape.model;

import ml.mypals.vectorthree.core.Mod;
import com.mojang.blaze3d.platform.NativeImage;
import ml.mypals.vectorthree.core.shape.media.ImageDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed Wavefront OBJ: v / vt / vn / f (negative indices, polygons fanned into triangles), plus the MTL
 * libraries it names (newmtl, Kd, d / Tr, map_Kd, norm / bump / map_Bump, and LabPBR _n / _s companions). Triangle corners are grouped by material so each material is one
 * contiguous range. Files are resolved relative to the OBJ; a resource OBJ ("ns:path") resolves them as resources
 * beside it.
 */
final class ObjModel {
    static final String ROOT = "root";

    /** A texture either on disk or in a resource pack. */
    record TextureRef(@Nullable Path file, @Nullable Identifier resource) {
        NativeImage read() throws IOException {
            if (file != null) return ImageDecoder.decode(file);
            if (resource != null) {
                Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(resource);
                if (found.isPresent()) {
                    try (InputStream stream = found.get().open()) {
                        return NativeImage.read(stream);
                    }
                }
            }
            throw new IOException("Texture not found: " + this);
        }

        boolean exists() {
            if (file != null) return Files.isRegularFile(file);
            return resource != null && Minecraft.getInstance().getResourceManager().getResource(resource).isPresent();
        }

        /** The LabPBR companion ("stone.png" -> "stone_n.png"), if it exists. */
        @Nullable TextureRef companion(String suffix) {
            TextureRef ref;
            if (file != null) {
                ref = new TextureRef(file.resolveSibling(withSuffix(file.getFileName().toString(), suffix)), null);
            } else if (resource != null) {
                Identifier id = Identifier.tryBuild(resource.getNamespace(), withSuffix(resource.getPath(), suffix));
                ref = id == null ? null : new TextureRef(null, id);
            } else {
                ref = null;
            }
            return ref != null && ref.exists() ? ref : null;
        }

        private static String withSuffix(String name, String suffix) {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? name + suffix : name.substring(0, dot) + suffix + name.substring(dot);
        }
    }

    /** Colour, opacity and textures. Normal and specular maps only matter to shader packs (through Iris). */
    record Material(String name, float red, float green, float blue, float alpha, @Nullable TextureRef texture,
            @Nullable TextureRef normal, @Nullable TextureRef specular) {
        static final Material DEFAULT = new Material("", 1, 1, 1, 1, null, null, null);

        /** A plain material around one texture, with its LabPBR companions if they sit next to it. */
        static Material of(@Nullable TextureRef texture) {
            return new Material("", 1, 1, 1, 1, texture, texture == null ? null : texture.companion("_n"),
                    texture == null ? null : texture.companion("_s"));
        }
    }

    record Corner(int position, int uv, int normal) {}

    /** Corners {@code [first, first + count)} use {@code material} and belong to part {@code part}. */
    record Range(Material material, int first, int count, int part) {}

    /** An {@code o} / {@code g} group: its parent part (-1 for none) and the centre of its own faces' bounds. */
    record Part(String name, int parent, Vector3f center) {}

    private record Group(Material material, int part) {}

    private static final class PartBuilder {
        final String name;
        String parent;
        final Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);

        PartBuilder(String name) {
            this.name = name;
        }
    }

    final List<Vec3> positions = new ArrayList<>();
    final List<float[]> uvs = new ArrayList<>();
    final List<Vector3f> normals = new ArrayList<>();
    final List<Corner> corners = new ArrayList<>();
    final List<Range> ranges = new ArrayList<>();
    final List<Part> parts = new ArrayList<>();

    private record Location(@Nullable Path file, @Nullable Identifier resource) {
        static Location of(String source) {
            try {
                Path path = Path.of(source);
                if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
                if (Files.isRegularFile(path)) return new Location(path.toAbsolutePath().normalize(), null);
            } catch (RuntimeException ignored) {
                // Not a file path, e.g. "namespace:models/x.obj" on Windows.
            }
            Identifier id = Identifier.tryParse(source);
            return id == null ? new Location(null, null) : new Location(null, id);
        }

        Location sibling(String name) {
            String clean = name.replace('\\', '/');
            if (file != null) return new Location(file.resolveSibling(clean).normalize(), null);
            if (resource == null) return this;
            String path = resource.getPath();
            int slash = path.lastIndexOf('/');
            Identifier id = Identifier.tryBuild(resource.getNamespace(), (slash < 0 ? "" : path.substring(0, slash + 1)) + clean);
            return new Location(null, id);
        }

        InputStream open() throws IOException {
            if (file != null && Files.isRegularFile(file)) return Files.newInputStream(file);
            if (resource != null) {
                Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(resource);
                if (found.isPresent()) return found.get().open();
            }
            throw new FileNotFoundException("Not found: " + (file != null ? file : resource));
        }

        TextureRef texture() {
            return new TextureRef(file, resource);
        }
    }

    static ObjModel load(String source) throws IOException {
        ObjModel model = new ObjModel();
        Location location = Location.of(source);
        Map<String, Material> materials = new LinkedHashMap<>();
        Map<Group, List<Corner>> byGroup = new LinkedHashMap<>();
        Map<String, PartBuilder> partMap = new LinkedHashMap<>();
        partMap.put(ROOT, new PartBuilder(ROOT));
        Material material = Material.DEFAULT;
        int part = 0;
        PartBuilder bounds = partMap.get(ROOT);
        List<Corner> current = byGroup.computeIfAbsent(new Group(material, part), key -> new ArrayList<>());
        try (BufferedReader reader = reader(location)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> model.positions.add(new Vec3(number(parts, 1), number(parts, 2), number(parts, 3)));
                    case "vt" -> model.uvs.add(new float[]{(float) number(parts, 1), 1f - (float) number(parts, 2)});
                    case "vn" -> model.normals.add(new Vector3f((float) number(parts, 1), (float) number(parts, 2),
                            (float) number(parts, 3)).normalize());
                    case "mtllib" -> {
                        for (int i = 1; i < parts.length; i++) readMaterials(location.sibling(parts[i]), materials);
                    }
                    case "usemtl" -> {
                        material = materials.getOrDefault(rest(line), Material.DEFAULT);
                        current = byGroup.computeIfAbsent(new Group(material, part), key -> new ArrayList<>());
                    }
                    case "o", "g" -> {
                        String[] names = rest(line).split("\s+");
                        if (names.length == 0 || names[0].isEmpty()) names = new String[]{ROOT};
                        part = declare(partMap, names);
                        bounds = new ArrayList<>(partMap.values()).get(part);
                        current = byGroup.computeIfAbsent(new Group(material, part), key -> new ArrayList<>());
                    }
                    case "f" -> {
                        if (parts.length < 4) continue;
                        Corner first = model.corner(parts[1]);
                        for (int i = 2; i < parts.length - 1; i++) {
                            Corner b = model.corner(parts[i]), c = model.corner(parts[i + 1]);
                            if (!model.valid(first) || !model.valid(b) || !model.valid(c)) continue;
                            current.add(first);
                            current.add(b);
                            current.add(c);
                            for (Corner corner : new Corner[]{first, b, c}) {
                                Vec3 at = model.positions.get(corner.position());
                                bounds.min.min(new Vector3f((float) at.x, (float) at.y, (float) at.z));
                                bounds.max.max(new Vector3f((float) at.x, (float) at.y, (float) at.z));
                            }
                        }
                    }
                    default -> {}
                }
            }
        }
        for (Map.Entry<Group, List<Corner>> entry : byGroup.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            model.ranges.add(new Range(entry.getKey().material(), model.corners.size(), entry.getValue().size(),
                    entry.getKey().part()));
            model.corners.addAll(entry.getValue());
        }
        List<String> names = new ArrayList<>(partMap.keySet());
        for (PartBuilder builder : partMap.values()) {
            Vector3f center = builder.min.x > builder.max.x ? new Vector3f()
                    : builder.min.add(builder.max, new Vector3f()).mul(0.5f);
            model.parts.add(new Part(builder.name, builder.parent == null ? -1 : names.indexOf(builder.parent), center));
        }
        return model;
    }

    // "g a b c" nests a > b > c, and "arm/hand" is "hand" under "arm". Returns the innermost part's index; the
    // first parent given to a part stays.
    private static int declare(Map<String, PartBuilder> parts, String[] names) {
        String parent = null;
        for (String group : names) {
            String[] path = group.split("/");
            StringBuilder full = new StringBuilder();
            for (String segment : path) {
                if (segment.isEmpty()) continue;
                if (!full.isEmpty()) full.append('/');
                String before = full.isEmpty() ? parent : full.toString();
                full.append(segment);
                PartBuilder builder = parts.computeIfAbsent(full.toString(), PartBuilder::new);
                if (builder.parent == null && before != null && !before.equals(full.toString())) builder.parent = before;
                parent = full.toString();
            }
        }
        return parent == null ? 0 : new ArrayList<>(parts.keySet()).indexOf(parent);
    }

    // A missing or broken MTL only loses its materials; the geometry still loads. Many OBJs name an MTL they
    // don't ship with, so that case is one line in the log rather than a stack trace.
    private static void readMaterials(Location location, Map<String, Material> materials) {
        try (BufferedReader reader = reader(location)) {
            String name = null;
            float red = 1, green = 1, blue = 1, alpha = 1;
            TextureRef texture = null, normal = null, specular = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "newmtl" -> {
                        if (name != null) materials.put(name, material(name, red, green, blue, alpha, texture, normal, specular));
                        name = rest(line);
                        red = green = blue = alpha = 1;
                        texture = normal = specular = null;
                    }
                    case "Kd" -> {
                        red = (float) number(parts, 1);
                        green = parts.length > 2 ? (float) number(parts, 2) : red;
                        blue = parts.length > 3 ? (float) number(parts, 3) : red;
                    }
                    case "d" -> alpha = (float) number(parts, 1);
                    case "Tr" -> alpha = 1 - (float) number(parts, 1);
                    // Options (-s, -o, -bm ...) come first; the file name is last.
                    case "map_Kd" -> texture = location.sibling(parts[parts.length - 1]).texture();
                    case "norm", "bump", "map_Bump", "map_bump" -> normal = location.sibling(parts[parts.length - 1]).texture();
                    // Not standard MTL: a LabPBR specular map ("_s"), which is what shader packs read.
                    case "map_Pbr", "map_pbr_s" -> specular = location.sibling(parts[parts.length - 1]).texture();
                    default -> {}
                }
            }
            if (name != null) materials.put(name, material(name, red, green, blue, alpha, texture, normal, specular));
        } catch (FileNotFoundException missing) {
            Mod.LOGGER.warn("OBJ names an MTL that isn't there, using plain materials: {}",
                    location);
        } catch (Exception exception) {
            Mod.LOGGER.warn("Could not read MTL {}", location, exception);
        }
    }

    // Maps the MTL doesn't name fall back to LabPBR companions next to the colour texture.
    private static Material material(String name, float red, float green, float blue, float alpha,
            @Nullable TextureRef texture, @Nullable TextureRef normal, @Nullable TextureRef specular) {
        if (texture != null && normal == null) normal = texture.companion("_n");
        if (texture != null && specular == null) specular = texture.companion("_s");
        return new Material(name, red, green, blue, alpha, texture, normal, specular);
    }

    private static BufferedReader reader(Location location) throws IOException {
        return new BufferedReader(new InputStreamReader(location.open(), StandardCharsets.UTF_8));
    }

    private Corner corner(String value) {
        String[] indices = value.split("/", -1);
        return new Corner(index(indices, 0, positions.size()), index(indices, 1, uvs.size()), index(indices, 2, normals.size()));
    }

    private boolean valid(Corner corner) {
        return corner.position() >= 0 && corner.position() < positions.size();
    }

    float[] uv(Corner corner) {
        return corner.uv() >= 0 && corner.uv() < uvs.size() ? uvs.get(corner.uv()) : new float[]{0, 0};
    }

    @Nullable Vector3f normal(Corner corner) {
        return corner.normal() >= 0 && corner.normal() < normals.size() ? normals.get(corner.normal()) : null;
    }

    private static int index(String[] values, int slot, int size) {
        if (slot >= values.length || values[slot].isBlank()) return -1;
        int value = Integer.parseInt(values[slot]);
        return value < 0 ? size + value : value - 1;
    }

    private static double number(String[] values, int slot) {
        return slot < values.length ? Double.parseDouble(values[slot]) : 0;
    }

    private static String rest(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1).trim();
    }
}
