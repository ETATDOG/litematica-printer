package me.aleksilassila.litematica.printer.integration.bundle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Drives one extraction of a single bundle entry into the player inventory, using the same
 * vanilla click protocol the bundle panel automation uses: select the entry, right-click the
 * bundle slot with an empty cursor, then left-click an empty inventory slot.
 *
 * <p>Clicks are spaced by a few ticks so the server can confirm each step; on servers that
 * ignore the bundle selection packet the SELECT strategy extracts the wrong entry, so after
 * repeated failures AUTO escalates to the slower ROTATE strategy, which removes the stacks in
 * front of the wanted one into temporary slots and puts them back afterwards.</p>
 */
public final class BundleExtractionEngine {
    public enum Strategy { SELECT, ROTATE, AUTO }

    public enum Outcome { RUNNING, DONE, FAILED }

    /** One extraction job: pull a stack matching {@code expectedStack} out of the bundle in this slot. */
    public record Job(int inventorySlot, ItemStack expectedStack) {}

    private enum Phase { IDLE, SELECT, EXTRACT, STASH, VERIFY, ROTATE_PREPARE, ROTATE_TARGET, RESTORE_PREPARE, RESTORE_INSERT }

    private static final int MAX_JOB_ATTEMPTS = 2;
    private static final int SELECT_FAILURES_BEFORE_ROTATE = 2;

    private final BundleMenuPort port;
    private final boolean autoEscalate;
    private Phase phase = Phase.IDLE;
    private Strategy strategy;
    private Job job;
    private int waitTicks;
    private int attempts;
    private int selectFailures;
    /** Matching entries in the player inventory when the sequence began; VERIFY needs an increase. */
    private int beforeCount;
    /** True when the stack currently on the cursor is the wanted entry, not a rotated-out front stack. */
    private boolean stashIsTarget;
    /** Slots already used to hold rotated-out stacks; never reused within one sequence. */
    private final Set<Integer> claimedMenuSlots = new HashSet<>();
    /** Rotated-out front stacks in removal order; restored last-removed-first. */
    private final List<Integer> tempMenuSlots = new ArrayList<>();

    public BundleExtractionEngine(BundleMenuPort port, Strategy strategy) {
        this.port = port;
        this.autoEscalate = strategy == Strategy.AUTO;
        this.strategy = autoEscalate ? Strategy.SELECT : strategy;
    }

    public boolean running() {
        return this.phase != Phase.IDLE;
    }

    public Strategy strategy() {
        return this.strategy;
    }

    public void start(Job job) {
        this.reset();
        this.job = job;
        this.attempts = 0;
        this.beginSequence();
    }

    public void reset() {
        Job job = this.job;
        this.phase = Phase.IDLE;
        this.job = null;
        this.waitTicks = 0;
        this.attempts = 0;
        this.tempMenuSlots.clear();
        this.claimedMenuSlots.clear();
        this.recoverCarried(job);
    }

    public Outcome tick() {
        if (this.phase == Phase.IDLE) {
            return Outcome.DONE;
        }
        if (this.waitTicks > 0) {
            this.waitTicks--;
            return Outcome.RUNNING;
        }
        // A screen or a foreign menu opened mid-sequence: pause without counting a failure.
        if (!this.port.isInventoryMenuIdle()) {
            return Outcome.RUNNING;
        }
        return this.advance();
    }

