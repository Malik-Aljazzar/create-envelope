package net.multyfora.compat.mixin;

import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.AddressTagItem;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(AddressTagItem.class)
public class AddressTagItemMixin {

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true, remap = false)
    private void onOverrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action,
                                          Player player, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player))
            return;

        ItemStack target = slot.getItem();
        if (!PackageItem.isPackage(target))
            return;

        var tagAddress = stack.get(Envelope.DataComponents.ADDRESS_TAG_ADDRESS);
        var existingRecipient = Mail.getRecipient(target).orElse(null);

        if (Objects.equals(tagAddress, existingRecipient)) {
            cir.setReturnValue(true);
            return;
        }

        if (tagAddress == null) {
            if (stack.getCount() >= target.getCount())
                player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
            cir.setReturnValue(true);
            return;
        }

        ItemStack copy = target.copy();
        Mail.setRecipient(copy, tagAddress);
        Mail.removePreviousDeliveryData(copy);

        if (slot.mayPlace(copy)) {
            slot.setByPlayer(copy);
            PackageItem.addAddress(copy, tagAddress.getString());
        } else {
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
            cir.setReturnValue(true);
            return;
        }

        stack.shrink(target.getCount());
        player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f);
        cir.setReturnValue(true);
    }

    @Inject(method = "overrideStackedOnOther", at = @At("RETURN"), remap = false)
    private void onPostOverrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action,
                                              Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        ItemStack target = slot.getItem();
        if (!PackageItem.isPackage(target)) return;
        Mail.getRecipient(target).ifPresent(addr -> PackageItem.addAddress(target, addr.getString()));
    }
}
