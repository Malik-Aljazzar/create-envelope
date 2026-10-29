package net.multyfora;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.mortuusars.envelope.Envelope;
import net.multyfora.compat.MailboxItemHandler;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(Createenvelopecompat.MODID)
public class Createenvelopecompat {
    public static final String MODID = "create_envelope";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Createenvelopecompat(IEventBus modEventBus, ModContainer modContainer) {
        // HIGHEST: NeoForge uses the first provider that returns a handler, and RegisterCapabilitiesEvent runs all
        // mods' HIGHEST listeners first - so another mod's generic container handler can't take over the mailbox.
        modEventBus.addListener(EventPriority.HIGHEST, this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            Envelope.BlockEntityTypes.MAILBOX.get(),
            (be, side) -> new MailboxItemHandler(be)
        );
    }
}
