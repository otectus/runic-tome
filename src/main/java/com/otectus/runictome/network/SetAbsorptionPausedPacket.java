package com.otectus.runictome.network;

import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.impl.RequestLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server request to switch ambient absorption on or off for the sender.
 *
 * <p>Absolute, not a flip: applying a received state must be idempotent, or a retransmitted or
 * reordered request would invert the flag instead of converging on it. The answer is a narrow
 * {@link SyncAbsorptionPausedPacket} rather than a full capability sync, for the reason
 * {@link ToggleFavoritePacket} documents — answering a one-bit change by re-serializing the whole
 * library is an amplification lever.
 */
public record SetAbsorptionPausedPacket(boolean paused) {

    public static void encode(SetAbsorptionPausedPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.paused);
    }

    public static SetAbsorptionPausedPacket decode(FriendlyByteBuf buf) {
        return new SetAbsorptionPausedPacket(buf.readBoolean());
    }

    public static void handle(SetAbsorptionPausedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Registered via consumerMainThread: already on the server thread.
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            player.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                    .ifPresent(data -> apply(player, data, msg.paused));
        }
    }

    private static void apply(ServerPlayer player, IRunicTomeData data, boolean paused) {
        long now = player.server == null ? 0L : player.server.getTickCount();
        if (!RequestLimits.allow(RequestLimits.Kind.PAUSE, player, now)) {
            // Over the allowance: do not apply, but still answer. The client flipped optimistically,
            // so staying silent would leave its button reading the wrong state until the next full
            // sync. Correcting costs the same tiny packet as accepting.
            respond(player, data);
            return;
        }
        boolean changed = data.isAbsorptionPaused() != paused;
        data.setAbsorptionPaused(paused);
        respond(player, data);
        if (changed) {
            // Confirm in chat as well as on the button: the setting outlives the screen, and a
            // player who forgets it is on would otherwise see books silently stop being absorbed.
            player.displayClientMessage(Component.translatable(
                    paused ? "runictome.absorb.paused" : "runictome.absorb.resumed"), false);
        }
    }

    /** Reports the authoritative flag, read back rather than echoing what the client asked for. */
    private static void respond(ServerPlayer player, IRunicTomeData data) {
        RunicTomeNetwork.sendTo(player, new SyncAbsorptionPausedPacket(data.isAbsorptionPaused()));
    }
}
