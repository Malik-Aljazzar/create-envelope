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
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The mailbox's address without {@code getAddress()}'s null check (which throws while no address is set). */
@Mixin(value = MailboxBlockEntity.class, remap = false)
public interface MailboxBlockEntityAccessor {
    @Accessor("address")
    BlockAddress createEnvelope$getAddress();
}
