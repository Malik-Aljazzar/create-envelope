package net.multyfora.compat;

import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Item handler for Envelope's mailbox (replaces Envelope's own SidedInvWrapper, see EnvelopeCapabilityRegistrationMixin).
 * Slot layout (Envelope 0.8): 0 = outgoing mail, 1 = pigeon food, 2 = bat food, 3.. = inbox (one slot per received
 * mail), followed by a read-only view of the outgoing Create package's contents.
 */
public class MailboxItemHandler implements IItemHandler {
    private final MailboxBlockEntity mailbox;

    public MailboxItemHandler(MailboxBlockEntity mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public int getSlots() {
        int mailSize = mailbox.getAllMail().size();
        int base = MailboxBlockEntity.SLOT_INBOX + mailSize;
        ItemStack mailStack = mailbox.getItem(MailboxBlockEntity.SLOT_MAIL);
        if (PackageItem.isPackage(mailStack)) {
            ItemStackHandler contents = PackageItem.getContents(mailStack);
            if (contents.getSlots() > 0)
                base += contents.getSlots();
        }
        return base;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot >= 0 && slot < MailboxBlockEntity.SLOT_INBOX)
            return mailbox.getItem(slot);

        if (slot >= MailboxBlockEntity.SLOT_INBOX) {
            int mailSize = mailbox.getAllMail().size();
            int inboxIndex = slot - MailboxBlockEntity.SLOT_INBOX;
            if (inboxIndex < mailSize)
                return mailbox.getAllMail().get(inboxIndex);

            int contentIndex = inboxIndex - mailSize;
            ItemStack mailStack = mailbox.getItem(MailboxBlockEntity.SLOT_MAIL);
            if (PackageItem.isPackage(mailStack)) {
                ItemStackHandler contents = PackageItem.getContents(mailStack);
                if (contentIndex < contents.getSlots())
                    return contents.getStackInSlot(contentIndex).copy();
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

        if (slot == MailboxBlockEntity.SLOT_MAIL && PackageItem.isPackage(stack) && !simulate) {
            Level level = mailbox.getLevel();

            PackageConversion.syncEnvelopeContents(stack);

            PackageConversion.syncAddresses(stack, level);
        }

        ItemStack existing = mailbox.getItem(slot);
        int maxInsert = Math.min(stack.getMaxStackSize(), getSlotLimit(slot)) - existing.getCount();
        if (maxInsert <= 0) return stack;

        int toInsert = Math.min(stack.getCount(), maxInsert);
        ItemStack remaining = stack.copy();
        remaining.shrink(toInsert);

        if (!simulate) {
            if (existing.isEmpty()) {
                mailbox.setItem(slot, stack.copyWithCount(toInsert));
            } else {
                existing.grow(toInsert);
            }
            mailbox.setChanged();
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.SLOT_INBOX + mailSize;
        if (slot < MailboxBlockEntity.SLOT_INBOX || slot >= inboxEnd)
            return ItemStack.EMPTY;

        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copyWithCount(toExtract);

        if (!simulate) {
            mailbox.removeItem(slot, toExtract);
        }

        if (PackageConversion.isEnvelopePackage(extracted))
            extracted = PackageConversion.toCreatePackage(extracted);

        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.SLOT_INBOX + mailSize;
        if (slot >= inboxEnd) return 0;
        return slot < MailboxBlockEntity.SLOT_INBOX ? 64 : 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.SLOT_INBOX + mailSize;
        if (slot < 0 || slot >= inboxEnd) return false;
        // Outgoing mail and the courier food slots: Envelope decides (isSendable / pigeon food / bat food tags).
        if (slot < MailboxBlockEntity.SLOT_INBOX) return mailbox.canPlaceItem(slot, stack);
        return false; // inbox slots only hand out received mail
    }
}
