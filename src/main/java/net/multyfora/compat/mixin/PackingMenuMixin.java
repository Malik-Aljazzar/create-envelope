/*
 * Create: Envelope Compat - bridges Envelope and Create.
 * Copyright (C) multyfora (original work)
 * Copyright (C) 2026 ManOfKott (modifications - see git history)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.multyfora.compat.mixin;

import io.github.mortuusars.envelope.world.inventory.PackingMenu;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackingMenu.class)
public class PackingMenuMixin {

    /** Player inventory right before packing (server side only), to find what packing changed. */
    @Unique
    private ItemStack[] createEnvelope$inventoryBeforePack;

    @Inject(method = "createPackingResult", at = @At("RETURN"), cancellable = true, remap = false)
    private void onCreatePackingResult(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();
        if (PackageConversion.isEnvelopePackage(original)) {
            cir.setReturnValue(PackageConversion.toCreatePackage(original));
        }
    }

    @Inject(method = "pack", at = @At("HEAD"), remap = false)
    private void createEnvelope$rememberInventory(CallbackInfo ci) {
        createEnvelope$inventoryBeforePack = null;
        if (!(((PackingMenu) (Object) this).getPlayer() instanceof ServerPlayer player))
            return;
        Inventory inventory = player.getInventory();
        ItemStack[] before = new ItemStack[inventory.getContainerSize()];
        for (int i = 0; i < before.length; i++)
            before[i] = inventory.getItem(i).copy();
        createEnvelope$inventoryBeforePack = before;
    }

    /**
     * The packing screen packs on the client as a prediction and closes right away, so the server's own update of
     * the packing menu reaches a client that has already left it and is dropped. Whatever the client predicted then
     * stays in its inventory - a package the server doesn't have, if the prediction differed (a client without this
     * mod, or an older version of it, predicts a different item). Sends the inventory slots packing changed directly
     * (container -2 = player inventory, applied whichever menu the client has open).
     */
    @Inject(method = "pack", at = @At("TAIL"), remap = false)
    private void createEnvelope$resyncInventory(CallbackInfo ci) {
        ItemStack[] before = createEnvelope$inventoryBeforePack;
        createEnvelope$inventoryBeforePack = null;
        if (before == null || !(((PackingMenu) (Object) this).getPlayer() instanceof ServerPlayer player))
            return;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Math.min(before.length, inventory.getContainerSize()); i++) {
            ItemStack now = inventory.getItem(i);
            if (!ItemStack.matches(before[i], now))
                player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, i, now.copy()));
        }
    }
}
