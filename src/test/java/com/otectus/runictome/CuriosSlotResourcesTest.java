package com.otectus.runictome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.runictome.integration.curios.CuriosSupport;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Curios slot is declared entirely in data, so nothing else in the build checks it — a typo
 * surfaces only as a missing slot or a magenta checkerboard after launching the game. These tests
 * read the shipped resources directly.
 *
 * <p>Deliberately free of any Curios import: Curios is {@code compileOnly}, so it is not on the test
 * runtime classpath. Enum names and tag paths are compared against literals.
 */
class CuriosSlotResourcesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final String SLOT = "runic_tome";
    private static final String TOME_ITEM = "runictome:runic_tome";

    /** Mirrors {@code ICurio.DropRule}; kept as literals so the test needs no Curios on the path. */
    private static final Set<String> DROP_RULES =
            Set.of("DEFAULT", "ALWAYS_DROP", "ALWAYS_KEEP", "DESTROY");

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing resource: " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    private static Path slotFile() {
        return RESOURCES.resolve(Path.of("data", "runictome", "curios", "slots", SLOT + ".json"));
    }

    @Test
    void slotDefinitionIsWellFormed() throws IOException {
        JsonObject slot = readJson(slotFile());
        assertEquals(1, slot.get("size").getAsInt(), "the tome is a single, unique item");
        assertTrue(slot.get("order").getAsInt() > 200,
                "order must fall after the built-in slots (charm is 200)");
        assertEquals(List.of("curios:tag"), strings(slot.getAsJsonArray("validators")),
                "the tag validator is what makes the curios:runic_tome item tag authoritative");
    }

    @Test
    void slotKeepsTheTomeThroughDeath() throws IOException {
        String rule = readJson(slotFile()).get("drop_rule").getAsString();
        assertTrue(DROP_RULES.contains(rule), rule + " is not a valid Curios DropRule");
        // ALWAYS_KEEP is the only rule that leaves the stack in the slot; every other outcome
        // clears it, including the destroy path.
        assertEquals("ALWAYS_KEEP", rule);
    }

    @Test
    void slotIconResolvesToAShippedTexture() throws IOException {
        // Curios turns icon "ns:slot/x" into assets/ns/textures/slot/x.png. A typo here is silent:
        // the game renders the missing-texture checkerboard rather than failing.
        String icon = readJson(slotFile()).get("icon").getAsString();
        String[] parts = icon.split(":", 2);
        assertEquals(2, parts.length, "icon must be a namespaced ResourceLocation, got " + icon);
        Path texture = RESOURCES.resolve(
                Path.of("assets", parts[0], "textures", parts[1] + ".png"));
        assertTrue(Files.exists(texture), "slot icon " + icon + " resolves to missing file " + texture);
    }

    @Test
    void entityBindingAttachesTheSlotToPlayers() throws IOException {
        // Curios ships no entity bindings at all, so without this file the slot exists but is
        // attached to nobody and simply never appears.
        JsonObject binding = readJson(RESOURCES.resolve(
                Path.of("data", "runictome", "curios", "entities", "player.json")));
        assertTrue(strings(binding.getAsJsonArray("entities")).contains("minecraft:player"));
        assertTrue(strings(binding.getAsJsonArray("slots")).contains(SLOT),
                "the entity binding must reference the slot id, which is the slot file's own name");
    }

    @Test
    void itemTagLivesInTheCuriosNamespaceAndListsTheTome() throws IOException {
        // The curios:tag validator resolves ItemTags.create(new ResourceLocation("curios", slotId)),
        // so this file must sit under data/curios/ -- not under this mod's own namespace.
        JsonObject tag = readJson(RESOURCES.resolve(
                Path.of("data", "curios", "tags", "items", SLOT + ".json")));
        assertFalse(tag.get("replace").getAsBoolean(), "never clobber another pack's contributions");
        assertTrue(strings(tag.getAsJsonArray("values")).contains(TOME_ITEM));
    }

    @Test
    void iconMatchesTheBuiltInCuriosStyle() throws IOException {
        // Measured from the shipped Curios icons: 16x16 with exactly two RGBA values, no
        // anti-aliasing, and art confined inside the outer border.
        BufferedImage image = ImageIO.read(
                RESOURCES.resolve(Path.of("assets", "runictome", "textures", "slot",
                        "empty_runic_tome_slot.png")).toFile());
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());

        int opaque = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;
                assertEquals(0xFF555555, argb,
                        String.format("pixel (%d,%d) is not the Curios slot grey", x, y));
                opaque++;
                assertTrue(x >= 1 && x <= 14 && y >= 1 && y <= 14,
                        String.format("pixel (%d,%d) touches the outer border", x, y));
            }
        }
        assertTrue(opaque > 0, "the icon is entirely transparent");
    }

    @Test
    void curiosSupportDegradesToUnavailableWithoutForge() {
        // This is the crash-without-Curios path: the probe must fail closed rather than let
        // ModList.get() escape, and it must never classload the Curios-typed integration class.
        assertFalse(CuriosSupport.isAvailable());
        assertFalse(CuriosSupport.isTomeEquipped(null));
        assertFalse(CuriosSupport.tryEquip(null, null));
    }
}
