package net.multyfora.compat.mixin;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.neoforge.event.NeoForgeCommonEvents;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Since 0.8.0 Envelope registers its own item handler (a plain SidedInvWrapper) for the mailbox. NeoForge uses the
 * first registered provider that returns a handler, so which one wins would depend on mod load order. Skip only
 * Envelope's mailbox item handler registration, so {@link net.multyfora.compat.MailboxItemHandler} is always used.
 */
@Mixin(value = NeoForgeCommonEvents.class, remap = false)
public class EnvelopeCapabilityRegistrationMixin {

    @Redirect(
            method = "onRegisterCapabilities",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent;registerBlockEntity(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/neoforged/neoforge/capabilities/ICapabilityProvider;)V"),
            require = 0
    )
    private static <T, C, BE extends net.minecraft.world.level.block.entity.BlockEntity> void createEnvelope$skipMailboxItemHandler(
            RegisterCapabilitiesEvent event, BlockCapability<T, C> capability, BlockEntityType<BE> type, ICapabilityProvider<? super BE, C, T> provider) {
        if (capability == Capabilities.ItemHandler.BLOCK && type == Envelope.BlockEntityTypes.MAILBOX.get())
            return;
        event.registerBlockEntity(capability, type, provider);
    }
}
