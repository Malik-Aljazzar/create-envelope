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

import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.block.mailbox.Mailboxes;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.multyfora.compat.MailboxLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Envelope only finds a mailbox block entity in a FULL chunk. A chunk that dropped below FULL still holds the
 * block entity - and with it the inbox, which Envelope stores only once the block entity is removed - so deliveries
 * to it came back as "Recipient Not Found". See {@link MailboxLookup}.
 */
@Mixin(value = Mailboxes.class, remap = false)
public abstract class MailboxesMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    public abstract Optional<BlockPos> getPositionOf(BlockAddress address);

    @Inject(method = "getBlockEntityOf", at = @At("RETURN"), cancellable = true)
    private void createEnvelope$findInMemory(BlockAddress address, CallbackInfoReturnable<Optional<MailboxBlockEntity>> cir) {
        if (cir.getReturnValue().isPresent())
            return;
        getPositionOf(address)
            .filter(pos -> !level.isLoaded(pos))
            .flatMap(pos -> MailboxLookup.findInMemory(level, pos, address))
            .ifPresent(mailbox -> cir.setReturnValue(Optional.of(mailbox)));
    }
}