    private Outcome advance() {
        switch (this.phase) {
            case SELECT -> {
                Integer menuSlot = this.bundleMenuSlot();
                Integer itemIndex = this.currentTargetIndex();
                if (menuSlot == null || itemIndex == null) {
                    return this.failSequence();
                }
                this.port.sendSelectBundleItem(menuSlot, itemIndex);
                this.enter(Phase.EXTRACT, this.port.stepDelayTicks());
            }
            case EXTRACT -> {
                Integer menuSlot = this.bundleMenuSlot();
                if (menuSlot == null || !this.port.carriedEmpty()) {
                    return this.failSequence();
                }
                if (this.strategy == Strategy.SELECT && this.currentTargetIndex() == null) {
                    return this.failSequence();
                }
                this.port.click(menuSlot, 1);
                this.enter(Phase.STASH, this.port.stepDelayTicks());
            }
            case STASH -> {
                if (this.port.carriedEmpty()) {
                    // The extract click produced nothing (ignored selection or an already-taken entry).
                    return this.failSequence();
                }
                int empty = this.port.findEmptyInventoryMenuSlot(this.claimedMenuSlots);
                if (empty < 0) {
                    return this.failSequence();
                }
                this.claimedMenuSlots.add(empty);
                if (!this.stashIsTarget) {
                    this.tempMenuSlots.add(empty);
                    this.port.click(empty, 0);
                    this.enter(Phase.ROTATE_PREPARE, this.port.stepDelayTicks());
                } else if (!this.tempMenuSlots.isEmpty()) {
                    // Target secured; rotated-out front stacks must go back before verifying.
                    this.port.click(empty, 0);
                    this.enter(Phase.RESTORE_PREPARE, this.port.stepDelayTicks());
                } else {
                    this.port.click(empty, 0);
                    this.enter(Phase.VERIFY, this.port.stepDelayTicks());
                }
            }
            case VERIFY -> {
                if (!this.port.carriedEmpty()) {
                    // The deposit click was rejected; retry the stash step with the same cursor.
                    this.enter(Phase.STASH, 1);
                    return Outcome.RUNNING;
                }
                if (this.matchingCount() > this.beforeCount) {
                    return this.completeSequence();
                }
                return this.failSequence();
            }
            case ROTATE_PREPARE -> {
                Integer itemIndex = this.currentTargetIndex();
                if (itemIndex == null) {
                    return this.failSequence();
                }
                if (itemIndex == 0) {
                    this.stashIsTarget = true;
                    this.enter(Phase.ROTATE_TARGET, 1);
                    return Outcome.RUNNING;
                }
                this.stashIsTarget = false;
                // Rotating needs one slot per stack in front plus one for the target itself.
                Set<Integer> probe = new HashSet<>(this.claimedMenuSlots);
                for (int needed = 0; needed <= itemIndex; needed++) {
                    int slot = this.port.findEmptyInventoryMenuSlot(probe);
                    if (slot < 0) {
                        return this.failSequence();
                    }
                    probe.add(slot);
                }
                this.enter(Phase.EXTRACT, 1);
            }
            case ROTATE_TARGET -> {
                Integer menuSlot = this.bundleMenuSlot();
                if (menuSlot == null || !this.port.carriedEmpty()) {
                    return this.failSequence();
                }
                this.port.click(menuSlot, 1);
                this.enter(Phase.STASH, this.port.stepDelayTicks());
            }
            case RESTORE_PREPARE -> {
                if (this.tempMenuSlots.isEmpty()) {
                    this.enter(Phase.VERIFY, 1);
                    return Outcome.RUNNING;
                }
                if (!this.port.carriedEmpty()) {
                    return this.failSequence();
                }
                int tempSlot = this.tempMenuSlots.get(this.tempMenuSlots.size() - 1);
                this.port.click(tempSlot, 0);
                this.enter(Phase.RESTORE_INSERT, this.port.stepDelayTicks());
            }
            case RESTORE_INSERT -> {
                Integer menuSlot = this.bundleMenuSlot();
                if (menuSlot == null || this.port.carriedEmpty()) {
                    return this.failSequence();
                }
                this.port.click(menuSlot, 0);
                this.tempMenuSlots.remove(this.tempMenuSlots.size() - 1);
                this.enter(Phase.RESTORE_PREPARE, this.port.stepDelayTicks());
            }
            default -> {
                return Outcome.FAILED;
            }
        }
        return Outcome.RUNNING;
    }

    /** SELECT extracts the selected entry directly; ROTATE first clears any leftover selection. */
    private void beginSequence() {
        this.tempMenuSlots.clear();
        this.claimedMenuSlots.clear();
        this.beforeCount = this.matchingCount();
        this.stashIsTarget = this.strategy == Strategy.SELECT;
        if (this.strategy == Strategy.SELECT) {
            this.enter(Phase.SELECT, 1);
        } else {
            this.clearSelection(this.job);
            this.enter(Phase.ROTATE_PREPARE, 1);
        }
    }

