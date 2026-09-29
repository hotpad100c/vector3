package ml.mypals.vectorthree.core;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Mod {
    public static final String ID = "vector3";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    private Mod() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    /** A stable id for something named by a string (a file path, a font spec): 128 bits, so two keys never share one. */
    public static Identifier idFor(String folder, String key) {
        return id(folder + "/" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
    }
}
