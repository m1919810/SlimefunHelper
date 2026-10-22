package me.matl114.hacks.modules.mine;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.*;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.*;
import me.matl114.events.Event;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.task.ServerFeature;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.NetworkUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;

public class MineExtra extends BaseModule {
    public static MineExtra INSTANCE;

    private record PendingFailBreak(int seqNum, BlockPos pos, int startBreakTick) {}

    private PendingFailBreak pendingFailBreak;

    public MineExtra() {
        super("MineExtra");
        INSTANCE = this;
    }

    public final ModulePath fastbreak = makePath(Configs.MINE_CONFIG, "fast-break");

    public final FlagRef quickMine = flagBuilder(fastbreak.addEnable()).build();

    public final KeyBindRef quickMineKeyBind = toggleHotkey(
                    fastbreak.addHotkey(), new MultiKeyBind(), fastbreak.addEnable())
            .build();

    public final FlagRef fakeInstaBreak =
            flagBuilder(fastbreak.add("use-fake-instant-break")).build();

    public final EnumRef<Mode> fastBreakBypassMode = builder(fastbreak.add("bypass-mode"), Mode.class)
            .defaultValue(Mode.NO_BYPASS)
            .build();

    public final DoubleRef breakThreshold = builder(fastbreak.add("break-threshold"), Double.class)
            .defaultValue(0.99)
            .validator(Configs.doubleRange(0.0, 1.1))
            .build();

