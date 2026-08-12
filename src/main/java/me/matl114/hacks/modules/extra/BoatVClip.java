package me.matl114.hacks.modules.extra;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.NetworkUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BoatVClip extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;

    public BoatVClip() {
        super("BoatVClip");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final ModulePath boatVClip = makePath(Configs.TEST_CONFIG, "other.boat-vclip");

    public final FlagRef enable = flagBuilder(boatVClip.addEnable()).build();
    public final KeyBindRef hotkey = moduleEntry(boatVClip.addHotkey(), new MultiKeyBind(), boatVClip.addEnable())
            .build();

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        minedBlockPos.clear();
        blockStateMap.clear();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
    }

    Map<BlockPos, BlockState> blockStateMap = new HashMap<>();
    Set<BlockPos> minedBlockPos = new HashSet<>();

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> update) {
        if (enable.get()) {
            // minedBlockPos.remove(update.context.getPos());
            BlockPos pos = update.context.getPos();

            if (blockStateMap.containsKey(pos)) {
                //                if(mc.player.hasVehicle()){
                //                    Tasks.scheduleDelayedPre(()->{
                //                        Listener.sendPacketNoEvents(new
                // PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP,
                // NetworkUtils.generateNextSequence()));
                //                        Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                //                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP,
                // NetworkUtils.generateNextSequence()));
                //                    }, 0);
                //                }
                update.cancel();
            }
        }
    }

    Entity lastVehicle;
    int cd = 0;
    Vec3d vec3d = Vec3d.ZERO;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enable.get()) {
            if (!EntityUtils.isEntityValid(lastVehicle)
                    || lastVehicle.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                            > CombatExtra.INSTANCE.getAttackRange()) {
                lastVehicle = null;
            }
            if (lastVehicle == null) {
                lastVehicle =
                        mc
                                .world
                                .getOtherEntities(
                                        mc.player, mc.player.getBoundingBox().expand(1.5, 1.5, 1.5))
                                .stream()
                                .filter(s -> s instanceof VehicleEntity)
                                .findAny()
                                .orElse(null);
            }
            if (lastVehicle != null) {
                if (!mc.player.hasVehicle()) {
                    if (vec3d == null || vec3d.squaredDistanceTo(lastVehicle.getPos()) > 0.25) {
                        vec3d = lastVehicle.getPos();
                        blockStateMap.clear();
                        ;
                        Box box = lastVehicle.getBoundingBox();
                        Box expand = box.withMinY(box.minY - 0.5).withMaxY(box.minY + 0.5);
                        List<BlockPos> boxes = CollisionUtil.getIntersectingBlockPositions(mc.world, expand, false);
                        for (BlockPos boxPos : boxes) {
                            BlockState state = mc.world.getBlockState(boxPos);
                            if (!state.isAir() && !state.isLiquid()) {
                                blockStateMap.put(boxPos, state);
                            }
                        }
                        for (var re : blockStateMap.entrySet()) {
                            BlockPos pos = re.getKey();
                            //                            Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                            //                                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                            //                                    pos,
                            //                                    Direction.UP,
                            //                                    NetworkUtils.generateNextSequence()));
                            Listener.sendPacketNoEvents(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                                    pos,
                                    Direction.UP,
                                    NetworkUtils.generateNextSequence()));
                            //                    }
                            cd = 0;
                        }
                    }

                    if (cd > 4) {
                        //
                        cd = 0;
                        Box boxxx = lastVehicle.getBoundingBox();
                        var re = new EntityHitResult(
                                lastVehicle, boxxx.getCenter().add(0, boxxx.getLengthY() / 2, 0));
                        InteractUtils.simulateInteract(re);
                    } else {
                        cd += 1;
                    }
                } else {
                    vec3d = null;
                }
                for (var re : blockStateMap.entrySet()) {
                    BlockPos pos = re.getKey();
                    mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (enable.get()) {
            //            for(var pos : blockStateMap.entrySet()){
            //                mc.world.setBlockState(pos.getKey(), pos.getValue());
            //            }
            //            blockStateMap.clear();
        }
        return true;
    }
}
