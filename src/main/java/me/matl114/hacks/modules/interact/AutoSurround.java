package me.matl114.hacks.modules.interact;

import java.util.ArrayList;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoSurround extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public AutoSurround() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    @Override
    public int priority() {
        return PRIORITY_MONITOR;
    }

    public final FlagRef enable = flagBuilder(Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.enable"))
            .build();

    public final KeyBindRef hotkey = toggleHotkey(
                    Configs.INTERACT_CONFIG,
                    makePath("place-utils.auto-surround.hotkey"),
                    new MultiKeyBind(),
                    makePath("place-utils.auto-surround.enable"))
            .build();

    public final IntRef delay = builder(
                    Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.delay"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef multiply = builder(
                    Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.multiply"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(
                    Configs.INTERACT_CONFIG,
                    makePath("place-utils.auto-surround.mode"),
                    Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.DELAY_MOVEMENT)
            .build();

    public final FlagRef placeUpper = flagBuilder(Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.upper"))
            .build();

    //    public final FlagRef antiKnockBack = flagBuilder(Configs.INTERACT_CONFIG,
    // makePath("place-utils.auto-surround.no-knockback-when-surround"))
    //        .build();

    public final FlagRef autoAttackCrystals = flagBuilder(
                    Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.auto-attack-crystal"))
            .build();

    public final FlagRef onlyGround = flagBuilder(
                    Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.only-ground"))
            .build();

    public final FlagRef autoCenter = flagBuilder(
                    Configs.INTERACT_CONFIG, makePath("place-utils.auto-surround.auto-center"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        triggerCenterFix = autoCenter.get();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        triggerCenterFix = false;
    }

    int delayTicks;

    public void onInput(Event<Void> inputEvent) {
        if (enable.get()) {
            if (++delayTicks >= delay.get()) {
                if (checkSurround()) {
                    if (autoCenter.get()) {
                        triggerCenterFix = true;
                    }
                    delayTicks = 0;
                }
            }
        }
    }

    int[] dx = {0, 0, -1, 1};

    int[] dz = {-1, 1, 0, 0};
    Direction[] dd = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    public boolean checkSurround() {
        BlockPos vcPos = mc.player.getVelocityAffectingPos();
        if (onlyGround.get() && !mc.player.isOnGround() && !CollisionUtil.isEntitySupported(mc.player, 1.5D)) {
            return false;
        }
        boolean legal = mode.get().isLegal();
        Box playerBox = mc.player.getBoundingBox();
        int mul =
                (mode.get().canMultiRotPlace() || (ACTasks.getDisablerManager().isMultiRotPlaceCheckDisabled()))
                        ? multiply.get()
                        : 1;
        int minY = ((int) playerBox.getMin(Direction.Axis.Y)) - 1;
        int maxY = ((int) playerBox.getMax(Direction.Axis.Y)) + 1;
        int placeCnt = 0;
        Runnable invCallback = null;
        List<Entity> entities = new ArrayList<>();
        place:
        for (int direction = 0; direction < 4 + (placeUpper.get() ? 1 : 0); ++direction) {
            Direction dir = dd[direction];
            BlockPos testPos = vcPos.offset(dir);
            // do not place under me
            int coordYMax = dir == Direction.DOWN ? maxY - 2 : maxY;
            for (int y = minY; y <= coordYMax; ++y) {
                BlockPos test = testPos.withY(y);
                BlockState state = mc.world.getBlockState(test);
                if (state.isAir() || state.isReplaceable()) {
                    BlockHitResult hitResult = InteractionTasks.getPlaceSupportingResult(test, !legal, !legal);
                    boolean canPlace = hitResult != null;
                    List<Entity> collideEntities = mc.world.getOtherEntities(null, MathUtils.getBlockBox(test));

                    boolean hasCollision = !collideEntities.isEmpty();
                    if (hasCollision) {
                        for (var entity : collideEntities) {
                            if (entity instanceof EndCrystalEntity end) {
                                entities.add(end);
                            }
                        }
                    }
                    if (canPlace && !hasCollision) {
                        if (placeCnt == 0) {
                            var re = supplyBlocks();
                            if (re == null) {
                                break place;
                            }
                            mul = Math.min(mul, re.val().getCount());
                            invCallback = InvTasks.getInvExtra().swapInventoryIndexToHand(re.index());
                        } else {
                            InteractionTasks.flushACPlaceQueue();
                        }
                        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND);
                        placeCnt += 1;
                        if (placeCnt >= mul) {
                            break place;
                        }
                    }
                }
            }
        }
        if (invCallback != null) {
            invCallback.run();
        }
        if (placeCnt == 0 && !entities.isEmpty() && autoAttackCrystals.get()) {
            for (var re : entities) {
                CombatTasks.getAttack().attackEntity(re);
            }
        }
        return placeCnt > 0;
    }

    public IndexEntry<ItemStack> supplyBlocks() {
        return InventoryUtils.findBestPlayerItem(
                item -> {
                    if (item.getItem() instanceof BlockItem blockItem) {
                        return (double) (blockItem.getBlock().getBlastResistance())
                                + ((blockItem == Items.OBSIDIAN) ? 1E8 : 0);
                    }
                    return null;
                },
                true,
                false);
    }

    boolean lastOnGround = false;
    boolean triggerCenterFix = false;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        boolean last = lastOnGround;
        lastOnGround = mc.player.isOnGround();
        if (enable.get() && autoCenter.get()) {
            if (!last && lastOnGround) {
                triggerCenterFix = true;
            }
        }
        if (triggerCenterFix && mc.player.isOnGround()) {
            BlockPos blockPos = mc.player.getVelocityAffectingPos();
            boolean fixed = mc.player.getX() - blockPos.getX() - 0.5 <= 0.2
                    && mc.player.getX() - blockPos.getX() - 0.5 >= -0.2
                    && mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2
                    && mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2;
            if (!fixed) {
                PlayerInputUtils.Input inputUtils = PlayerInputUtils.of(mc.options);
                if (!inputUtils.hasMovement() && !movementManagerEvent.context.hasImportantRotation()) {
                    Vec3d lookHorizontal = blockPos.toCenterPos().subtract(mc.player.getPos());
                    EntityUtils.setEntityYawSafe(mc.player, EntityUtils.rotationToYaw(lookHorizontal.normalize()));
                    movementManagerEvent.context.markForResetRot();
                }
            } else {
                triggerCenterFix = false;
            }
        }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (triggerCenterFix && mc.player.isOnGround()) {
            var input = PlayerInputUtils.of(mc.player.input);
            if (!input.hasWASDMovement()) {
                input.forward(true).applyInput(mc.player.input);
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