    public final IntRef breakSpeedExtraTicks = builder(fastbreak.add("break-speed-extra-ticks"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.intRange(0, 5))
            .build();

    public final IntRef grimACProgressThreshold = builder(
                    fastbreak.add("grim-progress-punishment-threshold"), Integer.class)
            .defaultValue(150)
            .validator(Configs.intRange(0, 1000))
            .show(() -> fastBreakBypassMode.get().isIn(Mode.BYPASS_GRIM_LEGIT))
            .build();

    public final FlagRef optimizeOneBlock =
            flagBuilder(fastbreak.add("same-block-optimize")).build();

    public final FlagRef ghostHandMine =
            flagBuilder(fastbreak.add("ghost-hand-mine")).build();

    public final FlagRef ghostHandSwapWhenStart = flagBuilder(fastbreak.add("ghost-hand-swap-when-start"))
            .show(ghostHandMine::get)
            .build();

    public final EnumRef<GhostHandMode> ghostHandPreferredMode = builder(
                    fastbreak.add("ghost-hand-preferred-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final ModulePath cooldownGroup = fastbreak.add("cooldown-manage");
    public final FlagRef cooldownOverride = builder(cooldownGroup.add("cooldown-override"), Boolean.class)
            .defaultValue(true)
            .build();

    public final IntRef breakCooldown = builder(cooldownGroup.add("break-cooldown"), Integer.class)
            .defaultValue(5)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef grimAcCooldownThreshold = builder(
                    cooldownGroup.add("grim-cooldown-punishment-threshold"), Integer.class)
            .defaultValue(150)
            .validator(Configs.intRange(0, 1000))
            .show(() -> fastBreakBypassMode.get().isIn(Mode.BYPASS_GRIM_LEGIT, Mode.BYPASS_GRIM_BAD_PACKETS))
            .build();
    public final NBTRef<OptionalPrimitive<Integer>> cooldownFix = builder(
                    cooldownGroup.add("cooldown-punishment-fix"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 5))
            .show(() -> quickMine.get() && this.fastBreakBypassMode.get().isIn(Mode.BYPASS_GRIM_BAD_PACKETS))
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> startCooldownFix = builder(
                    cooldownGroup.add("cooldown-punishment-fix-start-mine"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 8))
            .show(() -> quickMine.get()
                    && this.fastBreakBypassMode.get().isIn(Mode.BYPASS_GRIM_BAD_PACKETS, Mode.BYPASS_GRIM_LEGIT))
            .build();

    public final ModulePath vanillaBreak = fastbreak.add("vanilla-break");

    {
        portConfigs(fastbreak.add("vanilla-fast-break-fix"), vanillaBreak.add("vanilla-fast-break-fix"));
        portConfigs(fastbreak.add("no-swing-for-vanilla-break"), vanillaBreak.add("no-swing-for-vanilla-break"));
    }

    public final FlagRef fasterVanillaBreak = builder(vanillaBreak.add("faster-vanilla-break"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef vanillaFastBreakFix = builder(vanillaBreak.add("vanilla-fast-break-fix"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef noSwing =
            flagBuilder(vanillaBreak.add("no-swing-for-vanilla-break")).build();

    public final ModulePath doubleBreakMine = fastbreak.add("double-break-mine");

    {
        portConfigs(fastbreak.add("double-break"), doubleBreakMine.addEnable());
        portConfigs(fastbreak.add("ghost-hand-fail-break"), doubleBreakMine.add("ghost-hand"));
    }

    public final FlagRef doubleBreak = flagBuilder(doubleBreakMine.addEnable()).build();

    public final FlagRef doubleBreakVanilla = builder(doubleBreakMine.add("enable-vanilla-behavior"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef ghostHandFailBreak =
            flagBuilder(doubleBreakMine.add("ghost-hand")).build();

    public final EnumRef<GhostHandMode> ghostHandFailBreakMode = builder(
                    doubleBreakMine.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef ghostHandFailBreakEatingAbort =
            flagBuilder(doubleBreakMine.add("eating-abort")).build();

    public final ModulePath badPackets = fastbreak.add("packet-fix");

    public final FlagRef silent = flagBuilder(badPackets.add("silent-break")).build();

    public final FlagRef multiBreakFix =
            flagBuilder(badPackets.add("fix-multi-break")).build();

    public final FlagRef swingFix =
            flagBuilder(badPackets.add("fix-swing-packet")).build();

    public final ModulePath render = fastbreak.add("render");

    {
        portConfigs(fastbreak.add("render-current-break-pos"), render.add("render-current-break-pos"));
    }

    public final FlagRef mineRender =
            flagBuilder(render.add("render-current-break-pos")).build();

    public final NBTRef<WrapColor> frameColor = builder(render.add("render-frame-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.BLUE)))
            .build();

    public final NBTRef<WrapColor> progressColor = builder(render.add("render-progress-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.YELLOW)))
            .build();

    public final NBTRef<WrapColor> doubleBreakColor = builder(render.add("double-break-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.MAGENTA)))
            .build();

    public final FlagRef renderOnlyWhenMine =
            flagBuilder(render.add("render-only-when-mine")).build();

    private IndexEntry<ItemStack> getBestMiningToFor(BlockState currentState, GhostHandMode mode) {
        IndexEntry<ItemStack> defaultEntry = InventoryUtils.getSelectedItem();
        BlockState calculatingState =
                (currentState.isAir() || currentState.isLiquid()) ? Blocks.OBSIDIAN.getDefaultState() : currentState;
        double defaultSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                mc.player, calculatingState, defaultEntry.val());
        // only select speed > handItem, in the case that player holding a low dur tool
        IndexEntry<ItemStack> result = InventoryUtils.findBestPlayerItem(
                item -> {
                    if (item.isEmpty()
                            || item.getMaxDamage() < 10
                            || item.contains(DataComponentTypes.UNBREAKABLE)
                            || item.getDamage() < item.getMaxDamage() - 10) {
                        double speed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                                mc.player, calculatingState, item);
                        if (speed >= defaultSpeed) {
                            return speed;
                        } else {
                            return null;
                        }
                    }
                    return null;
                },
                mode.getSearchSize(false),
                true,
                true);
        return result != null ? result : defaultEntry;
    }

    public IndexEntry<ItemStack> getGhostHandMiningTool(BlockState currentState) {
        IndexEntry<ItemStack> defaultEntry = InventoryUtils.getSelectedItem();
        if (!ghostHandMine.get()) {
            return defaultEntry;
        }
        return getBestMiningToFor(currentState, GhostHandMode.INV_SWAP);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(Listener.getGameJoinPoint(), this::resetStatistics);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onMine, Integer.MAX_VALUE);
        registerListener(
                SequencedActionManager.getSequencedActionResponse().getChannel(BlockBreak.class), this::onFailBreakAck);
        registerListener(Listener.getBlockUpdateListener(), this::onFailBreakBlockUpdate);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class),
                this::onGrimSBFastBreakExplode,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class),
                this::onSilentBreak,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPostScheduleSendPoint().getChannel(HandSwingC2SPacket.class),
                this::onLastSwing,
                true);
        registerListener(Listener.getPreGameTick(), this::onGrimCooldownResetPackets);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPlayerMove);
        registerListener(Listener.getPreGameTick(), this::onTickGhostHandFailBreak);
    }

    private void onFailBreakBlockUpdate(Event<BlockUpdate> event) {
        if (mc.interactionManager == null) {
            return;
        }
        BlockUpdate update = event.context();
        PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
        if (update.newState().isAir() && Objects.equals(access.getCurrentFailBreakPos(), update.pos())) {
            access.clearActiveFailBreak();
        }
    }

    private void onFailBreakAck(Event<IndexEntry<BlockBreak>> event) {
        if (checkNull()) return;
        if (pendingFailBreak == null) return;
        IndexEntry<BlockBreak> ackEntry = event.context();
        BlockBreak ack = ackEntry.val();
        PendingFailBreak pending = pendingFailBreak;
        // has more ack
        if (ackEntry.index() > pending.seqNum()) {
            pendingFailBreak = null;
            return;
        }
        if (ack.stage() != BlockBreak.Stage.POST || ackEntry.index() != pending.seqNum()) {
            return;
        }
        pendingFailBreak = null;
        PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
        if (!access.isFailBreakEmpty()) {
            return;
        }
        if (mc.world.getBlockState(pending.pos()).isAir()) {
            return;
        }
        access.commitFailBreak(pending.pos(), pending.startBreakTick());
    }

    int lastSwingPacket = 0;
    BlockPos lastGrimACWrongBreakCheck;
    BlockPos currentMiningBlock;

    public void onLastSwing() {
        lastSwingPacket = Tasks.getTick();
    }

    public Pair<Runnable, BlockPos> instaBreakGhostHand;
    BlockPos lastServerPos;
    Direction lastServerDirection;
    boolean thisTickHasBroken;
    boolean lastMineProgressDeceive = false;

    public void finishMineGhostHand(Runnable runnable) {
        if (ServerFeature.INSTANCE.attributeSwap.get()) {
            InvExtra.INSTANCE.pushNextTickGhostHandCallback(runnable);
        } else {
            runnable.run();
        }
    }

    public void onMine(Event<PlayerActionC2SPacket> packetEvent) {
        if (packetEvent.isCancelled()) {
            return;
        }
        PlayerActionC2SPacket packet = packetEvent.context();
        if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            updateGainedAdvantageCooldown(packetEvent);
        }
        if (instaBreakGhostHand != null
                && packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                && ghostHandMine.get()
                && mc.player != null
                && mc.interactionManager != null
                && Objects.equals(packet.getPos(), instaBreakGhostHand.getSecond())) {
            if (instaBreakGhostHand.getFirst() != null) {
                Runnable first = instaBreakGhostHand.getFirst();
                PacketManager.schedulePostScheduleCallback(packet, () -> finishMineGhostHand(first));
            }
            instaBreakGhostHand = null;
        }
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK -> {
                // just for fixing grimac abort badpackets WrongBreak module
                // filter badPackets
                if (packet.getPos().getY() < 1145) {
                    lastGrimACWrongBreakCheck = packet.getPos();
                    currentMiningBlock = packet.getPos();
                    lastMineProgressDeceive = false;
                } else {
                    lastMineProgressDeceive = true;
                }
            }
            case STOP_DESTROY_BLOCK -> {
                lastGrimACWrongBreakCheck = null;
                currentMiningBlock = null;
                // FastBreak cooldown tryBypass
                lastFinishBreakingTick = Tasks.getTick();
                lastFinishBreakingCooldownTill = Tasks.getTick() + cooldownManaging();
            }
            case ABORT_DESTROY_BLOCK -> {
                if (!Objects.equals(lastGrimACWrongBreakCheck, packet.getPos())) {
                    packetEvent.cancel();
                } else {
                    lastGrimACWrongBreakCheck = null;
                }
            }
            default -> {
                return;
            }
        }
        // statistic update
        PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);

        if (packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                && mc.world != null
                && access.isFailBreakEmpty()
                && pendingFailBreak == null
                && Objects.equals(access.getCurrentMiningPos(), packet.getPos())) {
            pendingFailBreak = new PendingFailBreak(
                    packet.getSequence(), packet.getPos().toImmutable(), lastStartMineBreakingProgressResetTick);
        }

        if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            // filter bad packets/ instant break
            if (Objects.equals(access.getCurrentMiningPos(), packet.getPos())) {
                lastStartMineBreakingProgressResetTick = Tasks.getTick();
            }
        }
        // swing packet fix
        if (swingFix.get()
                && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                && Tasks.getTick() != lastSwingPacket
                && Objects.equals(access.getCurrentMiningPos(), packet.getPos())) {
            // will set lastSwingPacket in the listener above
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (packet.getAction() != PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            if (thisTickHasBroken
                    && (packet.getDirection() != lastServerDirection
                            || Objects.equals(lastServerPos, packet.getPos()))) {
                if (multiBreakFix.get()) {
                    PacketManager.schedulePostScheduleCallback(packet, () -> {
                        Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                                packet.getAction(),
                                packet.getPos(),
                                packet.getDirection(),
                                NetworkUtils.generateNextSequence()));
                    });
                }
            }
            lastServerDirection = packet.getDirection();
            lastServerPos = packet.getPos();
            thisTickHasBroken = true;
        }
    }

    public void onPlayerMove(Event<PlayerMoveC2SPacket> packetEvent) {
        var pkt = PlayerMoveC2SPacketAccess.of(packetEvent.context);
        if (pkt.getCause() == PlayerMoveC2SPacketAccess.Cause.SET_BACK
                || pkt.getCause() == PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP
                || pkt.getCause() == PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION) {
            return;
        }
        thisTickHasBroken = false;
    }

    public void onGrimSBFastBreakExplode(Event<PlayerActionC2SPacket> event) {
        if (quickMine.get() && fastBreakBypassMode.get() == Mode.BYPASS_GRIM_BAD_PACKETS && mc.player != null) {
            var packet = event.context();
            if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    && packet.getPos().getY() < 1145
                    && Objects.equals(
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos(), packet.getPos())) {
                if (mc.player.getAbilities().creativeMode) {
                    return;
                }
                BlockState state = mc.world.getBlockState(packet.getPos());
                // vanilla instant break
                if (state.isAir()) {
                    return;
                }
                // hacking instant break
                double currentBreakSpeed = WorldUtils.calcBlockBreakingDelta(state, mc.world, packet.getPos());
                // instant break, no need to bypass fastbreak
                if (currentBreakSpeed > 1.01) {
                    return;
                }
                PacketManager.schedulePostScheduleCallback(packet, () -> {
                    if (!lastMineProgressDeceive) {
                        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                                    BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                                    Direction.DOWN,
                                    seq);
                        });
                    }
                });
            }
        }
    }

