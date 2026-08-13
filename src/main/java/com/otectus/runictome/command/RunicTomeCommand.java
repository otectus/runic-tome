package com.otectus.runictome.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import com.otectus.runictome.impl.AbsorptionPolicy;
import com.otectus.runictome.impl.AbsorptionScan;
import com.otectus.runictome.integration.HeuristicBookAdapter;
import com.otectus.runictome.integration.ModTags;
import com.otectus.runictome.item.ModItems;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.UnlockBookPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code /runictome} command tree.
 *
 * <p>Every player-scoped subcommand takes an optional target, so a server operator can inspect and
 * repair another player's library from the console — previously all of them called
 * {@code getPlayerOrException()}, which made the entire command tree unusable from a console and
 * offered no way to help a player who could not help themselves (RT-20). Output is localized and
 * paginated for the same reason: a 200-book library printed as raw literals is not a diagnostic.
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class RunicTomeCommand {

    /** Cap on entries listed by the purge dry run, so a large library cannot flood chat. */
    private static final int PURGE_PREVIEW_LIMIT = 30;

    /** Library entries per page of {@code /runictome list}. */
    private static final int PAGE_SIZE = 20;

    /** Cap on items listed per namespace by {@code /runictome debug scan <namespace>}. */
    private static final int SCAN_LIST_LIMIT = 60;

    /** Filename the full scan report is written to, relative to the server directory. */
    private static final String SCAN_REPORT_FILE = "runictome-scan.txt";

    private RunicTomeCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("runictome")
                .requires(s -> s.hasPermission(2))

                // list [page] | list <target> [page]
                // The page branch is registered first on purpose: a bare number means a page, and a
                // name means a player. A player whose name is only digits is still reachable with a
                // selector such as @a[name="5"].
                .then(Commands.literal("list")
                        .executes(c -> listBooks(c, self(c), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> listBooks(c, self(c), page(c))))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> listBooks(c, target(c), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> listBooks(c, target(c), page(c))))))

                .then(Commands.literal("give")
                        .executes(c -> giveTome(c, self(c)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> giveTome(c, target(c)))))

                .then(Commands.literal("unlock")
                        .then(Commands.argument("system", ResourceLocationArgument.id())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(c -> unlockBook(c, self(c)))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(c -> unlockBook(c, target(c)))))))

                .then(Commands.literal("lock")
                        .then(Commands.argument("system", ResourceLocationArgument.id())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(c -> lockBook(c, self(c)))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(c -> lockBook(c, target(c)))))))

                .then(Commands.literal("purge")
                        .executes(c -> purgeExcluded(c, self(c), false))
                        .then(Commands.literal("confirm")
                                .executes(c -> purgeExcluded(c, self(c), true))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> purgeExcluded(c, target(c), true))))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> purgeExcluded(c, target(c), false))))

                .then(Commands.literal("unmark")
                        .executes(c -> unmarkExtracted(c, self(c)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> unmarkExtracted(c, target(c)))))

                .then(Commands.literal("debug")
                        .then(Commands.literal("dump").executes(RunicTomeCommand::debugDump))
                        .then(Commands.literal("identify")
                                .executes(c -> debugIdentify(c, self(c)))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> debugIdentify(c, target(c)))))
                        .then(Commands.literal("scan")
                                .executes(c -> debugScan(c, null))
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(c -> debugScan(c,
                                                StringArgumentType.getString(c, "namespace")))))));
    }

    // ---------------------------------------------------------------- target resolution

    /**
     * The command's own player. Fails with the vanilla "must be a player" error when run from a
     * console without a target argument — every subcommand that calls this also offers a
     * {@code <target>} branch, which is the console's route.
     */
    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, "target");
    }

    private static int page(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "page");
    }

    /** Capability accessor that reports a localized failure rather than silently doing nothing. */
    private static IRunicTomeData dataOf(CommandSourceStack src, ServerPlayer player) {
        IRunicTomeData data = player.getCapability(RunicTomeCapabilities.PLAYER_DATA).orElse(null);
        if (data == null) {
            src.sendFailure(Component.translatable("commands.runictome.no_capability", name(player)));
        }
        return data;
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    // ---------------------------------------------------------------- library inspection

    private static int listBooks(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int page) {
        CommandSourceStack src = ctx.getSource();
        IRunicTomeData data = dataOf(src, player);
        if (data == null) return 0;

        // Sorted rather than insertion-ordered: paging is only meaningful over a stable order, and
        // absorption order is not stable across a re-login.
        List<BookKey> books = new ArrayList<>(data.getBooks());
        books.sort(Comparator.comparing(BookKey::toString));

        if (books.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.list.empty", name(player)), false);
            return 0;
        }

        int pages = (books.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pages) {
            final int requested = page;
            src.sendFailure(Component.translatable("commands.runictome.list.bad_page", requested, pages));
            return 0;
        }

        final int shownPage = page;
        src.sendSuccess(() -> Component.translatable("commands.runictome.list.header",
                name(player), books.size(), shownPage, pages).withStyle(ChatFormatting.GOLD), false);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, books.size());
        for (int i = from; i < to; i++) {
            BookKey key = books.get(i);
            boolean favorite = data.isFavorite(key);
            src.sendSuccess(() -> Component.translatable(
                    favorite ? "commands.runictome.list.entry.favorite" : "commands.runictome.list.entry",
                    key.toString()), false);
        }
        if (pages > 1) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.list.more",
                    name(player), Math.min(shownPage + 1, pages)).withStyle(ChatFormatting.GRAY), false);
        }
        return books.size();
    }

    private static int giveTome(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ItemStack tome = new ItemStack(ModItems.RUNIC_TOME.get());
        if (!player.getInventory().add(tome)) {
            player.drop(tome, false);
        }
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.runictome.give.success", name(player)), true);
        return 1;
    }

    private static int unlockBook(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack src = ctx.getSource();
        BookKey key = keyArg(ctx);
        IRunicTomeData data = dataOf(src, player);
        if (data == null) return 0;

        if (!data.unlockBook(key)) {
            src.sendFailure(Component.translatable("commands.runictome.unlock.already",
                    key.toString(), name(player)));
            return 0;
        }
        RunicTomeNetwork.sendTo(player, new UnlockBookPacket(key, ItemStack.EMPTY));
        CapabilityEvents.syncTo(player);
        src.sendSuccess(() -> Component.translatable("commands.runictome.unlock.success",
                key.toString(), name(player)), true);
        return 1;
    }

    private static int lockBook(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack src = ctx.getSource();
        BookKey key = keyArg(ctx);
        IRunicTomeData data = dataOf(src, player);
        if (data == null) return 0;

        if (!data.lockBook(key)) {
            src.sendFailure(Component.translatable("commands.runictome.lock.absent",
                    key.toString(), name(player)));
            return 0;
        }
        CapabilityEvents.syncTo(player);
        src.sendSuccess(() -> Component.translatable("commands.runictome.lock.success",
                key.toString(), name(player)), true);
        return 1;
    }

    private static BookKey keyArg(CommandContext<CommandSourceStack> ctx) {
        return new BookKey(ResourceLocationArgument.getId(ctx, "system"),
                ResourceLocationArgument.getId(ctx, "book"));
    }

    // ---------------------------------------------------------------- recovery

    /**
     * Lists (and, with {@code confirm}, removes) library entries that the current global exclusion
     * lists would no longer allow to be absorbed. This is the recovery path for a save that absorbed
     * a functional book before it was hard-excluded; without it the only fix is save editing.
     */
    private static int purgeExcluded(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                     boolean confirmed) {
        CommandSourceStack src = ctx.getSource();
        IRunicTomeData data = dataOf(src, player);
        if (data == null) return 0;

        var excluded = AbsorptionPolicy.findExcluded(data);
        if (excluded.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.purge.none", name(player)), false);
            return 0;
        }
        if (!confirmed) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.purge.preview",
                    excluded.size(), name(player)).withStyle(ChatFormatting.GOLD), false);
            // Bounded output: a large library must not flood the chat buffer.
            int shown = Math.min(excluded.size(), PURGE_PREVIEW_LIMIT);
            for (int i = 0; i < shown; i++) {
                var entry = excluded.get(i);
                src.sendSuccess(() -> Component.translatable("commands.runictome.purge.entry",
                        entry.key().toString(), entry.reason().name()), false);
            }
            if (excluded.size() > shown) {
                int hidden = excluded.size() - shown;
                src.sendSuccess(() -> Component.translatable("commands.runictome.purge.truncated", hidden), false);
            }
            src.sendSuccess(() -> Component.translatable("commands.runictome.purge.confirm_hint")
                    .withStyle(ChatFormatting.YELLOW), false);
            src.sendSuccess(() -> Component.translatable("commands.runictome.purge.shared_item_warning")
                    .withStyle(ChatFormatting.GRAY), false);
            return excluded.size();
        }
        // Remove exactly what was listed rather than re-scanning, so the operator confirms the set
        // they were shown.
        int removed = AbsorptionPolicy.purge(data, excluded);
        CapabilityEvents.syncTo(player);
        src.sendSuccess(() -> Component.translatable("commands.runictome.purge.done",
                removed, name(player)), true);
        RunicTome.LOGGER.info("Runic Tome: purged {} excluded entries for {}", removed, name(player));
        return removed;
    }

    /**
     * Clears the extraction exemption from the held stack, letting a book extracted by mistake be
     * absorbed again. The marker is permanent by design — without this the only remedy was to
     * destroy the item and re-obtain it, or edit the save.
     */
    private static int unmarkExtracted(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack src = ctx.getSource();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.translatable("commands.runictome.unmark.empty_hand", name(player)));
            return 0;
        }
        if (!AbsorptionPolicy.clearExtracted(held)) {
            src.sendFailure(Component.translatable("commands.runictome.unmark.not_marked",
                    held.getHoverName()));
            return 0;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        src.sendSuccess(() -> Component.translatable("commands.runictome.unmark.success",
                held.getHoverName(), name(player)), true);
        return 1;
    }

    // ---------------------------------------------------------------- diagnostics

    private static int debugDump(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var adapters = RunicTomeAPI.allAdapters();
        src.sendSuccess(() -> Component.translatable("commands.runictome.dump.header", adapters.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (GuideSystemAdapter a : adapters) {
            int bulk = a.supportsBulkEnumeration() ? a.enumerateAll().size() : -1;
            src.sendSuccess(() -> Component.translatable("commands.runictome.dump.entry",
                    a.systemId().toString(), a.priority(), bulk), false);
        }
        return 1;
    }

    /**
     * Sweeps the whole item registry and reports what the current configuration would absorb.
     *
     * <p>Every other diagnostic explains an item after the fact; this one answers the question
     * before a player loses a functional book. The full report goes to a file because the useful
     * answer for a large pack is hundreds of lines long.
     */
    private static int debugScan(CommandContext<CommandSourceStack> ctx, String namespace) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.translatable("commands.runictome.scan.working")
                .withStyle(ChatFormatting.GRAY), false);

        AbsorptionScan.Report report;
        try {
            report = AbsorptionScan.run();
        } catch (Throwable t) {
            RunicTome.LOGGER.error("Runic Tome: absorption scan failed", t);
            src.sendFailure(Component.translatable("commands.runictome.scan.failed", String.valueOf(t)));
            return 0;
        }

        if (namespace != null) {
            List<AbsorptionScan.Absorbed> items = report.inNamespace(namespace);
            if (items.isEmpty()) {
                src.sendSuccess(() -> Component.translatable("commands.runictome.scan.namespace_empty",
                        namespace), false);
                return 0;
            }
            src.sendSuccess(() -> Component.translatable("commands.runictome.scan.namespace_header",
                    items.size(), namespace).withStyle(ChatFormatting.GOLD), false);
            int shown = Math.min(items.size(), SCAN_LIST_LIMIT);
            for (int i = 0; i < shown; i++) {
                AbsorptionScan.Absorbed a = items.get(i);
                src.sendSuccess(() -> Component.translatable("commands.runictome.scan.entry",
                        a.itemId().toString(), a.systemId().toString()), false);
            }
            if (items.size() > shown) {
                int hidden = items.size() - shown;
                src.sendSuccess(() -> Component.translatable("commands.runictome.scan.truncated", hidden), false);
            }
            return items.size();
        }

        src.sendSuccess(() -> Component.translatable("commands.runictome.scan.summary",
                report.scanned(), report.absorbedCount(), report.excluded().size())
                .withStyle(ChatFormatting.GOLD), false);
        report.byAdapter().forEach((id, count) ->
                src.sendSuccess(() -> Component.translatable("commands.runictome.scan.by_adapter",
                        count, id.toString()), false));

        writeReport(src, report);
        src.sendSuccess(() -> Component.translatable("commands.runictome.scan.drilldown_hint")
                .withStyle(ChatFormatting.GRAY), false);
        return report.absorbedCount();
    }

    /** Writes the full report next to the world/server files, reporting the path or the failure. */
    private static void writeReport(CommandSourceStack src, AbsorptionScan.Report report) {
        if (src.getServer() == null) return;
        Path target = src.getServer().getServerDirectory().toPath().resolve(SCAN_REPORT_FILE);
        try {
            AbsorptionScan.write(report, target);
            src.sendSuccess(() -> Component.translatable("commands.runictome.scan.written",
                    target.toString()).withStyle(ChatFormatting.GREEN), false);
        } catch (Exception e) {
            // The chat summary is still useful, so a write failure is reported, not fatal.
            RunicTome.LOGGER.warn("Runic Tome: could not write the scan report to {}", target, e);
            src.sendFailure(Component.translatable("commands.runictome.scan.write_failed",
                    target.toString()));
        }
    }

    private static int debugIdentify(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack src = ctx.getSource();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.translatable("commands.runictome.identify.empty_hand", name(player)));
            return 0;
        }
        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(held.getItem());
        src.sendSuccess(() -> Component.translatable("commands.runictome.identify.item",
                String.valueOf(heldId)).withStyle(ChatFormatting.GOLD), false);
        var tag = held.getTag();
        String nbtSummary = tag == null || tag.isEmpty()
                ? "none"
                : tag.getAllKeys().size() + " key(s): " + String.join(",", tag.getAllKeys());
        src.sendSuccess(() -> Component.translatable("commands.runictome.identify.nbt", nbtSummary), false);
        src.sendSuccess(() -> Component.translatable("commands.runictome.identify.blocklist_tag",
                String.valueOf(held.is(ModTags.Items.ABSORB_BLOCKLIST))), false);

        AbsorptionPolicy.Decision policy = AbsorptionPolicy.evaluate(held);
        src.sendSuccess(() -> Component.translatable("commands.runictome.identify.policy",
                policy.excluded() ? "EXCLUDED (" + policy.reason() + ")" : "ALLOWED"), false);

        GuideSystemAdapter match = null;
        for (GuideSystemAdapter a : RunicTomeAPI.allAdapters()) {
            if (a.identify(held).isPresent()) {
                match = a;
                break;
            }
        }
        if (match == null) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.identify.no_adapter"), false);
            src.sendSuccess(() -> policy.excluded()
                    ? Component.translatable("commands.runictome.identify.verdict.excluded",
                            policy.reason().name()).withStyle(ChatFormatting.RED)
                    : Component.translatable("commands.runictome.identify.verdict.no_match")
                            .withStyle(ChatFormatting.RED), false);
            return 1;
        }
        final GuideSystemAdapter matched = match;
        src.sendSuccess(() -> Component.translatable("commands.runictome.identify.claimed_by",
                matched.systemId().toString()), false);
        if (matched instanceof HeuristicBookAdapter heuristic) {
            heuristic.matchedKeyword(held).ifPresent(kw ->
                    src.sendSuccess(() -> Component.translatable(
                            "commands.runictome.identify.keyword", kw), false));
        }
        if (policy.excluded()) {
            src.sendSuccess(() -> Component.translatable("commands.runictome.identify.verdict.excluded",
                    policy.reason().name()).withStyle(ChatFormatting.RED), false);
            src.sendSuccess(() -> Component.translatable("commands.runictome.identify.overridden",
                    matched.systemId().toString()).withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.translatable("commands.runictome.identify.verdict.absorb",
                    matched.systemId().toString()).withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }
}
