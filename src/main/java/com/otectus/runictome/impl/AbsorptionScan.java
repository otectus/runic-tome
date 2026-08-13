package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Answers the question a packmaker actually has: <em>given this configuration, which items in this
 * pack would the Runic Tome absorb?</em>
 *
 * <p>Every other diagnostic in the mod is reactive — {@code debug identify} explains one item you
 * are already holding, and the purge command cleans up after a functional book was absorbed by
 * mistake. Both require someone to have already lost an item. This sweeps the whole item registry
 * before that happens, so a pack can be audited up front.
 *
 * <p>Classification deliberately reuses the real absorption path rather than reimplementing it:
 * {@link AbsorptionPolicy#evaluate} for the global gate, then {@link RunicTomeAPI#identify} for the
 * adapter loop. A report that disagreed with what absorption actually does would be worse than no
 * report at all.
 */
public final class AbsorptionScan {

    private AbsorptionScan() {}

    /** One item the current configuration would absorb, and the adapter that would claim it. */
    public record Absorbed(ResourceLocation itemId, ResourceLocation systemId) {}

    /** One item the global safety gate would refuse, and why. */
    public record Excluded(ResourceLocation itemId, AbsorptionPolicy.Reason reason) {}

    /**
     * @param scanned      every item considered.
     * @param absorbed     items that would be absorbed, sorted by item id.
     * @param excluded     items an adapter claimed but the global gate refused, sorted by item id.
     * @param byAdapter    absorbed counts per adapter systemId, descending.
     * @param byNamespace  absorbed counts per item namespace, descending.
     */
    public record Report(int scanned,
                         List<Absorbed> absorbed,
                         List<Excluded> excluded,
                         Map<ResourceLocation, Integer> byAdapter,
                         Map<String, Integer> byNamespace) {

        public int absorbedCount() {
            return absorbed.size();
        }

        /** Absorbed entries in one namespace, for a drill-down that does not re-run the scan. */
        public List<Absorbed> inNamespace(String namespace) {
            return absorbed.stream()
                    .filter(a -> a.itemId().getNamespace().equals(namespace))
                    .toList();
        }
    }

    /**
     * Classifies every registered item.
     *
     * <p>This is O(items x adapters) and allocates one stack per item — measured in tens of
     * thousands of iterations on a large pack. It is a deliberate, operator-invoked cost, never run
     * on a tick.
     */
    public static Report run() {
        List<Absorbed> absorbed = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        Map<ResourceLocation, Integer> byAdapter = new LinkedHashMap<>();
        Map<String, Integer> byNamespace = new LinkedHashMap<>();
        int scanned = 0;

        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry
                : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation itemId = entry.getKey().location();
            Item item = entry.getValue();
            if (item == null) continue;

            ItemStack stack;
            try {
                // getDefaultInstance(), not new ItemStack(item): items that ship default NBT are
                // classified as they would actually be encountered, since NBT feeds the heuristic.
                stack = item.getDefaultInstance();
            } catch (Throwable t) {
                RunicTome.LOGGER.warn("Runic Tome scan: {} threw building its default stack; skipped",
                        itemId, t);
                continue;
            }
            if (stack == null || stack.isEmpty()) continue;
            scanned++;

            AbsorptionPolicy.Decision decision;
            try {
                decision = AbsorptionPolicy.evaluate(stack);
            } catch (Throwable t) {
                RunicTome.LOGGER.warn("Runic Tome scan: policy threw for {}; skipped", itemId, t);
                continue;
            }

            Optional<BookKey> claim = claimFor(stack, itemId);
            if (claim.isEmpty()) continue;

            if (decision.excluded()) {
                // An adapter wanted it but the safety gate wins. Worth reporting: this is the set a
                // packmaker's exclusion entries are actively protecting, and the set that would be
                // absorbed if one of those entries were removed.
                excluded.add(new Excluded(itemId, decision.reason()));
            } else {
                absorbed.add(new Absorbed(itemId, claim.get().systemId()));
                byAdapter.merge(claim.get().systemId(), 1, Integer::sum);
                byNamespace.merge(itemId.getNamespace(), 1, Integer::sum);
            }
        }

        absorbed.sort(Comparator.comparing(a -> a.itemId().toString()));
        excluded.sort(Comparator.comparing(e -> e.itemId().toString()));
        return new Report(scanned, List.copyOf(absorbed), List.copyOf(excluded),
                sortedByCountDescending(byAdapter, ResourceLocation::toString),
                sortedByCountDescending(byNamespace, s -> s));
    }

    /**
     * Which adapter would claim {@code stack}, ignoring the global gate.
     *
     * <p>{@link RunicTomeAPI#identify} applies the gate itself and returns empty for an excluded
     * item, which would make "excluded" and "no adapter wanted it" indistinguishable — and those
     * mean very different things to a packmaker. The adapter loop is therefore run directly, with
     * the same per-adapter exception isolation the registry uses, so one broken third-party adapter
     * cannot abort the scan.
     */
    private static Optional<BookKey> claimFor(ItemStack stack, ResourceLocation itemId) {
        for (var adapter : RunicTomeAPI.allAdapters()) {
            try {
                Optional<BookKey> result = adapter.identify(stack);
                if (result != null && result.isPresent()) return result;
            } catch (Throwable t) {
                RunicTome.LOGGER.warn("Runic Tome scan: adapter {} threw for {}; skipped",
                        safeId(adapter), itemId, t);
            }
        }
        return Optional.empty();
    }

    private static Object safeId(com.otectus.runictome.api.GuideSystemAdapter adapter) {
        try {
            return adapter.systemId();
        } catch (Throwable t) {
            return adapter.getClass().getName();
        }
    }

    private static <K> Map<K, Integer> sortedByCountDescending(Map<K, Integer> counts,
                                                               java.util.function.Function<K, String> keyName) {
        List<Map.Entry<K, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<K, Integer>comparingByValue().reversed()
                .thenComparing(e -> keyName.apply(e.getKey())));
        Map<K, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<K, Integer> e : entries) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    /**
     * Renders the full report as plain text. The in-game summary is necessarily bounded by the chat
     * buffer; a pack with 400 absorbable items needs the whole list somewhere reviewable.
     */
    public static String render(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Runic Tome — absorption scan\n");
        sb.append("=================================\n");
        sb.append("Items scanned:  ").append(report.scanned()).append('\n');
        sb.append("Would absorb:   ").append(report.absorbedCount()).append('\n');
        sb.append("Gate-protected: ").append(report.excluded().size())
                .append("  (an adapter claimed these; a global exclusion refused them)\n\n");

        sb.append("By adapter\n----------\n");
        if (report.byAdapter().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            report.byAdapter().forEach((id, count) ->
                    sb.append("  ").append(count).append("\t").append(id).append('\n'));
        }

        sb.append("\nBy namespace\n------------\n");
        if (report.byNamespace().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            report.byNamespace().forEach((ns, count) ->
                    sb.append("  ").append(count).append("\t").append(ns).append('\n'));
        }

        sb.append("\nWould be absorbed\n-----------------\n");
        if (report.absorbed().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            // Grouped by namespace so a packmaker can review one mod at a time.
            Map<String, List<Absorbed>> grouped = new TreeMap<>();
            for (Absorbed a : report.absorbed()) {
                grouped.computeIfAbsent(a.itemId().getNamespace(), k -> new ArrayList<>()).add(a);
            }
            grouped.forEach((ns, items) -> {
                sb.append("\n  [").append(ns).append("]  ").append(items.size()).append(" item(s)\n");
                for (Absorbed a : items) {
                    sb.append("    ").append(a.itemId()).append("\t<- ").append(a.systemId()).append('\n');
                }
            });
        }

        sb.append("\nProtected by a global exclusion\n-------------------------------\n");
        if (report.excluded().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Excluded e : report.excluded()) {
                sb.append("  ").append(e.itemId()).append("\t(").append(e.reason()).append(")\n");
            }
        }

        sb.append("\nAnything above that is functional gear rather than documentation should go in\n");
        sb.append("absorbExclusionItems / absorbExclusionMods, or the #runictome:absorb_blocklist tag.\n");
        return sb.toString();
    }

    /** Writes {@link #render} to {@code target}. */
    public static void write(Report report, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(target, render(report), StandardCharsets.UTF_8);
    }
}
