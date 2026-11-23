package net.multyfora.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.inventory.PackingMenu;
import io.github.mortuusars.envelope.world.inventory.StackIngredient;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.CustomAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.multyfora.Createenvelopecompat;
import net.multyfora.compat.PackageConversion;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Envelope's tools on a package packed from a Paper Box (which this mod turns into a Create package): the packing
 * result must be the same item every time (the client predicts it), Address Tags must set the Create address too -
 * right-clicked or crafted - and Seal Stamps must still seal it.
 */
@GameTestHolder(Createenvelopecompat.MODID)
@PrefixGameTestTemplate(false)
public class PackageTagTests {
    /** Main inventory slot used for clicks; the same index in the player's {@link InventoryMenu} and Inventory. */
    private static final int SLOT = 9;

    /** A fake player whose outgoing packets are recorded. */
    private static class RecordingPlayer {
        final FakePlayer player;
        final List<Packet<?>> sent = new ArrayList<>();

        RecordingPlayer(GameTestHelper helper, String name) {
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            // never registered with the server or ticked - only collects what the server sends to this player
            new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.CLIENTBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    sent.add(packet);
                }

                @Override
                public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
                    sent.add(packet);
                }
            };
        }

        InventoryMenu menu() {
            return player.inventoryMenu;
        }

        /** Right-clicks the carried stack onto the given slot of the player's inventory menu. */
        ItemStack rightClick(ItemStack carried, int slot) {
            menu().setCarried(carried);
            menu().clicked(slot, 1, ClickType.PICKUP, player);
            return menu().getSlot(slot).getItem();
        }
    }

    private static List<ItemStack> contents() {
        return List.of(new ItemStack(Items.STONE, 16), new ItemStack(Items.APPLE, 3));
    }

    private static ItemStack envelopePackage(@Nullable Address recipient) {
        ItemStack pkg = new ItemStack(Envelope.Items.PACKAGE.get());
        pkg.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(contents()));
        Mail.setRecipient(pkg, recipient);
        return pkg;
    }

    private static ItemStack addressTag(@Nullable Address address) {
        ItemStack tag = new ItemStack(Envelope.Items.ADDRESS_TAG.get(), 4);
        tag.set(Envelope.DataComponents.ADDRESS_TAG_ADDRESS, address);
        return tag;
    }

    private static Address address(String name) {
        return new CustomAddress(Component.literal(name));
    }

    private static String describe(ItemStack stack) {
        return stack + " " + stack.getComponentsPatch();
    }

    private static @Nullable String createAddress(ItemStack stack) {
        String address = stack.get(AllDataComponents.PACKAGE_ADDRESS);
        return address == null || address.isEmpty() ? null : address;
    }

    private static void assertCreatePackage(GameTestHelper helper, ItemStack stack, @Nullable Address recipient,
                                            @Nullable String createAddress, String when) {
        if (!PackageItem.isPackage(stack))
            helper.fail(when + ": expected a Create package, got " + describe(stack));
        Address actualRecipient = Mail.getRecipient(stack).orElse(null);
        if (!java.util.Objects.equals(recipient, actualRecipient))
            helper.fail(when + ": expected recipient " + recipient + ", got " + describe(stack));
        if (!java.util.Objects.equals(createAddress, createAddress(stack)))
            helper.fail(when + ": expected Create address " + createAddress + ", got " + describe(stack));
    }

    /** Packs {@link #contents()} through Envelope's packing menu, with an Address Tag preset like the screen does. */
    private static ItemStack pack(GameTestHelper helper, RecordingPlayer recording, @Nullable Address preset) {
        FakePlayer player = recording.player;
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Envelope.Items.PAPER_BOX.get(), 2));
        player.getInventory().setItem(20, addressTag(preset));
        PackingMenu menu = new PackingMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        List<ItemStack> items = contents();
        for (int i = 0; i < items.size(); i++)
            menu.getContainer().setItem(i, items.get(i).copy());
        menu.presetAddress(preset);
        recording.sent.clear();
        menu.clickMenuButton(player, PackingMenu.PACK_BUTTON_ID);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (PackageItem.isPackage(player.getInventory().getItem(i)))
                return player.getInventory().getItem(i);
        helper.fail("Packing gave no Create package, inventory: " + player.getInventory().items);
        return ItemStack.EMPTY;
    }

    @GameTest(template = "empty")
    public static void conversionGivesTheSamePackageEveryTime(GameTestHelper helper) {
        ItemStack first = PackageConversion.toCreatePackage(envelopePackage(address("Same")));
        for (int i = 0; i < 64; i++) {
            ItemStack again = PackageConversion.toCreatePackage(envelopePackage(address("Same")));
            if (!ItemStack.matches(first, again))
                helper.fail("Conversion " + i + " differs: " + describe(first) + " vs " + describe(again));
        }
        helper.succeed();
    }

    /** The client runs the packing as a prediction: its result has to be what the server makes. */
    @GameTest(template = "empty")
    public static void packingGivesTheSamePackageOnEverySide(GameTestHelper helper) {
        Address preset = address("Packed");
        ItemStack server = pack(helper, new RecordingPlayer(helper, "packServer"), preset).copy();
        assertCreatePackage(helper, server, preset, "Packed", "packed");
        for (int i = 0; i < 8; i++) {
            ItemStack prediction = pack(helper, new RecordingPlayer(helper, "packClient" + i), preset);
            if (!ItemStack.matches(server, prediction))
                helper.fail("Packing " + i + " differs: " + describe(server) + " vs " + describe(prediction));
        }
        helper.succeed();
    }

    /** The client has left the packing menu when the server's update for it arrives - the inventory is sent directly. */
    @GameTest(template = "empty")
    public static void packingSendsTheChangedInventorySlots(GameTestHelper helper) {
        RecordingPlayer recording = new RecordingPlayer(helper, "packResync");
        ItemStack packed = pack(helper, recording, null);
        int packageSlot = recording.player.getInventory().findSlotMatchingItem(packed);
        int handSlot = recording.player.getInventory().selected;
        List<Integer> sentSlots = new ArrayList<>();
        for (Packet<?> packet : recording.sent) {
            if (packet instanceof ClientboundContainerSetSlotPacket slotPacket && slotPacket.getContainerId() == -2) {
                sentSlots.add(slotPacket.getSlot());
                ItemStack serverStack = recording.player.getInventory().getItem(slotPacket.getSlot());
                if (!ItemStack.matches(serverStack, slotPacket.getItem()))
                    helper.fail("Slot " + slotPacket.getSlot() + " was sent as " + describe(slotPacket.getItem())
                            + " but holds " + describe(serverStack));
            }
        }
        if (!sentSlots.contains(packageSlot) || !sentSlots.contains(handSlot))
            helper.fail("Expected inventory slots " + packageSlot + " (package) and " + handSlot
                    + " (paper box) to be sent, got " + sentSlots);
        if (sentSlots.size() != 2)
            helper.fail("Only the changed slots should be sent, got " + sentSlots);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingAnAddressTagSetsTheCreateAddress(GameTestHelper helper) {
        ItemStack pkg = PackageConversion.toCreatePackage(envelopePackage(null));
        Address target = address("Crafted");
        CraftingInput input = CraftingInput.of(2, 1, List.of(pkg, addressTag(target).copyWithCount(1)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        if (recipe.isEmpty() || !recipe.get().id().equals(Envelope.resource("address_tag_application")))
            helper.fail("Expected Envelope's address tag recipe, got " + recipe.map(r -> r.id().toString()).orElse("none"));
        ItemStack result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
        assertCreatePackage(helper, result, target, "Crafted", "crafted");

        // a new address replaces the old one on both sides
        Address other = address("Crafted Again");
        CraftingInput again = CraftingInput.of(2, 1, List.of(result, addressTag(other).copyWithCount(1)));
        assertCreatePackage(helper, recipe.get().value().assemble(again, helper.getLevel().registryAccess()),
                other, "Crafted Again", "crafted again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickingAnAddressTagSetsTheCreateAddress(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "tagClick");
        player.menu().getSlot(SLOT).set(PackageConversion.toCreatePackage(envelopePackage(null)));
        Address target = address("Clicked");
        ItemStack result = player.rightClick(addressTag(target), SLOT);
        assertCreatePackage(helper, result, target, "Clicked", "address tag right-clicked");
        if (player.menu().getCarried().getCount() != 3)
            helper.fail("One Address Tag should be used up, carried: " + player.menu().getCarried());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBlankAddressTagRemovesBothAddresses(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "tagBlank");
        Address old = address("Old");
        player.menu().getSlot(SLOT).set(PackageConversion.toCreatePackage(envelopePackage(old)));
        assertCreatePackage(helper, player.menu().getSlot(SLOT).getItem(), old, "Old", "before");
        ItemStack result = player.rightClick(addressTag(null), SLOT);
        assertCreatePackage(helper, result, null, null, "blank tag right-clicked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBlankAddressTagKeepsAnAddressFromCreate(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "tagBlankCreate");
        Address old = address("Old");
        ItemStack pkg = PackageConversion.toCreatePackage(envelopePackage(old));
        PackageItem.addAddress(pkg, "Depot");
        player.menu().getSlot(SLOT).set(pkg);
        ItemStack result = player.rightClick(addressTag(null), SLOT);
        assertCreatePackage(helper, result, null, "Depot", "blank tag right-clicked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPaybackTagCanBeAttached(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "payback");
        player.menu().getSlot(SLOT).set(PackageConversion.toCreatePackage(envelopePackage(address("Pay"))));
        ItemStack payback = new ItemStack(Envelope.Items.PAYBACK_TAG.get(), 4);
        PaybackRequest request = new PaybackRequest(List.of(new StackIngredient(Items.DIAMOND, 2)));
        payback.set(Envelope.DataComponents.PAYBACK_TAG_CONTENTS, request);
        ItemStack result = player.rightClick(payback, SLOT);
        if (!request.equals(result.get(Envelope.DataComponents.MAIL_PAYBACK_TAG)))
            helper.fail("Payback Tag not attached: " + describe(result));
        assertCreatePackage(helper, result, address("Pay"), "Pay", "payback tag right-clicked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSealStampSealsAPackedPackage(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "sealer");
        Address recipient = address("Sealed");
        ItemStack packed = pack(helper, player, recipient).copy();
        player.player.getInventory().clearContent();
        player.menu().getSlot(SLOT).set(packed);
        ItemStack result = player.rightClick(new ItemStack(Envelope.Items.SEAL_STAMP.get()), SLOT);
        if (!result.is(Envelope.Items.SEALED_PACKAGE.get()) || !result.has(Envelope.DataComponents.SEAL))
            helper.fail("Expected a Sealed Package, got " + describe(result));
        if (!new PackageContents(contents()).equals(result.get(Envelope.DataComponents.PACKAGE_CONTENTS)))
            helper.fail("Contents changed: " + describe(result));
        if (!recipient.equals(Mail.getRecipient(result).orElse(null)))
            helper.fail("Recipient lost: " + describe(result));
        if (result.has(AllDataComponents.PACKAGE_CONTENTS) || result.has(AllDataComponents.PACKAGE_ADDRESS))
            helper.fail("Create data left on the Sealed Package: " + describe(result));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSealStampLeavesOtherCreatePackagesAlone(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper, "sealRefused");

        // addressed by Create only - sealing would drop the address
        ItemStack createAddressed = PackageItem.containing(contents());
        PackageItem.addAddress(createAddressed, "Depot");
        // more stacks than an Envelope package holds (different items - Create merges equal ones)
        List<ItemStack> seven = new ArrayList<>();
        for (var item : List.of(Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.APPLE, Items.BREAD))
            seven.add(new ItemStack(item, 5));
        ItemStack tooFull = PackageItem.containing(seven);

        for (ItemStack pkg : List.of(createAddressed, tooFull)) {
            ItemStack before = pkg.copy();
            player.menu().getSlot(SLOT).set(pkg);
            ItemStack result = player.rightClick(new ItemStack(Envelope.Items.SEAL_STAMP.get()), SLOT);
            if (!ItemStack.matches(before, result))
                helper.fail("Package should stay as it is: " + describe(before) + " -> " + describe(result));
        }
        helper.succeed();
    }

    /** A loot-table package can only be rolled on the server thread - elsewhere it must stay an Envelope package. */
    @GameTest(template = "empty")
    public static void aLootPackageIsOnlyConvertedOnTheServerThread(GameTestHelper helper) {
        ItemStack loot = new ItemStack(Envelope.Items.PACKAGE.get());
        loot.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(BuiltInLootTables.SIMPLE_DUNGEON, 7L));

        AtomicReference<ItemStack> offThread = new AtomicReference<>();
        Thread thread = new Thread(() -> offThread.set(PackageConversion.toCreatePackage(loot)), "create_envelope-test");
        thread.start();
        try {
            thread.join(10_000);
        } catch (InterruptedException e) {
            helper.fail("Interrupted");
        }
        if (offThread.get() == null || !ItemStack.matches(loot, offThread.get()))
            helper.fail("Off the server thread the loot package must stay unchanged, got "
                    + (offThread.get() == null ? "nothing" : describe(offThread.get())));

        ItemStack converted = PackageConversion.toCreatePackage(loot);
        if (!PackageItem.isPackage(converted) || converted.has(DataComponents.CONTAINER_LOOT))
            helper.fail("On the server thread it is rolled and converted, got " + describe(converted));
        helper.succeed();
    }
}
