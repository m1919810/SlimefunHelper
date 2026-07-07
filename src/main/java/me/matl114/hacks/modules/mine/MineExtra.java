package me.matl114.hacks.modules.mine;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.*;
import me.matl114.events.Event;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Unique;

public class MineExtra extends BaseModule {
    public static MineExtra INSTANCE;

    public MineExtra() {
        INSTANCE = this;
    }

    public List<Vec3i> blocksAround = new ArrayList<>();

    public List<Vector2i> platesAround = new ArrayList<>();

    public double lastRange;

    public List<Vec3i> getBlocksAround() {
        if (mc.player != null) {
            refreshInteractionRange(getReachDistance());
        }
        return Collections.unmodifiableList(blocksAround);
    }

    public List<Vector2i> getPlatesAround() {
        if (mc.player != null) {
            refreshInteractionRange(getReachDistance());
        }
        return Collections.unmodifiableList(platesAround);
    }

    public void refreshInteractionRange(double val) {
        if (mc.player != null) {
            if (lastRange != val) {
                // update interaction range lazily
                lastRange = val;
                List<Vec3i> points = new ArrayList<>();
                int range = (int) lastRange;
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                            points.add(new Vec3i(x, y, z));
                        }
                    }
                }
                points.sort(Comparator.comparingDouble(
                        v -> v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ()));
                blocksAround = points;
                List<Vector2i> plates = new ArrayList<>();
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        plates.add(new Vector2i(x, y));
                    }
                }
                plates.sort(Comparator.comparingDouble(v -> v.x * v.x + v.y * v.y));
                platesAround = plates;
            }
        }
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

    public final IntRef grimAcCounterThreshold = builder(fastbreak.add("grim-punishment-threshold"), Integer.class)
            .defaultValue(500)
            .show(() -> fastBreakBypassMode.get() == Mode.BYPASS_GRIM_LEGIT)
            .build();

    public final DoubleRef breakThreshold = builder(fastbreak.add("break-threshold"), Double.class)
            .defaultValue(0.99)
            .validator(Configs.doubleRange(0.0, 1.1))
            .build();

    public final IntRef breakCooldown = builder(fastbreak.add("break-cooldown"), Integer.class)
            .defaultValue(5)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    //    public final FlagRef enableReach = toggle(REACH_TOGGLE).showConfig().build();
    //
    //    public final KeyBindRef reachKeybind = toggleHotkey(
    //                    REACH_TOGGLE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_R))
    //            .build();

    public final DoubleRef reachDistance = builder(fastbreak.add("reach-distance"), Double.class)
            .defaultValue(0.0)
            .build();

    public final FlagRef doubleBreak =
            flagBuilder(fastbreak.add("double-break")).build();

    public final FlagRef optimizeOneBlock =
            flagBuilder(fastbreak.add("same-block-optimize")).build();

    public final FlagRef swingFix =
            flagBuilder(fastbreak.add("fix-swing-packet")).build();

    public final FlagRef mineRender =
            flagBuilder(fastbreak.add("render-current-break-pos")).build();

    public final FlagRef renderOnlyWhenMine =
            flagBuilder(fastbreak.add("render-only-when-mine")).build();

    public final FlagRef multiBreakFix =
            flagBuilder(fastbreak.add("fix-multi-break")).build();

    public final FlagRef ghostHandMine =
            flagBuilder(fastbreak.add("ghost-hand-mine")).build();

    public IndexEntry<ItemStack> getGhostHandMiningTool(BlockState currentState) {
        if (!ghostHandMine.get()) {
            return InventoryUtils.getSelectedItem();
        }
        BlockState calculatingState =
                currentState.isAir() || currentState.isLiquid() ? Blocks.OBSIDIAN.getDefaultState() : currentState;
        IndexEntry<ItemStack> result = InventoryUtils.findBestPlayerItem(
                item -> {
                    if (item.isEmpty()
                            || item.getMaxDamage() < 10
                            || item.contains(DataComponentTypes.UNBREAKABLE)
                            || item.getDamage() < item.getMaxDamage() - 10) {
                        return (double) WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                                mc.player, calculatingState, item);
                    }
                    return null;
                },
                true,
                true);
        return result != null ? result : InventoryUtils.getSelectedItem();
    }

    public double getReachDistance() {
        return mc.player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE) + reachDistance.get();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(Listener.getGameJoinPoint(), this::onGameJoin);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onMine);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onGrimSBFastBreakExplode);
        registerListener(Listener.getPacketPostSendPoint().getChannel(HandSwingC2SPacket.class), this::onLastSwing);
        registerListener(Listener.getPreGameTick(), this::onGrimCooldownResetPackets);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPlayerMove);
    }

    public void onGameJoin(Event<ClientPlayerEntity> gameJoin) {
        resetStatistics();
    }

    int lastSwingPacket = 0;
    BlockPos lastBreak;

    public void onLastSwing(Event<HandSwingC2SPacket> handSwingC2SPacketEvent) {
        lastSwingPacket = Tasks.getTick();
    }

    int lastFinishBreakPacket = 0;
    public Pair<Runnable, BlockPos> instaBreakGhostHand;
    public Pair<Runnable, BlockPos> fastBreakGhostHand;
    BlockPos lastServerPos;
    Direction lastServerDirection;
    boolean thisTickHasBroken;

    public void onMine(Event<PlayerActionC2SPacket> packetEvent) {
        PlayerActionC2SPacket packet = packetEvent.context();
        if (fastBreakGhostHand != null
                && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                && ghostHandMine.get()
                && mc.player != null
                && mc.interactionManager != null
                && Objects.equals(packet.getPos(), fastBreakGhostHand.getSecond())) {
            if (fastBreakGhostHand.getFirst() != null) {
                PacketManager.schedulePostScheduleCallback(packet, fastBreakGhostHand.getFirst());
            }
            fastBreakGhostHand = null;
        }
        if (instaBreakGhostHand != null
                && packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                && ghostHandMine.get()
                && mc.player != null
                && mc.interactionManager != null
                && Objects.equals(packet.getPos(), instaBreakGhostHand.getSecond())) {
            if (instaBreakGhostHand.getFirst() != null) {
                PacketManager.schedulePostScheduleCallback(packet, instaBreakGhostHand.getFirst());
            }
            instaBreakGhostHand = null;
        }
        // just for fixing grimac abort badpackets
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK -> {
                lastBreak = packet.getPos();
            }
            case STOP_DESTROY_BLOCK -> {
                lastBreak = null;
                lastFinishBreakingTick = Tasks.getTick();
            }
            case ABORT_DESTROY_BLOCK -> {
                if (!Objects.equals(lastBreak, packet.getPos())) {
                    packetEvent.cancel();
                } else {
                    lastBreak = null;
                }
            }
            default -> {
                return;
            }
        }
        // statistic update
        if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
            // filter bad packets
            if (Objects.equals(access.getCurrentMiningPos(), packet.getPos())) {
                lastStartMineBreakingProgressResetTick = Tasks.getTick();
            }
        }
        // swing packet fix
        if (swingFix.get()
                && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                && Tasks.getTick() != lastSwingPacket) {
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
                    gainedAdvantageCooldown = 150;
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
                int duplicate = (doubleBreak.get() && (Tasks.getTick() - lastFinishBreakingTick) >= 5) ? 6 : 1;
                PacketManager.schedulePostScheduleCallback(packet, () -> {
                    for (var i = 0; i < duplicate; ++i) {
                        //                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        //                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                        //                        BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                        //                        Direction.DOWN,
                        //                        packet.getSequence()));
                        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                                    BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                                    Direction.DOWN,
                                    seq);
                        });
                        if ((Tasks.getTick() - lastFinishBreakingTick) >= 5) {
                            gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
                        } else {
                            gainedAdvantageCooldown += (300 - (Tasks.getTick() - lastFinishBreakingTick) * 50);
                        }
                    }
                });
            }
        }
    }

    public void onGrimCooldownResetPackets(Event<ClientPlayerEntity> tickEvent) {
        if (quickMine.get() && fastBreakBypassMode.get() == Mode.BYPASS_GRIM_BAD_PACKETS && mc.player != null) {
            // exact tick we send,
            if (lastBreak != null && Tasks.getTick() - lastFinishBreakingTick == 6) {
                if (mc.player.getAbilities().creativeMode) {
                    gainedAdvantageCooldown = 150;
                    return;
                }
                do {
                    mc.interactionManager.sendSequencedPacket(
                            mc.world,
                            (seq) -> new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                                    BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                                    Direction.DOWN,
                                    seq));
                    gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
                } while (gainedAdvantageCooldown > 100);
            }
        }
    }

    public int lastFinishBreakingTick;

    public int gainedAdvantageCooldown;

    public boolean thisTimeOptimizedSamePosBreak;

    public int lastStartMineBreakingProgressResetTick = 0;

    public boolean lastStartingMineIsInstantBreak = false;

    public int gainedAdvantageMining;

    public int ignoreNextFastBreakStatus = 0;

    public void resetStatistics() {
        lastStartingMineIsInstantBreak = false;
        lastFinishBreakingTick = 0;
        gainedAdvantageCooldown = 0;
        thisTimeOptimizedSamePosBreak = false;
        lastStartingMineIsInstantBreak = false;
        gainedAdvantageMining = 0;
        ignoreNextFastBreakStatus = 0;
    }

    @Unique
    public int cooldownManaging() {
        boolean fastBreak = quickMine.get();
        int cooldownOverride = (fastBreak && breakCooldown.get() >= 0) ? breakCooldown.get() : 5;
        if (thisTimeOptimizedSamePosBreak) {
            thisTimeOptimizedSamePosBreak = false;
            cooldownOverride = Math.max(1, cooldownOverride);
        }
        if (cooldownOverride < 5) {
            if (fastBreakBypassMode.get().hasAc()) {
                // shit......
                if (false && doubleBreak.get()) {
                    return 5;
                }
                if (fastBreakBypassMode.get() == Mode.BYPASS_GRIM_LEGIT) {
                    if (gainedAdvantageCooldown > grimAcCounterThreshold.get()) {
                        return 5;
                    }
                } else if (fastBreakBypassMode.get() == Mode.BYPASS_GRIM_BAD_PACKETS) {
                    // still magic numbers...
                    if (doubleBreak.get()) {
                        if (gainedAdvantageCooldown > 100) {
                            return 5;
                        }
                    } else {
                        if (gainedAdvantageCooldown > 300) {
                            return 5;
                        }
                    }
                }
            }
        }
        return cooldownOverride;
    }

    public void onStartingMine(BlockPos pos, float speed, boolean instaBreak) {
        MineExtra mineExtra = this;
        lastStartingMineIsInstantBreak = instaBreak || speed > Math.min(1.0F, mineExtra.breakThreshold.get());

        if (!mineExtra.quickMine.get()) {
            return;
        }
        // escape init case
        if (lastFinishBreakingTick == 0) return;
        if (instaBreak) return;
        int thisCurrentTick = Tasks.getTick();
        // this means it is ok to directly mine
        boolean canResetThisTime = false;
        if (thisCurrentTick >= lastFinishBreakingTick + 5) {
            canResetThisTime = true;
            gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
        } else {
            gainedAdvantageCooldown += 300 - (thisCurrentTick - lastFinishBreakingTick) * 50;
        }
        int threshold = mineExtra.grimAcCounterThreshold.get();
        // we will deal the cooldown shit of bad packets mode in the duplication count of bad packets
        if (gainedAdvantageCooldown > threshold
                && canResetThisTime
                && mineExtra.fastBreakBypassMode.getValue() == Mode.BYPASS_GRIM_LEGIT
                && mc.player != null) {
            // reset
            gainedAdvantageCooldown = 150;
            if (!mc.player.getAbilities().creativeMode) {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                Direction dir = Direction.getFacing(pos.toCenterPos().subtract(player.getEyePos()))
                        .getOpposite();
                for (int i = 0; i < 20; ++i) {
                    mc.interactionManager.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
                        return new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir, sequence);
                    }));
                }
            }
        }
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
    }

    public void onPostStopMiningLegally(BlockPos pos) {
        MineExtra mineExtra = MineExtra.INSTANCE;
        int threshold = mineExtra.grimAcCounterThreshold.get();
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
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
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
        int threshold = mineExtra.grimAcCounterThreshold.get();
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
        thisTimeOptimizedSamePosBreak = true;
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
                : (currentProgress > 1.01D);
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

    public void onRender(Event<MatrixStack> renderEvent) {
        if (mineRender.get()) {

            RenderUtils.startDrawVirtual(renderEvent.context);
            try {
                if (mc.interactionManager != null && mc.player != null && mc.world != null) {
                    BlockPos blockPos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                    Vec3d pos = Vec3d.of(blockPos);
                    // 超过200格的不渲染
                    if (mc.player.getPos().squaredDistanceTo(pos) < 40000 && shouldRenderMine()) {
                        RenderUtils.drawOutlinedBox(renderEvent.context, pos, pos.add(1.0, 1.0, 1.0), Color.BLUE);
                        BlockState state = mc.world.getBlockState(blockPos);
                        var tool = getGhostHandMiningTool(state);
                        float progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .getCurrentMiningProgress(tool.val());
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
                                    renderEvent.context,
                                    vec3d.add(vec3.multiply(-clamped)),
                                    vec3d.add(vec3.multiply(clamped)),
                                    ColorUtils.withAlpha(Color.YELLOW, 0.25F));
                        }
                    }

                    BlockPos doubleMinePos =
                            PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
                    if (doubleMinePos != null) {
                        Vec3d doubleMineVec = Vec3d.of(doubleMinePos);
                        if (mc.player.getPos().squaredDistanceTo(doubleMineVec) < 40000
                                && !Objects.equals(doubleMineVec, pos)) {
                            float progressFail = PlayerInteractionAccess.of(mc.interactionManager)
                                    .getFailBreakMiningProgress();
                            RenderUtils.drawOutlinedBox(
                                    renderEvent.context,
                                    doubleMineVec,
                                    doubleMineVec.add(1.0, 1.0, 1.0),
                                    Color.MAGENTA);
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
                                        renderEvent.context,
                                        vec3d.add(vec3.multiply(-clamped)),
                                        vec3d.add(vec3.multiply(clamped)),
                                        ColorUtils.withAlpha(Color.ORANGE, 0.25F));
                            }
                        }
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(renderEvent.context);
            }
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                fastBreakBypassMode.set(Mode.BYPASS_GRIM_BAD_PACKETS);
            }
            default -> {
                fastBreakBypassMode.set(Mode.NO_BYPASS);
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
