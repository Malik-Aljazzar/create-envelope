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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.mortuusars.envelope.world.item.AddressTagItem;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Right-clicking an Address Tag onto mail is Envelope's own logic (Create packages are in its {@code mailable} tag
 * since 0.8, incl. removing the recipient with a blank tag and not using up tags in creative). This only makes the
 * Create address of a Create package follow the new recipient before the result goes into the slot.
 */
@Mixin(AddressTagItem.class)
public class AddressTagItemMixin {

    @WrapOperation(method = "overrideStackedOnOther",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"),
            remap = false)
    private void createEnvelope$followRecipient(Slot slot, ItemStack result, Operation<Void> original) {
        PackageConversion.followRecipient(slot.getItem(), result);
        original.call(slot, result);
    }
}
