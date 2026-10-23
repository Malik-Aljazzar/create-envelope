package net.multyfora.compat.mixin;

import io.github.mortuusars.envelope.world.inventory.MailboxMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MailboxMenu.class)
public class MailboxMenuMixin {

    @Inject(method = "extractMail", at = @At("RETURN"), cancellable = true, remap = false)
    private void onExtractMail(ServerLevel level, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        if (PackageConversion.isEnvelopePackage(result))
            cir.setReturnValue(PackageConversion.toCreatePackage(result));
    }
}
