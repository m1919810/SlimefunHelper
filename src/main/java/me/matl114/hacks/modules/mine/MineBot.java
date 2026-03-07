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
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class MineBot extends BaseModule {
    public MineBot() {
        super();
        bindFlag(enable);
    }

    public static final String[] MINE_BOT_MINE_MIN_DY = {"mine-bot", "min-dy"};
    public static final String[] MINE_BOT_MINE_MAX_DY = {"mine-bot", "max-dy"};
    public static final String[] MINE_BOT_MODE = {"mine-bot", "mine-mode"};
    public static final String[] MINE_BOT_WIDTH = {"mine-bot", "max-width"};
    private static final String[] MINE_BOT_DOWN_PRIORITY = {"mine-bot", "y-low-first"};
    public static final String[] MINE_BOT_MAX_INSTANT_MINE = {"mine-bot", "max-instant-mine"};
    public static final String[] MINE_BOT_WHITELIST = {"mine-bot", "whitelist"};
    public static final String[] MINE_BOT_PACKET_MULTIPLE = {"mine-bot", "multiple-packets"};
    public static final String[] MINE_BOT_LEGAL_MODE = {"mine-bot", "legal-mode"};
    private static final String[] MINE_BOT_RIGHT_CLICK = {"mine-bot", "right-click"};
    public static final String[] MINE_BOT_DURABILITY_PROTECT = {"mine-bot", "durability-protect"};
    public static final String[] MINEBOT_HOTKEYS = {"hotkeys-toggle", "mine-bot"};
    // should initialize before the config
    public Set<Block> whiteListed = new HashSet<>();
    private final Random rand = new Random();

    public void parseBlockRegex(String regex) {
        whiteListed = RegistryUtils.parseWhiteList(Registries.BLOCK, regex);
    }

    public final FlagRef enable = toggle(MINEBOT_HOTKEYS).build();

    public final KeyBindRef keyBind = toggleHotkey(
                    MINEBOT_HOTKEYS, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_B))
            .build();

    public final IntRef minY = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_BOT_MINE_MIN_DY)
            .defaultValue(0)
            .build();

    public final IntRef maxY = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_BOT_MINE_MAX_DY)
            .defaultValue(6)
            .build();
    // todo: test
    public final StringRef whiteListBlockRegex = builder(Configs.MINE_CONFIG, String.class)
            .path(MINE_BOT_WHITELIST)
            .defaultValue("^(cobblestone|stone|.*ore)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(this::parseBlockRegex)
            .build();

    public final EnumRef<MineBotMode> mineBotMode = builder(Configs.MINE_CONFIG, MineBotMode.class)
            .path(MINE_BOT_MODE)
            .defaultValue(MineBotMode.SPHERICAL)
            .build();

    public final IntRef width = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_BOT_WIDTH)
            .defaultValue(1)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef maxInstaMine = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_BOT_MAX_INSTANT_MINE)
            .defaultValue(30)
            .build();

    public final IntRef multiplePackets = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_BOT_PACKET_MULTIPLE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<Configs.MineTargetingMode> legalMode = builder(
                    Configs.MINE_CONFIG, Configs.MineTargetingMode.class)
            .path(MINE_BOT_LEGAL_MODE)
            .defaultValue(Configs.MineTargetingMode.NO_BYPASS)
            .build();

    public final FlagRef toolProtect = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_BOT_DURABILITY_PROTECT)
            .defaultValue(true)
            .build();

    public final FlagRef rightClickMode = builder(Configs.MINE_CONFIG, Boolean.class)
            .path(MINE_BOT_RIGHT_CLICK)
            .defaultValue(false)
            .build();

    private boolean isMineable(BlockState state) {
        if (state != null && !state.isAir() && !state.isLiquid()) {
            Block block = state.getBlock();
            if (block.getHardness() >= 0.0F && whiteListed.contains(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean noLongerCanMine(World world, BlockPos pos) {
        if (pos == null || world == null) {
            return true;
        }

        return !isMineable(world.getBlockState(pos));
    }

    public BlockPos lastMinePos = null;

    public void onTick(Event<ClientPlayerEntity> player) {
        if (isActive()) {
            onMineBotTick();
        }
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        lastMinePos = null;
    }

    public void onMineBotTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        for (int i = 0; i < multiplePackets.get(); ++i) {
            int mineResult =
                    switch (mineBotMode.get()) {
                        case SPHERICAL -> onMineSpherical();
                        case LAYERED_UP -> onMineLayered(true);
                        case LAYERED_DOWN -> onMineLayered(false);
                        case SQUARE -> onMineSquare();
                        case TUNNEL -> onMineTunnel();
                        case RANDOM -> onMineRandom();
                        case AUTO_TOOL -> onMineCustomTool();
                    };
            if (mineResult >= 5) {
                break;
            }
        }
    }

    public int durMultiply = 4;
    public int minDurLimit = 9;

    public boolean checkToolDurability(boolean instaMine) {
        if (toolProtect.get() && mc.player != null) {
            ItemStack item = mc.player.getMainHandStack();

            int durabilityLimit; // item.get(DataComponentTypes.UNBREAKABLE) != null ? Integer.MAX_VALUE: (
            if (item.get(DataComponentTypes.UNBREAKABLE) != null) {
                durabilityLimit = 0;
            } else if (item.get(DataComponentTypes.MAX_DAMAGE) != null) {
                var optionalUnbreaking = ItemStackUtils.registry()
                        .getOptional(RegistryKeys.ENCHANTMENT)
                        .orElseThrow()
                        .getEntry(Enchantments.UNBREAKING);
                int multiply = 1;
                if (optionalUnbreaking.isPresent()) {
                    multiply = EnchantmentHelper.getLevel(optionalUnbreaking.get(), item) + 1;
                }
                durabilityLimit = (durMultiply * (instaMine ? maxInstaMine.get() : 2)) / multiply;
            } else {
                // it is not a tool
                return true;
            }
            int max = Math.max(minDurLimit, durabilityLimit);
            if (!item.isEmpty() && item.getDamage() > item.getMaxDamage() - max) {
                //
                Debug.chat("Your tool runs out of durability! stop mining");
                enable.set(false);
                return false;
            }
        }
        return true;
    }

    private int noBlockAroundTick;
    private static final int NO_BLOCK_MENTION_LIMIT = 400;

    public int onMineCommon(Supplier<BlockPos> posFinder) {
        if (!checkToolDurability(false)) {
            return 0;
        }
        int tryMine = 0;
        boolean insta = false;
        Vec2f originPy = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        do {
            Vec3d playerPos = mc.player.getEyePos();
            ;
            if (MineTasks.distanceOutOfReach(lastMinePos, playerPos) || noLongerCanMine(mc.world, lastMinePos)) {
                lastMinePos = posFinder.get();
            }
            if (lastMinePos == null) {
                break;
            }
            if (rightClickMode.get()) {
                Vec3d facingTarget = lastMinePos.toCenterPos().subtract(mc.player.getEyePos());
                Vec2f vc2f = EntityUtils.rotationToPitchYaw(facingTarget.normalize());
                mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                    return new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, vc2f.y, vc2f.x);
                });
                tryMine += 1;

            } else {
                boolean preCalculation =
                        PlayerInteractionAccess.of(mc.interactionManager).preCalculateInstantBreak(lastMinePos);
                if (!insta && preCalculation) {
                    insta = true;
                    if (!checkToolDurability(true)) {
                        break;
                    }
                }
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
                            mc.player.setYaw(EntityUtils.getSafeYaw(mc.player, mc.player.getYaw() + 180));
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

                if (!preCalculation) {
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
            noBlockAroundTick++;
            if (noBlockAroundTick > NO_BLOCK_MENTION_LIMIT) {
                noBlockAroundTick = 0;
                Debug.chat(Text.literal("No more minable blocks nearby!"));
            }
        } else {
            noBlockAroundTick = 0;
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
        registerListener(Listener.getGameTick(), this::onTick);
    }

    public int a = 1;

    {
        boolean save = false;
        // add version compat
        if (Configs.MINE_CONFIG.contains(MINE_BOT_DOWN_PRIORITY)) {
            FlagRef ref = Configs.MINE_CONFIG.getBoolean(MINE_BOT_DOWN_PRIORITY);
            Configs.MINE_CONFIG.setValue(null, MINE_BOT_DOWN_PRIORITY);
            save = true;
            if (ref != null && ref.get()) {
                mineBotMode.set(MineBotMode.LAYERED_UP);
            }
        }
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
        public Text getDisplay() {
            return Text.translatable("configenum.mine-bot-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    private BlockPos findNextMinePosSpherical() {
        BlockPos posStanding = mc.player.getSteppingPos();
        BlockPos posCenter = posStanding.add(0, 1, 0);
        int lowest = minY.get();
        int highest = maxY.get();
        for (var vec : MineTasks.getMineExtra().getBlocksAround()) {
            int x = vec.getX();
            int y = vec.getY();
            int z = vec.getZ();
            if (y >= lowest && y <= highest) {
                BlockPos newPose = posCenter.add(x, y, z);
                if (MineTasks.distanceOutOfReach(newPose, mc.player.getEyePos())) {
                    continue;
                } else {
                    if (isMineable(mc.world.getBlockState(newPose))) {
                        return newPose;
                    }
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
            for (var plate : MineTasks.getMineExtra().getPlatesAround()) {
                int x = plate.x;
                int z = plate.y;
                BlockPos newPose = posCenter.add(x, y, z);
                if (MineTasks.distanceOutOfReach(newPose, mc.player.getEyePos())) {
                    continue;
                } else {
                    if (isMineable(mc.world.getBlockState(newPose))) {
                        return newPose;
                    }
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
        for (var k = 0; k <= MineTasks.getMineExtra().getReachDistance(); ++k) {
            BlockPos currentCenter = posCenter.offset(facingDirection, k);
            for (var i = lowest; i < highest; ++i) {
                BlockPos currentHeightCenter = currentCenter.add(0, i, 0);
                for (var j : searchingWidth) {
                    BlockPos newPos = currentHeightCenter.offset(facingDirectionCross, j);
                    if (MineTasks.distanceOutOfReach(newPos, mc.player.getEyePos())) {
                        continue;
                    } else {
                        if (isMineable(mc.world.getBlockState(newPos))) {
                            return newPos;
                        }
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
                    if (MineTasks.distanceOutOfReach(newPos, mc.player.getEyePos())) {
                        continue;
                    } else {
                        if (isMineable(mc.world.getBlockState(newPos))) {
                            return newPos;
                        }
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
        for (var vec : MineTasks.getMineExtra().getBlocksAround()) {
            int x = vec.getX();
            int y = vec.getY();
            int z = vec.getZ();
            if (y >= lowest && y <= highest) {
                BlockPos newPose = posCenter.add(x, y, z);
                if (MineTasks.distanceOutOfReach(newPose, mc.player.getEyePos())) {
                    continue;
                } else {
                    if (isMineable(mc.world.getBlockState(newPose))) {
                        availablePos.add(newPose);
                    }
                }
            }
        }
        // holy shit, who needs it
        return availablePos.isEmpty() ? null : availablePos.get(rand.nextInt(0, availablePos.size()));
    }
}
