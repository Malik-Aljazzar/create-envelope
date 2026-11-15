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
package net.multyfora.compat;

import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.multyfora.Createenvelopecompat;
import net.multyfora.compat.mixin.ChunkMapAccessor;
import net.multyfora.compat.mixin.MailboxBlockEntityAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Finds the inbox of a registered mailbox for a delivery where Envelope's own lookup gives up.
 * <p>
 * Envelope keeps a mailbox's inbox in the block entity for as long as the block entity exists: it takes the inbox
 * out of its {@code InboxStorage} when the block entity is created and puts it back only when the block entity is
 * removed (chunk unload). Its delivery lookup, however, only accepts mailboxes for which {@code Level#isLoaded} is
 * true - and that turns false as soon as a chunk drops below FULL, while vanilla keeps such a chunk, block entities
 * included, in memory until it leaves the loading range entirely (a ring of chunks around every player and every
 * force-loaded chunk). In that window the inbox is neither reachable nor stored, and Envelope returns the mail to the
 * sender as "Recipient Not Found".
 * <p>
 * Two steps, used only after Envelope found nothing:
 * <ol>
 *   <li>{@link #findInMemory}: the mailbox block entity that still exists in a chunk below FULL (or one that is
 *       about to be unloaded). Nothing is loaded; the mail goes into the live inbox, which Envelope stores as usual
 *       when the chunk unloads.</li>
 *   <li>{@link #loadForDelivery}: there is no block entity in memory and no stored inbox either - the stored inbox
 *       was lost (Envelope writes it back only on a clean unload, so a crash loses it). The chunk is loaded once so
 *       the delivery reaches the mailbox; when the chunk unloads, Envelope stores the inbox again. Per address at
 *       most one attempt per {@link #LOAD_COOLDOWN_TICKS}, so an address registered at a position without a mailbox
 *       does not load its chunk on every delivery.</li>
 * </ol>
 * Server thread only: both steps return nothing on any other thread.
 */
public final class MailboxLookup {
    public static final int LOAD_COOLDOWN_TICKS = 1200;

    private static final Map<ServerLevel, Map<BlockAddress, Long>> LOAD_ATTEMPTS = new WeakHashMap<>();

    private MailboxLookup() {
    }

    /** The live mailbox block entity at {@code pos}, if its chunk is still in memory although it is no longer FULL. */
    public static Optional<MailboxBlockEntity> findInMemory(ServerLevel level, BlockPos pos, BlockAddress address) {
        if (!level.getServer().isSameThread())
            return Optional.empty();

        long chunkKey = ChunkPos.asLong(pos);
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
        if (holder == null)
            holder = ((ChunkMapAccessor) chunkMap).createEnvelope$getPendingUnloads().get(chunkKey);
        if (holder == null)
            return Optional.empty();

        // Only a chunk that has been FULL has block entities; a chunk that was only read at a lower status has none,
        // and then the mailbox's inbox is in InboxStorage, where Envelope finds it itself.
        if (!(holder.getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk chunk))
            return Optional.empty();

        BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
        if (blockEntity instanceof MailboxBlockEntity mailbox && isLiveMailboxOf(mailbox, level, address)) {
            Createenvelopecompat.LOGGER.debug("Delivering to mailbox '{}'@[{}], whose chunk is in memory but not accessible.",
                address.getString(), pos.toShortString());
            return Optional.of(mailbox);
        }
        return Optional.empty();
    }

    /**
     * Loads the chunk at {@code pos} and returns the mailbox there. Only for a registered mailbox that has neither a
     * block entity in memory nor a stored inbox.
     */
    public static Optional<MailboxBlockEntity> loadForDelivery(ServerLevel level, BlockAddress address, BlockPos pos) {
        if (!level.getServer().isSameThread())
            return Optional.empty();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
            return Optional.empty();
        if (isOnLoadCooldown(level, address))
            return Optional.empty();
        LOAD_ATTEMPTS.computeIfAbsent(level, l -> new HashMap<>()).put(address, level.getGameTime());

        // Synchronous load up to FULL. The temporary ticket expires on the next tick; if nothing else keeps the chunk
        // loaded it unloads again, and Envelope stores the mailbox's inbox - with the mail delivered now.
        level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));

        if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox && isLiveMailboxOf(mailbox, level, address)) {
            Createenvelopecompat.LOGGER.warn("Mailbox '{}'@[{}] had neither a loaded block entity nor a stored inbox - its stored inbox "
                    + "was lost (e.g. by a server crash; Envelope only stores it when the chunk unloads cleanly). Loaded its chunk so the "
                    + "mail can be delivered; the inbox is stored again when the chunk unloads.",
                address.getString(), pos.toShortString());
            return Optional.of(mailbox);
        }

        Createenvelopecompat.LOGGER.warn("Mailbox address '{}' is registered at [{}], but there is no mailbox with that address. "
                + "Mail to it is returned to the sender.",
            address.getString(), pos.toShortString());
        return Optional.empty();
    }

    /** Whether {@link #loadForDelivery} already tried this address within the last {@link #LOAD_COOLDOWN_TICKS}. */
    public static boolean isOnLoadCooldown(ServerLevel level, BlockAddress address) {
        Map<BlockAddress, Long> attempts = LOAD_ATTEMPTS.get(level);
        Long last = attempts == null ? null : attempts.get(address);
        if (last == null)
            return false;
        long now = level.getGameTime();
        return now >= last && now - last < LOAD_COOLDOWN_TICKS;
    }

    private static boolean isLiveMailboxOf(MailboxBlockEntity mailbox, ServerLevel level, BlockAddress address) {
        if (mailbox.isRemoved() || mailbox.getLevel() != level)
            return false;
        BlockAddress mailboxAddress = ((MailboxBlockEntityAccessor) mailbox).createEnvelope$getAddress();
        return address.equals(mailboxAddress);
    }
}
