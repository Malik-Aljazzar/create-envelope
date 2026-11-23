/*
 * Create: Envelope Compat - bridges Envelope and Create.
 * Copyright (C) 2026 ManOfKott
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

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.crafting.AddressTagApplicationRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Addressing a Create package by crafting it with an Address Tag set only Envelope's recipient - Create kept showing
 * and routing by the old (or no) address. The crafting result's Create address now follows the recipient, the same
 * as when the tag is right-clicked onto the package.
 */
@Mixin(AddressTagApplicationRecipe.class)
public class AddressTagApplicationRecipeMixin {

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), remap = false)
    private void createEnvelope$followRecipient(CraftingInput input, HolderLookup.Provider registries,
                                                CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!PackageConversion.isCreatePackage(result))
            return;
        for (int i = 0; i < input.size(); i++) {
            ItemStack ingredient = input.getItem(i);
            if (ingredient.is(Envelope.Tags.Items.MAILABLE)) {
                PackageConversion.followRecipient(ingredient, result);
                return;
            }
        }
    }
}
