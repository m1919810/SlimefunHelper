package me.matl114.hacks.modules.inv;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Runnables;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.With;
import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.api.Displayable;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.complex.invcache.InventoryViewScreen;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.single.ConfirmingWidgetScreen;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.commands.commandGroup.BridgeSubCommand;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.inventory.MutableInventory;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.mutable.MutableObject;

public class KitReplenish extends BaseModule {
    // todo: ktiManager
    public static KitReplenish INSTANCE;

    public KitReplenish() {
        super("KitReplenish");
        INSTANCE = this;
    }

    public final ModulePath replenishRoot = makePath(Configs.INV_CONFIG, "auto-inv.replenish");
    public List<Vec3i> blockSeq = new ArrayList<>();

    public final EnumRef<Choice> shulkerMatchChoice = builder(replenishRoot.add("shulker-match-choice"), Choice.class)
            .defaultValue(Choice.NUM_MATCH)
            .build();

    public final FlagRef enableEnder =
            flagBuilder(replenishRoot.add("enable-ender-replenish")).build();

    public final NBTRef<OptionalPrimitive<Integer>> specificSlot = builder(
                    replenishRoot.add("specific-slot"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 8))
            .validator(s -> s.getValue() < 9 && s.getValue() >= 0)
            .build();

    public final DoubleRef searchRange = doubleBuilder(replenishRoot.add("search-range"))
            .defaultValue(4.5D)
            .validator(Configs.doubleRange(0, 100))
            .updateListener(s -> blockSeq = MathUtils.create3DPointListAroundPlayer(s))
            .build();

    public final FlagRef zeroTickSupplyShulker =
            flagBuilder(replenishRoot.add("zero-tick-supply")).build();

    public final FlagRef log =
            builder(replenishRoot.add("log"), Boolean.class).defaultValue(true).build();

    public final KeyBindRef hotkeyReplenish = hotkey(replenishRoot.add("execute-replenish"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::replenishCurrentKit))
            .build();

