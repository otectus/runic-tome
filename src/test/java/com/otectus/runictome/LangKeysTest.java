package com.otectus.runictome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing in the build checks translation keys: a missing one ships silently and renders in-game as
 * the raw key, so {@code gui.runictome.copy} appears on the button instead of "Copy". This test
 * reads the shipped sources and the shipped lang file and compares them, the same way
 * {@code CuriosSlotResourcesTest} checks the Curios data files.
 */
class LangKeysTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path LANG =
            Path.of("src", "main", "resources", "assets", "runictome", "lang", "en_us.json");

    /**
     * Keys this mod is responsible for. Anything outside these prefixes belongs to vanilla or
     * another mod and is deliberately not our problem -- {@code gui.done}, for instance.
     */
    private static final List<String> OWNED_PREFIXES = List.of(
            "runictome.", "gui.runictome.", "screen.runictome.", "commands.runictome.",
            "key.runictome.", "key.categories.runictome", "item.runictome.", "itemGroup.runictome",
            "curios.identifier.runic_tome");

    /** Matches a translation key written as a string literal. Dynamic keys are skipped. */
    private static final Pattern TRANSLATABLE = Pattern.compile(
            "Component\\.translatable\\(\\s*\"([^\"]+)\"");

    private static JsonObject lang() throws IOException {
        assertTrue(Files.exists(LANG), "missing lang file: " + LANG);
        return JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean owned(String key) {
        return OWNED_PREFIXES.stream().anyMatch(key::startsWith);
    }

    @Test
    void everyTranslationKeyUsedInSourceIsDefined() throws IOException {
        Set<String> defined = lang().keySet();
        Set<String> missing = new TreeSet<>();

        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = TRANSLATABLE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    String key = m.group(1);
                    if (owned(key) && !defined.contains(key)) {
                        missing.add(key + "  (" + file.getFileName() + ")");
                    }
                }
            }
        }

        assertEquals(Set.of(), missing, "translation keys used in code but absent from en_us.json");
    }

    @Test
    void theLangFileHasNoDuplicateKeys() throws IOException {
        // Gson silently keeps the last value for a repeated key, so a duplicate is invisible in the
        // parsed object and can only be caught by reading the raw text.
        String raw = Files.readString(LANG, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE).matcher(raw);
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        while (m.find()) {
            if (!seen.add(m.group(1))) duplicates.add(m.group(1));
        }
        assertEquals(List.of(), duplicates, "duplicate keys in en_us.json");
    }

    @Test
    void theCopyAndPauseFeaturesAreFullyTranslated() throws IOException {
        // Named explicitly as well as scanned, because several of these are only ever reached on a
        // failure path that no other test exercises.
        Set<String> defined = lang().keySet();
        for (String key : List.of(
                "gui.runictome.copy",
                "gui.runictome.absorb.on",
                "gui.runictome.absorb.off",
                "gui.runictome.absorb.tooltip",
                "screen.runictome.row.narration",
                "runictome.copy.success",
                "runictome.copy.disabled",
                "runictome.copy.not_unlocked",
                "runictome.copy.no_item",
                "runictome.copy.no_book",
                "runictome.copy.delivery_failed",
                "runictome.absorb.paused",
                "runictome.absorb.resumed")) {
            assertTrue(defined.contains(key), "missing translation key: " + key);
        }
    }
}
