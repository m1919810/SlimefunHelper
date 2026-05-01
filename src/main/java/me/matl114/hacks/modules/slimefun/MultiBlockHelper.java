package me.matl114.hacks.modules.slimefun;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.slimefun.SlimefunDispensorSuggestBookWidget;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.multiblock.BlockMatcher;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.containers.MetaData;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class MultiBlockHelper extends BaseModule {
    public static final String[] SLIMEFUN_MULTIBLOCK_CLICKER = {"multi-block-clicker", "enable"};
    public static final String[] SLIMEFUN_MB_RATE = {"multi-block-clicker", "rate"};
    public static final String[] SLIMEFUN_MB_LEGAL = {"multi-block-clicker", "bypass-anticheat"};
    public static final String[] SLIMEFUN_MB_LEGAL_MODE = {"multi-block-clicker", "bypass-targeting-mode"};

    public MultiBlockHelper() {}

    public final FlagRef enableClicker =
            flagBuilder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_MULTIBLOCK_CLICKER).build();

    public final IntRef rate = builder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_MB_RATE, IntRef.TYPE)
            .defaultValue(9)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef legal =
            flagBuilder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_MB_LEGAL).build();

    public final EnumRef<Configs.LegalInteractMode> legalMode = builder(
                    Configs.SLIMEFUN_CONFIG, SLIMEFUN_MB_LEGAL_MODE, Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostPlayerUseItemAtBlock(), this::onBlockClick);
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getServerLeavePoint(), this::onExit);
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInit);
    }

    private int lastChatTimestamp = 0;
    private int lastInteractTimestamp = 0;

    public void onBlockClick(Event<BlockHitResult> result) {
        if (enableClicker.get()) {
            onClickBlockExecute(result.context(), false, true);
        }
    }

    private long lastAutoTick;

    public void onTick(Event<ClientPlayerEntity> player) {
        if (!screens.isEmpty()) {
            long currentMs = System.currentTimeMillis();
            if (currentMs > (lastAutoTick + (null == mc.currentScreen ? 2 : 1) * 300)) {
                if (mc.player != null && mc.player.isSneaking()) {
                    Debug.chat(Text.literal("[自动多方块] 检测到长按下蹲,清除全部的执行中多方块"));
                    clearMultiBlockExecuteTasks();
                } else {
                    lastAutoTick = currentMs;

                    int cursorIndex = (++executeCursor) % screens.size();
                    var executeData = screens.get(cursorIndex);
                    if (executeData != null && executeData.getSecond() instanceof TileInventoryScreen holder) {
                        if (!holder.isVirtual() && holder.getBlockType() == Blocks.DISPENSER) {
                            // 当玩家关闭界面但并没有取消的时候,以低速运行
                            onMultiBlockExecute(holder.castHandled(), true, false);
                        }
                    } else {
                        Debug.chat(Text.literal("[自动多方块] 当前执行的界面并没有位置记录,已自动移除"));
                        screens.remove(cursorIndex);
                        executeCursor -= 1;
                    }
                }
            }
        }
    }

    public void onExit(Event<Void> event) {
        screens.clear();
        executeCursor = 0;
    }

    private final Random interactOffsetRand = new Random();
    private SlimefunDispensorSuggestBookWidget suggestBook;
    private static final String KEY_BOOK_WIDGET = "slimefunhelper:multiblock_suggestion_book_widget";
    private static final int[] AVAILABLE_SLOTS = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private void onScreenInit(Event<Screen> event) {
        if (event.context() instanceof TileInventoryScreen screen
                && event.context() instanceof Generic3x3ContainerScreen containerScreen) {
            HandledScreenAccess screenAccess = HandledScreenAccess.of(containerScreen);
            MetaData holder = screenAccess.getMetadata();
            SlimefunDispensorSuggestBookWidget dispensorWidget = holder.get(this, KEY_BOOK_WIDGET);
            // init while lately
            if (dispensorWidget == null) {
                Collection<String> co = screen.isVirtual()
                        ? null
                        : SlimefunTasks.getOptionalMultiBlockTypes(screen.getWorld(), screen.getPos()).stream()
                                .map(SlimefunTasks::getOptionalCraftingType)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(RecipeDatabase.CraftingType::id)
                                .collect(Collectors.toSet());
                dispensorWidget = new SlimefunDispensorSuggestBookWidget(
                        screen,
                        3,
                        3,
                        co,
                        (bol, entry) -> SlimefunTasks.moveSlimefunRecipePatternToContainer(
                                entry, containerScreen.getScreenHandler(), bol, true, AVAILABLE_SLOTS));
                holder.put(this, KEY_BOOK_WIDGET, dispensorWidget);
            }
            dispensorWidget.refreshActiveState();
            new ContentDelegateWidget<>(screenAccess.getScreenX(), screenAccess.getScreenY(), 0, 0)
                    .setContentDelegate(dispensorWidget)
                    .addTo(containerScreen);
        }
    }

    private void onClickBlockExecute(BlockHitResult result, boolean delayClick, boolean clickMany) {
        if (result == null) return;
        BlockPos pos = result.getBlockPos();
        Block block = mc.world.getBlockState(pos).getBlock();
        // do not speed up when opening crafting dispensor
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) {
            return;
        }
        var potentials = SlimefunTasks.getRecipeDatabase().getPotentialMultiBlocks(block);
        if (potentials == null || potentials.isEmpty()) return;
        Optional<RecipeDatabase.MultiBlockEntry> first = potentials.stream()
                .filter(m -> anyMatchMiddle(m, mc.world, pos))
                .findFirst();
        if (first.isEmpty()) return;
        if (lastChatTimestamp + 5 * 20 < Tasks.getTick()) {
            Debug.chat(
                    Text.literal("[MBHelper] Interacting with multiblock: ").formatted(Formatting.RED),
                    first.get().id());
            lastChatTimestamp = Tasks.getTick();
        }
        int rateLimit = (clickMany ? rate.get() : 1);
        boolean currentLookingAt = false;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK
                && Objects.equals(((BlockHitResult) mc.crosshairTarget).getBlockPos(), result.getBlockPos())) {
            currentLookingAt = true;
        }
        // illegal click, with legal mode, have to redirect
        if (!currentLookingAt && legal.get()) {
            // the 300ms limit or the legalMode
            if (!clickMany || lastInteractTimestamp + (5) < Tasks.getTick()) {
                lastInteractTimestamp = Tasks.getTick();
                Vec3d interactTarget = result.getBlockPos().toCenterPos();
                Vec3d interactLook = interactTarget.add(
                        interactOffsetRand.nextDouble(-0.05d, 0.05d),
                        interactOffsetRand.nextDouble(-0.05d, 0.05d),
                        interactOffsetRand.nextDouble(-0.05d, 0.05d));
                Vec3d cacheDirection =
                        interactLook.subtract(mc.player.getEyePos()).normalize();
                Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(cacheDirection);
                switch (legalMode.get()) {
                    case USEITEM_PACKET -> clickUsePacket(result, pitchYaw, rateLimit);
                    case MOVEMENT -> clickMovement(result, pitchYaw, rateLimit);
                    case DELAY_MOVEMENT -> clickDelayMovement(result, pitchYaw, rateLimit);
                }
            } else {
                Debug.chat(Text.literal("[AC] 你点的太快了,可能无法通过反作弊"));
            }
        } else {
            for (int i = 0; i < rateLimit; ++i) {
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence)));
            }
            if (delayClick && clickMany) {
                AtomicInteger count = new AtomicInteger(2);
                Tasks.scheduleRepeated(
                        () -> {
                            for (int i = 0; i < rateLimit; ++i) {
                                mc.interactionManager.sendSequencedPacket(
                                        mc.world,
                                        (sequence ->
                                                new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence)));
                            }
                            return count.decrementAndGet() <= 0;
                        },
                        3,
                        4);
            }
            ClientAccess.of(mc).setItemUseCooldown(0);
        }
    }

    public void clickDelayMovement(BlockHitResult result, Vec2f pitchYaw, int clickRate) {
        ClientPlayerAccess.of(mc.player)
                .getLegalMovementManager()
                .addMovementModifier(new LegalMovementManager.MovementModifier() {
                    @Override
                    public int priority() {
                        return PRIORITY_LOW;
                    }

                    @Override
                    public boolean mayModifyRotation() {
                        return true;
                    }

                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        ClientPlayerEntity args = movementManagerEvent.context().playerStatus.entity;
                        //                        float pitch = args.getPitch();
                        //                        float yaw = args.getYaw();
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        EntityUtils.setEntityPitchSafe(args, pitchYaw.x);
                        EntityUtils.setEntityYawSafe(args, pitchYaw.y);
                    }

                    @Override
                    public boolean postModify(
                            Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        // enable delay execute!
                        if (!enabledThisTick) return true;
                        for (int i = 0; i < clickRate; ++i) {
                            mc.interactionManager.sendSequencedPacket(
                                    mc.world,
                                    (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence)));
                        }
                        ClientAccess.of(mc).setItemUseCooldown(0);
                        movementManagerEvent.context().playerStatus.restoreRotation();
                        return false;
                    }
                });
    }

    public void clickMovement(BlockHitResult result, Vec2f pitchYaw, int clickRate) {
        mc.getNetworkHandler()
                .sendPacket(VPacket.newLookAndOnGround(
                        pitchYaw.y, pitchYaw.x, mc.player.isOnGround(), mc.player.horizontalCollision));
        for (int i = 0; i < clickRate; ++i) {
            mc.interactionManager.sendSequencedPacket(
                    mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence)));
        }
    }

    public void clickUsePacket(BlockHitResult result, Vec2f pitchYaw, int clickRate) {

        // find a hand which contains a item
        // do not pass grimac
        // will consume packet-limit, shit
        Hand hand;
        if (!mc.player.getMainHandStack().isEmpty()) {
            hand = Hand.MAIN_HAND;
        } else if (!mc.player.getOffHandStack().isEmpty()) {
            hand = Hand.OFF_HAND;
        } else {
            // try
            hand = null;
        }
        if (hand != null) {

            for (int i = 0; i < clickRate; ++i) {
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (z) -> new PlayerInteractItemC2SPacket(hand, z, pitchYaw.y, pitchYaw.x));
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, sequence)));
            }
            ClientAccess.of(mc).setItemUseCooldown(0);
        } else {
            clickMovement(result, pitchYaw, clickRate);
        }
    }

    private List<Pair<BlockPos, TileInventoryScreen>> screens = new ArrayList<>();
    private int executeCursor = 0;

    public void onMultiBlockExecute(Screen executingScreen, boolean clickMany, boolean clickDouble) {
        if (mc.player == null
                || !(executingScreen instanceof TileInventoryScreen tile)
                || tile.isVirtual()
                || tile.getWorld() != mc.world) {
            return;
        }
        BlockPos pos = tile.getPos();
        Block block = tile.getBlockType();
        if (pos.toCenterPos().squaredDistanceTo(mc.player.getPos()) > 50) {
            Debug.chat(Text.literal("[多方块执行] 你离着自动执行的多方块太远了,已关闭自动执行"));
            toggleMultiBlockAutoExecuteState(tile, false);
            return;
        }
        // opening current Executing
        if (mc.currentScreen instanceof TileInventoryScreen tileExecute && Objects.equals(pos, tileExecute.getPos())) {
            boolean hasItem = false;
            for (var slot : tileExecute.castHandled().getScreenHandler().slots) {
                if (slot.inventory instanceof PlayerInventory) {
                    break;
                } else if (!slot.getStack().isEmpty()) {
                    hasItem = true;
                    break;
                }
            }
            // return if there is no item in the screen
            if (!hasItem) return;
        }

        boolean find = false;
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) {
            for (var multiblock :
                    SlimefunTasks.getRecipeDatabase().getMultiBlockRegistry().values()) {
                var optional = getOptionalActionFromDispenser(multiblock, mc.world, pos);
                if (optional.isEmpty()) continue;
                find = true;
                for (var bp : optional) {
                    BlockHitResult result = RaycastUtils.createHitResult(bp);
                    onClickBlockExecute(result, clickDouble, clickMany);
                }
            }
        }
        if (!find) {
            Debug.chat(Text.literal("[多方块执行] 多方块结构与已记录的多方块无法匹配").formatted(Formatting.RED));
            toggleMultiBlockAutoExecuteState(tile, false);
        }
    }

    public void clearMultiBlockExecuteTasks() {
        Debug.chat(
                Text.literal("[自动多方块] 已清除 %d 个执行中多方块".formatted(screens.size())).formatted(Formatting.GREEN));
        screens.clear();
        executeCursor = 0;
    }
    //    private static boolean AUTO_EXECUTE = false;
    public boolean isMultiBlockExecuting(TileInventoryScreen screen) {
        return screens.stream().anyMatch(i -> Objects.equals(screen.getPos(), i.getFirst()));
    }

    public void toggleMultiBlockAutoExecuteState(TileInventoryScreen screen, boolean val) {
        if (screen.isVirtual()) {
            Debug.chat(Text.literal("[自动多方块] 找不到该屏幕对应的方块位置"));
        } else {
            BlockPos pos = screen.getPos();
            screens.removeIf(i -> Objects.equals(i.getFirst(), pos));
            if (val) {
                screens.add(Pair.of(pos, screen));
            }
            Debug.chat(Text.literal("[自动多方块] 已切换该屏幕的自动执行状态,目前有 %d 个自动执行中(长按下蹲以全部关闭)".formatted(screens.size()))
                    .formatted(Formatting.GREEN));
        }
    }

    public static record MultiblockOffset(int dy, Direction direction) {}

    @AllArgsConstructor
    public static class MultiBlockWithLocation {
        public RecipeDatabase.MultiBlockEntry multiBlockEntry;
        public MultiBlockLocation location;
    }

    @AllArgsConstructor
    public static class MultiBlockLocation {
        BlockPos leftDown;
        Vec3i left2Right;

        public BlockPos getComponentBlock(int x, int y) {
            return leftDown.add(left2Right.getX() * x, y, left2Right.getZ() * x);
        }
    }

    public static List<BlockPos> getPositionByDirection(
            RecipeDatabase.MultiBlockEntry entry, BlockPos blockPos, MultiblockOffset direcion) {
        blockPos.add(0, -direcion.dy, 0);
        int dy = direcion.dy;
        Direction dir = direcion.direction;
        var blockTypes = entry.blockTypes();
        List<BlockPos> pos = new ArrayList<>(9);
        BlockMatcher[] types = entry.blockTypes();
        for (int i = -1; i <= 1; ++i) {
            for (int j = 0; j <= 2; ++j) {
                // only match existing block

                if (blockTypes[3 * j + (i + 1)] != BlockMatcher.ANY_MATCH) {
                    pos.add(blockPos.add(i * dir.getOffsetX(), j - dy, i * dir.getOffsetZ()));
                }
            }
        }
        return pos;
    }

    public static Collection<BlockPos> getOptionalActionFromDispenser(
            RecipeDatabase.MultiBlockEntry entry, ClientWorld world, BlockPos pos) {
        var lookup = entry.lookup();
        var optionalActionBlock = entry.optionalActionBlock();
        Collection<MultiBlockLocation> locations = lookup.lookup(world, pos);
        if (locations != null && !locations.isEmpty()) {
            Set<BlockPos> poseSet = new HashSet<>();
            for (var ml : locations) {
                for (var pt : optionalActionBlock) {
                    poseSet.add(ml.getComponentBlock(pt.x, pt.y));
                }
            }
            return poseSet;
        }
        return Set.of();
    }

    static Direction[] DIR_CONSIDER =
            new Direction[] {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
    static Direction[] DIR_SYMM = new Direction[] {Direction.NORTH, Direction.WEST};

    public static MultiblockOffset matchDirection(
            RecipeDatabase.MultiBlockEntry entry, World world, BlockPos blockPos) {
        var blockTypes = entry.blockTypes();
        var symm = entry.symm();
        position:
        for (int i = 0; i <= 2; ++i) {
            // match middle first
            BlockPos.Mutable middleBotton = blockPos.mutableCopy().move(0, -i, 0);
            for (int s = 0; s <= 2; ++s) {
                Block block = world.getBlockState(middleBotton).getBlock();
                if (!blockTypes[1 + 3 * s].match(block)) {
                    continue position;
                }
                middleBotton.move(0, 1, 0);
            }
            // middle match
            // then consider symm
            Direction currentDir = null;
            directionMatch:
            for (Direction dir : symm ? DIR_SYMM : DIR_CONSIDER) {

                BlockPos.Mutable leftBotton =
                        blockPos.mutableCopy().move(0, -i, 0).move(dir);
                for (int s = 0; s <= 2; ++s) {

                    Block block = world.getBlockState(leftBotton).getBlock();
                    if (!blockTypes[3 * s].match(block)) {
                        continue directionMatch;
                    }
                    leftBotton.move(0, 1, 0);
                }
                currentDir = dir;
                if (!symm) {
                    BlockPos.Mutable rightBotton =
                            blockPos.mutableCopy().move(0, -i, 0).move(currentDir, -1);
                    // if not symm, still need check
                    for (int s = 0; s <= 2; ++s) {
                        Block block = world.getBlockState(rightBotton).getBlock();
                        if (!blockTypes[3 * s + 2].match(block)) {
                            continue directionMatch;
                        }
                        rightBotton.move(0, 1, 0);
                    }
                }
                return new MultiblockOffset(i, currentDir);
            }
        }
        return null;
    }

    public static boolean anyMatchMiddle(RecipeDatabase.MultiBlockEntry entry, World world, BlockPos blockPos) {
        return matchDirection(entry, world, blockPos) != null;
    }
}