    public final KeyBindRef autoEnderChest = hotkey(replenishRoot.add("auto-ender-chest"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(() -> {
                this.enderChestRequest = true;
            }))
            .build();

    public final KeyBindRef autoShulkerOpen = hotkey(replenishRoot.add("auto-place-shulker"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::openSelectedShulkerBox))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onSwitchWorld);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerCommandBootstrap(this::registerReplenishCommand);
    }

    private Kit requestKit = null;

    private Transaction transaction = null;

    private boolean enderChestRequest = false;
    private ShulkerBoxRequest shulkerBoxRequest = null;

    private void setShulkerBoxRequest(ShulkerBoxRequest request) {
        if (shulkerBoxRequest != null) {
            // abort
            shulkerBoxRequest.failureCallback().run();
            shulkerBoxRequest = null;
        }
        shulkerBoxRequest = request;
    }

    private final TimerExecutor slowInteract = new TimerExecutor();
    private int timeoutEnderChest = -1;

    private void clearReplenishingTask() {
        if (transaction != null && !transaction.isCompleted()) {
            transaction.complete(false);
        }
        transaction = null;
    }

    public void onSwitchWorld(Event<World> event) {
        clearReplenishingTask();
        enderChestRequest = false;
        shulkerBoxRequest = null;
    }

    int timerPostResortInventory = 0;

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (transaction != null) {
            process:
            {
                if (transaction.isCompleted()) {
                    clearReplenishingTask();
                    return;
                }
                if (transaction.stage == Transaction.STAGE_REORDER_INVENTORY) {
                    if (mc.currentScreen != null) {
                        mc.currentScreen.close();
                    }
                    if (transaction.rule.type() == Type.AUTO) {
                        resortInventories(
                                transaction.inventory, transaction.rule.from(), transaction.rule.to(), transaction);
                    }
                    transaction.stage = Transaction.STAGE_FIND_SHULKER;
                }
                if (transaction.stage == Transaction.STAGE_FIND_SHULKER) {
                    if (transaction.toReplenishSummary.isEmpty()) {
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.success.replenish-finish");
                        }
                        transaction.complete(true);
                        break process;
                    }
                    var re = findShulker(mc.player.getInventory(), transaction);
                    transaction.findResult = re;
                    if (re != null) {
                        transaction.stage = Transaction.STAGE_SWITCH_HOT_BAR;
                    } else {
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.failure.no-shulker");
                        }
                        if (transaction.enableUseEnderChest) {
                            transaction.stage = Transaction.STAGE_USE_ENDER_CHEST;
                            transaction.useEnderChest = true;
                            enderChestRequest = true;
                            break process;
                        } else {
                            transaction.complete(false);
                            break process;
                        }
                    }
                }
                if (transaction.stage == Transaction.STAGE_USE_ENDER_CHEST) {
                    if (!enderChestRequest) {
                        if (mc.currentScreen instanceof GenericContainerScreen screen) {
                            var handler = screen.getScreenHandler();
                            Inventory inventory = handler.getInventory();
                            IndexEntry<ItemStack> stack = findShulker(inventory, transaction);
                            int idx;
                            if (stack != null
                                    && (idx = handler.getSlotIndex(inventory, stack.index())
                                                    .orElse(-1))
                                            >= 0) {
                                IndexEntry<Slot> anyStack = InventoryUtils.findBestScreenSlot(
                                        handler.slots,
                                        (sl) -> {
                                            if (!(sl.inventory instanceof PlayerInventory)) return null;
                                            var st = sl.getStack();
                                            if (st.isEmpty()) {
                                                return 1E8;
                                            } else {
                                                if (st.isOf(Items.TOTEM_OF_UNDYING)
                                                        || st.isOf(Items.OBSIDIAN)
                                                        || st.isOf(Items.SHULKER_BOX)) {
                                                    return 128.0D - st.getCount();
                                                } else {
                                                    return (double) st.getMaxCount();
                                                }
                                            }
                                        },
                                        true);
                                if (anyStack != null) {
                                    InvExtra.INSTANCE.swapScreenSlots(idx, anyStack.index());
                                    transaction.findResult = new IndexEntry<>(
                                            anyStack.val().getIndex(),
                                            anyStack.val().getStack());
                                    transaction.stage = Transaction.STAGE_SWITCH_HOT_BAR;
                                    screen.close();
                                } else {
                                    if (log.get()) {
                                        logI18N("message.kit-manager.kit-replenish.failure.no-space-in-inventory");
                                    }
                                    transaction.complete(false);
                                    break process;
                                }
                            } else {
                                if (log.get()) {
                                    logI18N("message.kit-manager.kit-replenish.failure.no-shulker-in-ender-chest");
                                }
                                transaction.complete(false);
                                break process;
                            }
                        } else {
                            transaction.complete(false);
                            break process;
                        }
                    }
                }
                if (transaction.stage == Transaction.STAGE_SWITCH_HOT_BAR) {
                    if (specificSlot.get().isPresent()) {
                        int slot = Math.clamp(specificSlot.get().getValue(), 0, 8);
                        int findResult = transaction.findResult.index();
                        InvExtra.INSTANCE.swapInventoryIndexes(findResult, slot);
                        transaction.leftEmptySlotForShulker = slot;
                        transaction.findResult = new IndexEntry<>(slot, transaction.findResult.val());
                    } else {
                        transaction.leftEmptySlotForShulker = transaction.findResult.index();
                    }
                    transaction.stage = Transaction.STAGE_PLACE_SHULKER;
                }
                if (transaction.stage == Transaction.STAGE_PLACE_SHULKER) {
                    int index = transaction.findResult.index();
                    ItemStack stack = mc.player.getInventory().getStack(index);
                    if (isShulker(stack)) {
                        var block = searchAvailableShulkerPosition().findAny().orElse(null);
                        if (block != null) {
                            Transaction trans = this.transaction;
                            trans.stage = Transaction.STAGE_APPLY_INV;
                            setShulkerBoxRequest(new ShulkerBoxRequest(
                                    Optional.of(block),
                                    Optional.of(new Slot(mc.player.getInventory(), index, 0, 0)),
                                    (ch) -> {
                                        applyReplenish(ch, trans);
                                        trans.stage = Transaction.STAGE_POST_REORDER_INVENTORY;
                                        timerPostResortInventory = 5;
                                    },
                                    () -> {
                                        if (log.get()) {
                                            logI18N("message.kit-manager.kit-replenish.failure.replenish");
                                        }
                                        trans.complete(false);
                                    },
                                    zeroTickSupplyShulker.get()));
                        } else {
                            if (log.get()) {
                                logI18N("message.kit-manager.kit-replenish.failure.no-space-to-place");
                            }
                            transaction.complete(false);
                            break process;
                        }
                    } else {
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.failure.shulker-mismatch");
                        }
                        transaction.complete(false);
                        break process;
                    }
                }
                if (transaction.stage == Transaction.STAGE_POST_REORDER_INVENTORY) {
                    if (timerPostResortInventory > 0) {
                        --timerPostResortInventory;
                    } else {
                        resortInventories(
                                transaction.inventory, transaction.rule.from(), transaction.rule.to(), transaction);
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.success.replenish-finish");
                        }
                        transaction.complete(true);
                    }
                }
            }
        } else {
            clearReplenishingTask();
        }
        if (enderChestRequest) {
            if (timeoutEnderChest == -1) {
                timeoutEnderChest = 20;
            }
            if (timeoutEnderChest > 0) {
                timeoutEnderChest--;
                if (timeoutEnderChest == 0) {
                    timeoutEnderChest = -1;
                    enderChestRequest = false;
                }
            }
            if (ChestHistory.isEnderChest(mc.currentScreen)) {
                if (log.get()) {
                    logI18N("message.kit-manager.kit-replenish.success.open-ender-chest");
                }
                enderChestRequest = false;
            } else {
                if (mc.currentScreen != null) {
                    mc.currentScreen.close();
                }
                if (slowInteract.canRun(5)) {
                    BlockPos pos = findCurrentOpenEnderChest();
                    if (pos != null) {
                        Interact.INSTANCE.interactBlock(RaycastUtils.createHitResult(pos, mc.player.getEyePos()));
                        slowInteract.mark();
                    } else {
                        IndexEntry<ItemStack> stackEnderChest =
                                InventoryUtils.findPlayerItem(s -> s.isOf(Items.ENDER_CHEST), true, false);
                        if (stackEnderChest != null) {
                            var re = searchAvailableChestLikePosition(true)
                                    .findFirst()
                                    .orElse(null);
                            if (re == null) {
                                enderChestRequest = false;
                                if (log.get()) logI18N("message.kit-manager.kit-replenish.failure.no-space-to-place");
                            } else {
                                Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(stackEnderChest.index());
                                if (callback != null) {
                                    Interact.INSTANCE.interactBlock(re.getSecond());
                                    callback.run();
                                } else {
                                    enderChestRequest = false;
                                }
                            }
                        } else {
                            enderChestRequest = false;
                            if (log.get()) logI18N("message.kit-manager.kit-replenish.failure.no-ender-chest");
                        }
                    }
                }
            }
        } else {
            timeoutEnderChest = -1;
        }
        if (shulkerBoxRequest != null) {
            shulker_place:
            {
                if (shulkerBoxRequest.placePos().isEmpty()) {
                    var placePos = searchAvailableShulkerPosition().findAny().orElse(null);
                    if (placePos != null) {
                        shulkerBoxRequest = shulkerBoxRequest.withPlacePos(Optional.of(placePos));
                    } else {
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.failure.no-space-to-place");
                        }
                        shulkerBoxRequest.failureCallback().run();
                        shulkerBoxRequest = null;
                        break shulker_place;
                    }
                }
                int slotIndex = -1;
                Optional<Slot> currentPlayerSlot = shulkerBoxRequest.playerScreenSlot();
                if (currentPlayerSlot.isPresent()) {
                    Slot slot = shulkerBoxRequest.playerScreenSlot().get();
                    var idx = mc.player.currentScreenHandler.getSlotIndex(slot.inventory, slot.getIndex());
                    if (idx.isPresent()) {
                        slotIndex = idx.getAsInt();
                    } else {
                        shulkerBoxRequest = shulkerBoxRequest.withPlayerScreenSlot(Optional.empty());
                        currentPlayerSlot = Optional.empty();
                    }
                }
                if (currentPlayerSlot.isEmpty()) {
                    var entry = InventoryUtils.findScreenSlot(
                            mc.player.currentScreenHandler.slots, s -> isShulker(s.getStack()), false);
                    if (entry == null) {
                        if (log.get()) {
                            logI18N("message.kit-manager.kit-replenish.failure.no-shulker");
                        }
                        shulkerBoxRequest.failureCallback().run();
                        shulkerBoxRequest = null;
                        break shulker_place;
                    } else {
                        slotIndex = entry.index();
                        shulkerBoxRequest = shulkerBoxRequest.withPlayerScreenSlot(Optional.of(entry.val()));
                    }
                }
                Preconditions.checkArgument(slotIndex >= 0, "?");
                var re = shulkerBoxRequest.placePos().get();
                BlockPos pos = re.getFirst();
                if (!(mc.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity)) {
                    int slot = slotIndex;
                    Runnable runnable = InvExtra.INSTANCE.swapInventorySlotToHand(slot);
                    if (runnable != null) {
                        Interact.INSTANCE.interactBlock(
                                shulkerBoxRequest.placePos().get().getSecond());
                        runnable.run();
                        if (!(mc.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity)) {
                            if (log.get()) {
                                logI18N("message.kit-manager.kit-replenish.failure.place-failure");
                            }
                            shulkerBoxRequest.failureCallback().run();
                            shulkerBoxRequest = null;
                            break shulker_place;
                        }
                        if (!shulkerBoxRequest.useZeroTick()) {
                            break shulker_place;
                        }
                    } else {
                        shulkerBoxRequest.failureCallback().run();
                        shulkerBoxRequest = null;
                        break shulker_place;
                    }
                }
                if (mc.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerCurrent
                        && InteractUtils.canShulkerOpen(mc.world, pos, mc.world.getBlockState(pos))) {
                    if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler shulker
                            && mc.player.currentScreenHandler instanceof TileInventory tile
                            && Objects.equals(tile.getPos(), pos)) {
                        shulkerBoxRequest.successCallback().accept(mc.player.currentScreenHandler);
                        shulkerBoxRequest = null;
                        break shulker_place;
                    } else {
                        Interact.INSTANCE.interactBlock(pos);
                        // use predictor
                        if (shulkerBoxRequest.useZeroTick()) {
                            InvTasks.executePredictInventoryAction(
                                    convertShulkerCurrentToViaItems(shulkerCurrent), handler -> {
                                        shulkerBoxRequest.successCallback().accept(handler);
                                        shulkerBoxRequest = null;
                                    });
                        } else {
                            break shulker_place;
                        }
                    }
                } else if (shulkerBoxRequest.useZeroTick()) {
                    shulkerBoxRequest = shulkerBoxRequest.withUseZeroTick(false);
                    break shulker_place;
                } else {
                    shulkerBoxRequest.failureCallback().run();
                    shulkerBoxRequest = null;
                    break shulker_place;
                }
            }
        }
    }

    private Inventory convertShulkerCurrentToViaItems(Inventory inventory) {
        Inventory newInventory = new MutableInventory(inventory.size(), new ArrayList<>());
        Map<ReplenishTemplate, ItemStack> playerInventoryTemplate = new HashMap<>();
        for (var re : InventoryUtils.iterable(mc.player.getInventory())) {
            if (!re.isEmpty()) {
                playerInventoryTemplate.put(ReplenishTemplate.of(re), re);
            }
        }
        for (var re = 0; re < inventory.size(); re++) {
            ItemStack stackTemplate = inventory.getStack(re);
            if (stackTemplate.isEmpty()) {
                newInventory.setStack(re, stackTemplate);
            } else {
                ItemStack optional = playerInventoryTemplate.get(ReplenishTemplate.of(stackTemplate));
                if (optional == null) {
                    newInventory.setStack(re, stackTemplate);
                } else {
                    newInventory.setStack(re, optional.copyWithCount(stackTemplate.getCount()));
                }
            }
        }
        return inventory;
    }

    public IndexEntry<ItemStack> findShulker(Inventory inventory, Transaction transaction) {
        Function<ItemStack, Double> rule =
                switch (shulkerMatchChoice.get()) {
                    case NUM_MATCH -> (stack) -> estimateShulkerNumberValue(stack, transaction.toReplenishSummary);
                    case SLOT_MATCH -> (stack -> estimateShulkerSlotValue(stack, transaction.viewInventory));
                    case ITEM_EXIST -> (stack -> estimateShulkerItemMatchValue(stack, transaction.viewInventory));
                };
        return InventoryUtils.findBestItem(inventory, rule, false);
    }

    private boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bl && bl.getBlock() instanceof ShulkerBoxBlock;
    }

    private Double estimateShulkerSlotValue(ItemStack stack, Inventory view) {
        if (isShulker(stack) && stack.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && !Objects.equals(ContainerComponent.DEFAULT, container)) {
                Inventory shulkerView = InventoryUtils.createReadOnlyInventory(container.stacks);
                int size = Math.min(view.size(), shulkerView.size());
                double score = 0;
                for (var i = 0; i < size; ++i) {
                    if (ItemStack.areItemsEqual(view.getStack(i), shulkerView.getStack(i))) {
                        score += 1;
                    }
                }
                return score > 0 ? Double.valueOf(score * 1000) : estimateShulkerItemMatchValue(stack, view);
            }
        }
        return null;
    }

    private Double estimateShulkerItemMatchValue(ItemStack stack, Inventory view) {
        if (isShulker(stack) && stack.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && !Objects.equals(ContainerComponent.DEFAULT, container)) {
                Inventory shulkerView = InventoryUtils.createReadOnlyInventory(container.stacks);
                Map<Item, Integer> itemTypeCount = new LinkedHashMap<>();
                boolean hasSame = false;
                for (var re : InventoryUtils.iterable(view)) {
                    Item type = re.getItem();
                    Integer itemCount = itemTypeCount.get(type);
                    if (itemCount != null) {
                        itemTypeCount.put(type, itemCount - 1);
                    } else {
                        int countSlot = -1;
                        for (var item : InventoryUtils.iterable(shulkerView)) {
                            if (item.isOf(type)) {
                                hasSame = true;
                                countSlot += 1;
                            }
                        }
                        itemTypeCount.put(type, countSlot);
                    }
                }
                if (!hasSame) {
                    return null;
                }
                double value = 0;
                for (var values : itemTypeCount.values()) {
                    value += -Math.abs(values.doubleValue());
                }
                return value;
            }
        }
        return null;
    }

    private Double estimateShulkerNumberValue(ItemStack stack, Map<ReplenishTemplate, Integer> replenishSupply) {
        if (isShulker(stack) && stack.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && !Objects.equals(ContainerComponent.DEFAULT, container)) {
                Map<ReplenishTemplate, Integer> integerMap = new LinkedHashMap<>(replenishSupply);
                for (var re : integerMap.entrySet()) {
                    int maxCount = re.getKey().stackTemplate().getMaxCount();
                    if (maxCount < 64) {
                        re.setValue(re.getValue() * 64 / maxCount);
                    }
                }
                Map<ReplenishTemplate, Integer> extraItems = new LinkedHashMap<>();
                boolean hasNeeded = false;
                for (var re : container.iterateNonEmpty()) {
                    ReplenishTemplate sample = ReplenishTemplate.of(re);
                    Integer need = integerMap.get(sample);
                    int weightedCount = re.getCount();
                    int maxCount = re.getItem().getMaxCount();
                    if (maxCount < 64) {
                        weightedCount = weightedCount * 64 / maxCount;
                    }
                    if (need != null) {
                        hasNeeded = true;
                        need -= weightedCount;
                        if (need > 0) {
                            integerMap.put(sample, need);
                        } else {
                            integerMap.remove(sample);
                            if (need < 0) {
                                extraItems.merge(sample, -need, Integer::sum);
                            }
                        }
                    } else {
                        extraItems.merge(sample, weightedCount, Integer::sum);
                    }
                }
                if (!hasNeeded) {
                    return null;
                }
                int supplySum =
                        integerMap.values().stream().mapToInt(Integer::intValue).sum();
                int extraSum =
                        extraItems.values().stream().mapToInt(Integer::intValue).sum();
                return (double) (-supplySum * 1000 - extraSum);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void applyReplenish(ScreenHandler handler, Transaction transaction) {
        MovTasks.getMovExtra().sendPacketsForInventoryAction();
        switch (transaction.rule.type()) {
            case AUTO, STRICT -> {
                applyReplenishStrict(handler, transaction);
            }
            case GREEDY -> {
                applyReplenishGreedy(handler, transaction);
            }
            case ORDERED -> {
                applyReplenishOrdered(handler, transaction);
            }
        }
    }

    private void applyReplenishStrict(ScreenHandler handler, Transaction transaction) {
        Inventory topInventory = InventoryUtils.getTopInventory(handler);
        Rule rule = transaction.rule;

        int from = rule.from();
        int to = rule.to();
        Inventory templateInventory = transaction.inventory;
        Inventory playerInventory = mc.player.getInventory();
        for (var i = from; i < to; ++i) {
            // do not move my leftovers
            if (i == transaction.leftEmptySlotForShulker) {
                continue;
            }
            ItemStack templateItem = templateInventory.getStack(i);
            ItemStack currentStack = playerInventory.getStack(i);
            if (templateItem.isEmpty() || isShulker(templateItem)) {
                continue;
            }
            if (isShulker(currentStack)) {
                continue;
            }
            if (!rule.dump() && !currentStack.isEmpty() && !canReplenish(templateItem, currentStack)) {
                continue;
            }
            int slotI = handler.getSlotIndex(playerInventory, i).getAsInt();
            for (var s = 0; s < topInventory.size(); ++s) {
                ItemStack stack = topInventory.getStack(s);
                if (canReplenish(templateItem, stack)) {
                    InvExtra.INSTANCE.mergeScreenSlotTo(s, slotI);
                    if (playerInventory.getStack(i).getCount() >= templateItem.getCount()) {
                        break;
                    }
                }
            }
        }
        emptyLeftSlot(handler, transaction);
    }

    private void applyReplenishGreedy(ScreenHandler handler, Transaction transaction) {
        Inventory topInventory = InventoryUtils.getTopInventory(handler);
        Rule rule = transaction.rule;

        int from = rule.from();
        int to = rule.to();
        Inventory playerInventory = mc.player.getInventory();
        List<IndexEntry<ReplenishTemplate>> toReplenished = transaction.toReplenishSummary.entrySet().stream()
                .map(s -> new IndexEntry<>(
                        s.getValue() * 64 / s.getKey().stackTemplate().getMaxCount(), s.getKey()))
                .collect(Collectors.toCollection(ArrayList::new));
        Comparator<IndexEntry<ReplenishTemplate>> collector = Comparator.<IndexEntry<ReplenishTemplate>>comparingInt(
                        IndexEntry::index)
                .thenComparingLong(s -> s.val().hashCode())
                .reversed();
        toReplenished.sort(collector);

        while (!toReplenished.isEmpty()) {
            IndexEntry<ReplenishTemplate> replenishItem = toReplenished.remove(0);
            int supplyIndex = -1;
            for (var i = 0; i < topInventory.size(); ++i) {
                if (replenishItem.val().match(topInventory.getStack(i))) {
                    supplyIndex = i;
                    break;
                }
            }
            if (supplyIndex < 0) {
                continue;
            }
            boolean findAny = false;
            for (var j = from; j < to; ++j) {
                ItemStack stackJ = playerInventory.getStack(j);
                if (stackJ.isEmpty()) {
                    findAny = true;
                    break;
                }
                if (stackJ.getCount() < stackJ.getMaxCount()
                        && replenishItem.val().match(stackJ)) {
                    findAny = true;
                    break;
                }
            }
            if (!findAny) {
                continue;
            }
            int count = topInventory.getStack(supplyIndex).getCount();
            int maxCount = replenishItem.val().stackTemplate().getMaxCount();
            mc.interactionManager.clickSlot(handler.syncId, supplyIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
            int count2 = topInventory.getStack(supplyIndex).getCount();
            if (count2 >= count) {
                // no such space
                continue;
            }
            int newInd = replenishItem.index() - ((count - count2) * 64 / maxCount);
            if (newInd <= 0) {
                continue;
            }
            toReplenished.add(new IndexEntry<>(newInd, replenishItem.val()));
            toReplenished.sort(collector);
        }
        emptyLeftSlot(handler, transaction);
    }

    private void applyReplenishOrdered(ScreenHandler handler, Transaction transaction) {
        Inventory topInventory = InventoryUtils.getTopInventory(handler);
        Rule rule = transaction.rule;

        int from = rule.from();
        int to = rule.to();
        Inventory playerInventory = mc.player.getInventory();
        Map<ReplenishTemplate, Integer> replenishCount = new LinkedHashMap<>(transaction.toReplenishSummary);
        for (var i = 0; i < topInventory.size(); ++i) {
            if (replenishCount.isEmpty()) break;
            ItemStack stack = topInventory.getStack(i);
            if (stack.isEmpty()) continue;
            ReplenishTemplate sample = ReplenishTemplate.of(stack);
            Integer value = replenishCount.get(sample);
            if (value != null) {
                boolean findAny = false;
                for (var j = from; j < to; ++j) {
                    ItemStack stackJ = playerInventory.getStack(j);
                    if (stackJ.isEmpty()) {
                        findAny = true;
                        break;
                    }
                    if (stackJ.getCount() < stackJ.getMaxCount() && canReplenish(sample.stackTemplate(), stackJ)) {
                        findAny = true;
                        break;
                    }
                }
                if (!findAny) {
                    replenishCount.remove(sample);
                    continue;
                }
                int count = stack.getCount();
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                int count2 = topInventory.getStack(i).getCount();
                if (count <= count2) {
                    replenishCount.remove(sample);
                } else {
                    int count3 = value - (count - count2);
                    if (count3 < 0) {
                        replenishCount.remove(sample);
                    } else {
                        replenishCount.put(sample, count3);
                    }
                }
            }
        }
        emptyLeftSlot(handler, transaction);
    }

    private void emptyLeftSlot(ScreenHandler handler, Transaction transaction) {
        OptionalInt slotIndex = handler.getSlotIndex(mc.player.getInventory(), transaction.leftEmptySlotForShulker);
        if (slotIndex.isPresent()
                && !mc.player
                        .getInventory()
                        .getStack(transaction.leftEmptySlotForShulker)
                        .isEmpty()) {
            for (int i = 0; i < InventoryUtils.getPlayerBackpackSize(); ++i) {
                if (i != transaction.leftEmptySlotForShulker
                        && mc.player.getInventory().getStack(i).isEmpty()) {
                    InvExtra.INSTANCE.swapInventoryIndexes(transaction.leftEmptySlotForShulker, i);
                    return;
                }
            }
            mc.interactionManager.clickSlot(
                    handler.syncId, slotIndex.getAsInt(), 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private void resortInventories(Inventory inventory, int from, int to, Transaction trans) {
        if (checkNull()) {
            return;
        }
        PlayerInventory playerInventory = mc.player.getInventory();
        ScreenHandler handler = mc.player.currentScreenHandler;
        from = Math.clamp(from, 0, Math.min(inventory.size(), InventoryUtils.getPlayerBackpackSize()));
        to = Math.clamp(to, from, Math.min(inventory.size(), InventoryUtils.getPlayerBackpackSize()));
        for (int i = from; i < to; ++i) {
            if (i == trans.leftEmptySlotForShulker) {
                continue;
            }
            ItemStack templateStack = inventory.getStack(i);
            if (templateStack.isEmpty() || isShulker(templateStack)) {
                continue;
            }
            ItemStack currentStack = playerInventory.getStack(i);
            if (canReplenish(templateStack, currentStack)) {
                continue;
            }
            int candidate = findResortSwapCandidate(playerInventory, templateStack, i, to);
            if (candidate >= 0) {
                InvExtra.INSTANCE.swapInventoryIndexes(candidate, i);
            }
        }
        for (int i = from; i < to; ++i) {
            if (i == trans.leftEmptySlotForShulker) {
                continue;
            }
            ItemStack templateStack = inventory.getStack(i);
            if (templateStack.isEmpty() || isShulker(templateStack)) {
                continue;
            }
            ItemStack currentStack = playerInventory.getStack(i);
            if (!canReplenish(templateStack, currentStack) || currentStack.getCount() >= templateStack.getCount()) {
                continue;
            }
            int targetSlot = handler.getSlotIndex(playerInventory, i).orElse(-1);
            if (targetSlot < 0) {
                continue;
            }
            for (int j = from; j < to; ++j) {
                if (j == trans.leftEmptySlotForShulker) {
                    continue;
                }
                if (j == i) {
                    continue;
                }
                ItemStack otherStack = playerInventory.getStack(j);
                if (!canReplenish(templateStack, otherStack)) {
                    continue;
                }
                int sourceSlot = handler.getSlotIndex(playerInventory, j).orElse(-1);
                if (sourceSlot < 0) {
                    continue;
                }
                InvExtra.INSTANCE.mergeScreenSlotTo(sourceSlot, targetSlot);
                if (playerInventory.getStack(i).getCount() >= templateStack.getCount()) {
                    break;
                }
            }
        }
    }

    private int findResortSwapCandidate(
            PlayerInventory playerInventory, ItemStack templateStack, int targetIndex, int to) {
        int fallback = -1;
        for (int i = targetIndex + 1; i < to; ++i) {
            ItemStack candidate = playerInventory.getStack(i);
            if (!canReplenish(templateStack, candidate)) {
                continue;
            }
            return i;
        }
        return fallback;
    }

    private void registerReplenishCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name("replenish").build();
        SimpleCommandArgs.Argument kitNameArgument = SimpleCommandArgs.argumentBuilder()
                .name("name")
                .tabSupplier(this::streamKitNames)
                .build();
        main.subBuilder(SubCommand.taskBuilder())
                .name("import")
                .helper("message.command.kit.import.help")
                .arg(SimpleCommandArgs.argumentBuilder().name("name").build())
                .post(e -> e.executor(CommandContext.execute(this::onImportKitCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("importitem")
                .helper("message.command.kit.importitem.help")
                .arg(SimpleCommandArgs.argumentBuilder().name("name").build())
                .post(e -> e.executor(CommandContext.execute(this::onImportItemKitCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("request")
                .helper("message.command.kit.request.help")
                .arg(kitNameArgument)
                .post(e -> e.executor(CommandContext.execute(this::onRequestKitCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("clearRequest")
                .helper("message.command.kit.clear-request.help")
                .post(e -> e.executor(CommandContext.execute(this::onClearRequestCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("start")
                .helper("message.command.kit.start.help")
                .post(e -> e.executor(CommandContext.run(this::onStartCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("edit")
                .helper("message.command.kit.edit.help")
                .post(e -> e.executor(CommandContext.execute(this::onEditCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("give")
                .helper("message.command.kit.give.help")
                .arg(kitNameArgument)
                .post(e -> e.executor(CommandContext.execute(this::onGiveCommand)))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("giveshulker")
                .helper("message.command.kit.giveshulker.help")
                .arg(kitNameArgument)
                .post(s -> s.executor(CommandContext.execute(this::onGiveShulkerCommand)))
                .complete();
        ;
        mainCommand.registerSub(new BridgeSubCommand("kit", main));
    }

    private boolean onImportKitCommand(CommandExecution sender, ArgumentInputStream streamArgs) {
        if (mc.player == null) {
            sender.sendMessage("&c当前不在游戏内");
            return true;
        }
        String name = streamArgs.nextNonnullString();
        if (findKitByName(name) != null) {
            sender.sendMessage("&cKit已存在: " + name);
            return true;
        }
        Kit kit = saveInventory(name, mc.player.getInventory(), InventoryUtils.getPlayerInvSize(), Rule.DEFAULT);
        appendKit(kit);
        sender.sendMessage("&a已导入当前背包为Kit: " + name);
        return true;
    }

    private boolean onImportItemKitCommand(CommandExecution sender, ArgumentInputStream streamArgs) {
        String name = streamArgs.nextNonnullString();
        if (findKitByName(name) != null) {
            sender.sendMessage("&cKit已存在: " + name);
            return true;
        }
        ItemStack stack = ScreenUtils.getSelectingOrHandItem();
        if (stack == null || stack.isEmpty()) {
            sender.sendMessage("&c当前没有可导入的物品");
            return true;
        }
        ContainerComponent component = stack.get(DataComponentTypes.CONTAINER);
        if (component == null) {
            sender.sendMessage("&c当前物品不包含容器内容");
            return true;
        }
        Inventory supplyInventory = loadShulkerAsSupplyInventory(component);
        Kit kit = saveInventory(name, supplyInventory, supplyInventory.size(), Rule.DEFAULT);
        appendKit(kit);
        sender.sendMessage("&a已导入当前容器物品为Kit: " + name);
        return true;
    }

    private boolean onRequestKitCommand(CommandExecution sender, ArgumentInputStream streamArgs) {
        String name = streamArgs.nextNonnullString();
        Kit kit = findKitByName(name);
        if (kit == null) {
            sender.sendMessage("&c未找到Kit: " + name);
            return true;
        }
        this.requestKit = kit;
        sender.sendMessage("&a已设置本次补给Kit: " + name);
        return true;
    }

    private void onClearRequestCommand(CommandExecution sender) {
        this.requestKit = null;
        sender.sendMessage("&a已清除临时补给Kit");
    }

    private boolean onStartCommand(PlayerEntity ignored, ArgumentInputStream streamArgs) {
        replenishCurrentKit();
        return true;
    }

    private void onEditCommand(CommandExecution sender) {
        Tasks.scheduleDelayed(this::openKitEditScreen, 1);
        sender.sendMessage("&a正在打开Kit编辑界面");
    }

    private void onGiveCommand(CommandExecution sender, ArgumentInputStream streamArgs) {
        String name = streamArgs.nextNonnullString();
        Kit kit = findKitByName(name);
        if (kit == null) {
            sender.sendMessage("&c未找到Kit: " + name);
            return;
        }
        if (mc.interactionManager.getCurrentGameMode().isCreative()) {
            Rule rule = kit.rule();
            Inventory inventory = createInventory(kit);
            for (var i = rule.from(); i < rule.to(); ++i) {
                InvTasks.setCreativeInventory(inventory.getStack(i), i);
            }
        } else {
            sender.sendMessage("&c当前并不处于创造模式,无法使用该功能");
        }
    }

    private void onGiveShulkerCommand(CommandExecution sender, ArgumentInputStream streamArgs) {
        String name = streamArgs.nextNonnullString();
        Kit kit = findKitByName(name);
        if (kit == null) {
            sender.sendMessage("&c未找到Kit: " + name);
            return;
        }
        if (mc.interactionManager.getCurrentGameMode().isCreative()) {
            Rule rule = kit.rule();
            Inventory inventory = createInventory(kit);
            List<ItemStack> stacks = new ArrayList<>();
            Inventory shulkerInventory = new MutableInventory(27, stacks);
            int from = rule.from();
            for (var i = from; i < rule.to(); ++i) {
                int idx = i - from;
                if (idx >= shulkerInventory.size()) break;
                shulkerInventory.setStack(idx, inventory.getStack(i));
            }
            ContainerComponent shulkerComponent = ContainerComponent.fromStacks(stacks);
            ItemStack newShulker = new ItemStack(Items.SHULKER_BOX);
            newShulker.set(DataComponentTypes.CONTAINER, shulkerComponent);
            newShulker.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            InvTasks.creativeGive(newShulker, 1);
        } else {
            sender.sendMessage("&c当前并不处于创造模式,无法使用该功能");
        }
    }

    private Stream<String> streamKitNames() {
        return kitMap.kitList().stream().map(Kit::name);
    }

    private Kit findKitByName(String name) {
        return kitMap.kitList().stream()
                .filter(s -> Objects.equals(s.name(), name))
                .findFirst()
                .orElse(null);
    }

    private void appendKit(Kit kit) {
        List<Kit> newKits = new ArrayList<>(kitMap.kitList());
        newKits.add(kit);
        updateKitMap(new KitList(kitMap.index(), newKits));
    }

    public static class Transaction {
        static final int STAGE_REORDER_INVENTORY = 0;
        static final int STAGE_FIND_SHULKER = 1;
        static final int STAGE_USE_ENDER_CHEST = 2;
        static final int STAGE_SWITCH_HOT_BAR = 3;
        static final int STAGE_PLACE_SHULKER = 4;
        static final int STAGE_APPLY_INV = 5;
        static final int STAGE_POST_REORDER_INVENTORY = 6;
        static final int STAGE_COMPLETE = 7;
        static final int STAGE_FAIL = 8;
        BlockPos currentReplenishPos;
        String name;
        Rule rule = Rule.DEFAULT;
        Inventory inventory;
        Inventory viewInventory;
        IndexEntry<ItemStack> findResult;
        boolean enableUseEnderChest = true;
        boolean useEnderChest = false;
        int leftEmptySlotForShulker = -1;
        Map<ReplenishTemplate, Integer> toReplenishSummary = new LinkedHashMap<>();
        public CompletableFuture<Transaction> future = new CompletableFuture<>();
        int stage;

        private void createSummary() {
            int from =
                    Math.clamp(Math.clamp(rule.from(), 0, inventory.size()), 0, InventoryUtils.getPlayerBackpackSize());
            int to = Math.clamp(Math.clamp(rule.to(), 0, inventory.size()), 0, InventoryUtils.getPlayerBackpackSize());
            this.rule = this.rule.withFrom(from).withTo(to);
            this.viewInventory = InventoryUtils.createSubInventoryView(inventory, from, to);
            toReplenishSummary = new LinkedHashMap<>();
            for (int i = from; i < to; ++i) {
                var re = inventory.getStack(i);
                if (!re.isEmpty()) {
                    ReplenishTemplate sample = ReplenishTemplate.of(re);
                    toReplenishSummary.merge(sample, re.getCount(), Integer::sum);
                }
            }
            Inventory playerInventory = mc.player.getInventory();
            for (int i = from; i < to; ++i) {
                var re = playerInventory.getStack(i);
                if (!re.isEmpty()) {
                    ReplenishTemplate sample = ReplenishTemplate.of(re);
                    Integer value = toReplenishSummary.get(sample);
                    if (value != null) {
                        int newValue = value - re.getCount();
                        if (newValue <= 0) {
                            toReplenishSummary.remove(sample);
                        } else {
                            toReplenishSummary.put(sample, newValue);
                        }
                    }
                }
            }
        }

        public void setKit(@Nonnull Kit kit) {
            this.name = kit.name();
            this.rule = kit.rule();
            this.inventory = createInventory(kit);
            createSummary();
        }

        public void setInventory(@Nonnull Inventory inventory, String name) {
            this.name = name;
            this.rule = new Rule(Type.GREEDY, 0, inventory.size(), false);
            this.inventory = inventory;
            createSummary();
        }

        public void setUseEnderChest(boolean use) {
            this.useEnderChest = false;
            this.enableUseEnderChest = use;
        }

        public void complete(boolean success) {
            if (!isCompleted()) {
                stage = success ? STAGE_COMPLETE : STAGE_FAIL;
                future.complete(this);
            }
        }

        public boolean isCompleted() {
            return stage >= STAGE_COMPLETE;
        }
    }

    public void replenishCurrentKit() {
        if (checkNull()) return;
        if (this.transaction != null) {
            if (!this.transaction.isCompleted()) {
                logI18N("message.kit-manager.kit-replenish.request.blocked");
                return;
            }
            clearReplenishingTask();
        }
        clearReplenishingTask();
        shulkerBoxRequest = null;
        enderChestRequest = false;
        timeoutEnderChest = -1;
        Kit kit = this.requestKit != null ? this.requestKit : this.kitMap.getDefaultKit();
        if (kit != null) {
            logI18N("message.kit-manager.kit-replenish.request.success", kit.name());
            this.transaction = new Transaction();
            this.transaction.setKit(kit);
            this.transaction.setUseEnderChest(enableEnder.get());
        } else {
            logI18N("message.kit-manager.kit-replenish.request.failure");
        }
    }

    public boolean openSelectedShulkerBox() {
        if (mc.player != null && shulkerBoxRequest == null) {
            Slot stack = ScreenUtils.getSelectingOrHandSlot();
            if (stack != null && isShulker(stack.getStack())) {
                setShulkerBoxRequest(new ShulkerBoxRequest(
                        Optional.empty(), Optional.of(stack), Consumers.nop(), Runnables.doNothing(), false));
                return true;
            }
        }
        return false;
    }

    public Stream<Pair<BlockPos, BlockHitResult>> searchAvailableShulkerPosition() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d playerFeet = mc.player.getPos();
        Direction playerLook = mc.player.getFacing();
        return blockSeq.stream()
                .map(playerPos::add)
                .map(s -> {
                    BlockState state = mc.world.getBlockState(s);
                    if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
                        return null;
                    }
                    if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), s)) {
                        return null;
                    }
                    List<FlagEntry<BlockHitResult>> placeHitResult =
                            InteractionTasks.getAllPlaceSupportingResult(playerFeet, s, playerLook, false, false);
                    if (placeHitResult.isEmpty()) {
                        return null;
                    }
                    return placeHitResult.stream()
                            .filter(hitResult -> {
                                if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                                        && InteractExtra.INSTANCE.isWithinInteractRange(
                                                mc.player.getPos(),
                                                hitResult.val().getBlockPos())) {
                                    BlockState targetState = InteractUtils.getBlockPlacement(
                                            Blocks.SHULKER_BOX, mc.player, mc.world, hitResult.val());
                                    if (targetState != null) {
                                        // can open
                                        return InteractUtils.canShulkerOpen(mc.world, s, targetState);
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            })
                            .findFirst()
                            .map(hit -> Pair.of(s, hit.val()))
                            .orElse(null);
                })
                .filter(Objects::nonNull);
    }

    public Stream<Pair<BlockPos, BlockHitResult>> searchAvailableChestLikePosition(boolean ender) {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d currentPos = mc.player.getPos();
        Direction playerLook = mc.player.getFacing();
        return blockSeq.stream()
                .map(playerPos::add)
                .map(s -> {
                    BlockState state = mc.world.getBlockState(s);
                    if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
                        return null;
                    }
                    if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), s)) {
                        return null;
                    }
                    FlagEntry<BlockHitResult> hitResult =
                            InteractionTasks.getPlaceSupportingResult(currentPos, s, playerLook, false, false);
                    if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                            && InteractExtra.INSTANCE.isWithinInteractRange(
                                    mc.player.getPos(), hitResult.val().getBlockPos())) {
                        BlockState expectedState = InteractUtils.getBlockPlacement(
                                ender ? Blocks.ENDER_CHEST : Blocks.CHEST, mc.player, mc.world, hitResult.val());
                        if (expectedState != null
                                && (ender
                                        ? InteractUtils.canEnderChestOpen(mc.world, s)
                                        : InteractUtils.canChestOpen(mc.world, s, expectedState))) {
                            return Pair.of(s, hitResult.val());
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(s -> s.getFirst().getSquaredDistance(playerPos)));
    }

    public BlockPos findCurrentOpenEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        return blockSeq.stream()
                .map(playerPos::add)
                .map(s -> {
                    if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), s)) {
                        return null;
                    }
                    BlockState state = mc.world.getBlockState(s);
                    if (state.getBlock() == Blocks.ENDER_CHEST) {
                        if (InteractUtils.canEnderChestOpen(mc.world, s)) {
                            return s;
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(s -> s.getSquaredDistance(playerPos)))
                .orElse(null);
    }

    public final ModulePath kitRoot = makePath(Configs.INV_CONFIG, "kit-manager");
    public FileStorage fileStorage;
    public KitList kitMap;

    {
        fileStorage = FileManager.getInstance().getInternalStorage("kit.nbt");
        kitMap = fileStorage.read(KitList.CODEC, () -> new KitList());
    }

    public void updateKitMap(KitList list) {
        kitMap = list;
        fileStorage.write(KitList.CODEC, kitMap);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        SubScreenWidget kitEditEntry = new SubScreenWidget(0, dblank, dx, dy);
        kitEditEntry.addDrawableChild(createRefKeyLabel(
                () -> Text.translatable("widget.kit-manager.kit-save-map"),
                () -> ChatUtils.parseTooltipsTranslation("widget.kit-manager.kit-save-map.tooltips", "暂无介绍"),
                indexWidth,
                dy));
        kitEditEntry.addDrawableChild(createExecuteButton(
                "widget.kit-manager.open-kit-list",
                ButtonAction.run(this::openKitEditScreen),
                indexWidth + blankWidth,
                0,
                dx - indexWidth - blankWidth,
                dy));
        acceptor.accept(kitEditEntry);
        acceptor.accept(ExecutableWidget.instance(0, dblank, dx, dy)
                .setElementHandler(new ButtonElement(
                        el -> {
                            Kit kit = kitMap.getDefaultKit();
                            if (kit != null) {
                                return Text.translatable(
                                        "widget.kit-manager.kit-default.present",
                                        kit.name(),
                                        kit.rule().type().getDisplay());
                            } else {
                                return Text.translatable("widget.kit-manager.kit-default.absent");
                            }
                        },
                        ButtonAction.run(this::openKitEditScreen))));
        acceptor.accept(createTitleLabel("widget.kit-manager.command", 0, dblank, dx, dy));
        acceptor.accept(createTitleLabel("widget.interact.interact-all.use-argument", 0, dblank, dx, dy));
    }

    public void openKitEditScreen() {
        List<MutableRecord> records = kitMap.kitList().stream()
                .map(s -> MutableRecord.of(Kit.KEYS, s))
                .collect(Collectors.toCollection(ArrayList::new));
        int index = kitMap.index();
        MutableObject<MutableRecord> indexOf =
                new MutableObject<>((index >= 0 && index < records.size()) ? records.get(index) : null);
        ListEntryWidgetController mutableList = ListEntryWidgetController.mutable(
                records,
                () -> MutableRecord.of(Kit.KEYS, Kit.EMPTY),
                (v) -> createEditWidget(indexOf, records, v),
                45,
                250);
        ListModifyWidget listWidget = new ListModifyWidget(mutableList, 0, 0, 330, 260);
        ConfirmingWidgetScreen confirmScreen = new ConfirmingWidgetScreen(
                Text.translatable("widget.kit-manager.open-kit-list.title"), listWidget, () -> true, () -> {
                    List<Kit> newKits =
                            records.stream().map(s -> s.toRecord(Kit.class)).toList();
                    int newIndex = records.indexOf(indexOf.getValue());
                    updateKitMap(new KitList(newIndex, newKits));
                });
        confirmScreen.access().openFromCurrent();
    }

    private SubScreenWidget createEditWidget(
            MutableObject<MutableRecord> indexOf, List<MutableRecord> mutableList, MutableRecord record) {
        SubScreenWidget widget = new SubScreenWidget(0, 0, 250, 45);
        widget.addDrawableChild(new ExecutableWidget(0, 12, 21, 21)
                .setElementHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.run(() -> {
                            if (indexOf.getValue() != record) {
                                indexOf.setValue(record);
                            } else {
                                indexOf.setValue(null);
                            }
                        }))
                        .setActivePredicate(el -> indexOf.getValue() == record)));
        final String nameKey = Kit.KEYS.get(0);
        AttrKeyValue<String> name =
                AttrKeyValue.str("widget.kit-manager.open-kit-list.name", record.getOrPut(nameKey, ""));
        name.addValidator(s -> {
            for (var re : mutableList) {
                if (re != record && Objects.equals(re.get(nameKey), s)) {
                    return false;
                }
            }
            return true;
        });
        name.addListener(s -> record.set(nameKey, s));
        widget.addDrawableChild(name.generateKeyValueInput(30, 1, 30, 0, 60, 20));
        String maxSizeKey = Kit.KEYS.get(2);
        AttrKeyValue<Integer> maxSize =
                AttrKeyValue.integer("widget.kit-manager.open-kit-list.max-size", record.getOrPut(maxSizeKey, 0));
        maxSize.addValidator(Configs.INT_NONNEGATIVE);
        maxSize.addListener(s -> record.set(maxSizeKey, s));

        widget.addDrawableChild(maxSize.generateKeyValueInput(120, 1, 30, 0, 20, 20));

        String ruleKey = Kit.KEYS.get(3);
        Rule currentRule = record.getOrPut(ruleKey, Rule.DEFAULT);
        AttrKeyValue<Type> rulesType =
                AttrKeyValue.enumMap("widget.kit-manager.open-kit-list.rule.type", currentRule.type(), Type.class);
        rulesType.addListener(s -> record.set(ruleKey, record.<Rule>get(ruleKey).withType(s)));
        AttrKeyValue<Integer> ruleMin =
                AttrKeyValue.integer("widget.kit-manager.open-kit-list.rule.from", currentRule.from());
        ruleMin.addListener(s -> record.set(ruleKey, record.<Rule>get(ruleKey).withFrom(s)));
        ruleMin.addValidator(Configs.intRange(0, InventoryUtils.getPlayerInvSize()));
        AttrKeyValue<Integer> ruleMax =
                AttrKeyValue.integer("widget.kit-manager.open-kit-list.rule.to", currentRule.to());
        ruleMax.addListener(s -> record.set(ruleKey, record.<Rule>get(ruleKey).withTo(s)));
        ruleMax.addValidator(Configs.intRange(0, InventoryUtils.getPlayerInvSize()));
        Runnable reload = () -> {
            name.valueChangeInternal(null, record.getOrPut(nameKey, ""));
            maxSize.valueChangeInternal(null, record.getOrPut(maxSizeKey, 0));
            Rule newRule = record.getOrPut(ruleKey, Rule.DEFAULT);
            rulesType.valueChangeInternal(null, newRule.type());
            ruleMin.valueChangeInternal(null, newRule.from());
            ruleMax.valueChangeInternal(null, newRule.to());
        };
        widget.addDrawableChild(rulesType.generateKeyValueInput(30, 24, 30, 0, 60, 20));
        widget.addDrawableChild(ruleMin.generateKeyValueInput(120, 24, 30, 0, 20, 20));
        widget.addDrawableChild(ruleMax.generateKeyValueInput(170, 24, 30, 0, 20, 20));
        widget.addDrawableChild(new ExecutableWidget(220, 24, 30, 20)
                .setElementHandler(new ButtonElement(
                                (el -> record.<Rule>get(ruleKey).dump()
                                        ? Text.translatable("widget.kit-manager.open-kit-list.rule.dump.true")
                                        : Text.translatable("widget.kit-manager.open-kit-list.rule.dump.false")),
                                ButtonAction.run(() -> record.<Rule>update(ruleKey, s -> s.withDump(!s.dump()))))
                        .withTooltips(TooltipHandler.of(
                                () -> record.<Rule>get(ruleKey).dump()
                                        ? ChatUtils.parseTooltipsTranslation(
                                                "widget.kit-manager.open-kit-list.rule.dump.true.tooltips", "")
                                        : ChatUtils.parseTooltipsTranslation(
                                                "widget.kit-manager.open-kit-list.rule.dump.false.tooltips", "")))));
        Function<Kit, Runnable> openViewScreen = (temporaryKit) -> () -> {
            Inventory mutableInventory = createInventory(temporaryKit);
            InventoryViewScreen screen = new InventoryViewScreen(
                    mutableInventory, Text.literal(temporaryKit.name()), new ItemStack(Items.SHULKER_BOX), true);
            screen.access().addCloseFuture(() -> {
                Kit saveKit = saveInventory(
                        temporaryKit.name(),
                        mutableInventory,
                        mutableInventory.size(),
                        record.getOrPut(ruleKey, Rule.DEFAULT));
                MutableRecord newRecord = MutableRecord.of(Kit.KEYS, saveKit);
                record.replaceMap(newRecord);
                reload.run();
            });
            screen.access().openFromCurrent();
        };
        if (mc.getNetworkHandler() != null) {
            widget.addDrawableChild(ExecutableWidget.instance(170, 1, 40, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items")),
                            ButtonAction.run(() -> {
                                Kit temporaryKit = record.toRecord(Kit.class);
                                openViewScreen.apply(temporaryKit).run();
                            }))));
            widget.addDrawableChild(ExecutableWidget.instance(210, 1, 40, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items.import")),
                            ButtonAction.run(() -> {
                                if (mc.player != null) {
                                    Kit saveKit = saveInventory(
                                            name.getOriginValue(),
                                            mc.player.getInventory(),
                                            InventoryUtils.getPlayerInvSize(),
                                            record.getOrPut(ruleKey, record.getOrPut(ruleKey, Rule.DEFAULT)));
                                    openViewScreen.apply(saveKit).run();
                                }
                            }))));
        } else {
            widget.addDrawableChild(ExecutableWidget.instance(180, 1, 70, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items.error")),
                            ButtonAction.empty())));
        }
        return widget;
    }

    public static Inventory createInventory(Kit kit) {
        List<IndexEntry<ItemStack>> list = kit.toItem();
        int maxSize = kit.maxSize();
        ItemStack[] stackArray = new ItemStack[maxSize];
        Arrays.fill(stackArray, ItemStack.EMPTY);
        for (var re : list) {
            if (re.index() >= 0 && re.index() < maxSize) {
                stackArray[re.index()] = re.val();
            }
        }

        return InventoryUtils.createInventory(stackArray);
    }

    private static final List<ItemStack> EMPTY_SLOTS = Collections.nCopies(9, ItemStack.EMPTY);

    public static Inventory loadShulkerAsSupplyInventory(ContainerComponent component) {
        List<ItemStack> stacks = new ArrayList<>(EMPTY_SLOTS);
        component.stream().forEach(stacks::add);
        return InventoryUtils.createInventory(stacks);
    }

    public Kit saveInventory(String name, Inventory inventory, int maxSize, Rule type) {
        List<IndexEntry<ItemStack>> stack = InventoryUtils.getInventoryEntries(inventory);
        return Kit.fromItem(name, stack, maxSize, type);
    }

    private static final List<ComponentType<?>> MUST_MATCH = List.of(
            DataComponentTypes.ENCHANTMENTS,
            DataComponentTypes.STORED_ENCHANTMENTS,
            DataComponentTypes.UNBREAKABLE,
            DataComponentTypes.FIREWORKS,
            DataComponentTypes.FIREWORK_EXPLOSION,
            DataComponentTypes.CONSUMABLE,
            DataComponentTypes.FOOD,
            DataComponentTypes.USE_EFFECTS,
            DataComponentTypes.DEATH_PROTECTION,
            DataComponentTypes.POTION_CONTENTS,
            DataComponentTypes.POTION_DURATION_SCALE,
            DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
            DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER,
            DataComponentTypes.PROFILE,
            DataComponentTypes.CUSTOM_NAME,
            DataComponentTypes.LORE,
            DataComponentTypes.ATTRIBUTE_MODIFIERS);

    public static boolean canReplenish(ItemStack template, ItemStack realStack) {
        return template.isOf(realStack.getItem())
                && MUST_MATCH.stream().allMatch(s -> Objects.equals(template.get(s), realStack.get(s)));
    }

    public static record Kit(String name, List<IndexEntry<NbtCompound>> itemNBT, int maxSize, Rule rule) {
        public static final Kit EMPTY = new Kit("", List.of(), 0, new Rule());

        public static List<String> KEYS = List.of("name", "item-nbt", "max-size", "rule");
        public static Codec<Kit> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.STRING.fieldOf("name").forGetter(Kit::name),
                        Codec.list(InventoryUtils.NBT_STACK_WITH_SLOT_CODEC)
                                .fieldOf("items")
                                .forGetter(Kit::itemNBT),
                        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("maxSize").forGetter(Kit::maxSize),
                        Rule.MAP_CODEC.forGetter(Kit::rule))
                .apply(oinstance, Kit::new));

        public static Kit fromItem(String name, List<IndexEntry<ItemStack>> itemNBT, int maxSize, Rule rule) {
            return new Kit(
                    name,
                    itemNBT.stream()
                            .filter(s -> !s.val().isEmpty())
                            .map(s -> {
                                return new IndexEntry<>(
                                        s.index(), VItem.getInstance().toNbt(s.val(), ItemStackUtils.registry()));
                            })
                            .toList(),
                    maxSize,
                    rule);
        }

        public List<IndexEntry<ItemStack>> toItem() {
            return itemNBT.stream()
                    .map(s -> {
                        return new IndexEntry<>(
                                s.index(), VItem.getInstance().fromNbt(s.val(), ItemStackUtils.registry()));
                    })
                    .toList();
        }
    }

    public static record KitList(int index, List<Kit> kitList) {
        public KitList() {
            this(-1, List.of());
        }

        public static Codec<KitList> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.INT.fieldOf("index").forGetter(KitList::index),
                        Codec.list(Kit.CODEC).fieldOf("kit-map").forGetter(KitList::kitList))
                .apply(oinstance, KitList::new));

        public Kit getDefaultKit() {
            if (index >= 0 && index < kitList.size()) {
                return kitList.get(index);
            } else {
                return null;
            }
        }
    }

    public static enum Type implements Displayable {
        AUTO,
        STRICT,
        ORDERED,
        GREEDY;

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "widget.kit-manager.open-kit-list.rule.type." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    @With
    public static record Rule(Type type, int from, int to, boolean dump) {
        public static Rule DEFAULT = new Rule();
        static final MapCodec<Rule> MAP_CODEC = RecordCodecBuilder.mapCodec(oinstance -> oinstance
                .group(
                        CodecUtils.enumCodec(Type.class)
                                .optionalFieldOf("type", Type.GREEDY)
                                .forGetter(Rule::type),
                        Codec.INT.optionalFieldOf("from", 9).forGetter(Rule::from),
                        Codec.INT.optionalFieldOf("to", 36).forGetter(Rule::to),
                        Codec.BOOL.optionalFieldOf("dump", false).forGetter(Rule::dump))
                .apply(oinstance, Rule::new));

        public Rule() {
            this(Type.GREEDY, 9, 36, false);
        }
    }

    public static record ReplenishTemplate(ItemStack stackTemplate, int hash) {
        public ReplenishTemplate(ItemStack stack) {
            this(stack.copyWithCount(1), hashTemplate(stack));
        }

        public static ReplenishTemplate of(ItemStack stack) {
            return new ReplenishTemplate(stack);
        }

        private static int hashTemplate(ItemStack stack) {
            int code = stack.getItem().hashCode();
            for (var re : MUST_MATCH) {
                var r = stack.get(re);
                code = 31 * code + (r == null ? 0 : r.hashCode());
            }
            return code;
        }

        public boolean match(ItemStack stack) {
            return canReplenish(stackTemplate, stack);
        }

        @Override
        public boolean equals(Object o) {
            return o == this || (o instanceof ReplenishTemplate temp && match(temp.stackTemplate));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    public static enum Choice implements ConfigEnum {
        SLOT_MATCH,
        ITEM_EXIST,
        NUM_MATCH;
    }

    @With
    public static record ShulkerBoxRequest(
            Optional<Pair<BlockPos, BlockHitResult>> placePos,
            Optional<Slot> playerScreenSlot,
            Consumer<ScreenHandler> successCallback,
            Runnable failureCallback,
            boolean useZeroTick) {}
}
