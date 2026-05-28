package me.matl114.hacks;

import com.google.common.util.concurrent.Runnables;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.*;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.managers.Configs;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.ApiStatus;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();
    //
    //    public static void placeBlock(int idx, BlockHitResult result){
    //
    //    }

    public static void placeBlock(Hand hand, BlockHitResult result) {
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (((ActionResult.Success) actionResult2).swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }

    public static void addPostRotationCorrectTask(Vec3d look3d, Runnable callback) {
        //        RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
        //            RenderTasks.DEBUG_TICK, new RenderTasks.BoxObject(look3d.add(-0.1, -0.1, -0.1), look3d.add(0.1,
        // 0.1, 0.1), Color.MAGENTA)));
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
                        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                        Vec2f rotation = EntityUtils.rotationToPitchYaw(
                                look3d.subtract(mc.player.getEyePos()).normalize());
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        EntityUtils.setEntityYawSafe(player, rotation.y);
                        EntityUtils.setEntityPitchSafe(player, rotation.x);
                        movementManagerEvent.context.tryMarkForMoveFix();
                        movementManagerEvent.context.markForResetRot();
                    }

                    @Override
                    public boolean postModify(
                            Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        callback.run();
                        return false;
                    }
                });
    }

    public static void handlePlaceMode(Configs.LegalInteractMode mode, BlockHitResult result, Hand hand) {
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (i) -> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
                InteractionTasks.placeBlock(hand, result);
            }
            case DELAY_MOVEMENT -> {
                InteractionTasks.placeBlock(hand, result);
                InteractionTasks.addPostRotationCorrectTask(result.getBlockPos().toCenterPos(), Runnables.doNothing());
            }
            case LEGACY_SLIENT_ROT -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                LegacySnapRotManager.INSTANCE.snapAt(rotation.x, rotation.y, false);
                InteractionTasks.placeBlock(hand, result);
            }
            case NONE -> {
                InteractionTasks.placeBlock(hand, result);
            }
        }
    }

    public static void flushACPlaceQueue() {
        // for flush places
        //        ACTasks.getDisablerManager().flushACPlaceQueue();
    }

    public static void handlePlaceModeMulti(
            Configs.LegalInteractMode mode, Vec3d targetCenter, List<Pair<BlockHitResult, Hand>> resultList) {
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(
                        targetCenter.subtract(mc.player.getEyePos()).normalize());

                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    if (selectedSlot == -1) {
                        selectedSlot = mc.player.getInventory().selectedSlot;
                        mc.interactionManager.sendSequencedPacket(
                                mc.world,
                                (i) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, rotation.y, rotation.x));
                    } else {
                        flushACPlaceQueue();
                    }
                    InteractionTasks.placeBlock(hand, result);
                }
            }
            case DELAY_MOVEMENT -> {
                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    if (selectedSlot == -1) {
                        selectedSlot = mc.player.getInventory().selectedSlot;
                    } else {
                        // for flush places
                        flushACPlaceQueue();
                    }
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    InteractionTasks.placeBlock(hand, result);
                }
                InteractionTasks.addPostRotationCorrectTask(targetCenter, Runnables.doNothing());
            }
            case LEGACY_SLIENT_ROT -> {
                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    if (selectedSlot == -1) {
                        selectedSlot = mc.player.getInventory().selectedSlot;
                    } else {
                        // for flush places
                        flushACPlaceQueue();
                    }
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    LegacySnapRotManager.INSTANCE.snapAt(
                            result.getBlockPos()
                                    .toCenterPos()
                                    .subtract(mc.player.getEyePos())
                                    .normalize(),
                            false);
                    InteractionTasks.placeBlock(hand, result);
                }
            }
            case NONE -> {
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    InteractionTasks.placeBlock(hand, result);
                }
            }
        }
    }

    public static BlockHitResult getPlaceSupportingResult(
            BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getEyePos(), blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static BlockHitResult getPlaceSupportingResult(
            BlockPos blockPos, Direction preferredDirection, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getEyePos(), blockPos, preferredDirection, enableAirPlace, enablePositionPlace);
    }

    public static BlockHitResult getPlaceSupportingResult(
            Vec3d predictEyePos, BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                predictEyePos, blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static BlockHitResult getPlaceSupportingResult(
            Vec3d predictEyePos,
            BlockPos blockPos,
            Direction preferredDirection,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        Direction dir = preferredDirection;
        List<Direction> order = new ArrayList<>();
        order.add(dir);
        for (var direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != dir) {
                order.add(direction);
            }
        }
        Vec3d centerPos = blockPos.toCenterPos();
        if (enableAirPlace) {
            if (order.isEmpty()) {
                return null;
            }
            Direction availableDirection = order.get(0);
            Vec3d plateCenter = centerPos.offset(availableDirection, 0.5);
            return new BlockHitResult(plateCenter, availableDirection.getOpposite(), blockPos, false);
        } else {
            for (var direction : order) {
                Vec3d plateCenter = centerPos.offset(direction, 0.5);
                Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                BlockState interactState = mc.world.getBlockState(targetPos);
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                if (Box.from(Vec3d.of(targetPos)).contains(predictEyePos)) {
                    // ?
                    return new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true);
                } else {
                    Vec3d iSeeVect = predictEyePos.subtract(plateCenter);
                    Vec3d plateLLL = direction.getDoubleVector();
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        return new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false);
                    }
                }
            }
        }
        return null;
    }

    @ApiMethod
    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Interaction");

    @Getter
    public static InteractExtra interactExtra;

    @ApiStatus.Experimental
    public static AutoInteract autoInteract;

    @ApiStatus.Experimental
    public static AutoAttack autoAttack;

    @Getter
    public static Scaffold scaffold;

    @Getter
    public static TpInteract tpInteract;

    @Getter
    public static Airplace airplace;

    @Getter
    public static AutoSurround autoSurround;

    @Getter
    public static BlockRotate blockRotate;

    @Getter
    public static PrinterRewrite printerRewrite;

    private static void initModules(ModuleManager m) {
        interactExtra = new InteractExtra().register(m);

        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);
        autoSurround = new AutoSurround().register(m);
        blockRotate = new BlockRotate().register(m);
        printerRewrite = new PrinterRewrite().register(m);
    }

    static {
        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
