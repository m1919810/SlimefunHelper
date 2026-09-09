package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.function.Predicate;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.catchers.PacketCatcherImpl;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.algorithms.StateMachine;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class Airplace extends BaseModule {
    public final ModulePath interactionTweaks = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks");
    public final ModulePath airPlace = interactionTweaks.add("air-place");

    public Airplace() {
        super("Airplace");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(airPlace.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(airPlace.add("hotkey"), new MultiKeyBind(), airPlace.add("enable"))
            .build();

    public final DoubleRef range = builder(airPlace.add("range"), DoubleRef.TYPE)
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0, 10000))
            .build();

    public final FlagRef render = flagBuilder(airPlace.add("render")).build();
    // todo: add to switch mode
    public final EnumRef<Mode> enableAirWall = builder(airPlace.add("mode"), Mode.class)
            .defaultValue(Mode.VANILLA)
            .updateListener(s -> {
                onSwitch();
            })
            .build();

    public final IntRef maxBatch = intBuilder(airPlace.add("max-batch-place"))
            .defaultValue(64)
            .validator(Configs.INT_POSITIVE)
            .show(() -> enableAirWall.get().isIn(Mode.GRIM_FAST_GHOST_BLOCK_WALL))
            .build();

    public final IntRef invSleepTick = intBuilder(airPlace.add("inv-sleep-tick"))
            .defaultValue(2)
            .validator(Configs.INT_POSITIVE)
            .show(() -> enableAirWall.get().isIn(Mode.GRIM_FAST_GHOST_BLOCK_WALL))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getItemUseAction(), this::onInteract);
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(RenderListener.getRender3DEvent(), this::onRenderPos);
        registerListener(
                PacketManager.getPacketQueueEvent().getChannel(NetworkSide.CLIENTBOUND), this::onPacketAcceptQueue);
        registerListener(Listener.getPostTick(), this::onPostTick);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onShutdownQueue);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearCurrentAirWall();
    }

    public void onInteract(Event<HitResult> event) {
        if (!event.isCancelled() && enable.get()) {
            Hand hand = event.getArgs(0);
            ItemStack stack = mc.player.getStackInHand(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                HitResult hitResult = event.context();
                if (hitResult.getType() == HitResult.Type.MISS) {
                    HitResult result = getCameraEntity().raycast(range.get(), 0, false);
                    if (result.getType() == HitResult.Type.MISS && result instanceof BlockHitResult block) {
                        switch (enableAirWall.get()) {
                            case VANILLA -> {
                                BlockHitResult newResult = new BlockHitResult(
                                        block.getPos(), block.getSide(), block.getBlockPos(), block.isInsideBlock());
                                event.context(newResult);
                                return;
                            }
                            case GRIM_GHOST_BLOCK_WALL -> {
                                onGrimAirWall(block);
                                return;
                            }
                            case GRIM_FAST_GHOST_BLOCK_WALL -> {
                                onGrimFastWall(block, hand);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void onShutdownQueue(Event<Void> event) {
        clearCurrentAirWall();
    }

    public void onSwitch() {
        clearCurrentAirWall();
    }

    public void clearCurrentAirWall() {
        targetPos = null;
        lastDelayTick = 0;
    }

    public void onGrimAirWall(BlockHitResult hitResult) {
        clearCurrentAirWall();
        if (DisablerManager.INSTANCE.isGrimSelfCheckDisabled()) {
            targetPos = hitResult.getBlockPos();
        } else {
            Debug.chat("[AirWall] 当前暂未禁用GrimSelfCheck,无法执行");
        }
    }

    BlockPos targetPos = null;
    int lastDelayTick = 0;

    public void flush() {
        lastDelayTick--;
        if (targetPos == null && lastDelayTick == 0) {
            PacketManager.flushInBound();
        } else {
            if (lastDelayTick > 3) lastDelayTick = 3;
            PacketManager.flushInBound((packetStorage -> {
                long timeMS = packetStorage.timestampMS();
                long currentMs = System.currentTimeMillis();
                if (currentMs > timeMS + 50L) {
                    return PacketManager.FlushAction.FLUSH;
                }
                return PacketManager.FlushAction.QUEUE;
            }));
        }
    }

    public void onPacketAcceptQueue(Event<PacketStorage> packet) {
        if (enable.get() && targetPos != null) {
            var pkt = packet.context;

            if (PacketManager.isAsyncOrNotTransactionS2CPacket(pkt.packetType())) {
                return;
            }
            lastDelayTick += 1;
            packet.cancel();
        }
    }

    public void onPostTick(Event<Void> event) {
        if ((lastDelayTick > 0)) {
            flush();
        }
    }

    public void onInput(Event<Void> event) {
        onInputGrimWall();
        onInputFastWall();
    }

    public void onInputGrimWall() {
        if (enable.get()
                && targetPos != null
                && mc.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof BlockItem block
                && block != Items.AIR
                && targetPos.toCenterPos().subtract(mc.player.getEyePos()).horizontalLengthSquared()
                        <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {
            for (var i = 1; i < 256; ++i) {
                BlockPos checkPos = targetPos.add(0, -i, 0);
                BlockState state = mc.world.getBlockState(checkPos);
                if (!state.isAir() && !state.isLiquid()) {
                    if (i == 1) targetPos = null;
                    var ppp = checkPos;
                    RenderTasks.drawBox(Box.from(new BlockBox(ppp)), 50, Color.MAGENTA);
                    InteractionTasks.interactBlock(
                            Hand.MAIN_HAND,
                            new BlockHitResult(ppp.toBottomCenterPos().add(0, 1, 0), Direction.UP, ppp, false),
                            false);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    // work by magic
                    // work by placeAfterPlace bypass
                    if (!PlayerInputUtils.of(mc.options).hasWASDMovement()) {
                        FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    }
                    return;
                }
            }
        } else {
            targetPos = null;
        }
    }

    FastPlaceTaskInfo currentTask;

    public static record FastPlaceTaskInfo(
            BlockPos.Mutable startPos,
            BlockPos targetPos,
            int itemCount,
            ItemStack item,
            int selectedSlot,
            Hand hand,
            int way) {}

    int startWaitTick = 0;
    static final int FAST_STATE_NONE = 0;
    static final int FAST_STATE_PLACE = 1;
    static final int FAST_STATE_WAIT_SLOT_UPDATE = 2;
    static final int FAST_STATE_WAIT_300MS = 3;
    StateMachine stateMachine;
    // 状态机
    // place -> send swap -> wait response -> continue ->
    // if reach -> wait tick = 7 ~ 300ms -> place real
    public void clearFastWall() {
        currentTask = null;
        stateMachine = null;
    }

    public boolean isState() {
        return currentTask != null;
    }

    public void onGrimFastWall(BlockHitResult hitResult, Hand hand) {
        clearFastWall();
        if (DisablerManager.INSTANCE.isGrimSelfCheckDisabled()) {
            if (!DisablerManager.INSTANCE.autoFlushPlaceQueue.get()) {
                Debug.chat(
                        "[AirWall] 请先在",
                        Text.translatable("config.index.disablers"),
                        "中启用配置项: ",
                        Text.translatable("disablers.auto-flush-multi-place-queue"));
                return;
            }
            ItemStack usingItem = mc.player.getStackInHand(hand);
            if (usingItem.isEmpty()) return;
            if (usingItem.getCount() < 2) {
                Debug.chat("[AirWall] 手上物品太少,无法执行,该模式下手上尽可能有足够多的方块");
                return;
            }
            int recommendCnt = Math.min(maxBatch.get(), 48);
            if (usingItem.getCount() < recommendCnt) {
                Debug.chat("[AirWall] 提示: 我们推荐该模式手上最好有足够多(>= %d)的方块,当前数量可能会导致放置较慢".formatted(recommendCnt));
            }
            BlockPos startPos = hitResult.getBlockPos();
            Vec3d centerPos = startPos.toCenterPos();
            // under eye -> from down, else from up
            int way = centerPos.y < mc.player.getEyePos().y ? -1 : 1;
            BlockPos fastStartPos = null;
            for (var i = 1; i < 256; ++i) {
                BlockPos checkPos = startPos.add(0, way * i, 0);
                BlockState state = mc.world.getBlockState(checkPos);
                if (!state.isAir() && !state.isLiquid()) {
                    fastStartPos = checkPos;
                    break;
                }
            }
            if (fastStartPos != null) {
                currentTask = new FastPlaceTaskInfo(
                        fastStartPos.mutableCopy(),
                        startPos,
                        usingItem.getCount(),
                        usingItem.copy(),
                        InventoryUtils.getSelectedSlot(),
                        hand,
                        way);
                stateMachine = createStateMachine();
            }
        } else {
            Debug.chat("[AirWall] 当前暂未禁用GrimSelfCheck,无法执行");
        }
    }

    public void onInputFastWall() {
        if (enable.get() && currentTask != null && stateMachine != null) {
            BlockPos targetPos = currentTask.targetPos;
            ItemStack stack = currentTask.item;
            Hand hand = currentTask.hand;
            ItemStack stackInHand = mc.player.getStackInHand(hand);
            if (ItemStack.areItemsEqual(stackInHand, stack)) {
                if (targetPos.toCenterPos().subtract(mc.player.getEyePos()).horizontalLengthSquared()
                        <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {
                    stateMachine.step();
                } else {
                    Debug.chat("[AirWall] 你移动的位置太多了, 终止任务");
                    currentTask = null;
                    stateMachine = null;
                }
            } else {
                Debug.chat("[AirWall] 手上的物品被切换了，终止任务");
                currentTask = null;
                stateMachine = null;
            }
        }
    }

    public StateMachine createStateMachine() {
        return new StateMachine(
                FAST_STATE_PLACE,
                this::onUpdate,
                (state) -> FAST_STATE_NONE,
                this::onPlace,
                this::onWaitSlotUpdate,
                this::onWait300MS);
    }

    public int onUpdate(StateMachine machine, int t) {
        if (currentTask == null) {
            stateMachine = null;
            machine.markForEndState();
            return FAST_STATE_NONE;
        }
        return t;
    }

    public int onPlace(StateMachine machine) {
        BlockPos.Mutable mutable = currentTask.startPos;
        int endY = currentTask.targetPos.getY();
        int canPlaceCount = Math.min(maxBatch.get(), currentTask.itemCount - 1);
        int placeCnt = 0;
        for (; mutable.getY() != endY; ) {
            BlockPos pos = mutable.toImmutable();
            Direction dir = currentTask.way < 0 ? Direction.UP : Direction.DOWN;
            BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos().offset(dir, 0.5), dir, pos, false);
            mc.interactionManager.sendSequencedPacket(
                    mc.world, (seq) -> new PlayerInteractBlockC2SPacket(currentTask.hand, hitResult, seq));
            mutable.move(0, -currentTask.way, 0);
            placeCnt += 1;
            if (placeCnt >= canPlaceCount) {
                startWaitTick = 0;
                return FAST_STATE_WAIT_SLOT_UPDATE;
            }
        }
        // mutable.getY() == endY
        startWaitTick = 0;
        machine.markForEndState();
        return FAST_STATE_WAIT_300MS;
    }

    public int onWaitSlotUpdate(StateMachine machine) {
        // magic sleep
        int sleepLimit = invSleepTick.get();
        if (startWaitTick == sleepLimit) {
            //
            ItemStack stackCopy = mc.player.getStackInHand(currentTask.hand).copy();
            // make desync inventory packets
            mc.player.setStackInHand(currentTask.hand, ItemStack.EMPTY);
            try {
                int hotbarIndex = mc.player
                        .currentScreenHandler
                        .getSlotIndex(mc.player.getInventory(), currentTask.selectedSlot)
                        .orElse(-1);
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, hotbarIndex, 40, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, hotbarIndex, 40, SlotActionType.SWAP, mc.player);

            } finally {
                mc.player.setStackInHand(currentTask.hand, stackCopy);
            }
            Predicate<Event<?>> packetPredicate = (event) -> {
                if (machine.getState() == FAST_STATE_WAIT_SLOT_UPDATE && startWaitTick < 20) {
                    machine.setState(FAST_STATE_PLACE);
                }
                return true;
            };
            Listener.addPostPacketCatcher(
                    new PacketCatcherImpl(ScreenHandlerSlotUpdateS2CPacket.class, packetPredicate));
            Listener.addPostPacketCatcher(new PacketCatcherImpl(InventoryS2CPacket.class, packetPredicate));
        }
        startWaitTick++;
        machine.markForEndState();
        if (startWaitTick >= 20) {
            return FAST_STATE_PLACE;
        }
        return FAST_STATE_WAIT_SLOT_UPDATE;
    }

    public int onWait300MS(StateMachine machine) {
        machine.markForEndState();
        startWaitTick++;
        if (startWaitTick > 8) {
            // execute place
            stateMachine = null;
            BlockPos pos = currentTask.targetPos;
            Direction dir = currentTask.way < 0 ? Direction.UP : Direction.DOWN;
            BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos().offset(dir, 0.5), dir, pos, false);
            mc.interactionManager.sendSequencedPacket(
                    mc.world, (seq) -> new PlayerInteractBlockC2SPacket(currentTask.hand, hitResult, seq));
            currentTask = null;
            Debug.chat("[AirWall] 任务完成");
            return FAST_STATE_NONE;
        }
        return FAST_STATE_WAIT_300MS;
    }

    public void onRenderPos(Event<MatrixStack> event) {
        if (enable.get()) {
            if (mc.player.getStackInHand(Hand.MAIN_HAND).isEmpty()
                    && mc.player.getStackInHand(Hand.OFF_HAND).isEmpty()) {
                return;
            }
            MatrixStack stack = event.context();
            if (mc.crosshairTarget.getType() == HitResult.Type.MISS) {
                HitResult result = getCameraEntity().raycast(range.get(), 0, false);
                if (result.getType() == HitResult.Type.MISS && result instanceof BlockHitResult block) {
                    RenderUtils.startDrawVirtual(stack);
                    try {
                        BlockPos pos = block.getBlockPos();
                        RenderUtils.drawOutlinedBox(stack, Vec3d.of(pos), Vec3d.of(pos.add(1, 1, 1)), Color.RED);
                    } finally {
                        RenderUtils.stopDrawVirtual(stack);
                    }
                }
            }
        }
    }

    public Entity getCameraEntity() {
        if (mc.getCameraEntity() != null) {
            return mc.getCameraEntity();
        }
        return mc.player;
    }

    public static enum Mode implements ConfigEnum {
        VANILLA,
        GRIM_GHOST_BLOCK_WALL,
        GRIM_FAST_GHOST_BLOCK_WALL;

        @Override
        public String getConfigEnumType() {
            return "air_place_mode";
        }
    }
}
