package me.matl114.hacks.utils.interact;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/** Runs placement requests in order and restores item swaps at the end of each tick. */
public final class InteractionQueue {
    private static final TaskAction COMPLETED = new TaskAction(TaskAction.Action.SUCCESS, null);
    private static final TaskAction WAITING = new TaskAction(TaskAction.Action.PAUSE, null);
    private static final TaskAction ABORTED = new TaskAction(TaskAction.Action.TERMINATE, null);

    private final Deque<InteractRequest> requests = new ArrayDeque<>();
    private final Deque<SwapFrame> swaps = new ArrayDeque<>();
    private final int maxPerTick;
    private final Hand hand;
    private final GhostHandMode ghostHandMode;
    private final LegalInteractMode interactMode;
    private final boolean airPlace;
    private final boolean swingHand;
    private TaskAction.Action status = TaskAction.Action.SUCCESS;
    private boolean restorePending;
    private boolean ticking;
    private long tick;
    private long windowStart = -1;
    private int windowInteractions;

    public InteractionQueue(
            int maxPerTick,
            Hand hand,
            GhostHandMode ghostHandMode,
            boolean airPlace,
            LegalInteractMode interactMode,
            boolean swingHand) {
        if (maxPerTick <= 0) throw new IllegalArgumentException("maxPerTick must be positive");
        this.maxPerTick = maxPerTick;
        this.hand = hand;
        this.ghostHandMode = ghostHandMode;
        this.airPlace = airPlace;
        this.interactMode = interactMode;
        this.swingHand = swingHand;
    }

    public void enqueueAll(Collection<? extends InteractRequest> batch) {
        requests.addAll(batch);
        if (!batch.isEmpty()) status = TaskAction.Action.PAUSE;
    }

    /** Inserts follow-up requests ahead of the remaining queue; call from a success callback. */
    public void enqueueNext(Collection<? extends InteractRequest> batch) {
        var reversed = new ArrayDeque<InteractRequest>();
        reversed.addAll(batch);
        while (!reversed.isEmpty()) requests.addFirst(reversed.removeLast());
        if (!batch.isEmpty()) status = TaskAction.Action.PAUSE;
    }

    public void clear() {
        requests.clear();
        status = TaskAction.Action.TERMINATE;
        if (!ticking) finishSwaps();
    }

    public boolean isEmpty() {
        return requests.isEmpty() && swaps.isEmpty() && !restorePending;
    }

    public boolean isWait() {
        return status == TaskAction.Action.PAUSE;
    }

    public boolean isSuccess() {
        return status == TaskAction.Action.SUCCESS;
    }

    public boolean isTerminate() {
        return status == TaskAction.Action.TERMINATE;
    }

    public boolean enableAirPlace() {
        return airPlace;
    }

    public boolean enablePositionPlace() {
        return !interactMode.isLegal();
    }

    public TaskAction success(BlockHitResult hit) {
        return new TaskAction(TaskAction.Action.SUCCESS, new FlagEntry<>(false, hit));
    }

    public TaskAction success() {
        return COMPLETED;
    }

    public TaskAction pause() {
        return WAITING;
    }

    public TaskAction abort() {
        return ABORTED;
    }

    public void tick() {
        if (MinecraftClient.getInstance().player == null) {
            requests.clear();
            swaps.clear();
            restorePending = false;
            status = TaskAction.Action.TERMINATE;
            return;
        }
        if (status == TaskAction.Action.TERMINATE) return;
        tick++;
        status = TaskAction.Action.PAUSE;
        if (restorePending) return;
        if (windowStart >= 0 && tick - windowStart >= 6) {
            windowStart = -1;
            windowInteractions = 0;
        }
        ticking = true;
        try {
            int executed = 0;
            while (executed < maxPerTick && !requests.isEmpty()) {
                if (windowInteractions >= 9) return;
                InteractRequest request = requests.peekFirst();
                IndexEntry<ItemStack> item = findItem(request);
                TaskAction result = request.tickTask(this, item != null);
                if (status == TaskAction.Action.TERMINATE) return;
                if (result == null || result.action() == TaskAction.Action.TERMINATE) {
                    clear();
                    return;
                }
                if (result.action() == TaskAction.Action.PAUSE) return;
                if (result.action() == TaskAction.Action.SUCCESS) {
                    BlockHitResult hit =
                            result.hitResult() != null && result.hitResult().val() instanceof BlockHitResult blockHit
                                    ? blockHit
                                    : null;
                    if (hit != null && !place(request, item, hit)) {
                        clear();
                        return;
                    }
                    requests.removeFirst();
                    request.successCallback(this);
                    if (status == TaskAction.Action.TERMINATE) return;
                } else {
                    requests.removeFirst();
                }
                executed++;
            }
            if (status != TaskAction.Action.TERMINATE) {
                status = requests.isEmpty() ? TaskAction.Action.SUCCESS : TaskAction.Action.PAUSE;
            }
        } finally {
            ticking = false;
            finishSwaps();
        }
    }

