package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.QueueMine;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.entity.EntityMovementStatus;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3i;
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

    public final FlagRef autoSneak =
            flagBuilder(litematicaPrinterRewrite.add("auto-sneak")).build();

    public final FlagRef supportWater =
            flagBuilder(litematicaPrinterRewrite.add("support-water-place")).build();

    public final FlagRef useIce = flagBuilder(litematicaPrinterRewrite.add("use-ice-to-form-water"))
            .show(supportWater::get)
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
            .defaultValue(new WrapColor(ColorUtils.color(Color.GREEN)))
            .build();

    public final NBTRef<WrapColor> renderFailColor = builder(
                    litematicaPrinterRewrite.add("render-fail-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Color.RED)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    int countDown;
    //    final Set<BlockPos> placeFailureBlocks = new HashSet<>();
    //    final Set<BlockPos> placeSuccessBlocks = new HashSet<>();
    final RenderCollector<Box> drawOutlines = RenderCollectors.createBoxCollector(true, false, false);
    boolean needSneak = false;
    boolean drainWater = false;
    Map<BlockPos, Integer> waterBlocks = new HashMap<>();

    public void tickWaterBlocks() {
        if (waterBlocks.isEmpty()) {
            return;
        }
        var iter = waterBlocks.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue() < Tasks.getTick() - 20) {
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
        tickWaterBlocks();
        //        placeFailureBlocks.clear();
        //        placeSuccessBlocks.clear();
        boolean bl = mc.player.isSneaking();
        drawOutlines.clear();
        if (enable.get() && LitematicaHooks.getInstance().isEnabled()) {
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();
            BlockPos posStanding = mc.player.getSteppingPos();
            BlockPos posCenter = posStanding.add(0, 1, 0);
            int multiply = (mode.get().canMultiRotPlace() || (DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled()))
                    ? mul.get()
                    : 1;
            int placeCount = 0;
            for (var offset : blocksSeq) {
                BlockPos checkPos = posCenter.add(offset);
                if (LitematicaHooks.getInstance().isPositionWithinRange(checkPos)) {
                    BlockState state = litematicaWorld.getBlockState(checkPos);
                    if (!state.isAir()) {
                        BlockState clientState = mc.world.getBlockState(checkPos);
                        if (supportWater.get()
                                && clientState != state
                                && ((clientState.isAir()
                                                && (state.isLiquid()
                                                        || state.getFluidState().getFluid() == Fluids.WATER))
                                        || (clientState.getBlock() == state.getBlock()
                                                && clientState.getFluidState() != state.getFluidState()))
                                && !waterBlocks.containsKey(checkPos)) {
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
                            if (placeCount != 0) {
                                InteractionTasks.flushACPlaceQueue();
                            }
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
                if (ViaFabricPlusHooks.isSupportInstaSneak()) {
                    if (mc.player.isSneaking() != bl) {
                        PlayerInputUtils.of(mc.player)
                                .sneak(bl)
                                .sendPlayerSneakUpdatePacket()
                                .applyInput(mc.player);
                    }
                } else {
                    PlayerInputManager.INSTANCE.addSneakModifier(0, true, Math.max(delay.get() - 1, 0), 2);
                }
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
                if (!needSneak
                        && autoSneak.get()
                        && ViaFabricPlusHooks.isSupportInstaSneak()
                        && !mc.player.isSneaking()) {
                    PlayerInputUtils.of(mc.player)
                            .sneak(true)
                            .sendPlayerSneakUpdatePacket()
                            .applyInput(mc.player);
                    mc.player.setSneaking(true);
                }
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

                waterBlocks.put(pos, Tasks.getTick());
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
                    EntityUtils.setEntityYawSafe(mc.player, rot.y);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    playerStatus.restoreRotation();
                    suc = true;
                    waterBlocks.put(pos, Tasks.getTick());
                }
            }
            if (suc) {
                putSuccessPlace(pos);
            } else {
                putCanNotPlace(pos);
            }
            if (rotation.flag()) {
                if (!needSneak
                        && autoSneak.get()
                        && ViaFabricPlusHooks.isSupportInstaSneak()
                        && !mc.player.isSneaking()) {
                    PlayerInputUtils.of(mc.player)
                            .sneak(true)
                            .sendPlayerSneakUpdatePacket()
                            .applyInput(mc.player);
                    mc.player.setSneaking(true);
                }
                needSneak = true;
            }
            return suc;
        }
        return false;
    }

    public void tickTryDrainWater() {}

    public void handlePlace(BlockHitResult result) {
        if (!mode.get().isLegal()) {
            InteractionTasks.placeBlock(Hand.MAIN_HAND, result);
        } else {
            InteractionTasks.handlePlaceMode(mode.get(), result, Hand.MAIN_HAND);
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
