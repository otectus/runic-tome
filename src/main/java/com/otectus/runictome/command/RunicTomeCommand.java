package com.otectus.runictome.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.event.CapabilityEvents;
import com.otectus.runictome.item.ModItems;
import com.otectus.runictome.network.RunicTomeNetwork;
import com.otectus.runictome.network.UnlockBookPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class RunicTomeCommand {

    private RunicTomeCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("runictome")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(RunicTomeCommand::listBooks))
                .then(Commands.literal("give").executes(RunicTomeCommand::giveTome))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("system", ResourceLocationArgument.id())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(RunicTomeCommand::unlockBook))))
                .then(Commands.literal("lock")
                        .then(Commands.argument("system", ResourceLocationArgument.id())
                                .then(Commands.argument("book", ResourceLocationArgument.id())
                                        .executes(RunicTomeCommand::lockBook))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("dump").executes(RunicTomeCommand::debugDump))));
    }

    private static int debugDump(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var adapters = RunicTomeAPI.allAdapters();
        src.sendSuccess(() -> Component.literal("Adapters registered: " + adapters.size()), false);
        for (GuideSystemAdapter a : adapters) {
            int bulk = a.supportsBulkEnumeration() ? a.enumerateAll().size() : -1;
            src.sendSuccess(() -> Component.literal(" - " + a.systemId() + "  bulk=" + bulk), false);
        }
        return 1;
    }

    private static int listBooks(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        sp.getCapability(RunicTomeCapabilities.PLAYER_DATA).ifPresent(data -> {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("Runic Tome: " + data.getBooks().size() + " unlocked"), false);
            data.getBooks().forEach(k ->
                    ctx.getSource().sendSuccess(() -> Component.literal(" - " + k), false));
        });
        return 1;
    }

    private static int giveTome(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ItemStack tome = new ItemStack(ModItems.RUNIC_TOME.get());
        if (!sp.getInventory().add(tome)) {
            sp.drop(tome, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Granted Runic Tome"), false);
        return 1;
    }

    private static int unlockBook(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ResourceLocation system = ResourceLocationArgument.getId(ctx, "system");
        ResourceLocation book = ResourceLocationArgument.getId(ctx, "book");
        BookKey key = new BookKey(system, book);
        boolean added = sp.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.unlockBook(key))
                .orElse(false);
        if (added) {
            RunicTomeNetwork.sendTo(sp, new UnlockBookPacket(key));
            CapabilityEvents.syncTo(sp);
            ctx.getSource().sendSuccess(() -> Component.literal("Unlocked " + key), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("Already unlocked or cap missing: " + key));
        }
        return 1;
    }

    private static int lockBook(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ResourceLocation system = ResourceLocationArgument.getId(ctx, "system");
        ResourceLocation book = ResourceLocationArgument.getId(ctx, "book");
        BookKey key = new BookKey(system, book);
        boolean removed = sp.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                .map(data -> data.lockBook(key))
                .orElse(false);
        if (removed) {
            CapabilityEvents.syncTo(sp);
            ctx.getSource().sendSuccess(() -> Component.literal("Locked " + key), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("Not present: " + key));
        }
        return 1;
    }
}
