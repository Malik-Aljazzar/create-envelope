package net.multyfora.compat.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "removeItem", at = @At("RETURN"), cancellable = true, remap = false)
    private void onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < MailboxBlockEntity.SLOT_INBOX) return;
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        if (PackageConversion.isEnvelopePackage(result))
            cir.setReturnValue(PackageConversion.toCreatePackage(result));
    }


}
