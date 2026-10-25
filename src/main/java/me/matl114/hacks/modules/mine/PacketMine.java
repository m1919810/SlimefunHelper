package me.matl114.hacks.modules.mine;

import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.channels.EventChannelDispatcher;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalMiningMode;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class PacketMine extends BaseModule {
    private static final long TICK_MINE_EVENT_WINDOW_MS = 50L;

    public static PacketMine INSTANCE;

    public PacketMine() {
        super("PacketMine");
        bindFlag(autoEnable);
        INSTANCE = this;
    }

    public ModulePath packetMine = makePath(Configs.MINE_CONFIG, "mine-oneblock");

    public final FlagRef autoEnable = flagBuilder(packetMine.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final IntRef multiplePackets = intBuilder(packetMine.add("multiple-packets"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef realBreak =
            flagBuilder(packetMine.add("simulate-real-break")).build();

    public final FlagRef airBreak =
            flagBuilder(packetMine.add("consider-air-break")).build();

    public final DoubleRef mineThreshold = builder(packetMine.add("mine-threshold"), DoubleRef.TYPE)
            .defaultValue(0.7)
            .validator(Configs.doubleRange(-0.0001F, 1.0001F))
            .build();

    public final FlagRef autoTool = flagBuilder(packetMine.add("auto-pickaxe")).build();

    public final FlagRef autoToolDoubleBreak =
            flagBuilder(packetMine.add("auto-pickaxe-double-break")).build();

    public final FlagRef groundDeceive =
            flagBuilder(packetMine.add("ground-deceive")).build();

    public final FlagRef groundOnlyWhenNoControl =
            flagBuilder(packetMine.add("ground-only-when-no-control")).build();

    public final FlagRef mineOnce =
            flagBuilder(packetMine.add("only-mine-once-per-click")).build();

    public final FlagRef whiteList =
            flagBuilder(packetMine.add("enable-mine-white-list")).build();

    public final NBTRef<EntrySet<Block>> whiteListRegex = builder(
                    packetMine.add("mine-white-list"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.BLOCK))
            .build();

    public final FlagRef eatingAbort = builder(packetMine.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(packetMine.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef swingHand = flagBuilder(packetMine.add("swing-hand")).build();

    public final EnumRef<LegalMiningMode> legalMiningMode = builder(
                    packetMine.add("legal-mining-mode"), LegalMiningMode.class)
            .defaultValue(LegalMiningMode.NONE)
            .build();

    BlockPos lastMinePos;
    private BlockPos requestedBlock;
    private boolean delayMine;
    private long lastTickTime;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreTick(), this::onPreTick);
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getMineBlockAction(), this::onMineBlockAction);
        registerListener(Listener.getBlockUpdateListener(), this::onBlockUpdate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    private void clearRequest() {
        requestedBlock = null;
        delayMine = false;
    }

    private void refreshRequestedBlock() {
        if (checkNull()
                || (requestedBlock != null
                        && (mc.world.getBlockState(requestedBlock).isAir()
                                || !InteractExtra.INSTANCE.isWithinInteractRange(
                                        mc.player.getPos(), requestedBlock)))) {
            clearRequest();
        }
    }

    private void onPreTick() {
        refreshRequestedBlock();
    }

    public BlockPos getRequestedBlock() {
        refreshRequestedBlock();
        return requestedBlock;
    }

    private boolean wasRecentlyTicked() {
        return lastTickTime > 0L && Util.getMeasuringTimeMs() - lastTickTime <= TICK_MINE_EVENT_WINDOW_MS;
    }

    private void onBlockUpdate(Event<BlockUpdate> event) {
        BlockUpdate update = event.context();
        if (update.newState().isAir() && Objects.equals(requestedBlock, update.pos())) {
            clearRequest();
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context.getValue();
        if (preset.isMultitaskPreset()) eatingAbort.set(preset.getEatingAbort(eatingAbort.get()));
    }

    public boolean hasMiningTarget() {
        return getCurrentMiningPos() != null;
    }

    public boolean isMining() {
        return hasMiningTarget();
    }

    public boolean isMining(BlockPos pos) {
        return Objects.equals(getCurrentMiningPos(), pos);
    }

    public BlockPos getCurrentMiningPos() {
        if (mc.interactionManager == null) {
            return null;
        }
        return PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
    }

    public void cancelPacketMine(BlockPos pos) {
        BlockPos po = getCurrentMiningPos();
        if (Objects.equals(po, pos)) {
            PlayerInteractionAccess.of(mc.interactionManager).resetCurrentMiningPos();
        }
    }

    public void onTick() {
        if (isActive()) {
            BlockPos request = getRequestedBlock();
            if (checkNull()) return;
            if (delayMine) {
                if (MineExtra.INSTANCE.getMiningPacketCooldown(0) == 0) {
                    if (request != null) {
                        if (!MineExtra.INSTANCE.handleMineMode(request, legalMiningMode.get())) {
                            return;
                        }
                        PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(request);
                    }
                    delayMine = false;
                }
                return;
            }
            if (eatingAbort.get() && mc.player.isUsingItem()) {
                return;
            }
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (mineOnce.get() && Objects.equals(pos, lastMinePos)) {
                return;
            }
            tickMine();
        }
    }

    public void tickMine() {
        lastTickTime = Util.getMeasuringTimeMs();
        if (mc.interactionManager != null && mc.player != null) {
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (pos == null) return;

            Runnable currentTickCallback = null;
            BlockBreak postMineCallback = null;
            float progress = 0;
            if (InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
                BlockState blockState = mc.world.getBlockState(pos);
                IndexEntry<ItemStack> currentItemSlot = getCurrentUsableTool(blockState);

                    ItemStack currentTool = currentItemSlot.val();
                    if (canMine(blockState, currentTool)) {
                        progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .predictCurrentMiningProgressWithTool(currentTool);
                        Event<BlockBreak> eventPre = new Event<>(
                                new BlockBreak(pos, BlockBreak.Stage.PRE, currentTool, progress), true, false);
                        packetMineAction.handleValue(eventPre);
                        if (!eventPre.isCancelled()) {
                            if (!MineExtra.INSTANCE.handleMineMode(pos, legalMiningMode.get())) {
                                return;
                            }
                            if (groundDeceive.get() && !mc.player.isOnGround()) {
                                boolean shouldExecute = true;
                                if (groundOnlyWhenNoControl.get()
                                        && !PlayerInputUtils.of(mc.options).hasMovementControl()) {
                                    shouldExecute = false;
                                }
                                if (shouldExecute) {

                                mc.getNetworkHandler()
                                        .sendPacket(LegacySnapRotManager.INSTANCE.createSnapAt(
                                                PlayerStateManager.INSTANCE.lastPitch,
                                                PlayerStateManager.INSTANCE.lastYaw,
                                                true));
                                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                                FloatingUtils.INSTANCE.setForceOnGroundVia(true);
                            }
                        }
                        Runnable callback =
                                InvExtra.INSTANCE.swapItemToHand(currentItemSlot.index(), false, ghostHand.get());

                        for (int i = 0; i < multiplePackets.get(); ++i) {
                            if (swingHand.get())
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                            PlayerInteractionAccess.of(mc.interactionManager)
                                    .sendBreakPacket(pos, !(realBreak.get() && progress > 0.7F));
                        }
                        currentTickCallback = callback;
                        postMineCallback = eventPre.context();
                    }
                }
            }
            if (autoToolDoubleBreak.get()
                    && PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos() != null) {
                MineExtra.INSTANCE.tickGhostHandDoubleBreak(currentTickCallback, ghostHand.get(), groundDeceive.get());
            } else {
                if (currentTickCallback != null) {
                    MineExtra.INSTANCE.finishMineGhostHand(currentTickCallback);
                }
            }
            if (postMineCallback != null) {
                packetMineAction.broadcast(new BlockBreak(
                        postMineCallback.blockPos(),
                        BlockBreak.Stage.POST,
                        postMineCallback.stack(),
                        postMineCallback.progress()));
            } else if (WorldUtils.isChunkLoaded(pos)) {
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || state.isLiquid()) {
                    lastMinePos = pos;
                }
            }
        }
    }

    public void onMineBlockAction(Event<HitResult> event) {
        if (event.context instanceof BlockHitResult hitResult) {
            lastMinePos = null;
            BlockPos pos = hitResult.getBlockPos().toImmutable();
            if (checkNull() || mc.world.getBlockState(pos).isAir()) {
                clearRequest();
                return;
            }
            if (Objects.equals(getCurrentMiningPos(), pos)) {
                clearRequest();
                return;
            } else {
                requestedBlock = pos;
                getRequestedBlock();
            }
        }
        if (!isActive() || !wasRecentlyTicked()) return;
        if (event.context instanceof BlockHitResult hitResult) {
            BlockPos pos = hitResult.getBlockPos().toImmutable();
            if (!MineExtra.INSTANCE.handleMineMode(pos, legalMiningMode.get())) {
                event.cancel();
                delayMine = true;
                return;
            }
        }
        if (delayMine) {
            event.cancel();
            return;
        } else if (event.context instanceof BlockHitResult
                && requestedBlock != null
                && !MineExtra.INSTANCE.isSafeCooldownThreshold()) {
            event.cancel();
            delayMine = true;
        }
    }

    @Nonnull
    public IndexEntry<ItemStack> getCurrentUsableTool(BlockState currentState) {
        if (autoTool.get()) {
            BlockState calS;
            if (currentState.isAir() || currentState.isLiquid()) {
                calS = Blocks.OBSIDIAN.getDefaultState();
            } else {
                calS = currentState;
            }
            var re = InventoryUtils.findBestPlayerItem(
                    item -> {
                        return (double)
                                WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, calS, item);
                    },
                    ghostHand.get().getSearchSize(false),
                    true,
                    true);
            if (re != null) {
                return re;
            }
        }
        return InventoryUtils.getSelectedItem();
    }

    public boolean isMineable(BlockState state) {
        return state.getBlock().getHardness() >= 0.0F
                && !state.isLiquid()
                && (airBreak.get() || !state.isAir())
                && (!whiteList.get() || !whiteListRegex.get().test(state.getBlock()));
    }

    public boolean canMine(BlockState state, ItemStack tool) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (isMineable(state)) {
            if (mineThreshold.get() > 0) {
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                float speed = access.predictCurrentMiningProgressWithTool(tool);
                if (groundDeceive.get() && !mc.player.isOnGround()) {
                    speed *= 5;
                }
                return speed > Math.min(0.98, mineThreshold.get());
            }
            return true;
        } else {
            return false;
        }
    }

    @Getter
    @Cancelable
    public static final EventChannelDispatcher<BlockBreak> packetMineAction =
            new EventChannelDispatcher<>(BlockBreak::stage);
}
