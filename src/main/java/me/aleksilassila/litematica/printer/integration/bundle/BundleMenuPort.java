package me.aleksilassila.litematica.printer.integration.bundle;

import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Client menu boundary for bundle extraction.
 *
 * <p>The extraction protocol is pure vanilla container clicking (select a bundle entry, then
 * right-click the bundle slot with an empty cursor, then left-click an empty inventory slot),
 * so the engine drives it through this port; tests replace the live inventory menu with a
 * simulator instead of a headless client.</p>
 */
public interface BundleMenuPort {
    /** True while the bare inventory menu is active and nothing else owns clicks. */
    boolean isInventoryMenuIdle();

    /** Player-inventory container slot ({@code 0..inventorySize()-1}) -> current stack. */
    ItemStack inventoryStack(int inventorySlot);

    int inventorySize();

    /** Menu slot index carrying the given player-inventory container slot, or -1 when absent. */
    int menuSlotOf(int inventorySlot);

    boolean carriedEmpty();

    /**
     * First empty menu slot backed by the player inventory, main slots first and hotbar last,
     * mirroring the slot preference of the bundle panel automation.
     */
    int findEmptyInventoryMenuSlot(Set<Integer> excludeMenuSlots);

    /** Normal client click path so prediction and packet state stay vanilla-consistent. */
    void click(int menuSlot, int button);

    /** Vanilla bundle selection: one ServerboundSelectBundleItemPacket plus the local toggle. */
    void sendSelectBundleItem(int menuSlot, int itemIndex);

    /** Creative players never need bundle extraction. */
    boolean isCreativeLike();

    /** Ticks automated clicks are spaced by (shorter in singleplayer, longer on servers). */
    int stepDelayTicks();
}