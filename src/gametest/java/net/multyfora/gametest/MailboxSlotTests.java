package net.multyfora.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.multyfora.Createenvelopecompat;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Courier food fed into a mailbox by logistics blocks must end up in its food slot (seeds -> pigeon food, spider
 * eyes -> bat food), never in the outgoing mail slot or the inbox.
 */
@GameTestHolder(Createenvelopecompat.MODID)
@PrefixGameTestTemplate(false)
public class MailboxSlotTests {
    private static final BlockPos MAILBOX = new BlockPos(1, 1, 1);

    private static MailboxBlockEntity placeMailbox(GameTestHelper helper) {
        helper.setBlock(MAILBOX, Envelope.Blocks.MAILBOX.get());
        return helper.getBlockEntity(MAILBOX);
    }

    private static IItemHandler handler(GameTestHelper helper, Direction side) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MAILBOX), side);
        if (handler == null) helper.fail("Mailbox has no item handler");
        return handler;
    }

    private static String describe(MailboxBlockEntity mailbox) {
        return "mail=" + mailbox.getItem(MailboxBlockEntity.SLOT_MAIL)
                + " pigeonFood=" + mailbox.getItem(MailboxBlockEntity.SLOT_PIGEON_FOOD)
                + " batFood=" + mailbox.getItem(MailboxBlockEntity.SLOT_BAT_FOOD)
                + " inbox=" + mailbox.getAllMail();
    }

    private static void assertOnlyIn(GameTestHelper helper, MailboxBlockEntity mailbox, int slot, Item item, int count) {
        ItemStack inSlot = mailbox.getItem(slot);
        if (!inSlot.is(item) || inSlot.getCount() != count)
            helper.fail("Expected " + count + "x " + item + " in slot " + slot + ", got " + describe(mailbox));
        for (int other = 0; other < MailboxBlockEntity.SLOT_INBOX; other++)
            if (other != slot && !mailbox.getItem(other).isEmpty())
                helper.fail("Slot " + other + " should be empty: " + describe(mailbox));
        if (!mailbox.getAllMail().isEmpty())
            helper.fail("Inbox should be empty: " + describe(mailbox));
    }

    private static void chuteFeeds(GameTestHelper helper, Item item, int expectedSlot) {
        MailboxBlockEntity mailbox = placeMailbox(helper);
        BlockPos chutePos = MAILBOX.above();
        helper.setBlock(chutePos, AllBlocks.CHUTE.get());
        ChuteBlockEntity chute = helper.getBlockEntity(chutePos);
        chute.setItem(new ItemStack(item, 16));

        helper.succeedWhen(() -> {
            if (!chute.getItem().isEmpty())
                helper.fail("Chute still holds " + chute.getItem() + ", mailbox: " + describe(mailbox));
            assertOnlyIn(helper, mailbox, expectedSlot, item, 16);
        });
    }

    @GameTest(template = "empty")
    public static void chuteSeedsGoToPigeonFood(GameTestHelper helper) {
        chuteFeeds(helper, Items.WHEAT_SEEDS, MailboxBlockEntity.SLOT_PIGEON_FOOD);
    }

    @GameTest(template = "empty")
    public static void chuteSpiderEyesGoToBatFood(GameTestHelper helper) {
        chuteFeeds(helper, Items.SPIDER_EYE, MailboxBlockEntity.SLOT_BAT_FOOD);
    }

    @GameTest(template = "empty")
    public static void handlerRoutesFoodFromEverySide(GameTestHelper helper) {
        MailboxBlockEntity mailbox = placeMailbox(helper);

        for (Direction side : Direction.values()) {
            mailbox.clearContent();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler(helper, side), new ItemStack(Items.WHEAT_SEEDS, 8), false);
            if (!rest.isEmpty()) helper.fail("Seeds rejected from " + side + ": " + describe(mailbox));
            assertOnlyIn(helper, mailbox, MailboxBlockEntity.SLOT_PIGEON_FOOD, Items.WHEAT_SEEDS, 8);

            mailbox.clearContent();
            rest = ItemHandlerHelper.insertItemStacked(handler(helper, side), new ItemStack(Items.SPIDER_EYE, 8), false);
            if (!rest.isEmpty()) helper.fail("Spider eyes rejected from " + side + ": " + describe(mailbox));
            assertOnlyIn(helper, mailbox, MailboxBlockEntity.SLOT_BAT_FOOD, Items.SPIDER_EYE, 8);
        }

        helper.succeed();
    }

    // Once the food slot is full, more food must stay in the chute instead of overflowing into the mail slot.
    private static void chuteOverflow(GameTestHelper helper, Item item, int foodSlot) {
        MailboxBlockEntity mailbox = placeMailbox(helper);
        mailbox.setItem(foodSlot, new ItemStack(item, 64));
        BlockPos chutePos = MAILBOX.above();
        helper.setBlock(chutePos, AllBlocks.CHUTE.get());
        ChuteBlockEntity chute = helper.getBlockEntity(chutePos);
        chute.setItem(new ItemStack(item, 16));

        helper.runAfterDelay(40, () -> {
            if (chute.getItem().getCount() != 16)
                helper.fail("Chute should still hold 16, holds " + chute.getItem() + ", mailbox: " + describe(mailbox));
            assertOnlyIn(helper, mailbox, foodSlot, item, 64);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void chuteSeedsDoNotOverflowIntoMail(GameTestHelper helper) {
        chuteOverflow(helper, Items.WHEAT_SEEDS, MailboxBlockEntity.SLOT_PIGEON_FOOD);
    }

    @GameTest(template = "empty")
    public static void chuteSpiderEyesDoNotOverflowIntoMail(GameTestHelper helper) {
        chuteOverflow(helper, Items.SPIDER_EYE, MailboxBlockEntity.SLOT_BAT_FOOD);
    }

    // A caller that ignores the slot rules and writes food straight into the mail slot: the mailbox moves it to the
    // food slot, and drops what doesn't fit instead of keeping it in the mail slot or deleting it.
    @GameTest(template = "empty")
    public static void foodSetIntoMailSlotIsRedirected(GameTestHelper helper) {
        MailboxBlockEntity mailbox = placeMailbox(helper);
        mailbox.setItem(MailboxBlockEntity.SLOT_PIGEON_FOOD, new ItemStack(Items.WHEAT_SEEDS, 60));

        mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, new ItemStack(Items.WHEAT_SEEDS, 10));
        assertOnlyIn(helper, mailbox, MailboxBlockEntity.SLOT_PIGEON_FOOD, Items.WHEAT_SEEDS, 64);

        mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, new ItemStack(Items.SPIDER_EYE, 5));
        if (!mailbox.getItem(MailboxBlockEntity.SLOT_MAIL).isEmpty())
            helper.fail("Mail slot should stay empty: " + describe(mailbox));
        if (mailbox.getItem(MailboxBlockEntity.SLOT_BAT_FOOD).getCount() != 5)
            helper.fail("Spider eyes should be in the bat food slot: " + describe(mailbox));

        helper.runAfterDelay(5, () -> {
            int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(helper.absolutePos(MAILBOX)).inflate(3))
                    .stream().filter(e -> e.getItem().is(Items.WHEAT_SEEDS)).mapToInt(e -> e.getItem().getCount()).sum();
            if (dropped != 6) helper.fail("Expected the 6 seeds that didn't fit to be dropped, found " + dropped);
            helper.succeed();
        });
    }

    // A different kind of seeds must not be merged into (and turned into) the seeds already in the slot.
    @GameTest(template = "empty")
    public static void differentSeedsAreNotMerged(GameTestHelper helper) {
        MailboxBlockEntity mailbox = placeMailbox(helper);
        IItemHandler handler = handler(helper, Direction.UP);

        ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.WHEAT_SEEDS, 8), false);
        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.BEETROOT_SEEDS, 4), false);

        if (rest.getCount() != 4 || !rest.is(Items.BEETROOT_SEEDS))
            helper.fail("Beetroot seeds should be rejected, rest=" + rest + ", mailbox: " + describe(mailbox));
        assertOnlyIn(helper, mailbox, MailboxBlockEntity.SLOT_PIGEON_FOOD, Items.WHEAT_SEEDS, 8);
        helper.succeed();
    }
}