    private IndexEntry<ItemStack> findItem(InteractRequest request) {
        if (InvExtra.INSTANCE == null) return null;
        IndexEntry<ItemStack> bestEntry = findInventoryItem(request, null, -1);
        Double bestScore = bestEntry == null ? null : request.itemScore(this, bestEntry.val());
        IndexEntry<ItemStack> selectedEntry = bestEntry;
        Double selectedScore = bestScore;
        boolean frameSelected = false;
        for (SwapFrame frame : swaps) {
            Double frameScore = request.itemScore(this, frame.previousHand());
            if (frameScore == null) continue;
            if (bestScore != null && frameScore < bestScore) continue;
            if (frameSelected && frameScore <= selectedScore) continue;
            IndexEntry<ItemStack> previousItem = findInventoryItem(request, frame.previousHand(), frame.previousSlot());
            if (previousItem != null) {
                selectedEntry = previousItem;
                selectedScore = frameScore;
                frameSelected = true;
            }
        }
        return frameSelected ? selectedEntry : bestEntry;
    }

    private IndexEntry<ItemStack> findInventoryItem(
            InteractRequest request, ItemStack preferredStack, int preferredSlot) {
        var inventory = MinecraftClient.getInstance().player.getInventory();
        if (preferredStack != null
                && preferredSlot >= 0
                && inventory.getStack(preferredSlot) == preferredStack
                && InvExtra.INSTANCE.canSwapItemToHand(preferredSlot, hand == Hand.OFF_HAND, ghostHandMode)) {
            Double score = request.itemScore(this, preferredStack);
            if (score != null) return new IndexEntry<>(preferredSlot, preferredStack);
        }
        IndexEntry<ItemStack> item = InventoryUtils.findBestPlayerInventory(
                entry -> {
                    if (!InvExtra.INSTANCE.canSwapItemToHand(entry.index(), hand == Hand.OFF_HAND, ghostHandMode)) {
                        return null;
                    }
                    if (preferredStack != null && entry.val() != preferredStack) return null;
                    return request.itemScore(this, entry.val());
                },
                InventoryUtils.getPlayerInvSize(),
                false,
                true,
                true);
        if (item == null) return null;
        Double score = request.itemScore(this, item.val());
        return score == null ? null : item;
    }

    private boolean matches(InteractRequest request, ItemStack stack) {
        return stack != null && request.itemScore(this, stack) != null;
    }

    private boolean place(InteractRequest request, IndexEntry<ItemStack> item, BlockHitResult hit) {
        if (item == null || !InteractionTasks.canHandleInteract(interactMode, hit)) return false;
        if (!selectItem(request, item)) return false;
        if (!InteractionTasks.handlePlaceMode(interactMode, hit, hand, swingHand)) return false;
        if (windowStart < 0) windowStart = tick;
        windowInteractions++;
        return true;
    }

    private boolean selectItem(InteractRequest request, IndexEntry<ItemStack> item) {
        if (item == null) return false;
        Double expectedScore = request.itemScore(this, item.val());
        if (expectedScore == null) return false;
        var player = MinecraftClient.getInstance().player;
        ItemStack held = player.getStackInHand(hand);
        boolean alreadySelected =
                hand == Hand.MAIN_HAND ? item.index() == InventoryUtils.getSelectedSlot() : item.index() == 40;
        if (!alreadySelected) {
            int previousSlot = hand == Hand.OFF_HAND
                    ? item.index()
                    : ghostHandMode == GhostHandMode.HOT_BAR_ONLY ? InventoryUtils.getSelectedSlot() : item.index();
            Runnable restore = InvExtra.INSTANCE.swapItemToHand(item.index(), hand == Hand.OFF_HAND, ghostHandMode);
            if (restore == null) return false;
            ItemStack selected = player.getStackInHand(hand);
            // Keep the previous stack instance and its inventory slot so later requests can reuse it.
            swaps.push(new SwapFrame(held, previousSlot, restore));
            if (!hasItemScore(request, selected, expectedScore)) return false;
        }
        return hasItemScore(request, player.getStackInHand(hand), expectedScore);
    }

    private boolean hasItemScore(InteractRequest request, ItemStack stack, double expectedScore) {
        Double score = request.itemScore(this, stack);
        return score != null && Double.compare(score, expectedScore) == 0;
    }

    private void finishSwaps() {
        if (swaps.isEmpty() || restorePending) return;
        if (interactMode.canMultiRotPlace()) {
            unwindSwaps();
            return;
        }
        restorePending = true;
        if (status != TaskAction.Action.TERMINATE) status = TaskAction.Action.PAUSE;
        try {
            InteractionTasks.finishInteractGhostHand(interactMode, () -> {
                try {
                    unwindSwaps();
                } finally {
                    restorePending = false;
                    if (status != TaskAction.Action.TERMINATE) {
                        status = requests.isEmpty() ? TaskAction.Action.SUCCESS : TaskAction.Action.PAUSE;
                    }
                }
            });
        } catch (RuntimeException exception) {
            restorePending = false;
            throw exception;
        }
    }

    private void unwindSwaps() {
        while (!swaps.isEmpty()) swaps.pop().restore().run();
    }

    private record SwapFrame(ItemStack previousHand, int previousSlot, Runnable restore) {}
}
