# Changelog

All notable changes to Create: Envelope Compat (Zeta Fix) are documented in this file.

## [1.1.3]

### Fixes
- Packing a Paper Box no longer leaves a "ghost" package in your inventory ([#2](https://github.com/Malik-Aljazzar/create-envelope/issues/2)). The packing screen packs on your side right away and lets the server do the same. This mod picked the Create box at random, so you and the server ended up with different packages: you saw one the server didn't have, and tags or crafting with it seemed to work for a moment and then didn't. The box now depends only on the contents, so both sides make the same package. The server also sends the packed package straight to your inventory, so it is right even for players on an older version of this mod.
- Crafting a package with an Address Tag now also sets its Create address. Before, only Envelope's address was set, so Create still treated the package as having no address (or its old one).
- Right-clicking an Address Tag onto a Create package now follows Envelope's own rules: a blank Address Tag removes the address (before, it did nothing), and no tag is used up in creative mode. An address that Create itself gave the package is kept when the Envelope address is removed.
- Seal Stamps work on packed packages again. Packing turns a Paper Box into a Create package, and Envelope can't seal those, so the stamp did nothing. Such a package is now sealed into Envelope's Sealed Package, with its contents, address and Payback Tag. A Create package that can't become an Envelope package without losing something (part of a Create order, more than 6 stacks, or an address only Create has) stays as it is.

Update the server for all of this. Clients should update too, so that what you see right away already matches the server.

## [1.1.2]

### Fixes
- Mail to a mailbox that was loaded a short while ago is now delivered instead of coming back as "Recipient Not Found". This hit mailboxes a player had just left (or one that sits a few chunks away from a force-loaded area): when a chunk drops out of the fully loaded area, Minecraft keeps it - mailbox included - in memory for a while, but Envelope only looked for mailboxes in fully loaded chunks, and it writes a mailbox's inbox to its storage only once the chunk is unloaded completely. In between, the inbox could not be found anywhere. Deliveries now reach the mailbox in that state too. Affects Envelope 0.8.0 to 0.8.2.
- A mailbox whose stored inbox went missing no longer refuses all mail until someone visits it. Envelope keeps the inbox of a loaded mailbox only in memory, so a server crash loses it (the mail that was in it cannot be restored by this mod). Its chunk is now loaded once for the delivery, and the inbox is stored again when the chunk unloads. This is logged as a warning, as is an address that is registered without a mailbox at its position (such an address is tried at most once a minute).

This is a server-side fix; players don't need to update.

## [1.1.1]

### Fixes
- Seeds fed into a mailbox by chutes (or other logistics blocks) no longer end up in the outgoing mail slot once the pigeon food slot is full. The same applies to Spider Eyes and the bat food slot. Food that lands in the mail slot is now moved to its food slot; whatever doesn't fit there is dropped on top of the mailbox instead of blocking outgoing mail. Seeds already sitting in the mail slot from before can simply be taken out.
- Logistics blocks can no longer merge a different item into a mailbox slot (e.g. beetroot seeds into a stack of wheat seeds, which turned them into wheat seeds).

## [1.1.0]

**Requires Envelope 0.8.x** (the "Echoes In The Dark" update with the courier bats). Envelope 0.7.x is no longer supported - stay on 1.0.3 with Envelope 0.7.x.

### Changes
- Updated to Envelope 0.8: the mailbox's new slot layout (outgoing mail, pigeon food, **bat food**, inbox) is supported, including for hoppers, Create funnels/chutes and other logistics blocks - they can now also fill the bat food slot (Spider Eyes).
- Envelope 0.8 now has its own item handler for the mailbox. This mod's handler is always the one used, so Create packages put in by logistics blocks are still recognized as mail and received mail still comes out as Create packages - no matter in which order the mods load.
- Removed the JEI crash workaround from 1.0.2 (and the bundled MixinSquared): Envelope 0.8 fixed its JEI integration itself.

### Fixes
- Sealed Packages and Payback Packages taken out of a mailbox stay what they are. Before, they were turned into plain Create packages, which removed the seal (including soulbound seals) and broke the payback flow.
- Removed this mod's attempt to add Create packages to Envelope's `mailable` tag - it never loaded on 1.21.1 (wrong folder name). Envelope 0.8 now includes Create packages in that tag itself, so attaching a Payback Tag or Address Tag to a Create package works.

### Upgrading from Envelope 0.7.x
- Envelope 0.8 replaced several item data fields without migrating them. Items are kept, but on old items the replaced data is dropped when the world loads (a "Tried to load invalid item" error per item in the log). For Create packages this only loses the Envelope copy of the address - the Create address and the contents are kept, and this mod restores the Envelope address from the Create address when the package is sent. A Payback Tag already attached to a package that was not sent yet is lost.

## [1.0.3]

### Fixes
- Create packages put into a mailbox by hand now work with the Mail Service (e.g. ordering a Payback Tag with an Address Tag + red dye, or canceling payback requests). Before, the Mail Service saw such packages as empty and could not process them - only packages sent through a Create Packager or a hopper/funnel worked.

## [1.0.2]

### Fixes
- Fixed a crash when loading a world with Just Enough Items 19.52.0.421 or newer (`Critical injection failure: Callback method onTransferRecipe ... jei.BasicRecipeTransferHandlerMixin from mod envelope`). Envelope 0.7.5's own JEI hook no longer matches newer JEI versions; this mod now switches it off and brings its own replacement, so JEI can be kept up to date. Moving a mail recipe from JEI into the packing table still fills in the recipe's address - with old and new JEI versions alike. Should JEI change again, only that address shortcut stops working; the game no longer crashes.
- Fixed a second crash with newer Just Enough Items, which hit as soon as JEI handled its first key press or click in a world (`Mixin apply for mod envelope failed envelope-common.mixins.json:jei.JeiKeyConflictContextMixin`). Usually the game then froze instead of showing a crash screen. Replaced the same way: JEI's keys (show recipe, toggle overlay, ...) are still ignored while you type in an Envelope screen.
- Bundles MixinSquared (needed to switch off Envelope's hook). Nothing to install separately.

## [1.0.1]

### Fixes
- Removed leftover debug logging that flooded server and client logs with `Saw checking block: ...` for every block any Create saw looked at - including saws that have nothing to do with packages ([#1](https://github.com/ManOfKott/create-envelope/issues/1)).

## [1.0.0]

First release of this fork of [Create: Envelope compat](https://github.com/multyfora/createenvelope-1.21.1) by multyfora.

### Fixes
- Packages addressed by a plain name (instead of an Address Tag) can be delivered again: the name is now looked up among Envelope's known addresses. Previously they became an undeliverable custom address and never arrived.
- Create packages can be mailed with Envelope (added to Envelope's `mailable` item tag).
- Updated for Envelope 0.7.x (requires Envelope 0.7.0 up to 0.8.0).
