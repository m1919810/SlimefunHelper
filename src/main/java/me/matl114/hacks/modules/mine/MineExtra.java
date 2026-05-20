package me.matl114.hacks.modules.mine;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.*;
import me.matl114.events.Event;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.NetworkUtils;
import me.matl114.utils.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Unique;

public class MineExtra extends BaseModule {
    public static final String[] QUICK_MINE = {"fast-break", "quick-mine"};
    public static final String[] QUICK_MINE_HOTKEY = {"fast-break", "quick-mine-hotkey"};
    //    public static final String[] MINE_REACH_HOTKEY = {"hotkeys-toggle", "reach"};
    public static final String[] MINE_ENABLE_FAKE_INSTANT_BREAK = {"fast-break", "use-fake-instant-break"};

    public static final String[] MINE_BYPASS_FAST_BREAK_BYPASS_MODE = {"fast-break", "bypass-mode"};

    public static final String[] MINE_FASTBREAK_THRESHOLD = {"fast-break", "break-threshold"};
    public static final String[] MINE_FASTBREAK_BREAKCOOLDOWN = {"fast-break", "break-cooldown"};
    public static final String[] MINE_FASTBREAK_REACH = {"fast-break", "reach-distance"};

    public static final String[] MINE_DOUBLE_BREAK = {"fast-break", "double-break"};

    public static final String[] MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE = {"fast-break", "same-block-optimize"};
    public static final String[] MINE_RENDER_CURRENT_MINING_BLOCK = {"fast-break", "render-current-break-pos"};
    public static final String[] FAST_BREAK_GRIMAC_THRESHOLD = {"fast-break", "grim-punishment-threshold"};

    public static final String[] BREAK_FIX_SWING_PACKET = {"fast-break", "fix-swing-packet"};

    public MineExtra() {}

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

    public final FlagRef quickMine =
            flagBuilder(Configs.MINE_CONFIG, QUICK_MINE).build();

    public final KeyBindRef quickMineKeyBind = toggleHotkey(
                    Configs.MINE_CONFIG,
                    QUICK_MINE_HOTKEY,
                    new MultiKeyBind(),
                    QUICK_MINE)
            .build();

