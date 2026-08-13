package com.otectus.runictome;

import com.otectus.runictome.integration.datapack.BookDef;
import com.otectus.runictome.network.SyncBookDefsPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for RT-07: decoding must not size collections from an untrusted VarInt. */
class SyncBookDefsPacketTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void roundTripPreservesDefinitions() {
        List<BookDef> defs = List.of(
                new BookDef(new ResourceLocation("runictome", "datapack/pack/one"),
                        new ResourceLocation("immersiveengineering", "manual"), "Engineer's Manual"),
                new BookDef(new ResourceLocation("runictome", "datapack/pack/two"),
                        new ResourceLocation("occultism", "dictionary_of_spirits"), null));

        FriendlyByteBuf b = buf();
        SyncBookDefsPacket.encode(new SyncBookDefsPacket(defs), b);
        SyncBookDefsPacket decoded = SyncBookDefsPacket.decode(b);

        assertEquals(2, decoded.defs().size());
        assertEquals(defs.get(0), decoded.defs().get(0));
        assertEquals(defs.get(1).itemId(), decoded.defs().get(1).itemId());
        assertNull(decoded.defs().get(1).name(), "a blank name must decode back to null, not \"\"");
        assertEquals(0, b.readableBytes(), "the whole payload must be consumed");
    }

    @Test
    void hostileCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf b = buf();
        b.writeVarInt(Integer.MAX_VALUE);
        // Must fail fast on validation, not by trying to pre-size a 2-billion-entry list.
        assertThrows(IllegalArgumentException.class, () -> SyncBookDefsPacket.decode(b));
    }

    @Test
    void countJustOverTheLimitIsRejected() {
        FriendlyByteBuf b = buf();
        b.writeVarInt(SyncBookDefsPacket.MAX_DEFS + 1);
        assertThrows(IllegalArgumentException.class, () -> SyncBookDefsPacket.decode(b));
    }

    @Test
    void negativeCountIsRejected() {
        FriendlyByteBuf b = buf();
        b.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class, () -> SyncBookDefsPacket.decode(b));
    }

    @Test
    void truncatedPayloadFailsOnReadNotOnAllocation() {
        FriendlyByteBuf b = buf();
        b.writeVarInt(64); // within the limit, but no elements follow
        assertThrows(RuntimeException.class, () -> SyncBookDefsPacket.decode(b));
    }

    @Test
    void encodingClampsAnOversizedDefinitionSet() {
        List<BookDef> tooMany = new ArrayList<>();
        for (int i = 0; i < SyncBookDefsPacket.MAX_DEFS + 5; i++) {
            tooMany.add(new BookDef(new ResourceLocation("runictome", "datapack/pack/n" + i),
                    new ResourceLocation("minecraft", "book"), null));
        }
        FriendlyByteBuf b = buf();
        SyncBookDefsPacket.encode(new SyncBookDefsPacket(tooMany), b);
        SyncBookDefsPacket decoded = SyncBookDefsPacket.decode(b);
        assertEquals(SyncBookDefsPacket.MAX_DEFS, decoded.defs().size());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void overlongNameIsTruncatedRatherThanCrashingTheEncoder() {
        String huge = "x".repeat(SyncBookDefsPacket.MAX_NAME_LENGTH * 4);
        FriendlyByteBuf b = buf();
        SyncBookDefsPacket.encode(new SyncBookDefsPacket(List.of(
                new BookDef(new ResourceLocation("runictome", "datapack/pack/long"),
                        new ResourceLocation("minecraft", "book"), huge))), b);
        SyncBookDefsPacket decoded = SyncBookDefsPacket.decode(b);
        assertTrue(decoded.defs().get(0).name().length() <= SyncBookDefsPacket.MAX_NAME_LENGTH);
    }
}
