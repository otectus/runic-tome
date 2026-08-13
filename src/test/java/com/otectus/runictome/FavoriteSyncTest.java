package com.otectus.runictome;

import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.capability.RunicTomeData;
import com.otectus.runictome.network.SyncFavoritePacket;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the incremental favorite path that replaced the full-library re-sync (RT-10).
 *
 * <p>Two things have to hold for that replacement to be safe: the packet must round-trip, and
 * applying a received state must be <em>idempotent</em>. The old client path only knew how to flip,
 * which is fine for a locally-initiated toggle but wrong for an authoritative update — a repeated or
 * reordered message would invert the flag instead of converging on the server's value.
 */
class FavoriteSyncTest {

    private static final ResourceLocation HEURISTIC = new ResourceLocation("runictome", "heuristic");
    private static final BookKey KEY =
            new BookKey(HEURISTIC, new ResourceLocation("mod", "field_guide"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void packetRoundTripsBothStates() {
        for (boolean favorite : new boolean[]{true, false}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            SyncFavoritePacket.encode(new SyncFavoritePacket(KEY, favorite), buf);
            SyncFavoritePacket decoded = SyncFavoritePacket.decode(buf);

            assertEquals(KEY, decoded.key());
            assertEquals(favorite, decoded.favorite());
            assertEquals(0, buf.readableBytes(), "the decoder must consume exactly what was written");
        }
    }

    @Test
    void thePacketIsSmallAndIndependentOfLibrarySize() {
        // The whole point of RT-10: the response to a toggle must not scale with the library. A
        // fixed-size payload is what removes the amplification, so pin that it stays fixed.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncFavoritePacket.encode(new SyncFavoritePacket(KEY, true), buf);
        int size = buf.readableBytes();

        assertTrue(size < 128, "expected a small fixed payload, was " + size + " bytes");
    }

    @Test
    void applyingAReceivedStateIsIdempotent() {
        RunicTomeData data = new RunicTomeData();
        data.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));

        // Applying "true" repeatedly must converge on true, not oscillate.
        for (int i = 0; i < 5; i++) {
            data.setFavorite(KEY, true);
            assertTrue(data.isFavorite(KEY), "repeat #" + i + " must not flip the flag back");
        }
        for (int i = 0; i < 5; i++) {
            data.setFavorite(KEY, false);
            assertFalse(data.isFavorite(KEY), "repeat #" + i + " must not flip the flag back");
        }
    }

    @Test
    void setFavoriteReturnsTheResultingState() {
        RunicTomeData data = new RunicTomeData();
        data.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));

        assertTrue(data.setFavorite(KEY, true));
        assertTrue(data.setFavorite(KEY, true), "already true: still reports true");
        assertFalse(data.setFavorite(KEY, false));
        assertFalse(data.setFavorite(KEY, false), "already false: still reports false");
    }

    @Test
    void aBookThatIsNotUnlockedCanNeverBecomeAFavorite() {
        // The server answers every toggle request, including ones for books the player does not own.
        // Applying that answer must not invent a favorite for a book that is not in the library.
        RunicTomeData data = new RunicTomeData();

        assertFalse(data.setFavorite(KEY, true), "an unowned book must not report becoming a favorite");
        assertFalse(data.isFavorite(KEY));
        assertTrue(data.getFavorites().isEmpty());
    }

    @Test
    void anOptimisticLocalFlipConvergesOnTheServerState() {
        // The client flips locally for instant feedback, then the server's authoritative value
        // arrives. Whether the two agree or not, the end state must be the server's.
        RunicTomeData client = new RunicTomeData();
        client.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));

        client.toggleFavorite(KEY);              // optimistic: now true
        client.setFavorite(KEY, true);           // server agreed
        assertTrue(client.isFavorite(KEY));

        client.toggleFavorite(KEY);              // optimistic: now false
        client.setFavorite(KEY, true);           // server disagreed (e.g. rate limited)
        assertTrue(client.isFavorite(KEY), "the server's value wins, without a full re-sync");
    }

    @Test
    void lockingABookStillClearsItsFavorite() {
        // Pins that the incremental path did not bypass the invariant that a locked book cannot
        // remain favorited.
        RunicTomeData data = new RunicTomeData();
        data.unlockBook(KEY, new ItemStack(Items.WRITTEN_BOOK));
        data.setFavorite(KEY, true);

        assertTrue(data.lockBook(KEY));
        assertFalse(data.isFavorite(KEY));
        assertTrue(data.getFavorites().isEmpty());
    }
}