    public final FlagRef fakeInstaBreak = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_ENABLE_FAKE_INSTANT_BREAK)
            .defaultValue(false)
            .build();

    public final EnumRef<FastBreakBypassMode> fastBreakBypassMode = builder(
                    Configs.MINE_CONFIG, FastBreakBypassMode.class)
            .path(MINE_BYPASS_FAST_BREAK_BYPASS_MODE)
            .defaultValue(FastBreakBypassMode.NO_BYPASS)
            .build();

    public final IntRef grimAcCounterThreshold = builder(Configs.MINE_CONFIG, Integer.class)
            .path(FAST_BREAK_GRIMAC_THRESHOLD)
            .defaultValue(500)
            .build();

    public final DoubleRef breakThreshold = builder(Configs.MINE_CONFIG, Double.class)
            .path(MINE_FASTBREAK_THRESHOLD)
            .defaultValue(0.99)
            .validator(Configs.doubleRange(0.0, 1.1))
            .build();

    public final IntRef breakCooldown = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_FASTBREAK_BREAKCOOLDOWN)
            .defaultValue(5)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    //    public final FlagRef enableReach = toggle(REACH_TOGGLE).showConfig().build();
    //
    //    public final KeyBindRef reachKeybind = toggleHotkey(
    //                    REACH_TOGGLE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_R))
    //            .build();

    public final DoubleRef reachDistance = builder(Configs.MINE_CONFIG, Double.class)
            .path(MINE_FASTBREAK_REACH)
            .defaultValue(0.0)
            .build();

    public final FlagRef doubleBreak = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_DOUBLE_BREAK)
            .defaultValue(false)
            .build();

    public final FlagRef optimizeOneBlock = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE)
            .defaultValue(false)
            .build();

    public final FlagRef swingFix =
            flagBuilder(Configs.MINE_CONFIG, BREAK_FIX_SWING_PACKET).build();

    public final FlagRef mineRender = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_RENDER_CURRENT_MINING_BLOCK)
            .defaultValue(false)
            .build();

    public final FlagRef grimBadPacketFix1 = flagBuilder(Configs.MINE_CONFIG, makePath("fast-break.grim-badpackets-1"))
            .build();

    public double getReachDistance() {
        return mc.player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE) + reachDistance.get();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(Listener.getGameJoinPoint(), this::onGameJoin);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onMine);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onGrimSBFastBreakExplode);
        registerListener(Listener.getPacketPostSendPoint().getChannel(HandSwingC2SPacket.class), this::onLastSwing);
        registerListener(Listener.getPreGameTick(), this::onGrimCooldownResetPackets);
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

    public void onMine(Event<PlayerActionC2SPacket> packetEvent) {
        PlayerActionC2SPacket packet = packetEvent.context();
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
    }

    public void onGrimSBFastBreakExplode(Event<PlayerActionC2SPacket> event) {
        if (quickMine.get()
                && fastBreakBypassMode.get() == FastBreakBypassMode.BYPASS_GRIM_BAD_PACKETS
                && mc.player != null) {
            var packet = event.context();
            if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    && packet.getPos().getY() < 1145) {
                int duplicate = (doubleBreak.get() && (Tasks.getTick() - lastFinishBreakingTick) >= 5) ? 6 : 1;
                List<PlayerActionC2SPacket> actionPackets = new ArrayList<>();

                for (var i = 0; i < duplicate; ++i) {
                    //                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    //                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    //                        BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                    //                        Direction.DOWN,
                    //                        packet.getSequence()));
                    actionPackets.add(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                            BlockPos.ofFloored(mc.player.getPos()).withY(9178),
                            Direction.DOWN,
                            NetworkUtils.generateNextSequence()));
                    if ((Tasks.getTick() - lastFinishBreakingTick) >= 5) {
                        gainedAdvantageCooldown = (int) (gainedAdvantageCooldown * 0.9);
                    } else {
                        gainedAdvantageCooldown += (300 - (Tasks.getTick() - lastFinishBreakingTick) * 50);
                    }
                }
                PacketManager.schedulePostCallback(packet, () -> {
                    for (var pkt : actionPackets) {
                        mc.getNetworkHandler().sendPacket(pkt);
                    }
                });
            }
        }
    }

    public void onGrimCooldownResetPackets(Event<ClientPlayerEntity> tickEvent) {
        if (quickMine.get() && fastBreakBypassMode.get() == FastBreakBypassMode.BYPASS_GRIM_BAD_PACKETS) {
            // exact tick we send,
            if (lastBreak != null && Tasks.getTick() - lastFinishBreakingTick == 6) {
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

    public boolean nextTickEarlyBreak = false;

    public void resetStatistics() {
        lastStartingMineIsInstantBreak = false;
        lastFinishBreakingTick = 0;
        gainedAdvantageCooldown = 0;
        thisTimeOptimizedSamePosBreak = false;
        lastStartingMineIsInstantBreak = false;
        gainedAdvantageMining = 0;
        ignoreNextFastBreakStatus = 0;
        nextTickEarlyBreak = false;
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
                if (fastBreakBypassMode.get() == FastBreakBypassMode.BYPASS_GRIM_LEGIT) {
                    if (gainedAdvantageCooldown > grimAcCounterThreshold.get()) {
                        return 5;
                    }
                } else if (fastBreakBypassMode.get() == FastBreakBypassMode.BYPASS_GRIM_BAD_PACKETS) {
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
                && mineExtra.fastBreakBypassMode.getValue() == FastBreakBypassMode.BYPASS_GRIM_LEGIT) {
            // reset
            gainedAdvantageCooldown = 150;
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
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
    }

    public void onPostStopMiningLegally(BlockPos pos) {
        MineExtra mineExtra = MineTasks.getMineExtra();
        int threshold = mineExtra.grimAcCounterThreshold.get();
        ignoreNextFastBreakStatus = 0;
        if (mineExtra.fastBreakBypassMode.getValue() == FastBreakBypassMode.BYPASS_GRIM_BAD_PACKETS) {
            // badpackets, no punishment, 桀桀桀
            gainedAdvantageMining = 0;
        } else {
            gainedAdvantageMining = (int) (gainedAdvantageMining * 0.9);
            if (gainedAdvantageMining > threshold
                    && mineExtra.fastBreakBypassMode.getValue() == FastBreakBypassMode.BYPASS_GRIM_LEGIT) {
                gainedAdvantageMining = 150;
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
        gainedAdvantageCooldown = MathHelper.clamp(gainedAdvantageCooldown, -1000, 1000);
    }

    public void onPostStopMiningFastBreak(BlockPos pos, double speed, double currentProgress) {
        ignoreNextFastBreakStatus = 0;
        if (lastStartMineBreakingProgressResetTick == 0) {
            return;
        }
        MineExtra mineExtra = MineTasks.getMineExtra();
        int predictTick = (int) Math.ceil(1 / speed);
        int tickUsed = (int) Math.ceil(currentProgress / speed);
        int diff = predictTick - tickUsed;
        gainedAdvantageMining += (diff + 1) * 50;
        int threshold = mineExtra.grimAcCounterThreshold.get();
        gainedAdvantageMining = MathHelper.clamp(gainedAdvantageMining, -1000, 1000);
        if (gainedAdvantageMining > threshold
                && mineExtra.fastBreakBypassMode.getValue() == FastBreakBypassMode.BYPASS_GRIM_LEGIT) {
            // only when starting bypass will we do
            // trigger a common mine
            ignoreNextFastBreakStatus = 2;
        }
    }

    public boolean shouldExecuteOptimizeOneBlock() {
        if (this.lastStartMineBreakingProgressResetTick == 0 || this.lastStartingMineIsInstantBreak) {
            return false;
        }
        thisTimeOptimizedSamePosBreak = true;
        return true;
    }

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

    public boolean shouldApplyNextTickFirstBreak() {
        if (this.nextTickEarlyBreak) {
            this.nextTickEarlyBreak = false;
            return true;
        }
        return false;
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
                    if (mc.player.getPos().squaredDistanceTo(pos) < 40000) {
                        RenderUtils.drawOutlinedBox(renderEvent.context, pos, pos.add(1.0, 1.0, 1.0), Color.BLUE);
                        float progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .getCurrentMiningProgress(false);
                        if (progress > 0.0F) {
                            BlockState state = mc.world.getBlockState(blockPos);
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
                fastBreakBypassMode.set(FastBreakBypassMode.BYPASS_GRIM_BAD_PACKETS);
            }
            default -> {
                fastBreakBypassMode.set(FastBreakBypassMode.NO_BYPASS);
            }
        }
    }

    public static enum FastBreakBypassMode implements ConfigEnum {
        NO_BYPASS,
        BYPASS_GRIM_LEGIT,
        BYPASS_GRIM_BAD_PACKETS;

        public boolean hasAc() {
            return this != NO_BYPASS;
        }

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.fast-break-bypass-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
