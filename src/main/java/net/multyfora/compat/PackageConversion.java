package net.multyfora.compat;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.CustomAddress;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import io.github.mortuusars.envelope.world.mail.service.ServiceAddressDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Locale;

public class PackageConversion {

    public static boolean isEnvelopePackage(ItemStack stack) {
        return stack.has(Envelope.DataComponents.PACKAGE_CONTENTS);
    }

    public static boolean isCreatePackage(ItemStack stack) {
        return PackageItem.isPackage(stack);
    }

    public static boolean isAnyPackage(ItemStack stack) {
        return isEnvelopePackage(stack) || isCreatePackage(stack);
    }

    public static ItemStack toCreatePackage(ItemStack envelopePackage) {
        if (isCreatePackage(envelopePackage))
            return envelopePackage;
        if (!isEnvelopePackage(envelopePackage) && !envelopePackage.has(DataComponents.CONTAINER_LOOT))
            return envelopePackage;

        ItemStack workingStack = envelopePackage.copy();

        if (workingStack.has(DataComponents.CONTAINER_LOOT))
            resolveLootTable(workingStack);

        PackageContents contents = workingStack.get(Envelope.DataComponents.PACKAGE_CONTENTS);
        List<ItemStack> items = contents != null ? contents.copyItems() : List.of();

        ItemStack createPackage = PackageItem.containing(items);

        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_RECIPIENT);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_SENDER);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_ID);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_DELIVERY_LOG);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_PAYBACK_REQUEST);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.PACKAGE_CONTENTS);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.PACKAGE_EXPERIENCE);

        if (workingStack.has(Envelope.DataComponents.MAIL_RECIPIENT)) {
            var address = workingStack.get(Envelope.DataComponents.MAIL_RECIPIENT);
            PackageItem.addAddress(createPackage, address.getString());
        }

        return createPackage;
    }

    public static void syncAddresses(ItemStack stack, Level level) {
        if (!PackageItem.isPackage(stack)) return;

        boolean hasCreateAddr = stack.has(AllDataComponents.PACKAGE_ADDRESS)
            && !stack.getOrDefault(AllDataComponents.PACKAGE_ADDRESS, "").isEmpty();
        boolean hasEnvAddr = stack.has(Envelope.DataComponents.MAIL_RECIPIENT);

        if (hasCreateAddr && !hasEnvAddr) {
            String addr = stack.get(AllDataComponents.PACKAGE_ADDRESS);
            Address resolved = resolveAddress(addr, level);
            stack.set(Envelope.DataComponents.MAIL_RECIPIENT, resolved);
        } else if (hasEnvAddr && !hasCreateAddr) {
            var addr = stack.get(Envelope.DataComponents.MAIL_RECIPIENT);
            PackageItem.addAddress(stack, addr.getString());
        }
    }

    private static Address resolveAddress(String addr, Level level) {
        String[] parts = addr.split(",");
        if (parts.length == 3 && level != null) {
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox
                    && mailbox.getAddress() != null)
                    return mailbox.getAddress();
            } catch (NumberFormatException ignored) {}
        }

        if (level instanceof ServerLevel serverLevel) {
            // Fix (2026-09-15): a plain name (e.g. printed by a third-party
            // "address printer" block instead of an Envelope Address Tag)
            // used to always fall through to the CustomAddress below, which
            // Envelope has no delivery handler for at all - mail addressed
            // this way could never arrive, no matter how correctly the
            // target mailbox was set up. Look the name up against
            // Envelope's own known-address registry first, so it resolves
            // to the real, deliverable address (Block/Player/Service) if
            // one with that name is already registered.
            var byName = MailService.of(serverLevel).getKnownAddresses().byName(addr);
            if (byName.isPresent())
                return byName.get();

            ServiceAddress service = resolveService(serverLevel, addr);
            if (service != null) return service;
        }

        return new CustomAddress(Component.literal(addr));
    }

    private static ServiceAddress resolveService(ServerLevel level, String name) {
        RegistryAccess registries = level.registryAccess();
        String normalised = name.toLowerCase(Locale.ROOT).replace(" ", "_");

        ResourceLocation loc = Envelope.resource(normalised);
        ResourceKey<ServiceAddressDefinition> key =
            ResourceKey.create(Envelope.Registries.SERVICE_ADDRESS_DEFINITION, loc);
        var result = ServiceAddress.get(registries, key);
        if (result.isPresent())
            return result.get();

        var registry = registries.registryOrThrow(Envelope.Registries.SERVICE_ADDRESS_DEFINITION);
        for (var entry : registry.entrySet()) {
            ResourceLocation entryId = entry.getKey().location();
            if (entryId.getPath().equals(normalised))
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
        }

        for (var entry : registry.entrySet()) {
            String defName = entry.getValue().name().getString();
            if (defName.equalsIgnoreCase(name))
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
            String defKey = defName.toLowerCase(Locale.ROOT).replace(" ", "_");
            if (defKey.equals(normalised))
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
        }

        return null;
    }

    private static void resolveLootTable(ItemStack stack) {
        SeededContainerLoot containerLoot = stack.get(DataComponents.CONTAINER_LOOT);
        if (containerLoot == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel serverLevel = server.overworld();
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(containerLoot.lootTable());
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
            .create(LootContextParamSets.CHEST);
        SimpleContainer tempContainer = new SimpleContainer(6);
        lootTable.fill(tempContainer, params, containerLoot.seed());
        stack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(tempContainer));
        stack.remove(DataComponents.CONTAINER_LOOT);
    }

    private static <T> void copyEnvelopeComponent(ItemStack from, ItemStack to,
                                                   net.minecraft.core.component.DataComponentType<T> component) {
        if (from.has(component))
            to.set(component, from.get(component));
    }
}
