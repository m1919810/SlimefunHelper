package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class TpInteract extends BaseModule {
    public static final String[] TP_INTERACT_ENABLE = {"tp-interact", "enable"};
    public static final String[] TP_INTERACT_HOTKEY = {"tp-interact", "enable-hotkey"};

    public static final String[] TP_MINE_USE_FALL_MINE = {"tp-interact", "mine-interact-use-fail-mine"};

    public TpInteract() {}

    public final FlagRef enable =
            flagBuilder(Configs.INTERACT_CONFIG, TP_INTERACT_ENABLE).build();

    public final KeyBindRef keyBindRef = toggleHotkey(
                    Configs.INTERACT_CONFIG, TP_INTERACT_HOTKEY, new MultiKeyBind(), TP_INTERACT_ENABLE)
            .build();

    public final FlagRef useFallMine =
            flagBuilder(Configs.INTERACT_CONFIG, TP_MINE_USE_FALL_MINE).build();

    public final KeyBindRef tryTpSteal = hotkey(Configs.INTERACT_CONFIG, makePath("tp-interact.try-tp-steal-chest-key"))
            .defaultValue(new MultiKeyBind())
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class), this::onInteractBlock);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onInteractEntity);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onBlockMine);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    private final float ENABLE_NO_TP_DISTANCE = 1.14f;

    public void onInteractBlock(Event<PlayerInteractBlockC2SPacket> event) {
        if (event.isCancelled()) return;
        if (enable.get()) {
            var packetToSend = event.context;
            BlockHitResult hit = event.context.getBlockHitResult();
            BlockPos blockPos = hit.getBlockPos();
            double distance = MineTasks.getMineExtra().getReachDistance() + ENABLE_NO_TP_DISTANCE;
            if (new Box(blockPos).squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(distance)) {
                if (!mc.player.isSneaking() && tryTpSteal.get().isAllPressed()) {
                    if (mc.world.getBlockEntity(blockPos) instanceof Inventory inventory) {
                        int size = inventory.size();
                        if (inventory instanceof ChestBlockEntity chest) {
                            BlockState state = chest.getCachedState();
                            if (state.getBlock() instanceof ChestBlock chestBlock) {
                                if (ChestBlock.isChestBlocked(mc.world, blockPos)) {
                                    size = 0;
                                } else if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
                                    size = 54;
                                }
                            }
                        }
                        if (inventory instanceof ShulkerBoxBlockEntity shulker) {
                            BlockState state = shulker.getCachedState();
                            if (shulker.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED
                                    && canShulkerOpen(blockPos, state)) {
                                size = 0;
                            }
                        }
                        if (size != 0) {
                            int nextPredictedIndex = (InvTasks.LAST_SYNC_ID % 100) + 1;
                            int containerSize = size;
                            if (tpToBlock(
                                    blockPos,
                                    (sel) -> executeTp(sel, () -> {
                                        Debug.chat("[TpInteract] 尝试和物品栏交互");
                                        Listener.sendPacketNoEvents(packetToSend);
                                        ScreenHandler fakeScreenHandler =
                                                GenericContainerScreenHandler.createGeneric9x6(
                                                        nextPredictedIndex, mc.player.getInventory());
                                        ScreenHandler handler = mc.player.currentScreenHandler;
                                        try {
                                            mc.player.currentScreenHandler = fakeScreenHandler;
                                            for (var i = 0; i < containerSize; ++i) {
                                                mc.interactionManager.clickSlot(
                                                        fakeScreenHandler.syncId,
                                                        i,
                                                        0,
                                                        SlotActionType.QUICK_MOVE,
                                                        mc.player);
                                            }
                                        } finally {
                                            mc.player.currentScreenHandler = handler;
                                        }
                                    }))) {
                                event.cancel();
                            }
                            return;
                        }
                    }
                }
                if (tpToBlock(blockPos, (sel) -> executeTp(sel, packetToSend))) {
                    event.cancel();
                }
            }
        }
    }

    private boolean canShulkerOpen(BlockPos pos, BlockState state) {
        Box box = ShulkerEntity.calculateBoundingBox(
                        1.0F, (Direction) state.get(ShulkerBoxBlock.FACING), 0.0F, 0.5F, pos.toBottomCenterPos())
                .contract(1.0E-6);
        return mc.world.isSpaceEmpty(box);
    }

    public void onInteractEntity(Event<PlayerInteractEntityC2SPacket> event) {
        if (event.isCancelled()) return;
        if (enable.get()) {
            var packet = event.context;
            // filter ATTACK packets
            if (true || !Objects.equals(((Enum) packet.type.getType()).name(), "ATTACK")) {
                int entityId = packet.entityId;
                Entity entity = mc.world.getEntityById(entityId);
                if (entity != null
                        && entity.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                                > MathUtils.s2(CombatTasks.getCombatExtra().getAttackRange() + ENABLE_NO_TP_DISTANCE)) {
                    if (tpToEntity(entity, packet)) {
                        event.cancel();
                    }
                }
            }
        }
    }

    public void onBlockMine(Event<PlayerActionC2SPacket> event) {
        if (event.isCancelled()) return;
        if (enable.get()) {
            var packet = event.context;
            switch (packet.getAction()) {
                case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {}
                default -> {
                    return;
                }
            }
            BlockPos blockPos = packet.getPos();
            double distance = MineTasks.getMineExtra().getReachDistance() + ENABLE_NO_TP_DISTANCE;
            if (new Box(blockPos).squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(distance)) {
                Packet<?> packetToSend = event.context();
                if (tpToBlock(
                        blockPos,
                        useFallMine.get()
                                ? (selectedPos) -> {
                                    PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
                                    if (access.setStartFailBreakPos(blockPos)) {
                                        PlayerActionC2SPacket stopMinePacket = new PlayerActionC2SPacket(
                                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                                                blockPos,
                                                Direction.UP,
                                                NetworkUtils.generateNextSequence());
                                        if (executeTp(selectedPos, packetToSend, stopMinePacket)) {
                                            return true;
                                        } else {
                                            access.setStartFailBreakPos(null);
                                            return false;
                                        }
                                    }
                                    return executeTp(selectedPos, packetToSend);
                                }
                                : (sel) -> executeTp(sel, packetToSend))) {
                    event.cancel();
                }
            }
        }
    }

    public boolean tpToBlock(BlockPos pos, Predicate<Vec3d> callBack) {
        // compat Freecam
        Vec3d selectedPos = RenderUtils.getCameraEntityPos();
        double eyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        if (MineTasks.distanceOutOfReach(pos, selectedPos.add(0, eyeHeight, 0))
                || MovTasks.ENGIN.checkEnvironmentCollision(mc.player, selectedPos, true)) {
            selectedPos = null;
            for (var deltaPos : MineTasks.getMineExtra().getBlocksAround()) {
                Vec3d checkPos = pos.add(deltaPos).toBottomCenterPos().add(0, 1E-4, 0);
                if (!MineTasks.distanceOutOfReach(pos, checkPos.add(0, eyeHeight, 0))
                        && !MovTasks.ENGIN.checkEnvironmentCollision(mc.player, checkPos, true)) {
                    selectedPos = checkPos;
                    break;
                }
            }
        }
        if (selectedPos == null) {
            Debug.chat("[TpAct] Can not reach the target");
            return false;
        } else {
            return callBack.test(selectedPos);
        }
    }

    public boolean tpToEntity(Entity pos, Packet<?> packetToSend) {
        Vec3d selectedPos = RenderUtils.getCameraEntityPos();
        Box entityBox = pos.getBoundingBox();
        double attackRange = CombatTasks.getCombatExtra().getAttackRange() + 1.0d;
        double eyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        if (entityBox.squaredMagnitude(selectedPos.add(0, eyeHeight, 0)) > MathUtils.s2(attackRange)) {
            selectedPos = null;
            // make an algorithm to
            BlockPos entityPos = pos.getBlockPos();
            // todo: move this to CombatExtra or PositionPredictor or something
            for (var deltaPos : MineTasks.getMineExtra().getBlocksAround()) {
                Vec3d checkPos = entityPos.add(deltaPos).toBottomCenterPos().add(0, 1E-4, 0);
                if (entityBox.squaredMagnitude(checkPos.add(0, eyeHeight, 0)) < MathUtils.s2(attackRange)
                        && !MovTasks.ENGIN.checkEnvironmentCollision(mc.player, checkPos, true)) {
                    selectedPos = checkPos;
                    break;
                }
            }
        }
        if (selectedPos == null) {
            Debug.chat("[TpAct] Can not reach the target");
            return false;
        } else {
            return executeTp(selectedPos, packetToSend);
        }
    }

    public boolean executeTp(Vec3d pos, Runnable callback) {
        Vec3d current = mc.player.getPos();
        MovTasks.MovingContext context = MovTasks.createPlayerMovContext();
        List<Vec3d> from = MovTasks.generateTpSequence(current, pos, false, 200, true);
        List<Vec3d> to = MovTasks.generateTpSequence(pos, current, false, 200, true);
        if (from.isEmpty() || to.isEmpty()) {
            Debug.chat("[TpAct] Can not reach the target");
            return false;
        } else {
            if (RenderTasks.DEBUG_RENDER_INTERACTION) {
                RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                        RenderTasks.DEBUG_TICK,
                        new RenderTasks.BoxObject(
                                mc.player.dimensions.getBoxAt(pos), ColorUtils.withAlpha(Color.MAGENTA, 0.25F))));
            }
            List<MovTasks.MovInfo> moveInfo = new ArrayList<>();
            moveInfo.addAll(MovTasks.createMovInfoList(from));
            moveInfo.addAll(MovTasks.createMovInfoList(to));
            var actions = MovTasks.createMovingPacketsForMovSequence(context, moveInfo, false, true);
            for (var i = 0; i < from.size(); ++i) {
                actions.get(i).run();
            }
            callback.run();
            for (int i = from.size(); i < actions.size(); ++i) {
                if (actions.get(i).success) {
                    actions.get(i).run();

                } else {
                    List<MovTasks.MovInfo> leftTasks = moveInfo.subList(i, actions.size());
                    Tasks.scheduleDelayed(
                            () -> {
                                MovTasks.scheduleFarawayMoveInternal(leftTasks, false, context.resetTick(), true);
                            },
                            1);
                    break;
                }
            }
            MovTasks.setupAutoResync();
            ClientPlayerAccess.of(mc.player).setForceNoFall(true);
            return true;
        }
    }

    public boolean executeTp(Vec3d pos, Packet<?>... packetToSend) {
        return executeTp(pos, () -> {
            for (Packet<?> packet : packetToSend) {
                Listener.sendPacketNoEvents(packet);
            }
        });
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event){
        switch (event.context().getValue()){
            case HACKING, VANILLA -> enable.set(true);
            default -> enable.set(false);
        }
    }
}
