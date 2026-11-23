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

import io.github.mortuusars.envelope.world.item.SealStampItem;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Packing a Paper Box gives a Create package (so it can go straight into Create logistics), but Envelope can only
 * seal its own packages - a Seal Stamp did nothing on it. Envelope's sealing now sees such a package as the Envelope
 * package it would be, and seals that (the result is Envelope's Sealed Package, as before this mod). Packages that
 * can't be one without losing something (part of a Create order, more than 6 stacks, a Create-only address) stay
 * as they are.
 */
@Mixin(SealStampItem.class)
public class SealStampItemMixin {

    // The local "target" (= slot.getItem()), stored after the right-click and slot permission checks. ItemStack
    // locals in order: stack (parameter), target, sealResult.
    @ModifyVariable(method = "overrideStackedOnOther", at = @At("STORE"), ordinal = 1, remap = false)
    private ItemStack createEnvelope$sealCreatePackage(ItemStack target) {
        ItemStack envelopePackage = PackageConversion.toSealableEnvelopePackage(target);
        return envelopePackage.isEmpty() ? target : envelopePackage;
    }
}
