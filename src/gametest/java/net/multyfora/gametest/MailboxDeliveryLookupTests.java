package net.multyfora.gametest;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.Inbox;
import io.github.mortuusars.envelope.world.block.mailbox.InboxStorage;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.block.mailbox.UnloadedInbox;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.dropoff.BlockDropOffHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.multyfora.Createenvelopecompat;
import net.multyfora.compat.MailboxLookup;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deliveries must reach a registered mailbox wherever its inbox currently lives.
 * <p>
 * Envelope keeps the inbox in the mailbox block entity while that exists and moves it to {@link InboxStorage} only
 * when the block entity is removed (chunk unload). Its delivery lookup asks {@code Level#isLoaded}, which turns false
 * as soon as a chunk drops below FULL - but vanilla keeps such a chunk, block entities included, in memory until it
 * leaves the loading range entirely. Each test uses its own far-away chunk so the tests don't see each other's tickets.
 */
@GameTestHolder(Createenvelopecompat.MODID)
@PrefixGameTestTemplate(false)
public class MailboxDeliveryLookupTests {

    /** A chunk far enough from the test area (and from the other tests) that only the test's own tickets load it. */
    private record Far(BlockPos pos, int cx, int cz) {
        static Far of(GameTestHelper helper, int chunksAway) {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int cx = (origin.getX() >> 4) + chunksAway;
            int cz = origin.getZ() >> 4;
            return new Far(new BlockPos((cx << 4) + 8, origin.getY() + 2, (cz << 4) + 8), cx, cz);
        }
    }

    private static BlockAddress uniqueAddress(String tag) {
        return new BlockAddress("zt_" + tag + "_" + (System.nanoTime() % 1_000_000));
    }

    private static void assertLoaded(GameTestHelper helper, BlockPos pos, boolean expected) {
        if (helper.getLevel().isLoaded(pos) != expected)
            helper.fail("chunk at " + pos.toShortString() + " should " + (expected ? "" : "not ") + "be accessible yet");
    }

    private static MailboxBlockEntity placeMailbox(GameTestHelper helper, BlockPos pos, BlockAddress address) {
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox))
            throw new IllegalStateException("No mailbox block entity at " + pos.toShortString());
        mailbox.setAddress(address);
        if (!address.equals(mailbox.getAddress()))
            helper.fail("Mailbox got address " + mailbox.getAddress() + " instead of " + address);
        return mailbox;
    }

    private static Optional<Inbox> stored(ServerLevel level, BlockAddress address) {
        return InboxStorage.get(level).getForDelivery(address);
    }

    private static Optional<Inbox> lookup(ServerLevel level, BlockAddress address) {
        return BlockDropOffHandler.getInboxByAddress(MailService.of(level), address);
    }

    private static ItemStack mail() {
        return new ItemStack(Items.PAPER);
    }

    private static boolean chunkInMemory(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(pos)) != null;
    }

    private static void cleanup(ServerLevel level, BlockAddress address) {
        stored(level, address).ifPresent(inbox -> InboxStorage.get(level).remove(((UnloadedInbox) inbox).getId()));
        MailService.of(level).getMailboxes().remove(address);
    }

    /**
     * The reported bug: the mailbox's chunk was loaded, then dropped below FULL while staying in memory. Envelope
     * returned such deliveries as "Recipient Not Found" - the inbox is neither reachable nor stored.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void mailboxInInaccessibleChunkReceivesMail(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = Far.of(helper, 64);
        BlockPos neighbour = far.pos().offset(48, 0, 0);
        BlockAddress address = uniqueAddress("inacc");
        AtomicReference<MailboxBlockEntity> mailbox = new AtomicReference<>();

        helper.startSequence()
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), true))
            .thenWaitUntil(() -> assertLoaded(helper, far.pos(), true))
            .thenExecute(() -> mailbox.set(placeMailbox(helper, far.pos(), address)))
            // A forced chunk 3 chunks away keeps the mailbox chunk at ticket level 34: in memory, but not FULL.
            .thenExecute(() -> level.setChunkForced(far.cx() + 3, far.cz(), true))
            .thenWaitUntil(() -> assertLoaded(helper, neighbour, true))
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), false))
            .thenWaitUntil(() -> assertLoaded(helper, far.pos(), false))
            .thenExecute(() -> {
                if (mailbox.get().isRemoved() || !chunkInMemory(level, far.pos()))
                    helper.fail("precondition: the mailbox block entity should still be in memory");
                if (stored(level, address).isPresent())
                    helper.fail("precondition: the inbox should not be stored while its block entity exists");

                Optional<Inbox> inbox = lookup(level, address);
                if (inbox.isEmpty())
                    helper.fail("No inbox found for a mailbox whose chunk is in memory but below FULL (Recipient Not Found)");
                if (inbox.get() != mailbox.get())
                    helper.fail("Expected the live mailbox block entity, got " + inbox.get());
                if (!inbox.get().addMail(mail()))
                    helper.fail("Could not add mail to the inbox");
            })
            .thenExecute(() -> level.setChunkForced(far.cx() + 3, far.cz(), false))
            // Once the chunk is really unloaded, Envelope stores the inbox - the delivered mail must be in it.
            .thenWaitUntil(() -> {
                Optional<Inbox> stored = stored(level, address);
                if (stored.isEmpty()) helper.fail("inbox not stored yet");
                if (stored.get().getAllMail().size() != 1)
                    helper.fail("The stored inbox should hold the delivered mail, got " + stored.get().getAllMail());
            })
            .thenExecute(() -> cleanup(level, address))
            .thenSucceed();
    }

    /**
     * A registered mailbox whose stored inbox is gone (e.g. lost in a crash: Envelope takes the inbox out of storage
     * when the block entity loads and only writes it back on unload). Its chunk is loaded so the mail can arrive,
     * and the inbox is stored again when the chunk unloads.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void mailboxWithLostStoredInboxIsLoadedForDelivery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = Far.of(helper, 96);
        BlockAddress address = uniqueAddress("lost");

        helper.startSequence()
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), true))
            .thenWaitUntil(() -> assertLoaded(helper, far.pos(), true))
            .thenExecute(() -> placeMailbox(helper, far.pos(), address))
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), false))
            .thenWaitUntil(() -> {
                if (chunkInMemory(level, far.pos())) helper.fail("chunk not unloaded yet");
                if (stored(level, address).isEmpty()) helper.fail("inbox not stored yet");
            })
            .thenExecute(() -> {
                InboxStorage.get(level).remove(((UnloadedInbox) stored(level, address).orElseThrow()).getId());
                if (stored(level, address).isPresent()) helper.fail("precondition: the stored inbox should be gone");

                Optional<Inbox> inbox = lookup(level, address);
                if (inbox.isEmpty())
                    helper.fail("No inbox found for a registered mailbox whose stored inbox was lost (Recipient Not Found)");
                if (!(inbox.get() instanceof MailboxBlockEntity))
                    helper.fail("Expected the mailbox block entity, got " + inbox.get());
                if (!inbox.get().addMail(mail()))
                    helper.fail("Could not add mail to the inbox");
            })
            // Nothing keeps the chunk loaded: it unloads by itself and its inbox is stored again, mail included.
            .thenWaitUntil(() -> {
                if (chunkInMemory(level, far.pos())) helper.fail("chunk not unloaded yet");
                Optional<Inbox> stored = stored(level, address);
                if (stored.isEmpty()) helper.fail("inbox not stored yet");
                if (stored.get().getAllMail().size() != 1)
                    helper.fail("The stored inbox should hold the delivered mail, got " + stored.get().getAllMail());
            })
            .thenExecute(() -> cleanup(level, address))
            .thenSucceed();
    }

    /** The normal case: an unloaded mailbox with a stored inbox is served from storage, without loading its chunk. */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void unloadedMailboxIsServedFromStorage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = Far.of(helper, 128);
        BlockAddress address = uniqueAddress("stored");

        helper.startSequence()
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), true))
            .thenWaitUntil(() -> assertLoaded(helper, far.pos(), true))
            .thenExecute(() -> placeMailbox(helper, far.pos(), address))
            .thenExecute(() -> level.setChunkForced(far.cx(), far.cz(), false))
            .thenWaitUntil(() -> {
                if (chunkInMemory(level, far.pos())) helper.fail("chunk not unloaded yet");
                if (stored(level, address).isEmpty()) helper.fail("inbox not stored yet");
            })
            .thenExecute(() -> {
                Optional<Inbox> inbox = lookup(level, address);
                if (inbox.isEmpty()) helper.fail("The stored inbox was not found");
                if (!(inbox.get() instanceof UnloadedInbox))
                    helper.fail("Expected the stored inbox, got " + inbox.get());
                if (chunkInMemory(level, far.pos()))
                    helper.fail("The lookup loaded the chunk although the inbox was stored");
            })
            .thenExecute(() -> cleanup(level, address))
            .thenSucceed();
    }

    /**
     * An address registered at a position without a mailbox (stale registration): one attempt to load the chunk,
     * then a cooldown, so repeated deliveries don't load the chunk every time.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void staleRegistrationLoadsChunkOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = Far.of(helper, 160);
        BlockAddress address = uniqueAddress("stale");

        helper.startSequence()
            .thenExecute(() -> {
                MailService.of(level).getMailboxes().correctOrRegisterIfNeeded(address, far.pos());
                if (lookup(level, address).isPresent())
                    helper.fail("Found an inbox for an address without a mailbox");
                if (!MailboxLookup.isOnLoadCooldown(level, address))
                    helper.fail("A failed load attempt should start the cooldown");
            })
            .thenWaitUntil(() -> {
                if (chunkInMemory(level, far.pos())) helper.fail("chunk not unloaded yet");
            })
            .thenExecute(() -> {
                if (lookup(level, address).isPresent())
                    helper.fail("Found an inbox for an address without a mailbox");
                if (chunkInMemory(level, far.pos()))
                    helper.fail("The chunk was loaded again during the cooldown");
            })
            .thenExecute(() -> cleanup(level, address))
            .thenSucceed();
    }
}
