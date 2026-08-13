package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.AbsorptionScan;
import com.otectus.runictome.impl.AdapterRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code /runictome debug scan} — the pre-emptive pack audit (audit §13.5).
 *
 * <p>The report's whole value is that it agrees with what absorption would actually do. A scan that
 * over-reports trains packmakers to ignore it; one that under-reports is worse than nothing, since
 * it certifies a pack as safe when it is not. These tests pin the agreement in both directions:
 * an item an adapter claims <em>and</em> the gate allows is reported as absorbable, and an item an
 * adapter claims but the gate refuses is reported as protected, never as absorbable.
 */
class AbsorptionScanTest {

    private static final ResourceLocation SCAN_ADAPTER =
            new ResourceLocation("runictome", "config/scan_test");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The mod constructor installs this at runtime. Without it every RunicTomeAPI call falls
        // through to the warning stub that returns an empty adapter list, so the scan would report
        // "nothing would be absorbed" for reasons that have nothing to do with the code under test.
        RunicTomeAPI._installDelegate(AdapterRegistry.get());
    }

    @BeforeEach
    @AfterEach
    void resetGlobalState() {
        AdapterRegistry.get().unregisterAdapter(SCAN_ADAPTER);
        AbsorptionPolicy.rebuild(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS,
                RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);
    }

    /** Claims exactly the item ids it was given — a stand-in for any explicit adapter. */
    private record FixedItemAdapter(ResourceLocation systemId,
                                    List<ResourceLocation> claimed) implements GuideSystemAdapter {
        @Override
        public Optional<BookKey> identify(ItemStack stack) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && claimed.contains(id)
                    ? Optional.of(new BookKey(systemId, id))
                    : Optional.empty();
        }

        @Override public void open(BookKey key, Player clientPlayer) { }
    }

    private static void registerClaiming(ResourceLocation... itemIds) {
        RunicTomeAPI.registerAdapter(new FixedItemAdapter(SCAN_ADAPTER, List.of(itemIds)));
    }

    @Test
    void reportsAnItemAnAdapterWouldClaim() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);

        AbsorptionScan.Report report = AbsorptionScan.run();

        assertTrue(report.absorbed().stream().anyMatch(a -> a.itemId().equals(writtenBook)),
                "an item the registered adapter claims must appear in the report");
        assertEquals(SCAN_ADAPTER, report.absorbed().stream()
                        .filter(a -> a.itemId().equals(writtenBook))
                        .findFirst().orElseThrow().systemId(),
                "the report must name the adapter that would actually claim it");
    }

    @Test
    void doesNotReportItemsNoAdapterClaims() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);

        AbsorptionScan.Report report = AbsorptionScan.run();
        ResourceLocation stone = ForgeRegistries.ITEMS.getKey(Items.STONE);

        assertFalse(report.absorbed().stream().anyMatch(a -> a.itemId().equals(stone)),
                "an item no adapter claims must not be reported as absorbable");
    }

    @Test
    void anExcludedItemIsReportedAsProtectedRatherThanAbsorbable() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);
        // Hard-exclude the very item the adapter claims: the gate must win, and the report must say
        // so explicitly. Reporting it as absorbable would send a packmaker chasing a non-problem;
        // omitting it entirely would hide which items their exclusions are actively protecting.
        AbsorptionPolicy.rebuild(List.of(writtenBook.toString()), List.of());

        AbsorptionScan.Report report = AbsorptionScan.run();

        assertFalse(report.absorbed().stream().anyMatch(a -> a.itemId().equals(writtenBook)),
                "a globally excluded item must never be reported as absorbable");
        assertTrue(report.excluded().stream().anyMatch(e -> e.itemId().equals(writtenBook)
                        && e.reason() == AbsorptionPolicy.Reason.GLOBAL_ITEM),
                "it must be reported as protected, with the reason that protected it");
    }

    @Test
    void anExcludedNamespaceIsReportedWithItsReason() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);
        AbsorptionPolicy.rebuild(List.of(), List.of("minecraft"));

        AbsorptionScan.Report report = AbsorptionScan.run();

        assertTrue(report.excluded().stream().anyMatch(e -> e.itemId().equals(writtenBook)
                        && e.reason() == AbsorptionPolicy.Reason.GLOBAL_NAMESPACE),
                "the reported reason must distinguish a namespace exclusion from an item exclusion");
    }

    @Test
    void countsAndGroupingsAgreeWithTheItemList() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        ResourceLocation knowledgeBook = ForgeRegistries.ITEMS.getKey(Items.KNOWLEDGE_BOOK);
        assertNotNull(writtenBook);
        assertNotNull(knowledgeBook);
        registerClaiming(writtenBook, knowledgeBook);

        AbsorptionScan.Report report = AbsorptionScan.run();

        assertEquals(report.absorbed().size(), report.absorbedCount());
        assertEquals(report.absorbedCount(),
                report.byAdapter().values().stream().mapToInt(Integer::intValue).sum(),
                "per-adapter counts must total the number of absorbable items");
        assertEquals(report.absorbedCount(),
                report.byNamespace().values().stream().mapToInt(Integer::intValue).sum(),
                "per-namespace counts must total the number of absorbable items");
        assertEquals(2, report.byAdapter().get(SCAN_ADAPTER));
    }

    @Test
    void scansTheWholeRegistryAndReportsHowMuchItCovered() {
        AbsorptionScan.Report report = AbsorptionScan.run();

        // The count is what tells an operator the scan actually ran over their pack rather than
        // silently covering a subset.
        assertTrue(report.scanned() > 100,
                "expected the full vanilla item registry to be scanned, saw " + report.scanned());
    }

    @Test
    void theNamespaceDrillDownMatchesTheFullReport() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);

        AbsorptionScan.Report report = AbsorptionScan.run();
        List<AbsorptionScan.Absorbed> vanilla = report.inNamespace("minecraft");

        assertEquals(report.absorbed().stream()
                        .filter(a -> a.itemId().getNamespace().equals("minecraft")).count(),
                vanilla.size(),
                "the drill-down must be a filter of the report, not a second classification pass");
        assertTrue(report.inNamespace("no_such_mod").isEmpty());
    }

    @Test
    void aBrokenAdapterDoesNotAbortTheScan() {
        // One broken third-party adapter must not deny an operator the diagnostic — that is exactly
        // the situation in which they most need it.
        RunicTomeAPI.registerAdapter(new GuideSystemAdapter() {
            @Override public ResourceLocation systemId() { return SCAN_ADAPTER; }
            @Override public Optional<BookKey> identify(ItemStack stack) {
                throw new IllegalStateException("adapter is broken");
            }
            @Override public void open(BookKey key, Player clientPlayer) { }
        });

        AbsorptionScan.Report report = AbsorptionScan.run();

        assertTrue(report.scanned() > 100, "the scan must still cover the registry");
        assertTrue(report.absorbed().isEmpty(), "a throwing adapter claims nothing");
    }

    @Test
    void theRenderedReportIsSelfDescribing() {
        ResourceLocation writtenBook = ForgeRegistries.ITEMS.getKey(Items.WRITTEN_BOOK);
        assertNotNull(writtenBook);
        registerClaiming(writtenBook);

        String text = AbsorptionScan.render(AbsorptionScan.run());

        assertTrue(text.contains("Items scanned:"), "the report must state its coverage");
        assertTrue(text.contains(writtenBook.toString()), "the report must name the affected item");
        assertTrue(text.contains(SCAN_ADAPTER.toString()), "the report must name the claiming adapter");
        assertTrue(text.contains("absorbExclusionItems"),
                "the report must tell the reader what to do about what it found");
    }

    @Test
    void anEmptyReportRendersWithoutFailing() {
        // No adapters registered: every section is empty, and the renderer must still produce a
        // readable document rather than a wall of blank headings or a crash.
        String text = AbsorptionScan.render(AbsorptionScan.run());

        assertTrue(text.contains("Would absorb:   0"));
        assertTrue(text.contains("(none)"));
    }
}
