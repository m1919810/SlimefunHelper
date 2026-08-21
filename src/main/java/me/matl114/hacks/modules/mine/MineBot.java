package me.matl114.hacks.modules.mine;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class MineBot extends BaseModule {
    public MineBot() {
        super("MineBot");
        bindFlag(enable);
    }

    // should initialize before the config
    private final Random rand = new Random();
    public final ModulePath mineBot = makePath(Configs.MINE_CONFIG, "mine-bot");
    public final FlagRef enable = flagBuilder(mineBot.addEnable()).build();

    public final KeyBindRef keyBind = moduleEntry(
                    mineBot.addHotkey(), new MultiKeyBind(), mineBot.addEnable(), moduleMeta(() -> this.mineBotMode))
            .build();

    public final NBTRef<EntrySet<Block>> whiteListBlockRegex = builder(
                    mineBot.add("whitelist"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(cobblestone|stone|.*ore)$"), Registries.BLOCK))
            .build();

    public final EnumRef<MineBotMode> mineBotMode = builder(mineBot.add("mine-mode"), MineBotMode.class)
            .defaultValue(MineBotMode.SPHERICAL)
            .build();

    public final EnumRef<Configs.MineTargetingMode> legalMode = builder(
                    mineBot.add("legal-mode"), Configs.MineTargetingMode.class)
            .defaultValue(Configs.MineTargetingMode.NO_BYPASS)
            .show(() -> this.mineBotMode.get().isNotIn(MineBotMode.AUTO_TOOL))
            .build();

    public final IntRef minY = intBuilder(mineBot.add("min-dy"))
            .defaultValue(0)
            .show(() -> this.mineBotMode.get().isNotIn(MineBotMode.AUTO_TOOL))
            .build();

    public final IntRef maxY = intBuilder(mineBot.add("max-dy"))
            .defaultValue(6)
            .show(() -> this.mineBotMode.get() != MineBotMode.AUTO_TOOL)
            .build();

    public final IntRef width = intBuilder(mineBot.add("max-width"))
            .defaultValue(1)
            .show(() -> this.mineBotMode.get().isIn(MineBotMode.SQUARE, MineBotMode.TUNNEL))
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef maxInstaMine =
            intBuilder(mineBot.add("max-instant-mine")).defaultValue(30).build();

    public final FlagRef doubleBreak =
            flagBuilder(mineBot.add("use-double-break")).build();

    public final FlagRef considerCooldown = builder(mineBot.add("consider-cooldown"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoSwap =
            flagBuilder(mineBot.add("auto-swap")).defaultValue(true).build();

    public final FlagRef toolProtect = builder(mineBot.add("durability-protect"), Boolean.class)
            .defaultValue(true)
            .build();

    private boolean isMineable(BlockState state) {
        if (state != null && !state.isAir() && !state.isLiquid()) {
            Block block = state.getBlock();
            if (block.getHardness() >= 0.0F && whiteListBlockRegex.get().test(block)) {
                return true;
            }
        }
        return false;
    }

    public BlockPos lastMinePos = null;

    public void onTick(Event<ClientPlayerEntity> player) {
        if (isActive()) {
            onMineBotTick();
        }
    }
    // todo: add cooldown

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        lastMinePos = null;
    }

    public void onMineBotTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        switch (mineBotMode.get()) {
            case SPHERICAL -> onMineSpherical();
            case LAYERED_UP -> onMineLayered(true);
            case LAYERED_DOWN -> onMineLayered(false);
            case SQUARE -> onMineSquare();
            case TUNNEL -> onMineTunnel();
            case RANDOM -> onMineRandom();
            case AUTO_TOOL -> onMineCustomTool();
        }
        ;
    }

    public static final int durMultiply = 4;
    public static final int minDurLimit = 9;

    public boolean isDurabilityOk(ItemStack item) {
        if (item.isEmpty()) return true;
        int durabilityLimit;
        if (item.get(DataComponentTypes.UNBREAKABLE) != null) {
            durabilityLimit = 0;
        } else if (item.get(DataComponentTypes.MAX_DAMAGE) != null) {
            RegistryEntry<Enchantment> unbreaking =
                    RegistryUtils.getRegistryEntry(ItemStackUtils.registry(), Enchantments.UNBREAKING);
            int multiply = 1;
            if (unbreaking != null) {
                multiply = EnchantmentHelper.getLevel(unbreaking, item) + 1;
            }
            durabilityLimit = (durMultiply * (2)) / multiply;
        } else {
            return true;
        }
        int max = Math.max(minDurLimit, durabilityLimit);
        if (item.getDamage() > item.getMaxDamage() - max) {
            return false;
        }
        return true;
    }

    private final TimerExecutor noBlocksAround = new TimerExecutor();
    private static final int NO_BLOCK_MENTION_LIMIT = 400;

    public int onMineCommon(Supplier<BlockPos> posFinder) {
        int tryMine = 0;
        Vec2f originPy = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        do {
            if (!checkDistanceAndCondition(lastMinePos)) {
                lastMinePos = posFinder.get();
            }
            if (lastMinePos == null) {
                break;
            }
            if (considerCooldown.get()) {
                if (MineExtra.INSTANCE.getMiningPacketCooldown() > 0) {
                    break;
                }
            }
            PlayerInteractionAccess.of(mc.interactionManager).setMiningCooldown(0);

            BlockState mineState = mc.world.getBlockState(lastMinePos);
            IndexEntry<ItemStack> bestStack = autoSwap.get()
                    ? InventoryUtils.findBestPlayerItem(
                            s -> {
                                if (isDurabilityOk(s)) {
                                    return (double) WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                                            mc.player, mineState, s);
                                } else return null;
                            },
                            true,
                            true)
                    : InventoryUtils.getSelectedItem();
            if (bestStack == null || !isDurabilityOk(bestStack.val())) {
                if (toolProtect.get()) {
                    logI18N("message.module.mine-bot.tool-broken-stop");
                    enable.set(false);
                    break;
                } else {
                    bestStack = InventoryUtils.getSelectedItem();
                }
            }
            InvExtra.INSTANCE.swapInventoryIndexToHand(bestStack.index());
            AttributeUtils.updateAttribute(mc.player);
            float speed = MineExtra.INSTANCE.predictBlockBreakingSpeedAt(lastMinePos);
            tryMine += 1;
            // use real Direction
            Vec3d shouldFacing = lastMinePos.toCenterPos().subtract(mc.player.getEyePos());
            Direction dir = Direction.getFacing(shouldFacing).getOpposite();
            switch (legalMode.get()) {
                case SWING_HAND_AND_ROT -> {
                    Vec3d rotate2f = mc.player.getRotationVector();
                    Vec3d rotateXZ = new Vec3d(rotate2f.x, 0, rotate2f.z);
                    // out of the sight
                    if (rotateXZ.dotProduct(shouldFacing) < 0) {
                        PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 180);
                        mc.getNetworkHandler()
                                .sendPacket(VPacket.newLookAndOnGround(
                                        mc.player.getYaw(),
                                        mc.player.getPitch(),
                                        mc.player.isOnGround(),
                                        mc.player.horizontalCollision));
                    }
                }
                case SWING_HAND_AND_TARGET -> {
                    Vec3d facing = shouldFacing.normalize();
                    Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(facing);
                    if (Math.abs(EntityUtils.getSafeYawDiff(mc.player.getYaw(), pitchYaw.y)) > 30) {
                        mc.player.setPitch(pitchYaw.x);
                        mc.player.setYaw(pitchYaw.y);
                        mc.getNetworkHandler()
                                .sendPacket(VPacket.newLookAndOnGround(
                                        mc.player.getYaw(),
                                        mc.player.getPitch(),
                                        mc.player.isOnGround(),
                                        mc.player.horizontalCollision));
                    }
                }
            }
            mc.interactionManager.updateBlockBreakingProgress(lastMinePos, dir);
            // fake a swing packet , so that we can bypass some packet check

            if (legalMode.get().hasSwing()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            if (!MineExtra.INSTANCE.shouldTreatAsInstantBreak(speed)) {
                //
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                if (doubleBreak.get()
                        && Objects.equals(access.getCurrentMiningPos(), lastMinePos)
                        && access.isFailBreakEmpty()) {
                    access.sendFailBreakCurrentPos(null);
                } else {
                    break;
                }
            }

        } while (!mc.interactionManager.isBreakingBlock() && tryMine < maxInstaMine.get());
        if (mc.player.getPitch() != originPy.x || mc.player.getYaw() != originPy.y) {
            mc.player.setPitch(originPy.x);
            mc.player.setYaw(originPy.y);
            ClientPlayerAccess.of(mc.player).resyncRot();
        }
        if (tryMine == 0) {
            noBlocksAround.run(NO_BLOCK_MENTION_LIMIT, () -> logI18N("message.module.mine-bot.no-blocks"));
        } else {
            noBlocksAround.mark();
        }
        return tryMine;
    }

    public int onMineSpherical() {
        return onMineCommon(this::findNextMinePosSpherical);
    }

    public int onMineLayered(boolean up) {
        return onMineCommon(() -> this.findNextMinePosLayer(up));
    }

    public int onMineSquare() {
        return onMineCommon(this::findNextMinePosSquare);
    }

    public int onMineTunnel() {
        return onMineCommon(this::findNextMinePosTunnel);
    }

    public int onMineRandom() {
        return onMineCommon(this::findNextMinePosRandom);
    }

    public int onMineCustomTool() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult result = ((BlockHitResult) mc.crosshairTarget);
            BlockPos pos = result.getBlockPos();
            if (isMineable(mc.world.getBlockState(pos))) {
                mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                    return new PlayerInteractItemC2SPacket(
                            Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch());
                });
            }
        }
        return 0;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    public static enum MineBotMode implements ConfigEnum {
        SPHERICAL,
        LAYERED_UP,
        LAYERED_DOWN,
        SQUARE,
        TUNNEL,
        RANDOM,
        AUTO_TOOL;

        @Override
        public String getConfigEnumType() {
            return "mine_bot_mode";
        }
    }

    private boolean checkDistanceAndCondition(BlockPos newPos) {
        var access = PlayerInteractionAccess.of(mc.interactionManager);
        // do not mine on double break
        if (Objects.equals(access.getCurrentFailBreakPos(), newPos)) {
            return false;
        }
        if (MineTasks.distanceOutOfReach(newPos, mc.player.getEyePos())) {
            return false;
        }
        if (!isMineable(mc.world.getBlockState(newPos))) {
            return false;
        }
        return true;
    }

    private BlockPos findNextMinePosSpherical() {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        for (var vec : InteractExtra.INSTANCE.getBlocksAround()) {
            int x = vec.getX();
            int y = vec.getY();
            int z = vec.getZ();
            if (y >= lowest && y <= highest) {
                BlockPos newPose = posCenter.add(x, y, z);
                if (checkDistanceAndCondition(newPose)) {
                    return newPose;
                }
            }
        }
        return null;
    }

    private BlockPos findNextMinePosLayer(boolean up) {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        IntList yLevelList = IntArrayList.toList(IntStream.range(lowest, highest));
        if (!up) {
            Collections.reverse(yLevelList);
        }
        for (int y : yLevelList) {
            for (var plate : InteractExtra.INSTANCE.getPlatesAround()) {
                int x = plate.x;
                int z = plate.y;
                BlockPos newPose = posCenter.add(x, y, z);
                if (checkDistanceAndCondition(newPose)) {
                    return newPose;
                }
            }
        }
        return null;
    }

    private BlockPos findNextMinePosTunnel() {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        // horizontal
        Direction facingDirection = mc.player.getHorizontalFacing();
        Direction facingDirectionCross = facingDirection.rotateYClockwise();
        IntList searchingWidth = new IntArrayList();
        searchingWidth.add(0);
        for (var i = 1; i <= width.get(); ++i) {
            searchingWidth.add(i);
            searchingWidth.add(-i);
        }
        double reach = InteractExtra.INSTANCE.getBlockReachDistance();
        for (var k = 0; k <= reach; ++k) {
            BlockPos currentCenter = posCenter.offset(facingDirection, k);
            for (var i = lowest; i < highest; ++i) {
                BlockPos currentHeightCenter = currentCenter.add(0, i, 0);
                for (var j : searchingWidth) {
                    BlockPos newPos = currentHeightCenter.offset(facingDirectionCross, j);
                    if (checkDistanceAndCondition(newPos)) {
                        return newPos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNextMinePosSquare() {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        // horizontal
        int wid = width.get();
        for (var i = lowest; i < highest; ++i) {
            for (var j = -wid; j <= wid; ++j) {
                for (int k = -wid; k <= wid; ++k) {
                    BlockPos newPos = posCenter.add(j, i, k);
                    if (checkDistanceAndCondition(newPos)) {
                        return newPos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNextMinePosRandom() {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        List<BlockPos> availablePos = new ArrayList<>();
        for (var vec : InteractExtra.INSTANCE.getBlocksAround()) {
            int x = vec.getX();
            int y = vec.getY();
            int z = vec.getZ();
            if (y >= lowest && y <= highest) {
                BlockPos newPose = posCenter.add(x, y, z);
                if (checkDistanceAndCondition(newPose)) {
                    availablePos.add(newPose);
                }
            }
        }
        // holy shit, who needs it
        return availablePos.isEmpty() ? null : availablePos.get(rand.nextInt(0, availablePos.size()));
    }
}
