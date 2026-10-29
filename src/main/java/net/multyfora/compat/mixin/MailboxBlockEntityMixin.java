package net.multyfora.compat.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.multyfora.Createenvelopecompat;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MailboxBlockEntity.class)
public class MailboxBlockEntityMixin {

    @Inject(method = "isSendable", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsSendable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PackageItem.isPackage(stack)) {
            boolean hasCreateAddr = stack.has(AllDataComponents.PACKAGE_ADDRESS)
                && !stack.getOrDefault(AllDataComponents.PACKAGE_ADDRESS, "").isEmpty();
            boolean hasEnvAddr = Mail.getRecipient(stack).isPresent();
            cir.setReturnValue(hasCreateAddr || hasEnvAddr);
        }
    }

    // A Create package placed into the send slot by hand bypasses MailboxItemHandler and the packager, so Envelope
    // (e.g. Mail Service recipes) would see it as empty. Packager/hopper paths already sync before calling setItem.
    @Inject(method = "setItem", at = @At("HEAD"), remap = false)
    private void onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != MailboxBlockEntity.SLOT_MAIL || !PackageItem.isPackage(stack)) return;
        Level level = ((MailboxBlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide()) return;
        PackageConversion.syncEnvelopeContents(stack);
        PackageConversion.syncAddresses(stack, level);
    }

    // Courier food never belongs in the outgoing mail slot. Envelope's canPlaceItem already says so, but in the pack
    // something still puts seeds there once the food slot is full (seen with chutes since Envelope 0.8). Instead of
    // trusting every caller, the mailbox itself moves food that lands in the mail slot to its food slot. Callers
    // have already taken the items from their source, so nothing is refused: what doesn't fit is dropped on top.
    @Unique
    private static boolean createEnvelope$foodRedirectLogged;

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetItemRedirectFood(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != MailboxBlockEntity.SLOT_MAIL || stack.isEmpty()) return;
        MailboxBlockEntity self = (MailboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (self.isSendable(stack)) return;

        int foodSlot;
        if (stack.is(Envelope.Tags.Items.PIGEON_FOOD)) foodSlot = MailboxBlockEntity.SLOT_PIGEON_FOOD;
        else if (stack.is(Envelope.Tags.Items.BAT_FOOD)) foodSlot = MailboxBlockEntity.SLOT_BAT_FOOD;
        else return;

        // Only the part that is new: a caller that grows a stack already in the mail slot (e.g. from an old save)
        // passes the whole new stack. Shrinking or emptying the slot goes through unchanged.
        ItemStack current = self.getItem(MailboxBlockEntity.SLOT_MAIL);
        int added = ItemStack.isSameItemSameComponents(current, stack) ? stack.getCount() - current.getCount() : stack.getCount();
        if (added <= 0) return;
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return; // not a plain insert

        ci.cancel();

        ItemStack rest = stack.copyWithCount(added);
        ItemStack food = self.getItem(foodSlot);
        if (food.isEmpty()) {
            self.setItem(foodSlot, rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
        } else if (ItemStack.isSameItemSameComponents(food, rest)) {
            int space = Math.max(0, food.getMaxStackSize() - food.getCount());
            int moved = Math.min(space, rest.getCount());
            food.grow(moved);
            rest.shrink(moved);
            self.setChanged();
        }

        if (!rest.isEmpty()) {
            BlockPos pos = self.getBlockPos();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rest);
        }

        if (!createEnvelope$foodRedirectLogged) {
            createEnvelope$foodRedirectLogged = true;
            Createenvelopecompat.LOGGER.warn("Moved {} from the outgoing mail slot of the mailbox at {} to its food slot. "
                    + "Something inserted it without checking the slot (logged once):", stack, self.getBlockPos(), new Throwable("caller"));
        }
    }

    @Inject(method = "removeItem", at = @At("RETURN"), cancellable = true, remap = false)
    private void onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < MailboxBlockEntity.SLOT_INBOX) return;
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        if (PackageConversion.isEnvelopePackage(result))
            cir.setReturnValue(PackageConversion.toCreatePackage(result));
    }


}
