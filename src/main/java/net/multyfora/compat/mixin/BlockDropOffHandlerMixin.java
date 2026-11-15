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

import io.github.mortuusars.envelope.world.block.mailbox.Inbox;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.dropoff.BlockDropOffHandler;
import net.multyfora.compat.MailboxLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Last resort for a registered mailbox whose inbox is nowhere - no block entity in memory, nothing in Envelope's
 * InboxStorage (lost, e.g. by a crash). The chunk is loaded so the mail arrives instead of being returned as
 * "Recipient Not Found". See {@link MailboxLookup}.
 */
@Mixin(value = BlockDropOffHandler.class, remap = false)
public class BlockDropOffHandlerMixin {
    @Inject(method = "getInboxByAddress", at = @At("RETURN"), cancellable = true)
    private static void createEnvelope$loadLostInbox(MailService service, BlockAddress address, CallbackInfoReturnable<Optional<Inbox>> cir) {
        if (cir.getReturnValue().isPresent())
            return;
        service.getMailboxes().getPositionOf(address)
            .flatMap(pos -> MailboxLookup.loadForDelivery(service.getLevel(), address, pos))
            .ifPresent(mailbox -> cir.setReturnValue(Optional.<Inbox>of(mailbox)));
    }
}
