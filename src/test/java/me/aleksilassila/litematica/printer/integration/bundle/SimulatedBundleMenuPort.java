package me.aleksilassila.litematica.printer.integration.bundle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless vanilla-bundle click simulator: right-clicking a bundle with an empty cursor takes
 * the selected entry (or the front entry when none), left-clicking an empty inventory slot
 * deposits the cursor, left-clicking a bundle with a cursor inserts at the front, and a
 * successful removal resets the selection on both sides.
 */
public final class SimulatedBundleMenuPort implements BundleMenuPort {
    public final List<String> clicks = new ArrayList<>();
    public final List<String> selectPackets = new ArrayList<>();
    public boolean idle = true;
    public boolean ignoreSelectionPackets;
    private final Inventory inventory;
    private final ItemStack[] slots;
    private ItemStack carried = ItemStack.EMPTY;
    private final java.util.Map<Integer, Integer> selection = new java.util.HashMap<>();

    public SimulatedBundleMenuPort(int size) {
        this.slots = new ItemStack[size];
        Arrays.fill(this.slots, ItemStack.EMPTY);
        this.inventory = null;
    }

    public SimulatedBundleMenuPort(Inventory inventory) {
        this.inventory = inventory;
        this.slots = null;
    }

    public static ItemStack bundle(ItemStack... entries) {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        BundleContents.Mutable mutable = new BundleContents.Mutable();
        // tryInsert puts each entry at the front, so insert reversed to keep the listed order.
        for (int index = entries.length - 1; index >= 0; index--) {
            mutable.tryInsert(entries[index].copy());
        }
        stack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        return stack;
    }

    public void set(int inventorySlot, ItemStack stack) {
        if (this.inventory != null) {
            this.inventory.setItem(inventorySlot, stack);
        } else {
            this.slots[inventorySlot] = stack;
        }
    }

    private ItemStack raw(int inventorySlot) {
        return this.inventory != null
                ? this.inventory.getItem(inventorySlot)
                : this.slots[inventorySlot];
    }

    private List<ItemStack> entriesOf(ItemStack bundleStack) {
        List<ItemStack> entries = new ArrayList<>();
        BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents != null) {
            contents.itemCopies().forEach(entries::add);
        }
        return entries;
    }

    private void writeEntries(int inventorySlot, ItemStack bundleStack, List<ItemStack> entries) {
        BundleContents.Mutable mutable = new BundleContents.Mutable();
        for (int index = entries.size() - 1; index >= 0; index--) {
            mutable.tryInsert(entries.get(index).copy());
        }
        bundleStack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        this.set(inventorySlot, bundleStack);
    }

    @Override public boolean isInventoryMenuIdle() { return this.idle; }

    @Override public ItemStack inventoryStack(int inventorySlot) { return this.raw(inventorySlot); }

    @Override public int inventorySize() {
        return this.inventory != null ? this.inventory.getContainerSize() : this.slots.length;
    }

    @Override public int menuSlotOf(int inventorySlot) { return inventorySlot; }

    @Override public boolean carriedEmpty() { return this.carried.isEmpty(); }

    @Override
    public int findEmptyInventoryMenuSlot(Set<Integer> excludeMenuSlots) {
        Set<Integer> excluded = excludeMenuSlots == null ? Set.of() : excludeMenuSlots;
        for (int pass = 0; pass < 2; pass++) {
            int min = pass == 0 ? 9 : 0;
            int max = pass == 0 ? 36 : 9;
            for (int slot = min; slot < max; slot++) {
                if (excluded.contains(slot)) continue;
                if (!this.raw(slot).isEmpty()) continue;
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void click(int menuSlot, int button) {
        this.clicks.add(button + ":" + menuSlot);
        ItemStack target = this.raw(menuSlot);
        if (BundleExtractionEngine.contents(target) != null) {
            if (button == 1 && this.carried.isEmpty()) {
                List<ItemStack> entries = this.entriesOf(target);
                if (entries.isEmpty()) return;
                int index = this.selection.getOrDefault(menuSlot, -1);
                index = (index >= 0 && index < entries.size()) ? index : 0;
                this.carried = entries.remove(index);
                this.selection.put(menuSlot, -1);
                this.writeEntries(menuSlot, target, entries);
            } else if (button == 0 && !this.carried.isEmpty()) {
                List<ItemStack> entries = this.entriesOf(target);
                entries.add(0, this.carried.copy());
                this.carried = ItemStack.EMPTY;
                this.writeEntries(menuSlot, target, entries);
            }
            return;
        }
        if (button == 0 && !this.carried.isEmpty() && target.isEmpty()) {
            this.set(menuSlot, this.carried.copy());
            this.carried = ItemStack.EMPTY;
            return;
        }
        if (button == 0 && this.carried.isEmpty() && !target.isEmpty()) {
            // Vanilla left-click on an occupied slot with an empty cursor picks the stack up.
            this.carried = target.copy();
            this.set(menuSlot, ItemStack.EMPTY);
        }
    }

    @Override
    public void sendSelectBundleItem(int menuSlot, int itemIndex) {
        this.selectPackets.add(menuSlot + ":" + itemIndex);
        if (this.ignoreSelectionPackets) return;
        this.selection.put(menuSlot, itemIndex);
    }

    @Override public boolean isCreativeLike() { return false; }

    @Override public int stepDelayTicks() { return 0; }

    public static BundleExtractionEngine.Outcome run(BundleExtractionEngine engine, int maxTicks) {
        BundleExtractionEngine.Outcome outcome = BundleExtractionEngine.Outcome.RUNNING;
        for (int tick = 0; tick < maxTicks && (outcome = engine.tick()) == BundleExtractionEngine.Outcome.RUNNING; tick++) {
        }
        return outcome;
    }
}