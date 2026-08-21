package me.matl114.hacks.modules.interact;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.QueueMine;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.entity.EntityMovementStatus;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class PrinterRewrite extends BaseModule {
    public final ModulePath blockRotate = makePath(Configs.INTERACT_CONFIG, "block-rotate");
    public final ModulePath litematicaPrinterRewrite = blockRotate.add("litematica-printer-rewrite");

    public PrinterRewrite() {
        super("Printer");
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(litematicaPrinterRewrite.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    litematicaPrinterRewrite.add("hotkey"), new MultiKeyBind(), litematicaPrinterRewrite.add("enable"))
            .build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(
                    litematicaPrinterRewrite.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.DELAY_MOVEMENT)
            .build();

    public final FlagRef airplace =
            flagBuilder(litematicaPrinterRewrite.add("air-place")).build();

    public final IntRef delay = builder(litematicaPrinterRewrite.add("delay"), IntRef.TYPE)
            .defaultValue(5)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = builder(litematicaPrinterRewrite.add("multiply"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef autoSneak = builder(litematicaPrinterRewrite.add("auto-sneak"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef supportWater = builder(litematicaPrinterRewrite.add("support-water-place"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef useIce = flagBuilder(litematicaPrinterRewrite.add("use-ice-to-form-water"))
            .show(supportWater::get)
            .build();

    public final FlagRef supportReplace = builder(litematicaPrinterRewrite.add("support-replace-block"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef swingHand = builder(litematicaPrinterRewrite.add("swing-hand"), Boolean.class)
            .defaultValue(true)
            .build();

    public List<Vec3i> blocksSeq = new ArrayList<>();

    public void updateBlocks(double i) {
        blocksSeq = new ArrayList<>();
        List<Vec3i> list = new ArrayList<>();
        int range = (int) i;
        for (var y = -range; y <= range; ++y) {
            for (var z = -range; z <= range; ++z) {
                list.add(new Vec3i(y, 0, z));
            }
        }
        list.sort(Comparator.comparingDouble(v -> v.getX() * v.getX() + v.getZ() * v.getZ()));
        for (var x = -range; x <= range; ++x) {
            for (var p : list) {
                blocksSeq.add(new Vec3i(p.getX(), x, p.getZ()));
            }
        }
    }

    public final DoubleRef interactRangeOverride = builder(litematicaPrinterRewrite.add("range"), DoubleRef.TYPE)
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .updateListener(this::updateBlocks)
            .build();

    public final FlagRef render = builder(litematicaPrinterRewrite.add("render"), Boolean.class)
            .defaultValue(true)
            .build();
    public final NBTRef<WrapColor> renderSuccessColor = builder(
                    litematicaPrinterRewrite.add("render-success-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.GREEN)))
            .build();

    public final NBTRef<WrapColor> renderFailColor = builder(
                    litematicaPrinterRewrite.add("render-fail-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.RED)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        var re = createTitleLabel("widget.queue-mine.mine.use-argument", 0, dblank, dx, dy);
        acceptor.accept(new DynamicContentWidget<>(() -> supportWater.get() && useIce.get() ? re : null, 0, 0));
    }

    int countDown;
    //    final Set<BlockPos> placeFailureBlocks = new HashSet<>();
    //    final Set<BlockPos> placeSuccessBlocks = new HashSet<>();
    final RenderCollector<Box> drawOutlines = RenderCollectors.createBoxCollector(true, false, false);
    boolean needSneak = false;
    boolean drainWater = false;
    Map<BlockPos, Integer> desyncWaitBlocks = new HashMap<>();

    public void tickDesyncWaitBlocks() {
        if (desyncWaitBlocks.isEmpty()) {
            return;
        }
        int tick = Tasks.getTick();
        var iter = desyncWaitBlocks.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue() < tick) {
                iter.remove();
            }
        }
    }

    public void onPreInputEvent(Event<Void> event) {
        if (++countDown > delay.get()) {
            countDown = 0;
        } else {
            return;
        }
        tickDesyncWaitBlocks();
        drawOutlines.clear();
        if (enable.get() && LitematicaHooks.getInstance().isEnabled()) {
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();
            BlockPos posStanding = mc.player.getSteppingPos();
            BlockPos posCenter = posStanding.add(0, 1, 0);
            int multiply = ((DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                            mode.get().canMultiRotPlace())))
                    ? mul.get()
                    : 1;
            int placeCount = 0;
            for (var offset : blocksSeq) {
                BlockPos checkPos = posCenter.add(offset);
                if (LitematicaHooks.getInstance().isPositionWithinRange(checkPos)) {
                    BlockState state = litematicaWorld.getBlockState(checkPos);
                    if (!state.isAir()) {
                        BlockState clientState = mc.world.getBlockState(checkPos);
                        if (supportReplace.get()
                                && clientState != state
                                && !clientState.isAir()
                                && !clientState.isLiquid()
                                && !clientState.isReplaceable()
                                && !desyncWaitBlocks.containsKey(checkPos)
                                && canBeReplaceTo(clientState, state)) {
                            if (doMultiReplace(checkPos, clientState, state)) {
                                placeCount++;
                                if (placeCount >= multiply) {
                                    break;
                                }
                            }
                        }
                        if (supportWater.get()
                                && clientState != state
                                && ((clientState.isAir()
                                                && (state.isLiquid()
                                                        || state.getFluidState().getFluid() == Fluids.WATER))
                                        || (clientState.getBlock() == state.getBlock()
                                                && clientState.getFluidState() != state.getFluidState()))
                                && !desyncWaitBlocks.containsKey(checkPos)) {
                            // fluid state change
                            FluidState fluidState = clientState.getFluidState();
                            FluidState targetState = state.getFluidState();
                            // place only source to empty state
                            if ((fluidState.getFluid() == Fluids.EMPTY
                                            || fluidState.getFluid() == Fluids.FLOWING_WATER
                                            || fluidState.getFluid() == Fluids.FLOWING_LAVA)
                                    && (targetState.getFluid() == Fluids.LAVA
                                            || targetState.getFluid() == Fluids.WATER)) {
                                if (doLiquidPlace(checkPos, state)) {
                                    placeCount += 1;
                                    if (placeCount >= multiply) {
                                        break;
                                    }
                                    continue;
                                }
                            }
                        }
                        if (!state.isLiquid()
                                && (clientState.isAir() || clientState.isLiquid() || clientState.isReplaceable())
                                && clientState != state) {
                            // do place
                            if (doPlace(
                                    checkPos, state, airplace.get(), !mode.get().isLegal())) {
                                placeCount += 1;
                                if (placeCount >= multiply) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (needSneak) {
            needSneak = false;
            if (autoSneak.get()) {
                PlayerInputManager.INSTANCE.addSneakModifier(0, true, Math.max(delay.get() - 1, 0), 2);
            }
        }
        if (drainWater) {
            tickTryDrainWater();
        }
    }

    public int supplyBlocks(Block needBlock) {
        Item needItem = needBlock.asItem();
        if (needItem == Items.AIR) return -1;
        var entry = InventoryUtils.findPlayerItem((item) -> item.getItem() == needItem, true, false);
        return entry == null ? -1 : entry.index();
    }

    public int supplyLiquid(Fluid fluid) {
        Item item = fluid == Fluids.WATER ? Items.WATER_BUCKET : (fluid == Fluids.LAVA ? Items.LAVA_BUCKET : null);
        if (item == null) return -1;
        var entry = InventoryUtils.findPlayerItem((itemStack) -> itemStack.getItem() == item, true, false);
        return entry == null ? -1 : entry.index();
    }

    public void putCanNotPlace(BlockPos pos) {
        //        placeFailureBlocks.add(pos);
        drawOutlines.submit(MathUtils.getBlockBox(pos), renderFailColor.get().withAlpha(255));
    }

    public void putSuccessPlace(BlockPos pos) {
        //        placeSuccessBlocks.add(pos);
        drawOutlines.submit(MathUtils.getBlockBox(pos), renderSuccessColor.get().withAlpha(255));
    }

    public boolean doPlace(BlockPos pos, BlockState targetState, boolean useAirPlace, boolean usePositionPlace) {
        Block needBlock = targetState.getBlock();
        if (needBlock instanceof CandleCakeBlock) {
            needBlock = Blocks.CAKE;
        } else if (needBlock instanceof FlowerPotBlock flowerPotBlock && flowerPotBlock.getContent() != Blocks.AIR) {
            needBlock = Blocks.FLOWER_POT;
        }
        int idx = supplyBlocks(needBlock);
        if (idx == -1) {
            putCanNotPlace(pos);
            return false;
        }
        var result = InteractionTasks.createSpecificStateHitResult(
                mc.player.getFacing(), pos, targetState, useAirPlace, usePositionPlace);
        // add placement collision check
        if (result != null
                && InteractExtra.INSTANCE.isWithinInteractRange(
                        mc.player.getPos(), result.val().getBlockPos(), interactRangeOverride.get())
                && InteractUtils.getBlockPlacement(needBlock, mc.player, mc.world, result.val()) != null) {
            if (result.flag()) {
                needSneak = true;

                if (!InteractUtils.canInteractAndPlace(mc.player, result)) {
                    putCanNotPlace(pos);
                    return false;
                }
            }
            FlagRef enableRotateFix = InteractionTasks.getBlockRotate().enable2;
            FlagRef enableLegalLook = InteractionTasks.getBlockRotate().legal;
            EnumRef<Configs.BypassMode> enableRot = InteractionTasks.getBlockRotate().bypassMode2;
            boolean state = enableRotateFix.get();
            boolean state2 = enableLegalLook.get();
            Configs.BypassMode bypassMode = enableRot.get();
            if (!state) {
                enableRotateFix.set(true);
            }
            if (!state2) {
                // cancel legal look fix because we here handle the look, do not duplicate
                enableLegalLook.set(true);
            }
            enableRot.set(Configs.BypassMode.NO_BYPASS);
            try {
                Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(idx);
                if (callback == null) {
                    putCanNotPlace(pos);
                    return false;
                }
                handlePlace(result.val());
                // sb grimac
                // callback.run()
                putSuccessPlace(pos);
                return true;
            } finally {
                if (!state) {
                    enableRotateFix.set(false);
                }
                if (!state2) {
                    enableLegalLook.set(false);
                }
                enableRot.set(bypassMode);
            }
        } else {
            putCanNotPlace(pos);
            return false;
        }
    }

    public boolean doLiquidPlace(BlockPos pos, BlockState targetState) {
        FluidState fluidState = targetState.getFluidState();
        // fill source
        BlockState clientState = mc.world.getBlockState(pos);
        if (useIce.get()
                && fluidState.getFluid() == Fluids.WATER
                && (clientState.isAir() || clientState.isLiquid() || clientState.isReplaceable())) {
            if (doPlace(
                    pos,
                    Blocks.ICE.getDefaultState(),
                    airplace.get(),
                    !mode.get().isLegal())) {
                if (DisablerManager.INSTANCE.flushACPlaceBreakQueue()) {
                    QueueMine.INSTANCE.sumitMine(pos);
                } else {
                    Tasks.scheduleDelayedPre(
                            () -> {
                                QueueMine.INSTANCE.sumitMine(pos);
                            },
                            0);
                }

                desyncWaitBlocks.put(pos, Tasks.getTick() + 20);
                return true;
            }
        }
        if (fluidState.getFluid() == Fluids.WATER || fluidState.getFluid() == Fluids.LAVA) {
            int item = supplyLiquid(fluidState.getFluid());
            if (item == -1) {
                putCanNotPlace(pos);
                if (fluidState.getFluid() == Fluids.WATER) {
                    drainWater = true;
                }
                return false;
            }
            boolean suc = false;
            FlagEntry<Vec2f> rotation =
                    InteractionTasks.createLiquidPlacementRaycast(mc.player.getEyePos(), pos, targetState);
            if (rotation == null) {
                putCanNotPlace(pos);
                return false;
            }
            if (rotation.flag() == mc.player.isSneaking()) {
                var rot = rotation.val();

                Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(item);
                if (callback != null) {
                    EntityMovementStatus<PlayerEntity> playerStatus = new EntityMovementStatus<>(mc.player);
                    EntityUtils.setEntityPitchSafe(mc.player, rot.x);
                    PlayerStateManager.setPlayerYawSafe(mc.player, rot.y);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    playerStatus.restoreRotation();
                    suc = true;
                    desyncWaitBlocks.put(pos, Tasks.getTick() + 20);
                }
            }
            if (suc) {
                putSuccessPlace(pos);
            } else {
                putCanNotPlace(pos);
            }
            if (rotation.flag()) {
                needSneak = true;
            }
            return suc;
        }
        return false;
    }

    public boolean canBeReplaceTo(BlockState fromState, BlockState toState) {
        return InteractUtils.canBeReplaceTo(fromState, toState);
    }

    private boolean isDesyncStateInteractTransition(BlockState currentState, BlockState targetState) {
        if (currentState == null || targetState == null) return false;
        Block currentBlock = currentState.getBlock();
        Block targetBlock = targetState.getBlock();
        if (currentBlock instanceof NoteBlock) {
            return true;
        }
        if (currentBlock instanceof LeverBlock) {
            return true;
        }
        if (currentBlock instanceof ButtonBlock) {
            return true;
        }
        if (currentBlock instanceof CandleBlock
                && targetBlock instanceof CandleBlock
                && currentState.get(CandleBlock.LIT)
                && targetState.equals(currentState.with(CandleBlock.LIT, false))) {
            return true;
        }
        if (currentBlock instanceof CandleBlock
                && targetBlock instanceof CandleBlock
                && !currentState.get(CandleBlock.LIT)
                && !currentState.get(CandleBlock.WATERLOGGED)
                && targetState.equals(currentState.with(CandleBlock.LIT, true))) {
            return true;
        }
        if (currentBlock instanceof CakeBlock && targetBlock instanceof CakeBlock) {
            return true;
        }
        if (currentBlock instanceof CakeBlock && targetBlock instanceof CandleCakeBlock) {
            return true;
        }
        if (currentBlock instanceof CandleCakeBlock && targetBlock instanceof CakeBlock) {
            return true;
        }
        return false;
    }

    private boolean isStackedPlacementTransition(BlockState currentState, BlockState targetState) {
        if (currentState == null || targetState == null) return false;
        if (!currentState.isOf(targetState.getBlock())) return false;
        Block block = currentState.getBlock();
        if (block instanceof SlabBlock) {
            return currentState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                    && targetState.get(SlabBlock.TYPE) == SlabType.DOUBLE;
        }
        if (block instanceof SnowBlock) {
            return currentState.get(SnowBlock.LAYERS) < targetState.get(SnowBlock.LAYERS);
        }
        if (block instanceof CandleBlock) {
            return currentState.get(CandleBlock.CANDLES) < targetState.get(CandleBlock.CANDLES);
        }
        if (block instanceof SeaPickleBlock) {
            return currentState.get(SeaPickleBlock.PICKLES) < targetState.get(SeaPickleBlock.PICKLES);
        }
        if (block instanceof FlowerbedBlock) {
            return currentState.get(FlowerbedBlock.FLOWER_AMOUNT) < targetState.get(FlowerbedBlock.FLOWER_AMOUNT);
        }
        if (block instanceof LeafLitterBlock) {
            return currentState.get(LeafLitterBlock.SEGMENT_AMOUNT) < targetState.get(LeafLitterBlock.SEGMENT_AMOUNT);
        }
        return false;
    }

    private BlockHitResult createStateInteractHitResult(BlockPos pos) {
        Vec2f rot = EntityUtils.rotationToPitchYaw(
                pos.toCenterPos().subtract(mc.player.getEyePos()).normalize());
        Direction side = EntityUtils.pitchYawToDirection(rot).getOpposite();
        return new BlockHitResult(pos.toCenterPos(), side, pos, false);
    }

    private int supplyInteractionItem(BlockState currentState, BlockState targetState) {
        if (currentState == null || targetState == null) return -2;
        Block currentBlock = currentState.getBlock();
        Block targetBlock = targetState.getBlock();

        if (currentBlock instanceof RespawnAnchorBlock
                && targetBlock instanceof RespawnAnchorBlock
                && currentState.get(RespawnAnchorBlock.CHARGES) < targetState.get(RespawnAnchorBlock.CHARGES)) {
            var entry = InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.GLOWSTONE), true, false);
            return entry == null ? -1 : entry.index();
        }
        if (currentBlock instanceof CandleBlock
                && targetBlock instanceof CandleBlock
                && !currentState.get(CandleBlock.LIT)
                && targetState.get(CandleBlock.LIT)) {
            var entry = InventoryUtils.findPlayerItem(
                    stack -> stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE), true, false);
            return entry == null ? -1 : entry.index();
        }
        if (currentBlock instanceof PumpkinBlock && targetBlock == Blocks.CARVED_PUMPKIN) {
            var entry = InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.SHEARS), true, false);
            return entry == null ? -1 : entry.index();
        }
        if (currentBlock instanceof FlowerPotBlock fromPot && targetBlock instanceof FlowerPotBlock targetPot) {
            if (fromPot.getContent() == Blocks.AIR && targetPot.getContent() != Blocks.AIR) {
                Item item = targetPot.getContent().asItem();
                if (item == Items.AIR) return -1;
                var entry = InventoryUtils.findPlayerItem(stack -> stack.isOf(item), true, false);
                return entry == null ? -1 : entry.index();
            }
            return -2;
        }
        if (currentBlock instanceof CakeBlock && targetBlock instanceof CandleCakeBlock) {
            Item item = targetBlock.asItem();
            if (item == Items.AIR) return -1;
            var entry = InventoryUtils.findPlayerItem(stack -> stack.isOf(item), true, false);
            return entry == null ? -1 : entry.index();
        }
        return -2;
    }

    public boolean doMultiReplace(BlockPos pos, BlockState currentState, BlockState targetState) {
        Pair<BlockState, Predicate<ItemStack>> nextStepState =
                InteractUtils.getNextInteractionStep(currentState, targetState).stream()
                        .findAny()
                        .orElse(null);
        if (nextStepState != null) {
            BlockState nextBlockState = nextStepState.getFirst();
            Predicate<ItemStack> needStack = nextStepState.getSecond();
            // self replacement
            var re = InventoryUtils.findPlayerItem(needStack, true, true);
            if (re != null) {
                FlagEntry<BlockHitResult> hitResult;
                if (nextBlockState.getBlock() == currentState.getBlock() && !needStack.test(ItemStack.EMPTY)) {
                    hitResult = InteractionTasks.createSpecificStateHitResult(
                            pos, targetState, airplace.get(), !mode.get().isLegal());
                } else {
                    hitResult = new FlagEntry<>(false, RaycastUtils.createHitResult(pos, mc.player.getEyePos()));
                }
                if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                    InvExtra.INSTANCE.swapInventoryIndexToHand(re.index());
                    InteractionTasks.handlePlaceMode(mode.get(), hitResult.val(), Hand.MAIN_HAND);
                    putSuccessPlace(pos);
                    if (isDesyncStateInteractTransition(currentState, targetState)) {
                        desyncWaitBlocks.put(pos, Tasks.getTick() + 20);
                    }
                    return true;
                } else {
                    putCanNotPlace(pos);
                }
            } else {
                putCanNotPlace(pos);
            }
        }
        return false;
    }

    private void onBlockUpdate(Event<BlockUpdateS2CPacket> event) {
        if (enable.get() && !desyncWaitBlocks.isEmpty()) {
            BlockPos pos = event.context.getPos();
            Integer waitUntil = desyncWaitBlocks.get(pos);
            if (waitUntil != null) {
                desyncWaitBlocks.put(pos, Math.min(waitUntil, Tasks.getTick() + 2));
            }
        }
    }

    public void tickTryDrainWater() {}

    public void handlePlace(BlockHitResult result) {
        if (!mode.get().isLegal()) {
            InteractionTasks.interactBlock(Hand.MAIN_HAND, result, swingHand.get());
        } else {
            InteractionTasks.handlePlaceMode(mode.get(), result, Hand.MAIN_HAND, swingHand.get());
        }
    }

    public void onRender(Event<MatrixStack> event) {
        MatrixStack stack = event.context();
        if (enable.get() && render.get()) {
            RenderUtils.startDrawVirtual(stack);
            try {
                drawOutlines.render3D(stack);
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public void onPresetReload(Event<EventContainer<ModulePreset>> event) {
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
        airplace.set(!event.context.getValue().hasAC());
    }
}
