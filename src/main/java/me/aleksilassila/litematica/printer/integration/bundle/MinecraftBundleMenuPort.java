package me.aleksilassila.litematica.printer.integration.bundle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Live inventory-menu implementation. Extraction never needs an open screen: the bare
 * inventory menu stays active while the printer works, the same surface the printer's own
 * hotbar swaps already click into.
 */
public final class MinecraftBundleMenuPort implements BundleMenuPort {
    private final Minecraft client;

    public MinecraftBundleMenuPort(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean isInventoryMenuIdle() {
        LocalPlayer player = this.client.player;
        //#if MC > 260100
        //$$ boolean noScreen = this.client.gui.screen() == null;
        //#else
        boolean noScreen = this.client.screen == null;
        //#endif
        return player != null
                && noScreen
                && this.client.gameMode != null
                && player.containerMenu == player.inventoryMenu;
    }

    @Override
    public ItemStack inventoryStack(int inventorySlot) {
        LocalPlayer player = this.client.player;
        if (player == null || inventorySlot < 0 || inventorySlot >= this.inventorySize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(inventorySlot);
    }

    @Override
    public int inventorySize() {
        LocalPlayer player = this.client.player;
        return player == null ? 0 : player.getInventory().getContainerSize();
    }

    @Override
    public int menuSlotOf(int inventorySlot) {
        LocalPlayer player = this.client.player;
        if (player == null) {
            return -1;
        }
        for (var slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                return slot.index;
            }
        }
        return -1;
    }

    @Override
    public boolean carriedEmpty() {
        LocalPlayer player = this.client.player;
        return player == null || player.containerMenu.getCarried().isEmpty();
    }

    @Override
    public int findEmptyInventoryMenuSlot(Set<Integer> excludeMenuSlots) {
        LocalPlayer player = this.client.player;
        if (player == null) {
            return -1;
        }
        // Main inventory first, hotbar last: the same order the bundle panel automation uses.
        for (int pass = 0; pass < 2; pass++) {
            int min = pass == 0 ? 9 : 0;
            int max = pass == 0 ? 36 : 9;
            for (var slot : player.containerMenu.slots) {
                if (excludeMenuSlots != null && excludeMenuSlots.contains(slot.index)) {
                    continue;
                }
                if (slot.container != player.getInventory() || slot.hasItem()) {
                    continue;
                }
                int containerSlot = slot.getContainerSlot();
                if (containerSlot >= min && containerSlot < max) {
                    return slot.index;
                }
            }
        }
        return -1;
    }

    @Override
    public void click(int menuSlot, int button) {
        LocalPlayer player = this.client.player;
        if (player == null || this.client.gameMode == null) {
            return;
        }
        this.client.gameMode.handleContainerInput(
                player.containerMenu.containerId, menuSlot, button, ContainerInput.PICKUP, player);
    }

    @Override
    public void sendSelectBundleItem(int menuSlot, int itemIndex) {
        LocalPlayer player = this.client.player;
        if (player == null) {
            return;
        }
        ItemStack bundle = player.containerMenu.getSlot(menuSlot).getItem();
        // The selection index is not part of BundleContents.equals(), so it is never synced
        // back; mirror vanilla prediction locally and tell the server with one packet.
        if (!bundle.isEmpty() && BundleItem.getSelectedItemIndex(bundle) != itemIndex) {
            BundleItem.toggleSelectedItem(bundle, itemIndex);
        }
        ClientPacketListener connection = this.client.getConnection();
        if (connection != null) {
            connection.send(new ServerboundSelectBundleItemPacket(menuSlot, itemIndex));
        }
    }

    @Override
    public boolean isCreativeLike() {
        LocalPlayer player = this.client.player;
        if (player == null) {
            return true;
        }
        return player.getAbilities().instabuild;
    }

    @Override
    public int stepDelayTicks() {
        return this.client.hasSingleplayerServer() ? 1 : 2;
    }
}