    private Outcome completeSequence() {
        Job job = this.job;
        this.phase = Phase.IDLE;
        this.job = null;
        this.waitTicks = 0;
        this.tempMenuSlots.clear();
        this.claimedMenuSlots.clear();
        this.clearSelection(job);
        return Outcome.DONE;
    }

    private Outcome failSequence() {
        this.attempts++;
        if (this.strategy == Strategy.SELECT) {
            this.selectFailures++;
            if (this.autoEscalate && this.selectFailures >= SELECT_FAILURES_BEFORE_ROTATE) {
                this.strategy = Strategy.ROTATE;
                this.selectFailures = 0;
                this.attempts = 0;
            }
        }
        if (this.attempts > MAX_JOB_ATTEMPTS) {
            this.recoverCarried(this.job);
            this.clearSelection(this.job);
            this.phase = Phase.IDLE;
            this.job = null;
            this.tempMenuSlots.clear();
            this.claimedMenuSlots.clear();
            return Outcome.FAILED;
        }
        this.beginSequence();
        return Outcome.RUNNING;
    }

    /**
     * A failure may leave the extracted stack on the cursor (for example when no empty slot was
     * free). Best effort: finish the deposit when possible, otherwise put the stack back into
     * the bundle it came from, so the cursor never stays occupied.
     */
    private void recoverCarried(Job job) {
        if (!this.port.isInventoryMenuIdle() || this.port.carriedEmpty()) {
            return;
        }
        int empty = this.port.findEmptyInventoryMenuSlot(this.claimedMenuSlots);
        if (empty >= 0) {
            this.port.click(empty, 0);
            return;
        }
        Integer menuSlot = job == null ? null : this.bundleMenuSlot(job);
        if (menuSlot != null) {
            this.port.click(menuSlot, 0);
        }
    }

    private void clearSelection(Job job) {
        if (job == null) {
            return;
        }
        Integer menuSlot = this.bundleMenuSlot(job);
        if (menuSlot == null) {
            return;
        }
        ItemStack bundle = this.port.inventoryStack(job.inventorySlot());
        if (!bundle.isEmpty() && BundleItem.getSelectedItemIndex(bundle) != -1) {
            this.port.sendSelectBundleItem(menuSlot, -1);
        }
    }

    private Integer bundleMenuSlot() {
        return this.job == null ? null : this.bundleMenuSlot(this.job);
    }

    private Integer bundleMenuSlot(Job job) {
        ItemStack stack = this.port.inventoryStack(job.inventorySlot());
        if (stack.isEmpty() || !(stack.getItem() instanceof BundleItem) || contents(stack) == null) {
            return null;
        }
        int menuSlot = this.port.menuSlotOf(job.inventorySlot());
        return menuSlot < 0 ? null : menuSlot;
    }

    /** Re-reads the live bundle contents; entry order can shift after server syncs. */
    private Integer currentTargetIndex() {
        if (this.job == null) {
            return null;
        }
        ItemStack bundle = this.port.inventoryStack(this.job.inventorySlot());
        List<ItemStack> items = contents(bundle);
        if (items == null) {
            return null;
        }
        for (int index = 0; index < items.size(); index++) {
            if (ItemStack.isSameItemSameComponents(items.get(index), this.job.expectedStack())) {
                return index;
            }
        }
        return null;
    }

    private int matchingCount() {
        if (this.job == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < this.port.inventorySize(); slot++) {
            ItemStack stack = this.port.inventoryStack(slot);
            if (ItemStack.isSameItemSameComponents(stack, this.job.expectedStack())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void enter(Phase phase, int waitTicks) {
        this.phase = phase;
        this.waitTicks = Math.max(0, waitTicks);
    }

    /** Copies of the bundle entries, or null when the stack carries no bundle contents. */
    public static List<ItemStack> contents(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BundleItem)) {
            return null;
        }
        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents == null) {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        //#if MC >= 260300
        //$$ bundleContents.itemCopies().forEach(items::add);
        //#else
        bundleContents.itemCopyStream().forEach(items::add);
        //#endif
        return items;
    }
}