    public void onSilentBreak(Event<PlayerActionC2SPacket> eventPlayerAction) {
        if (silent.get()
                && eventPlayerAction.context.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                && Objects.equals(
                        PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos(),
                        eventPlayerAction.context.getPos())) {
            BlockPos pos = eventPlayerAction.context.getPos();
            PacketManager.schedulePostScheduleCallback(eventPlayerAction.context, () -> {
                var acc = PlayerInteractionAccess.of(mc.interactionManager);
                if (Objects.equals(pos, acc.getCurrentMiningPos())) {
                    acc.sendAbortBreakPacket();
                }
            });
        }
    }

    int thisTickStartBreakPackets = 0;

    public void onGrimCooldownResetPackets() {
        thisTickStartBreakPackets = 0;
        if (quickMine.get() && fastBreakBypassMode.get() == Mode.BYPASS_GRIM_BAD_PACKETS && mc.player != null) {
            // exact tick we send,
            if (currentMiningBlock != null && Tasks.getTick() - lastFinishBreakingTick >= 6) {
                if (mc.player.getAbilities().creativeMode) {
                    return;
                }
                if (!cooldownFix.get().isPresent()) return;
                int cnt = cooldownFix.get().getValue();
                while (gainedAdvantageCooldown > 150 && thisTickStartBreakPackets < cnt) {
                    mc.interactionManager.sendSequencedPacket(
                            mc.world,
                            (seq) -> new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                                    BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                                    Direction.DOWN,
                                    seq));
                }
                ;
            }
        }
    }

    public void onTickGhostHandFailBreak() {
        if (checkNull()) return;
        InvExtra.INSTANCE.tickNextTickCallbacks();
        if (PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos() != null
                && ghostHandFailBreak.get()) {
            tickGhostHandDoubleBreak(null, ghostHandFailBreakMode.get(), false);
        }
    }

    public int lastTickGhostHandFailBreak = 0;

    public boolean canMineFailBreak(BlockState state, ItemStack tool, boolean groundDeceive) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (state.getBlock().getHardness() >= 0.0F && !state.isLiquid() && !state.isAir()) {
            var access = PlayerInteractionAccess.of(mc.interactionManager);
            var speed = access.predictFailMiningProgressWithTool(tool, 0);
            if (groundDeceive && !mc.player.isOnGround()) {
                speed *= 5;
            }
            return speed > 0.99;
        } else {
            return false;
        }
    }

    public void tickGhostHandDoubleBreak(Runnable currentTickCallback, GhostHandMode mode, boolean groundDeceive) {
        if (lastTickGhostHandFailBreak == Tasks.getTick()) {
            if (currentTickCallback != null) {
                finishMineGhostHand(currentTickCallback);
            }
            return;
        }
        if (mc.player.isUsingItem() && ghostHandFailBreakEatingAbort.get()) {
            if (currentTickCallback != null) {
                finishMineGhostHand(currentTickCallback);
            }
            return;
        }
        if (InvExtra.INSTANCE.hasNextTickGhostHandCallback()) {
            if (currentTickCallback != null) {
                finishMineGhostHand(currentTickCallback);
            }
            return;
        }
        lastTickGhostHandFailBreak = Tasks.getTick();

        BlockPos failPos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
        if (failPos == null) {
            if (currentTickCallback != null) {
                finishMineGhostHand(currentTickCallback);
            }
            return;
        }
        BlockState blockState = mc.world.getBlockState(failPos);
        IndexEntry<ItemStack> currentItemSlot = getBestMiningToFor(blockState, mode);
        ItemStack currentTool = currentItemSlot.val();
        if (canMineFailBreak(blockState, currentTool, groundDeceive)) {
            // force delay, because it might save some packets
            if (currentTickCallback != null) {
                InvExtra.INSTANCE.pushNextTickGhostHandCallback(currentTickCallback);
            }
            Runnable callback = InvExtra.INSTANCE.swapItemToHand(currentItemSlot.index(), false, mode);
            InvExtra.INSTANCE.pushNextTickGhostHandCallback(callback);
        } else {
            if (currentTickCallback != null) {
                finishMineGhostHand(currentTickCallback);
            }
        }
    }

    public int lastFinishBreakingTick;
    public int lastFinishBreakingCooldownTill;

    public int gainedAdvantageCooldown;

    public int lastStartMineBreakingProgressResetTick = 0;

    public boolean lastStartingMineIsInstantBreak = false;

    public int gainedAdvantageMining;

    public int ignoreNextFastBreakStatus = 0;

    public void resetStatistics() {
        lastFinishBreakingTick = 0;
        lastFinishBreakingCooldownTill = 0;
        gainedAdvantageCooldown = 0;
        lastStartingMineIsInstantBreak = false;
        gainedAdvantageMining = 0;
        ignoreNextFastBreakStatus = 0;
        pendingFailBreak = null;
    }

    public boolean isVanillaMineCooldownComplete(int extra) {
        return Tasks.getTick() - lastFinishBreakingTick >= 6 + extra;
    }

    public int getMiningPacketCooldown(int extra) {
        return Math.max(0, lastFinishBreakingCooldownTill + extra - Tasks.getTick());
    }

    public boolean isSafeCooldownThreshold() {
        if (fastBreakBypassMode.get().hasAc()) {
            if (fastBreakBypassMode.get() == Mode.BYPASS_GRIM_LEGIT) {
                if (gainedAdvantageCooldown > grimAcCooldownThreshold.get()) {
                    return false;
                }
            } else if (fastBreakBypassMode.get() == Mode.BYPASS_GRIM_BAD_PACKETS) {
                // still magic numbers...
                if (doubleBreak.get()) {
                    if (gainedAdvantageCooldown > 150) {
                        return false;
                    }
                } else {
                    if (gainedAdvantageCooldown > 300) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int cooldownManaging() {
        boolean fastBreak = cooldownOverride.get();
        int cooldownOverride = (fastBreak && breakCooldown.get() >= 0) ? breakCooldown.get() : 5;

        if (cooldownOverride < 5) {
            if (!isSafeCooldownThreshold()) {
                // shit......
                return 5;
            }
        }
        return cooldownOverride;
    }

    public void onStartingMine(BlockPos pos, float speed, boolean instaBreak) {
        MineExtra mineExtra = this;
        lastStartingMineIsInstantBreak = instaBreak || speed > Math.min(1.0F, mineExtra.breakThreshold.get());
    }

    private void updateGainedAdvantageCooldown(Event<PlayerActionC2SPacket> event) {
        thisTickStartBreakPackets += 1;
        MineExtra mineExtra = this;
        // escape init case
        if (lastFinishBreakingTick == 0) return;
        int thisCurrentTick = Tasks.getTick();
        // this means it is ok to directly mine
        boolean canResetThisTime = false;
        int threshold = mineExtra.grimAcCooldownThreshold.get();
        if (!mc.player.getAbilities().creativeMode) {
            if (thisCurrentTick >= lastFinishBreakingTick + 6) {
                canResetThisTime = true;
                gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
            } else {
                gainedAdvantageCooldown += 300 - (thisCurrentTick - lastFinishBreakingTick) * 50;
            }
        } else {
            gainedAdvantageCooldown = Math.min(threshold, gainedAdvantageCooldown);
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);

        // we will deal the cooldown shit of bad packets mode in the duplication count of bad packets
        if (gainedAdvantageCooldown > threshold
                && canResetThisTime
                && isVanillaMineCooldownComplete(1)
                && mineExtra.fastBreakBypassMode.get().isIn(Mode.BYPASS_GRIM_LEGIT, Mode.BYPASS_GRIM_BAD_PACKETS)
                && (startCooldownFix.get().isPresent() || doubleBreak.get())
                && mc.player != null
                && !mc.player.getAbilities().creativeMode) {
            // reset
            BlockPos pos = event.context.getPos();
            BlockState state = mc.world.getBlockState(pos);
            // vanilla instant break
            if (state.isAir()) {
                return;
            }
            // hacking instant break
            double currentBreakSpeed = WorldUtils.calcBlockBreakingDelta(state, mc.world, pos);
            // instant break, no need to bypass fastbreak
            if (currentBreakSpeed > 1.01) {
                return;
            }
            int duplicatedMax = startCooldownFix.get().orElse(5);
            PacketManager.schedulePostScheduleCallback(event.context, () -> {
                if (thisTickStartBreakPackets < duplicatedMax) {
                    mc.interactionManager.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                        return new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                                pos,
                                event.context.getDirection(),
                                sequence);
                    }));
                }
            });
        }
    }

    public void onPostStopMiningLegally(BlockPos pos) {
        MineExtra mineExtra = MineExtra.INSTANCE;
        int threshold = mineExtra.grimACProgressThreshold.get();
        ignoreNextFastBreakStatus = 0;
        if (mineExtra.fastBreakBypassMode.getValue() == Mode.BYPASS_GRIM_BAD_PACKETS) {
            // badpackets, no punishment, 桀桀桀
            gainedAdvantageMining = 0;
        } else {
            gainedAdvantageMining = (int) (gainedAdvantageMining * 0.9);
            if (gainedAdvantageMining > threshold
                    && mineExtra.fastBreakBypassMode.getValue() == Mode.BYPASS_GRIM_LEGIT
                    && mc.player != null) {
                gainedAdvantageMining = 150;
                if (!mc.player.getAbilities().creativeMode) {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos()))
                            .getOpposite();
                    for (int i = 0; i < 20; ++i) {
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> {
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence);
                        }));
                    }
                }
            }
        }
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -1000, 1000);
    }

    public void onPostStopMiningFastBreak(BlockPos pos, double speed, double currentProgress) {
        ignoreNextFastBreakStatus = 0;
        if (lastStartMineBreakingProgressResetTick == 0) {
            return;
        }
        MineExtra mineExtra = MineExtra.INSTANCE;
        int predictTick = (int) Math.ceil(1 / speed);
        int tickUsed = (int) Math.ceil(currentProgress / speed);
        int diff = predictTick - tickUsed;
        gainedAdvantageMining += (diff + 1) * 50;
        int threshold = mineExtra.grimACProgressThreshold.get();
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -1000, 1000);
        if (gainedAdvantageMining > threshold && mineExtra.fastBreakBypassMode.getValue() == Mode.BYPASS_GRIM_LEGIT) {
            // only when starting bypass will we do
            // trigger a common mine
            ignoreNextFastBreakStatus = 2;
        }
    }

    public boolean shouldExecuteOptimizeOneBlock() {
        if (this.lastStartMineBreakingProgressResetTick == 0) {
            return false;
        }
        return true;
    }

    /**
     * 判断当前主挖掘进度是否已经可以直接走 fastbreak 收尾。
     *
     * <p>这里不消费 ignore 状态，只负责告诉 mixin：当前这一次 update 是否应立刻走 stop 路径。
     */
    public boolean shouldExecuteFastBreak(float currentProgress) {
        return (quickMine.get() && ignoreNextFastBreakStatus <= 0)
                ? (currentProgress >= breakThreshold.get())
                : (currentProgress > 1.0D);
    }

    /**
     * 判断一个挖掘速度是否应被视为 instant / pseudo-instant 分支。
     *
     * <p>它统一复用 breakThreshold 与 fakeInstaBreak 的阈值语义，避免这些判定散落在多个 hook 和接口实现里。
     */
    public boolean shouldTreatAsInstantBreak(float speed) {
        return speed >= 1.0F || (speed > breakThreshold.get() && quickMine.get());
    }

    /**
     * 判断这次 attack 后是否应立即补一个 stop，实现 early stop。
     */
    public boolean shouldTriggerEarlyStop(float speed) {
        return quickMine.get() && fakeInstaBreak.get() && speed < 1.0F && speed > breakThreshold.get();
    }

    /**
     * 切换到新方块时，旧方块是否仍值得转入 doubleBreak / failBreak 支线。
     */
    public boolean shouldTryDoubleBreak(float predictedProgress) {
        return doubleBreak.get() && predictedProgress <= 1.0F;
    }

    /**
     * 判断 quickMine 当前 tick 是否允许生效，并在需要时消费一次忽略窗口。
     */
    public boolean shouldUseQuickMine() {
        if (ignoreNextFastBreakStatus > 0) {
            // I accept the status !
            ignoreNextFastBreakStatus -= 1;
            // somehow we left one status here because of fastBreak
            if (ignoreNextFastBreakStatus > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldRenderMine() {
        if (!renderOnlyWhenMine.get()) return true;
        if (!mc.interactionManager.isBreakingBlock() && !PacketMine.INSTANCE.autoEnable.get()) {
            if (!optimizeOneBlock.get()) {
                return false;
            }
            BlockPos blockPos =
                    PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            BlockState state = mc.world.getBlockState(blockPos);
            if (state.isAir()) {
                return false;
            }
        }
        return true;
    }

    public void onRender(Event<Render3D> renderEvent) {
        if (mineRender.get()) {

            RenderUtils.startDrawVirtual(renderEvent.context.stack());
            try {
                if (mc.interactionManager != null && mc.player != null && mc.world != null) {
                    BlockPos blockPos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                    Vec3d pos = Vec3d.of(blockPos);
                    // 超过200格的不渲染
                    if (mc.player.getPos().squaredDistanceTo(pos) < 40000 && shouldRenderMine()) {
                        RenderUtils.drawOutlinedBox(
                                renderEvent.context.stack(),
                                pos,
                                pos.add(1.0, 1.0, 1.0),
                                ColorUtils.withAlpha(frameColor.get().color(), 1.0F));
                        BlockState state = mc.world.getBlockState(blockPos);
                        var tool = getGhostHandMiningTool(state);
                        float progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .predictCurrentMiningProgressWithTool(tool.val(), 1);
                        if (progress > 0.0F) {
                            Box box;
                            if (state.isAir()) {
                                box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                            } else {
                                VoxelShape shape = state.getOutlineShape(mc.world, blockPos);
                                box = shape.isEmpty() ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) : shape.getBoundingBox();
                            }
                            Vec3d vec3 =
                                    box.getMaxPos().subtract(box.getMinPos()).multiply(0.5);

                            Vec3d vec3d = pos.add(box.getCenter());
                            float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
                            RenderUtils.drawSolidBox(
                                    renderEvent.context.stack(),
                                    vec3d.add(vec3.multiply(-clamped)),
                                    vec3d.add(vec3.multiply(clamped)),
                                    ColorUtils.withAlpha(progressColor.get().color(), 0.25F));
                        }
                    }

                    BlockPos doubleMinePos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
                    if (doubleMinePos != null) {
                        Vec3d doubleMineVec = Vec3d.of(doubleMinePos);
                        if (mc.player.getPos().squaredDistanceTo(doubleMineVec) < 40000
                                && !Objects.equals(doubleMineVec, pos)) {
                            float progressFail;
                            if (lastTickGhostHandFailBreak > Tasks.getTick() - 2) {
                                progressFail = PlayerInteractionAccess.of(mc.interactionManager)
                                        .predictFailMiningProgressWithTool(
                                                getBestMiningToFor(
                                                                mc.world.getBlockState(doubleMinePos),
                                                                GhostHandMode.INV_SWAP)
                                                        .val(),
                                                0);
                            } else {
                                progressFail = PlayerInteractionAccess.of(mc.interactionManager)
                                        .getFailBreakMiningProgress();
                            }

                            RenderUtils.drawOutlinedBox(
                                    renderEvent.context.stack(),
                                    doubleMineVec,
                                    doubleMineVec.add(1.0, 1.0, 1.0),
                                    ColorUtils.withAlpha(doubleBreakColor.get().color(), 1.0F));
                            if (progressFail > 0.0F) {
                                BlockState state = mc.world.getBlockState(doubleMinePos);
                                Box box;
                                if (state.isAir()) {
                                    box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                                } else {
                                    VoxelShape shape = state.getOutlineShape(mc.world, doubleMinePos);
                                    box = shape.isEmpty()
                                            ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                                            : shape.getBoundingBox();
                                }
                                Vec3d vec3 = box.getMaxPos()
                                        .subtract(box.getMinPos())
                                        .multiply(0.5);

                                Vec3d vec3d = doubleMineVec.add(box.getCenter());
                                float clamped = MathHelper.clamp(progressFail, 0.0F, 1.0F);
                                RenderUtils.drawSolidBox(
                                        renderEvent.context.stack(),
                                        vec3d.add(vec3.multiply(-clamped)),
                                        vec3d.add(vec3.multiply(clamped)),
                                        ColorUtils.withAlpha(progressColor.get().color(), 0.25F));
                            }
                        }
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(renderEvent.context.stack());
            }
        }
    }

    public float predictBlockBreakingSpeedAt(BlockPos pos) {
        if (mc.player.isCreative()) return 10000.0F;
        BlockState state = mc.world.getBlockState(pos);
        IndexEntry<ItemStack> stackEntry = getGhostHandMiningTool(state);
        float speed1 = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, state, stackEntry.val());
        return WorldUtils.calcBlockBreakingDelta(state, MinecraftClient.getInstance().world, pos, speed1);
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        if (presetEvent.context().getValue().isOptionPreset()) return;
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                fastBreakBypassMode.set(Mode.BYPASS_GRIM_BAD_PACKETS);
                vanillaFastBreakFix.set(true);
            }
            default -> {
                fastBreakBypassMode.set(Mode.NO_BYPASS);
                vanillaFastBreakFix.set(false);
            }
        }
    }

    public static enum Mode implements ConfigEnum {
        NO_BYPASS,
        BYPASS_GRIM_LEGIT,
        BYPASS_GRIM_BAD_PACKETS;

        public boolean hasAc() {
            return this != NO_BYPASS;
        }

        @Override
        public String getConfigEnumType() {
            return "fast_break_bypass_mode";
        }
    }
}
