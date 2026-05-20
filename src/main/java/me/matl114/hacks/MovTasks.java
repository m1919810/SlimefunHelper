package me.matl114.hacks;

import static me.matl114.hacks.RenderTasks.*;
import static me.matl114.utils.CollisionUtil.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.move.*;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import me.matl114.utils.commands.CommandUtils;
import me.matl114.utils.commands.interruption.LogicalError;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.api.TabResult;
import me.matl114.utils.commands.params.impl.AbstractArgumentType;
import me.matl114.utils.commands.params.impl.PosArgumentResult;
import me.matl114.utils.commands.params.impl.PosArgumentType;
import me.matl114.utils.commands.params.types.ExecutePos;
import me.matl114.utils.entity.EntityMovementStatus;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

// todo: add more Functional Method as API
public class MovTasks {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // todo more optimize

    // teleport module
    @ApiMethod(deprecate = true)
    public static void farawayMoveTo(Vec3d vec3d, boolean updatePlayer) {
        farawayMoveFromTo(mc.player.getPos(), vec3d, null, updatePlayer);
    }

    @ApiMethod(deprecate = true)
    public static void farawayMove(Vec3d vec3d, boolean updatePlayer) {
        farawayMoveFromTo(mc.player.getPos(), mc.player.getPos().add(vec3d), null, updatePlayer);
    }

    @ApiMethod(deprecate = true)
    public static void farawayMoveFromTo(Vec3d from, Vec3d to, Boolean onGroundOverride, boolean updatePlayer) {
        Vec3d vec3d = to.subtract(from);
        double len = vec3d.length();
        // tiny movements considered as a method to reset falldistance

        if (mc.player.hasVehicle()) {
            Entity vehicle = mc.player.getVehicle();
            double maxOnceLen = 10 - 1E-2;
            if (len >= maxOnceLen) {
                double speedArg = 10;
                int packetNum = (int) (((len + 1) / speedArg));
                // calculate when elytra
                // boolean mc.world.getGameRules().get()
                if (packetNum > 0)
                    for (var p = 0; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(vehicle));
            }
            double deltaY = vehicle.getY() - mc.player.getY();
            vehicle.setPosition(to.add(0, deltaY, 0));
            mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(vehicle));
            if (updatePlayer) {
                mc.player.setPosition(to);
            }
        } else {
            double maxOnceLen = (mc.player.isFallFlying() ? (10 * Math.sqrt(3)) : 10) - 1E-2;

            if (len >= maxOnceLen) {
                double speedArg = 10;
                int packetNum = (int) (((len + 1) / speedArg));
                // calculate when elytra
                // boolean mc.world.getGameRules().get()
                if (packetNum > 0)
                    for (var p = 0; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(mc.player.isOnGround(), false));
            }

            if (onGroundOverride != null) {
                mc.player.setOnGround(onGroundOverride);
            }
            mc.getNetworkHandler()
                    .sendPacket(VPacket.newPositionAndOnGround(
                            to.getX(), to.getY(), to.getZ(), mc.player.isOnGround(), false));
            if (updatePlayer) mc.player.setPosition(to);
        }
    }

    public static void moveToWithPackets(Vec3d to, Boolean onGroundOverride) {
        Entity entity = mc.player.getRootVehicle();
        if (Objects.equals(to, mc.player.getPos())) {
            if (mc.player.hasVehicle()) {
                mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(entity));
            } else {
                mc.getNetworkHandler()
                        .sendPacket(VPacket.newOnGroundOnly(
                                onGroundOverride != null ? onGroundOverride : mc.player.isOnGround(), false));
            }
        } else {
            double deltaY = entity.getY() - mc.player.getY();
            //            Vec3d curr = entity.getPos();
            //            Vec3d currPlayer = mc.player.getPos();
            entity.setPosition(to.add(0, deltaY, 0));
            mc.player.setPosition(to);
            if (mc.player.hasVehicle()) {
                mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(entity));
            } else {
                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                to.getX(),
                                to.getY(),
                                to.getZ(),
                                onGroundOverride != null ? onGroundOverride : mc.player.isOnGround(),
                                false));
            }
        }
    }

    @With
    public static record MovInfo(Vec3d vec3d, Boolean oGroundOverride, boolean updatePlayer, Vec2f rotationOverride) {
        public static MovInfo create(Vec3d to) {
            return new MovInfo(to, null, true, null);
        }

        public static MovInfo createNotOnGround(Vec3d to) {
            return new MovInfo(to, Boolean.FALSE, true, null);
        }

        public static MovInfo createNoUpdate(Vec3d to) {
            return new MovInfo(to, null, false, null);
        }

        public boolean isEmptyTo(Vec3d currentPos) {
            return vec3d.squaredDistanceTo(currentPos) < 1e-4 && rotationOverride == null && oGroundOverride == null;
        }
    }

    public static record MovingContext(
            MutableObject<Vec3d> from,
            MutableObject<Vec3d> tickFirstGoodVec,
            AtomicInteger currentTokenLimit,
            AtomicInteger currentTokenInTick) {
        public static MovingContext create(Vec3d from) {
            return new MovingContext(
                    new MutableObject<>(from), new MutableObject<>(from), new AtomicInteger(0), new AtomicInteger(0));
        }

        public MovingContext resetTick() {
            currentTokenInTick.set(0);

            currentTokenLimit.set(0);
            tickFirstGoodVec.setValue(from.getValue());
            return this;
        }
    }

    @ApiMethod
    public static MovingContext createMovContext(double a1, double b1, double c1) {
        return MovingContext.create(new Vec3d(a1, b1, c1));
    }

    @ApiMethod
    public static MovingContext createMovContext(Vec3d vec3d) {
        return MovingContext.create(vec3d);
    }

    @ApiMethod
    public static MovingContext createPlayerMovContext() {
        return MovingContext.create(mc.player.getPos());
    }

    @ApiMethod
    public static MovInfo createNotOnGround(Vec3d vec3) {
        return MovInfo.createNotOnGround(vec3);
    }

    @ApiMethod
    public static MovInfo createMovInfo(Vec3d to) {
        return MovInfo.create(to);
    }

    @ApiMethod
    public static MovInfo createMovInfo(Vec3d to, Boolean onGround, boolean updatePlayerPos, Vec2f rotationOverride) {
        return new MovInfo(to, onGround, updatePlayerPos, rotationOverride);
    }

    @ApiMethod
    public static List<MovInfo> createMovInfoList(List<Vec3d> vec3ds) {
        return vec3ds.stream().map(MovTasks::createMovInfo).collect(Collectors.toCollection(ArrayList::new));
    }

    @ApiMethod
    public static List<MovInfo> createMovInfoList(
            List<Vec3d> vec3ds, boolean onGround, boolean updateplayerpos, Vec2f rotation) {
        return vec3ds.stream()
                .map(v -> MovTasks.createMovInfo(v, onGround, updateplayerpos, rotation))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @ApiMethod
    public static List<MovInfo> createNotOnGroundList(List<Vec3d> vec3ds) {
        return vec3ds.stream().map(MovTasks::createNotOnGround).collect(Collectors.toCollection(ArrayList::new));
    }

    public static void scheuleFarawayMove(
            Vec3d from, List<MovInfo> deltaMovements, boolean allowNextTick, boolean considerNoFall) {

        scheduleFarawayMoveInternal(deltaMovements, allowNextTick, MovingContext.create(from), considerNoFall);
    }

    @ApiMethod
    public static void scheduleMoveSequence(
            MovingContext context, List<MovInfo> deltaMovements, boolean allowNextTick, boolean noFall) {
        scheduleFarawayMoveInternal(deltaMovements, allowNextTick, context, noFall);
    }

    public static void scheduleFarawayMoveInternal(
            List<MovInfo> deltaMovements, boolean allowNextTick, MovingContext context, boolean considerNoFall) {

        //        boolean shouldCheckSpeed = ! mc.player.isFallFlying() || (!
        // mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
        //        double maxOnceLen = (mc.player.isFallFlying()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        // filter front not-needed packets
        if (true) {
            List<StepActionBundle> bundleList =
                    createMovingPacketsForMovSequence(context, deltaMovements, allowNextTick, considerNoFall);
            for (int i = 0; i < bundleList.size(); ++i) {
                if (bundleList.get(i).success) {
                    bundleList.get(i).run();
                } else {
                    if (allowNextTick) {
                        List<MovInfo> leftTasks = deltaMovements.subList(i, deltaMovements.size());
                        Tasks.scheduleDelayed(
                                () -> {
                                    scheduleFarawayMoveInternal(
                                            leftTasks, allowNextTick, context.resetTick(), considerNoFall);
                                },
                                1);
                        return;
                    } else {
                        bundleList.get(i).run();
                    }
                }
            }
            return;
        }
    }

    public static final class StepActionBundle {
        public ArrayList<Packet<?>> packets = new ArrayList<>(4);
        public ArrayList<Runnable> tasks = new ArrayList<>(1);
        public ArrayList<Runnable> postTasks = new ArrayList<>(1);
        public boolean success = true;

        public void failure() {
            success = false;
        }

        public StepActionBundle() {}

        public void add(Packet<?> packet) {
            packets.add(packet);
        }

        public void add(Runnable task) {
            tasks.add(task);
        }

        public void addPost(Runnable task) {
            postTasks.add(task);
        }

        public void run() {
            tasks.forEach(Runnable::run);
            for (var packet : packets) {
                mc.getNetworkHandler().sendPacket(packet);
            }
            postTasks.forEach(Runnable::run);
            // post move task
        }
    }

    // todo reconstruct the fucking project
    // return a N + 1 size of StepActionBundle
    @ApiMethod
    public static List<StepActionBundle> createMovingPacketsForMovSequence(
            MovingContext context, List<MovInfo> deltaMovements, boolean allowFailure, boolean considerNoFall) {
        //        boolean shouldCheckSpeed = ! mc.player.isFallFlying() || (!
        // mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
        //        double maxOnceLen = (mc.player.isFallFlying()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        // filter front not-needed packets
        List<StepActionBundle> packets = new ArrayList<>();
        int emptyMoveCnt = 0;
        // prepare for all movements,
        for (int i = 0; i <= deltaMovements.size(); ++i) {
            packets.add(new StepActionBundle());
        }
        {
            boolean startFirstMove = false;
            List<MovInfo> moveList = new ArrayList<>();
            Vec3d currentVec330 = context.from.getValue();
            for (int i = 0; i < deltaMovements.size(); ++i) {
                // filter packets that are not removing at the front
                if (!startFirstMove && deltaMovements.get(i).isEmptyTo(currentVec330)) {
                    emptyMoveCnt += 1;
                    continue;
                } else {
                    startFirstMove = true;
                    moveList.add(deltaMovements.get(i));
                }
            }
            if (moveList.isEmpty()) return packets;
            deltaMovements = moveList;
        }

        Vec3d originalPositionTick = context.tickFirstGoodVec.getValue();
        boolean riding = mc.player.hasVehicle();
        Entity rootEntity = mc.player.getRootVehicle();
        double deltaY = rootEntity.getY() - mc.player.getY();
        double minY = context.from.getValue().y;
        double maxY = minY;
        boolean currentOnGround = !considerNoFall && mc.player.isOnGround();
        Vec2f currentPitchYaw = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        // calculate current tokens if it is the first move of the tick
        if (context.currentTokenInTick.get() == 0) {
            // gain tokens
            int maxTokenNeeded = 0;
            Vec3d lastPos = originalPositionTick;
            int packetCount = 0;
            for (var move : deltaMovements) {
                packetCount += 1;
                Vec3d movingPos = move.vec3d();
                Vec3d movement = movingPos.subtract(lastPos);

                lastPos = movingPos;
                double d7 = movement.length();
                double d8 = movingPos.subtract(originalPositionTick).length();
                double d10 = Math.max(d7, d8);
                // 比如
                // 使用 9 9 9 9 9作为移动的， 每次packet + 1
                // 即使不用token,d10也不会超过packetNum * (...)

                int tokenNeeded = ((int) Math.ceil(d10 / 9.9)) - packetCount;
                maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            }
            //            Debug.info("check ", maxTokenNeeded);
            if (maxTokenNeeded > 0) {
                // 粗略估计
                // remove check, just do it 对的
                //                if(maxTokenNeeded > 20 - deltaMovements.size()){
                //                    //unable to reach so faraway
                ////                    Debug.info("UnReachable ");
                //                    return packets;
                //                }
                if (maxTokenNeeded > 20 - deltaMovements.size()) {
                    // unable to reach so faraway
                    //                    Debug.info("UnReachable ");
                    return packets;
                }
                for (int i = 0; i < maxTokenNeeded; ++i) {
                    context.currentTokenLimit.set(20);
                    context.currentTokenInTick.incrementAndGet();
                    //  Debug.chat("send zero packet !",context.currentTokenInTick.get(),
                    // context.currentTokenLimit.get());
                    if (riding) {
                        packets.get(emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));

                    } else {
                        packets.get(emptyMoveCnt).add(VPacket.newOnGroundOnly(currentOnGround, false));
                        //
                        // mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(mc.player.isOnGround()));
                    }
                }
            }
        }
        // calculate tokens depends on paper sourceocode
        for (int i = 0; i < deltaMovements.size(); ++i) {
            MovInfo info = deltaMovements.get(i);
            Vec3d fromNow = context.from.getValue();
            Vec3d vec3d = info.vec3d().subtract(fromNow);
            maxY = Math.max(maxY, info.vec3d().y);
            minY = Math.min(minY, info.vec3d().y);
            double len = vec3d.length();
            double len2 = originalPositionTick.subtract(info.vec3d()).length();
            double speedArg = 9.8;
            if (len == 0 && len2 == 0) {
                // d10 is 0 server side, can regain token
                context.currentTokenLimit.set(20);
                context.currentTokenInTick.incrementAndGet();
                if (info.oGroundOverride != null) {
                    currentOnGround = info.oGroundOverride;
                    final boolean of = currentOnGround;
                    packets.get(i + emptyMoveCnt).add(() -> mc.player.setOnGround(of));
                    //                    mc.player.setOnGround(info.oGroundOverride);
                }
                boolean hasRot =
                        info.rotationOverride != null && !Objects.equals(info.rotationOverride, currentPitchYaw);
                if (hasRot) {
                    currentPitchYaw = new Vec2f(info.rotationOverride.x, info.rotationOverride.y);
                    final float px = currentPitchYaw.x;
                    final float py = currentPitchYaw.y;
                    packets.get(i + emptyMoveCnt).add(() -> {
                        mc.player.setPitch(px);
                        mc.player.setYaw(py);
                    });
                }
                if (riding) {
                    packets.get(i + emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));
                } else {
                    if (hasRot) {
                        packets.get(i + emptyMoveCnt)
                                .add(VPacket.newLookAndOnGround(
                                        currentPitchYaw.y, currentPitchYaw.x, currentOnGround, false));
                    } else {
                        packets.get(i + emptyMoveCnt).add(VPacket.newOnGroundOnly(currentOnGround, false));
                    }
                }
            } else {
                int ExtraTokenNeeded = (int) Math.ceil(((Math.max(len, len2)) / speedArg));
                // +1代表这个包发出去之后的结果
                int tokenNow = context.currentTokenInTick.get() + 1;
                //            tokenLimit = Math.max(tokenLimit, 1);
                // 超出了tokenLimit, 会被强制重置为1（server side)
                // 需要把剩下的移动到下一个tick执行
                if ((Math.min(ExtraTokenNeeded, tokenNow) >= Math.max(5, context.currentTokenLimit.get()))) {
                    if (allowFailure
                            && context.currentTokenInTick.get() > Math.max(5, context.currentTokenLimit.get())) {
                        //
                        packets.get(i + emptyMoveCnt).failure();
                        if (considerNoFall && i > 0) {
                            //                            if (Math.abs(maxY - minY)
                            //                                    >
                            // mc.player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) - 1) {

                            //                                mc.player.fallDistance = MovTasks.
                            // FORCE_RESET_DISTANCE;
                            // in case that resync packet cause OnGround falldamage
                            packets.get(i - 1 + emptyMoveCnt).addPost(() -> {
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                                mc.player.setOnGround(false);
                            });
                            // }
                        }
                        // return here because of MovingContext currentPosition
                        return packets;
                    }
                }
                context.currentTokenLimit.decrementAndGet();
                context.currentTokenInTick.incrementAndGet();
                //  Debug.chat("before move", context.currentTokenInTick.get(), context.currentTokenLimit.get());
                if (info.oGroundOverride() != null) {
                    currentOnGround = info.oGroundOverride();
                    final boolean of = currentOnGround;
                    packets.get(i + emptyMoveCnt).add(() -> mc.player.setOnGround(of));
                }
                boolean hasRot =
                        info.rotationOverride != null && !Objects.equals(info.rotationOverride, currentPitchYaw);
                if (hasRot) {
                    currentPitchYaw = new Vec2f(info.rotationOverride.x, info.rotationOverride.y);
                    final float px = currentPitchYaw.x;
                    final float py = currentPitchYaw.y;
                    packets.get(i + emptyMoveCnt).add(() -> {
                        mc.player.setPitch(px);
                        mc.player.setYaw(py);
                    });
                }
                Vec3d to = info.vec3d();
                if (riding) {
                    packets.get(i + emptyMoveCnt).add(() -> {
                        rootEntity.setPosition(to.add(0, deltaY, 0));
                        if (info.updatePlayer()) {
                            mc.player.setPosition(to);
                        }
                    });
                    packets.get(i + emptyMoveCnt).add(VPacket.newVehicleMove(rootEntity));
                    context.from.setValue(to);
                } else {
                    var packet = hasRot
                            ? VPacket.newFull(
                                    to.getX(),
                                    to.getY(),
                                    to.getZ(),
                                    currentPitchYaw.y,
                                    currentPitchYaw.x,
                                    currentOnGround,
                                    false)
                            : VPacket.newPositionAndOnGround(to.getX(), to.getY(), to.getZ(), currentOnGround, false);
                    packets.get(i + emptyMoveCnt).add(packet);
                    if (info.updatePlayer()) {
                        packets.get(i + emptyMoveCnt).add(() -> {
                            mc.player.setPosition(to);
                        });
                    }
                    context.from.setValue(to);
                }
            }
        }
        if (considerNoFall) {
            // if (Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) - 1) {
            packets.get(packets.size() - 1).add(() -> {
                ClientPlayerAccess.of(mc.player).setForceNoFall(true); // = MovTasks. FORCE_RESET_DISTANCE;
                // in case that resync packet cause OnGround falldamage
                mc.player.setOnGround(false);
            });
            // }
        }
        return packets;
    }

    public static double searchFirstNoCollisionSpaceYHeight(Vec3d origin, double min, double max, boolean upOrDown) {
        Box boundingBox = mc.player.dimensions.getBoxAt(origin);
        double tmin = upOrDown ? min : -max;
        double tmax = upOrDown ? max : -min;
        Box involved = CollisionUtil.resetY(
                boundingBox,
                origin.y + tmin,
                origin.y + tmax); //  boundingBox.stretch(0, max * (upOrDown? 1.0D : -1.0D), 0).stretch();
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.RED;
        debugBox(involved);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        CollisionUtil.getCollisions(
                mc.world,
                mc.player,
                involved,
                collisionsVoxel,
                collisionsBB,
                // CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null,
                null,
                null);
        return searchFirstNoYConflictYHeight(origin.y, collisionsBB, collisionsVoxel, min, max, upOrDown);
    }

    public static double searchFirstNoYConflictYHeight(
            double originY,
            List<Box> collisionsBB,
            List<VoxelShape> collisionShapes,
            double min,
            double max,
            boolean upOrDown) {
        List<Box> allBoxes = new ArrayList<>(collisionsBB);
        for (VoxelShape shape : collisionShapes) {
            allBoxes.addAll(shape.getBoundingBoxes());
        }
        allBoxes.sort(Comparator.comparingDouble(b -> b.minY * (upOrDown ? 1.0D : -1.0D)));
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.BLUE;
        for (var box : allBoxes) {
            debugBox(box);
        }
        DEBUG_RENDER_COLLISION_RENDERING = false;
        if (upOrDown) {
            // up sort minY from small to big
            double levelY = originY + min;
            // give 1E-7 more space
            double playerBoxHeight = mc.player.dimensions.height() + 2E-7;
            // double lastStableY;
            for (var re : allBoxes) {
                // 高度差上已经有碰撞了
                if (levelY + playerBoxHeight > re.minY && levelY < re.maxY) {
                    if (re.maxY < originY + max) {
                        levelY = re.maxY;
                    } else {
                        // new Y out of bound
                        break;
                    }
                } else if (levelY + playerBoxHeight <= re.minY) {
                    // 搜索结束
                    // 因为按re.minY从小到大排,这个位置将不会和任意后面的碰撞
                    break;
                }
            }
            // fixme returning y value out of bound (? need test, havn't seen problem )
            return levelY - originY;
        } else {
            double levelY = originY - min;
            double playerBoxHeight = mc.player.dimensions.height() + 2E-7;
            // 由大到小排
            for (var re : allBoxes) {
                // 高度差上已经有碰撞了
                if (levelY < re.maxY && levelY + playerBoxHeight > re.minY) {
                    double newLevelY = re.minY - playerBoxHeight;
                    if (newLevelY > originY - max) {
                        levelY = newLevelY;
                    } else {
                        // new Y out of bound
                        break;
                    }
                } else if (levelY >= re.maxY) {
                    // 搜索结束
                    break;
                }
            }
            return levelY - originY;
        }
    }

    @ApiMethod
    private static boolean checkEnvironmentCollision(
            Entity entity, Vec3d pos, boolean checkLiquid, boolean ignoreChunkBorder) {
        // should also consider entity collision, shit shulker
        // set position for bounding box update!
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        Box oldBox = entity.getBoundingBox();
        Box newBox = entity.dimensions.getBoxAt(pos);
        BiFunction<BlockState, BlockPos, Box> currentFromToPredicateFilter = checkLiquid
                ? (blockstate, blockpos) -> {
                    if (blockstate != null) {
                        Block block = blockstate.getBlock();
                        // I DO NOT WANT BY TP SEQUENCE ENTER ANY OF THESE FIRE BLOCKS BECAUSE IT MAY LIT ME UP!
                        if (block == Blocks.LAVA || block == Blocks.SOUL_FIRE || block == Blocks.FIRE) {
                            // filter lava blocks, we should be careful
                            Box lavaBlockBox = Box.enclosing(blockpos, blockpos);
                            if (CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, newBox)
                                    || CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, oldBox)) {
                                return lavaBlockBox;
                            }
                        }
                    }
                    return null;
                }
                : null;
        CollisionUtil.getCollisions(
                entity.getEntityWorld(),
                entity,
                newBox,
                collisionsVoxel,
                collisionsBB,
                // may cancel unloaded chunks?
                // COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
                ignoreChunkBorder
                        ? CollisionUtil.COLLISION_FLAG_CHECK_BORDER
                        : (COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS | CollisionUtil.COLLISION_FLAG_CHECK_BORDER),
                null,
                null,
                currentFromToPredicateFilter);

        for (int i = 0, len = collisionsBB.size(); i < len; ++i) {
            final Box box = collisionsBB.get(i);
            if (!CollisionUtil.voxelShapeIntersect(box, oldBox)) {
                return true;
            }
        }

        for (int i = 0, len = collisionsVoxel.size(); i < len; ++i) {
            final VoxelShape voxel = collisionsVoxel.get(i);
            if (!CollisionUtil.voxelShapeIntersectNoEmpty(voxel, oldBox)) {
                return true;
            }
        }
        return false;
    }
    //    public static boolean isPlayerPassable(Vec3d pos){
    //        boolean passMoveTest = false;
    //        Vec3d vecpos = mc.player.getPos();
    //        Vec3d savedPosition = new Vec3d(vecpos.x, vecpos.y, vecpos.z);
    //        try{
    ////            if(isPassMovement()){
    ////                passMoveTest = true;
    ////            }else {
    ////                mc.player.move(MovementType.PLAYER, pos.subtract(savedPosition));
    ////                if(mc.player.getPos().squaredDistanceTo(pos) < 0.0625){
    ////                    passMoveTest = true;
    ////                }
    ////            }
    //            Vec3d movement = pos.subtract(savedPosition);
    //            Vec3d simu = collide(mc.player, movement);
    //            return  validMovementAsServer(simu, movement) && !checkEnvironmentCollision(mc.player, pos,
    // );//!isPlayerCollidingWithAnythingNew(mc.world, mc.player.getBoundingBox(), pos.x, pos.y, pos.z) ;//
    // mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox()))
    //        }finally {
    //            mc.player.setPosition(savedPosition);
    //        }
    //
    //    }
    private static final double maxYDelta = 190;
    public static boolean doingTp = false;
    public static Vec3d LAST_TP_REQUEST;
    public static Vec3d LAST_TP_FROM;

    @ApiMethod
    public static void executeTp(Vec3d target, double farawayTp, boolean command, boolean considerNoFall) {
        if (mc.player == null) return;
        LAST_TP_FROM = mc.player.getPos();
        LAST_TP_REQUEST = Vec3d.ZERO.add(target);
        scheduleTpInternal(MovingContext.create(mc.player.getPos()), target, farawayTp, command, false, considerNoFall);
    }
    // TODO: add height limit: based on world height limit: some world do not want player to reach lower than height
    // limit-or higher than bedrock or sth
    @ApiMethod
    public static List<Vec3d> generateTpSequence(MovingContext context, Vec3d target, boolean considerEnvironment) {
        return generateTpSequence(context.from().getValue(), target, false, 200, considerEnvironment);
    }

    @ApiMethod
    public static List<Vec3d> generateTpSequence(
            Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
        return ENGIN.generateTpSequence(current, target, command, farawayTp, considerEnvironment);
    }

    private static List<Vec3d> generateTpSequenceInternal(
            Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
        boolean collideAtTarget = checkEnvironmentCollision(mc.player, target, false, true);
        if (collideAtTarget) {
            if (command) {
                Debug.chat("目标位置存在方块碰撞冲突, 无法执行tp");
            }
            return List.of();
        }

        Vec3d movement0 = target.subtract(current);
        Vec3d sim = simulateMovement(mc.player, current, movement0, true);
        if (validMovementAsServer(movement0, sim)) {
            return List.of(current, current.add(movement0));
        }
        Vec3d horizontalMovement;
        horizontalMovement = new Vec3d(movement0.x, 0, movement0.z);

        double len = horizontalMovement.length();
        //        Box tpHorizontalPlate = mc.player.dimensions.getBoxAt(current).stretch(horizontalMovement);
        Box tpSmallerAxisPlate;
        Box tpLargerAxisPlate;
        if (Math.abs(movement0.x) > Math.abs(movement0.z)) {
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch(movement0.x, 0, 0);
            tpLargerAxisPlate = mc.player
                    .dimensions
                    .getBoxAt(current)
                    .offset(movement0.x, 0, 0)
                    .stretch(0, 0, movement0.z);
        } else {
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch(0, 0, movement0.z);
            tpLargerAxisPlate = mc.player
                    .dimensions
                    .getBoxAt(current)
                    .offset(0, 0, movement0.z)
                    .stretch(movement0.x, 0, 0);
        }

        double currentY = current.y;
        double targetY = target.y;
        World world = mc.world;
        double horizontalY;

        if (len <= farawayTp) {
            if (currentY >= world.getBottomY() && currentY <= world.getBottomY() + world.getHeight()) {
                Box boundariesFromBox = mc.player.dimensions.getBoxAt(current);
                Box boundariesToBox = mc.player.dimensions.getBoxAt(target);
                Box boundariesSmallAxis;
                Box boundariesLargeAxis;
                boundariesSmallAxis = CollisionUtil.resetY(
                        tpSmallerAxisPlate, world.getBottomY(), world.getBottomY() + world.getHeight());
                boundariesLargeAxis = CollisionUtil.resetY(
                        tpLargerAxisPlate, world.getBottomY(), world.getBottomY() + world.getHeight());
                boolean debug0 = DEBUG_RENDER_COLLISION_RENDERING;
                // DEBUG_RENDER_COLLISION_RENDERING = true;
                STATIC_DEBUG_COLOR = Color.CYAN;
                debugBox(tpSmallerAxisPlate);
                debugBox(tpLargerAxisPlate);
                STATIC_DEBUG_COLOR = Color.MAGENTA;
                debugBox(boundariesSmallAxis);
                debugBox(boundariesLargeAxis);
                //  DEBUG_RENDER_COLLISION_RENDERING = debug0;
                //
                ArrayList<Box> intoAABB = new ArrayList<>();
                ArrayList<VoxelShape> intoVoxels = new ArrayList<>();
                // should add LAVA with current/target xz coord into to avoid searching TP into them
                BiFunction<BlockState, BlockPos, Box> currentFromToPredicateFilter = considerEnvironment
                        ? (blockstate, blockpos) -> {
                            if (blockstate != null) {
                                Block block = blockstate.getBlock();
                                // I DO NOT WANT BY TP SEQUENCE ENTER ANY OF THESE FIRE BLOCKS BECAUSE IT MAY LIT ME UP!
                                if (block == Blocks.LAVA || block == Blocks.SOUL_FIRE || block == Blocks.FIRE) {
                                    // filter lava blocks, we should be careful
                                    Box lavaBlockBox = Box.enclosing(blockpos, blockpos);
                                    if (CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, boundariesFromBox)
                                            || CollisionUtil.voxelShapeIntersectHorizontal(
                                                    lavaBlockBox, boundariesToBox)) {
                                        return lavaBlockBox;
                                    }
                                }
                            }
                            return null;
                        }
                        : null;
                CollisionUtil.getCollisions(
                        world,
                        mc.player,
                        boundariesLargeAxis,
                        intoVoxels,
                        intoAABB,
                        COLLISION_FLAG_CHECK_BORDER,
                        null,
                        null,
                        currentFromToPredicateFilter);
                CollisionUtil.getCollisions(
                        world,
                        mc.player,
                        boundariesSmallAxis,
                        intoVoxels,
                        intoAABB,
                        COLLISION_FLAG_CHECK_BORDER,
                        null,
                        null,
                        currentFromToPredicateFilter);
                debug0 = DEBUG_RENDER_COLLISION_RENDERING;
                DEBUG_RENDER_COLLISION_RENDERING = true;
                STATIC_DEBUG_COLOR = Color.BLUE;
                for (var box : intoAABB) {
                    if (box.minY > 20) debugBox(box);
                }
                if (considerEnvironment) {
                    // some blocks are dangerous , so we should consider them as not passable
                    // for example LAVA , shit LAVA
                }
                DEBUG_RENDER_COLLISION_RENDERING = debug0;
                STATIC_DEBUG_COLOR = Color.CYAN;
                if (!intoAABB.isEmpty() || !intoVoxels.isEmpty()) {
                    //                        Box startPlace;
                    //                        Vec3d movement ;
                    Vec3d result;
                    //                        Box lastPlace;
                    double height1 = searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180, true);
                    double height2 = searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180, false);
                    //                        Debug.chat(height1, height2);
                    double height;
                    if (Math.abs(height1) > Math.abs(height2)) {
                        height = height2;
                    } else {
                        height = height1;
                    }
                    result = new Vec3d(0, height, 0);
                    boolean debug = DEBUG_RENDER_COLLISION_RENDERING;
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
                    RenderTasks.STATIC_DEBUG_COLOR = Color.WHITE;
                    debugBox(tpSmallerAxisPlate.offset(0, height, 0));
                    debugBox(tpLargerAxisPlate.offset(0, height, 0));
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
                    horizontalY = height + currentY;

                    // tp
                } else {
                    horizontalY = currentY;
                }
                // small use
                //

            } else {
                horizontalY = currentY;
            }
        } else {
            if (command) Debug.chat("水平差距过大,当前tp模式无法完成");
            return List.of();
        }
        if (Math.abs(horizontalY - currentY) > maxYDelta || Math.abs(horizontalY - targetY) > maxYDelta) {
            if (command) Debug.chat("y 差距过大,当前tp模式无法完成");
            return List.of();
        }
        Vec3d vec3d1 = current;
        Vec3d vec3d2 = vec3d1.add(new Vec3d(0, horizontalY - currentY, 0));
        Vec3d vec3d3 = vec3d2.add(horizontalMovement);
        Vec3d vec3d4 = vec3d3.add(new Vec3d(0, targetY - horizontalY, 0));
        return List.of(vec3d1, vec3d2, vec3d3, vec3d4);
    }

    @ApiMethod
    public static void scheduleTpInternal(
            MovingContext context,
            Vec3d target,
            double farawayThreshold,
            boolean command,
            boolean fastMode,
            boolean considerNoFall) {
        if (mc.player == null) return;
        if (command) Debug.chat("正在向", ChatUtils.getDisplayedLocationDouble(target), "执行tp行为");
        Vec3d current = context.from.getValue();
        // simulate direct move
        Vec3d movement0 = target.subtract(current);
        // flying into lava fuck, so we consider Environment
        List<Vec3d> vc3d0 = generateTpSequence(current, target, command, farawayThreshold, true);
        if (vc3d0.size() == 2) {
            boolean downward = movement0.y < -4;
            Vec3d target0 = vc3d0.get(1);
            MovInfo mainMove = new MovInfo(target0, downward ? Boolean.FALSE : null, true, null);
            List<MovInfo> movements =
                    // downward ? List.of(mainMove, MovInfo.create(target0.add(0, 9E-8,0))):
                    List.of(mainMove);
            // direct tp should also consider about setBack falldistance, passing considerNoFall arguments to do that
            scheduleFarawayMoveInternal(movements, true, context, considerNoFall);
            if (considerNoFall) {
                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
            }
        } else if (vc3d0.size() == 4) {
            if (command) {
                Debug.chat("执行TP序列");
            }
            Vec3d vec3d1 = vc3d0.get(0);
            Vec3d vec3d2 = vc3d0.get(1);
            Vec3d vec3d3 = vc3d0.get(2);
            Vec3d vec3d4 = vc3d0.get(3);
            boolean downWard = vec3d1.y > vec3d2.y + 4;
            boolean downWard0 = vec3d3.y > vec3d4.y + 4;
            context.from.setValue(vec3d1);
            //            boolean currentOnGround = mc.player.isOnGround();
            mc.player.setOnGround(false);
            if (fastMode) {
                List<MovInfo> movingPositions = new ArrayList<>();
                movingPositions.add(MovInfo.createNotOnGround(vec3d2));
                if (downWard) movingPositions.add(MovInfo.create(vec3d2));
                movingPositions.add(MovInfo.create(
                        vec3d3
                        //  .add(0, downWard ? 9E-8: 0,0)
                        ));
                movingPositions.add(MovInfo.createNotOnGround(vec3d4));
                //                if(downWard0)
                //                    movingPositions.add();
                if (downWard0) movingPositions.add(MovInfo.create(vec3d4));
                scheduleFarawayMoveInternal(movingPositions, true, context, considerNoFall);
            } else {
                context.from.setValue(vec3d1);
                // it should be ignored consider No Fall
                scheduleFarawayMoveInternal(List.of(MovInfo.createNotOnGround(vec3d2)), false, context, true);
                // farawayMoveFromTo(vec3d1 , vec3d2, downWard? Boolean.FALSE: null, true);
                doingTp = true;

                Tasks.scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(MovInfo.create(vec3d3)), false, context.resetTick(), false);
                            // farawayMoveFromTo(vec3d2, vec3d3, null, true);
                            doingTp = true;
                        },
                        2);
                ;
                Tasks.scheduleDelayed(
                        () -> {
                            doingTp = false;
                            scheduleFarawayMoveInternal(
                                    List.of(downWard0 ? MovInfo.createNotOnGround(vec3d4) : MovInfo.create(vec3d4)),
                                    false,
                                    context.resetTick(),
                                    false);
                            // reset fall distance after scheduleTpInternal
                            if (considerNoFall && downWard0 && mc.player != null) {
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
                                mc.player.setOnGround(false);
                            }
                            //                    farawayMoveFromTo(vec3d3, vec3d4,  downWard0? Boolean.FALSE: null,
                            // true);
                            //                    if(downWard0)
                            //
                            // scheduleFarawayMoveInternal(Iterators.singletonIterator(MovInfo.create(vec3d4.add(0,
                            // 9E-8, 0))), false, context);
                        },
                        3);
            }

        } else {
            return;
        }

        if (command) Debug.chat("tp行为已经执行, 若出现回弹或者位置不变,则目标位置不可达");

        //        if(currentY > world.getBottomY() + 64){
        //            //most likely
        //            tpHorizontalPlate = world.getBottomY() + world.getHeight()
        //        }
    }

    private static boolean doIntercepteMovingPacketsWhileTp(PlayerMoveC2SPacket packet) {
        if (doingTp) {
            // capture no-tp packets
            return false;
        }
        return true;
    }

    private static boolean doIntercepteMoveVehiclePacketsWhileTp(VehicleMoveC2SPacket packet) {
        if (doingTp) {
            return false;
        }
        return true;
    }

    private static void doStopPlayerSendMovementPackets(Event<ClientPlayerEntity> entity) {

        if (doingTp) {
            entity.cancel();
        }
    }

    private static void configureTpMaskPlayer(Event<ClientPlayerEntity> plaayer) {
        doingTp = false;
        // remove tick Movement packets when doing tp
        ClientPlayerAccess.of(plaayer.context)
                .getLegalMovementManager()
                .addMovementModifier(new LegalMovementManager.MovementModifier() {
                    boolean resetThisTick;

                    @Override
                    public boolean mayModifyPos() {
                        // only collide with other when doing Tp
                        return doingTp;
                    }

                    @Override
                    public int priority() {
                        // every negative can override this
                        // it can override nofall or something
                        return 0;
                    }

                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        if (doingTp) {
                            resetThisTick = true;
                        }
                    }

                    @Override
                    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                        if (resetThisTick) {
                            movementManagerEvent.cancel();
                        }
                    }

                    @Override
                    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                        if (resetThisTick) {
                            movementManagerEvent.cancel();
                        }
                    }

                    @Override
                    public boolean postModify(
                            Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        if (resetThisTick) {
                            resetThisTick = false;
                            movementManagerEvent.context().playerStatus.restorePos();
                            movementManagerEvent.context().playerStatus.restoreOnGround();
                        }
                        return true;
                    }
                });
    }

    // movement simulation
    public static boolean hasCollidedSoftly(Vec3d adjustedMovement) {
        float f = mc.player.getYaw() * 0.017453292F;
        double d = (double) MathHelper.sin(f);
        double e = (double) MathHelper.cos(f);
        double g = (double) mc.player.sidewaysSpeed * e - (double) mc.player.forwardSpeed * d;
        double h = (double) mc.player.forwardSpeed * e + (double) mc.player.sidewaysSpeed * d;
        double i = MathHelper.square(g) + MathHelper.square(h);
        double j = MathHelper.square(adjustedMovement.x) + MathHelper.square(adjustedMovement.z);
        if (!(i < 9.999999747378752E-6) && !(j < 9.999999747378752E-6)) {
            double k = g * adjustedMovement.x + h * adjustedMovement.z;
            double l = Math.acos(k / Math.sqrt(i * j));
            return l < 0.13962633907794952;
        } else {
            return false;
        }
    }

    public static EntityMovementStatus<ClientPlayerEntity> startSimulation() {
        return new EntityMovementStatus<>(mc.player);
    }

    public static Vec3d collide(Entity entity, Vec3d movement) {
        // Paper start - optimise collisions
        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        if (xZero & yZero & zZero) {
            return movement;
        }
        final double stepHeight = (double) entity.getStepHeight();

        final Box currBoundingBox = entity.getBoundingBox();
        final List<Box> potentialCollisionsBB = new ArrayList<>();
        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        collectBoxInvolvingInMovements(
                entity, entity.getPos(), movement, potentialCollisionsBB, potentialCollisionsVoxel, true);
        //        if (CollisionUtil.isEmpty(currBoundingBox)) {
        //            return movement;
        //        }
        //        Box collisionBox = makeCollectorBoxInvolvingCollision(currBoundingBox, movement, stepHeight,
        // onGround);
        //
        //        CollisionUtil.getCollisions(
        //            world, entity, collisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
        //            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
        //            null, null, null
        //        );
        return collideWithTrustedList(
                currBoundingBox,
                movement,
                potentialCollisionsVoxel,
                potentialCollisionsBB,
                stepHeight,
                entity.isOnGround());
        // Paper end - optimise collisions
    }

    private static void collectBoxInvolvingInMovements(
            Entity entity,
            Vec3d startPos,
            Vec3d movement,
            List<Box> intoAABB,
            List<VoxelShape> intoVoxels,
            boolean ignoreUnloadedChunk) {
        final Box currBoundingBox = entity.dimensions.getBoxAt(startPos);
        if (CollisionUtil.isEmpty(currBoundingBox)) return;
        Box collisionBox = makeCollectorBoxInvolvingCollision(
                currBoundingBox, movement, entity.getStepHeight(), entity.isOnGround());
        CollisionUtil.getCollisions(
                entity.getEntityWorld(),
                entity,
                collisionBox,
                intoVoxels,
                intoAABB,
                ignoreUnloadedChunk
                        ? COLLISION_FLAG_CHECK_BORDER
                        : (COLLISION_FLAG_CHECK_BORDER | COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS),
                null,
                null,
                null);
        if (DEBUG_RENDER_COLLISION_RENDERING) {
            for (var coll : intoAABB) {
                debugBox(coll);
            }
            for (var voxel : intoVoxels) {
                for (var box : voxel.getBoundingBoxes()) {

                    debugBox(box);
                }
            }
        }
    }

    public static Box makeCollectorBoxInvolvingCollision(
            Box currBoundingBox, Vec3d movement, double stepHeight, boolean onGround) {

        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        final Box collisionBox;

        if (xZero & zZero) {
            if (movement.y > 0.0) {
                collisionBox = CollisionUtil.cutUpwards(currBoundingBox, movement.y);
            } else {
                collisionBox = CollisionUtil.cutDownwards(currBoundingBox, movement.y);
            }
        } else {
            // note: xZero == false or zZero == false
            // stepheight check 1.21.6
            if (!getMovExtra().noStepHeightFeature.get() && stepHeight > 0.0 && (onGround || (movement.y < 0.0))) {
                // don't bother getting the collisions if we don't need them.
                if (movement.y <= 0.0) {
                    collisionBox = CollisionUtil.expandUpwards(
                            currBoundingBox.stretch(movement.x, movement.y, movement.z), stepHeight);
                } else {
                    collisionBox = currBoundingBox.stretch(movement.x, Math.max(stepHeight, movement.y), movement.z);
                }
            } else {
                collisionBox = currBoundingBox.stretch(movement.x, movement.y, movement.z);
            }
        }
        return collisionBox;
    }

    private static final ThreadLocal<Boolean> INTERNAL_VALUE_USE_STEPHEIGHT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static Vec3d collideWithTrustedList(
            Box currBoundingBox,
            Vec3d movement,
            List<VoxelShape> potentialCollisionsVoxel,
            List<Box> potentialCollisionsBB,
            double stepHeight,
            boolean onGround) {
        if (potentialCollisionsVoxel.isEmpty() && potentialCollisionsBB.isEmpty()) {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return movement;
        }

        STATIC_DEBUG_COLOR = Color.YELLOW;
        final Vec3d limitedMoveVector = CollisionUtil.performCollisions(
                movement, currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);

        if (!getMovExtra().noStepHeightFeature.get()
                && stepHeight > 0.0
                && (onGround || (limitedMoveVector.y != movement.y && movement.y < 0.0))
                && (limitedMoveVector.x != movement.x || limitedMoveVector.z != movement.z)) {
            // stepheight cause movement invalid in higher version
            // mark as not planned in 1.21.1; will do it in 1.21.6 version
            // auto jump to go across, do not move
            STATIC_DEBUG_COLOR = Color.BLUE;
            Vec3d vec3d2 = CollisionUtil.performCollisions(
                    new Vec3d(movement.x, stepHeight, movement.z),
                    currBoundingBox,
                    potentialCollisionsVoxel,
                    potentialCollisionsBB);
            boolean debug = RenderTasks.DEBUG_RENDER_COLLISION_RENDERING;
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
            // speed up jumping head

            final Vec3d vec3d3 = CollisionUtil.performCollisions(
                    new Vec3d(0.0, stepHeight, 0.0),
                    currBoundingBox.stretch(movement.x, 0.0, movement.z),
                    potentialCollisionsVoxel,
                    potentialCollisionsBB);

            if (vec3d3.y < stepHeight) {
                final Vec3d vec3d4 = CollisionUtil.performCollisions(
                                new Vec3d(movement.x, 0.0D, movement.z),
                                currBoundingBox.offset(vec3d3),
                                potentialCollisionsVoxel,
                                potentialCollisionsBB)
                        .add(vec3d3);

                if (vec3d4.horizontalLengthSquared() > vec3d2.horizontalLengthSquared()) {
                    vec3d2 = vec3d4;
                }
            }
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
            if (vec3d2.horizontalLengthSquared() > limitedMoveVector.horizontalLengthSquared()) {
                //                STATIC_DEBUG_COLOR = Color.PINK;
                INTERNAL_VALUE_USE_STEPHEIGHT.set(true);
                return vec3d2.add(CollisionUtil.performCollisions(
                        new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D),
                        currBoundingBox.offset(vec3d2),
                        potentialCollisionsVoxel,
                        potentialCollisionsBB));
            }
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        } else {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        }
    }

    public static boolean doMovementInvolveStepheight(Entity entity, Vec3d movement) {
        boolean stepheightFlag = getMovExtra().noStepHeightFeature.get();
        // enable stepheight feature to simulate
        getMovExtra().noStepHeightFeature.set(false);
        INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
        DEBUG_RENDER_COLLISION_RENDERING = true;
        Vec3d c = collide(entity, movement);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        boolean useStepheight = INTERNAL_VALUE_USE_STEPHEIGHT.get();
        getMovExtra().noStepHeightFeature.set(stepheightFlag);
        return useStepheight;
    }

    @ApiMethod
    public static Vec3d simulateMovement(Entity entity, Vec3d from, Vec3d vec3d, boolean serverMode) {
        // var simu = startSimulation();
        Entity rootEnity = entity.getRootVehicle();
        double deltaY = rootEnity.getY() - entity.getY();
        //        entity.setPosition(from);
        Vec3d rootVec = rootEnity.getPos();
        rootEnity.setPosition(from.add(0, deltaY, 0));
        // allow down velocity
        //

        //        vec3d = ((PlayerEntity)mc.player).adjustMovementForSneaking(vec3d, MovementType.PLAYER);
        //        Vec3d movement = mc.player.adjustMovementForCollisions(vec3d);
        // mc.player.move(MovementType.PLAYER, vec3d);

        Vec3d result;
        RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;

        //        if(!serverMode){
        //            boolean onGround = mc.player.isOnGround();
        //            mc.player.setOnGround(false);
        //            Box box = mc.player.getBoundingBox();
        //            List<VoxelShape> list = mc.player.getWorld().getEntityCollisions(mc.player, box.stretch(vec3d));
        //            //0
        //            list =Entity.findCollisionsForMovement(mc.player, mc.player.getWorld(), list, box.stretch(vec3d));
        //
        //            if(RenderTasks.DEBUG_RENDER_COLLISION){
        //                for (var boxShape0 : list){
        //                    var box0 = boxShape0.getBoundingBox();
        //                    RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(box0.getMinPos(),
        // box0.getMaxPos(), DEBUG_TICK, Color.YELLOW));
        //                }
        //            }
        //
        //            result = Entity.adjustMovementForCollisions(vec3d, box, (List<VoxelShape>) list);
        //            mc.player.setOnGround(onGround);
        //
        //        }else{
        result = collide(rootEnity, vec3d);
        //        }
        RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
        rootEnity.setPosition(rootVec);
        //  simu.restore();
        return result;
    }

    public static boolean hasHorizontalCollision(Entity entity, Vec3d move) {
        if (entity.hasPassengers()) {
            return false;
        } else {
            Vec3d movement = simulateMovement(entity, entity.getPos(), move, true);
            return !MathHelper.approximatelyEquals(movement.x, move.x)
                    || !MathHelper.approximatelyEquals(movement.z, move.z);
        }
    }

    public static boolean validMovementAsServer(Vec3d expect, Vec3d sim) {
        return mc.interactionManager.getCurrentGameMode().isCreative()
                || MathUtils.s2(expect.x - sim.x) + MathUtils.s2(expect.z - sim.z) < 0.0625D;
    }

    private static final Random rand = Random.create();

    public static List<Vec3d> tpAttackSearch(
            Vec3d from, Box to, double availableRange, double maxAtOnce, int maxAttempt) {
        double avRS = MathUtils.s2(availableRange);
        List<Vec3d> vec = new ArrayList<>();
        // store player information
        Vec3d playerVec = mc.player.getVelocity();
        Vec3d playerPos = mc.player.getPos();

        Vec3d currentPos = from;
        for_loop:
        for (int i = 0; i < maxAttempt; ++i) {
            // Debug.chat("on loop", i);
            if (!vec.isEmpty()) {
                currentPos = vec.get(vec.size() - 1);
            }
            Vec3d currentTry = to.getHorizontalCenter().subtract(currentPos);
            double len = Math.max(1.0F, currentTry.length() - availableRange + 1.0F);
            currentTry = currentTry.normalize().multiply(Math.min(maxAtOnce, len));
            if (len >= maxAtOnce) {
                currentTry = currentTry.multiply(maxAtOnce / len);
            }
            // mc.player.move(MovementType.PLAYER, currentTry);
            switch (validMovToEntity(ENGIN, currentPos, currentTry, to, avRS, vec)) {
                case 2:
                    break for_loop;
                case 1:
                    continue;
            }
            //            for (int s = -1; s <= 1; ++s){
            //                for (int t = -1; t <= 1; ++t){
            //                    if(s != 0 || t!= 0){
            //                        //rotate for more try
            //                        Vec3d currentFacingTry = EntityUtils.rotateVec(currentTry, 20 * s , 30* t);
            //                        switch (validMov(currentPos, currentFacingTry, to, avRS, vec)){
            //                            case 2:break for_loop;
            //                            case 1: continue for_loop;
            //                        }
            //                    }
            //                }
            //            }
            // java.util.Random rand = new java.util.Random();
            // random select
            //            for (int s = 0; s< 10; ++s ){
            //                Vec3d currentFacingTry = EntityUtils.rotateVec(currentTry, rand.nextFloat(- 50,
            // 50),rand.nextFloat(- 50, 50) );
            //                switch (validMov(currentPos, currentFacingTry, to, avRS, vec)){
            //                    case 2:break for_loop;
            //                    case 1: continue for_loop;
            //                }
            //            }
            break for_loop;
            // can not continue move
            // back to currentPos
        }
        mc.player.setVelocity(playerVec);
        mc.player.setPosition(playerPos);
        return vec;
    }

    private static int validMovToEntity(
            CollisionContext engin, Vec3d currentPos, Vec3d currentTry, Box to, double avRS, List<Vec3d> vec) {
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);
        Vec3d testPos = currentPos.add(testMov);

        // test back
        // can back
        Vec3d expectedBack = Vec3d.ZERO.subtract(testMov);
        Vec3d backTry = engin.simulateMovement(mc.player, testPos, expectedBack);
        //        if(collisionDebugRender()){
        //            Vec3d backPos = testPos.add(backTry);
        //            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(testPos.add(new Vec3d(-0.5,
        // 0, -0.5)), testPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
        //            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(backPos.add(new Vec3d(-0.5,
        // 0, -0.5)), backPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
        //            Debug.chat("Boundback", backPos.squaredDistanceTo(currentPos));
        //        }

        // there should be bug, but it works well, that's because only the y is unlimited
        if (validMovementAsServer(expectedBack, backTry)) {
            // distance available
            // check collision for safety
            if (to.squaredMagnitude(testPos) < avRS && !engin.checkEnvironmentCollision(mc.player, testPos, false)) {
                vec.add(testPos);
                //  Debug.chat("add finish pos", RenderTasks.getDisplayedLocationDouble(testPos), "move",
                // RenderTasks.getDisplayedLocationDouble(testMov));
                return 2;
            }
            // valid move
            // move available

            else if (validMovementAsServer(currentTry, testMov)) {
                Vec3d nextPos = currentPos.add(currentTry);
                // fixme use expected Pos as target
                // fixme no need to check expected pos because of movement mech
                // check collision for safety
                if (!engin.checkEnvironmentCollision(mc.player, nextPos, false)) {
                    vec.add(nextPos);
                    return 1;
                }
                //  Debug.chat("add avail pos", RenderTasks.getDisplayedLocationDouble(testPos), "move",
                // RenderTasks.getDisplayedLocationDouble(testMov));
            }
        }
        // Debug.chat("fail check to ", RenderTasks.getDisplayedLocationDouble(testPos));
        return 0;
    }

    public static final CollisionContext ENGIN = new CollisionContext() {};
    public static final CollisionContext ENGIN_LOADED = new CollisionContext() {
        @Override
        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, false);
        }
    };

    public static interface CollisionContext {
        default Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry) {
            return MovTasks.simulateMovement(entity, currentPos, currentTry, true);
        }

        default boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, true);
        }

        default List<Vec3d> generateTpSequence(
                Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
            return MovTasks.generateTpSequenceInternal(current, target, command, farawayTp, considerEnvironment);
        }
        // todo: add raycast methods
    }

    public static class CollisionCache implements CollisionContext {
        Entity entity;
        Vec3d startPos;
        Vec3d endPos;
        List<Box> intoAABBs;
        List<VoxelShape> intoVoxels;
        List<Box> allBoxes;
        boolean ignoreChunkBorder;

        public CollisionCache(Entity entity, Vec3d startPos, Vec3d endPos, boolean ignoreChunkBorder) {
            this.entity = entity;
            this.startPos = startPos;
            this.endPos = endPos;
            this.ignoreChunkBorder = ignoreChunkBorder;
            intoAABBs = new ArrayList<>();
            intoVoxels = new ArrayList<>();
            collectBoxInvolvingInMovements(
                    entity, startPos, endPos.subtract(startPos), this.intoAABBs, this.intoVoxels, ignoreChunkBorder);
            // makeCollectorBoxInvolvingCollision(entity.dimensions.getBoxAt(startPos), endPos.subtract(startPos))
            // collected all aabbs
            allBoxes = new ArrayList<>();
            allBoxes.addAll(intoAABBs);
            for (var voxel : intoVoxels) {
                allBoxes.addAll(voxel.getBoundingBoxes());
            }
        }

        public Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry) {
            Entity rootEntity = entity.getRootVehicle();
            //  double y = entity.getY() - rootEntity.getY();
            //            Vec3d originRoot = rootEntity.getPos();
            //            rootEntity.setPosition(currentPos);
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
            Vec3d simu = collideWithTrustedList(
                    rootEntity.dimensions.getBoxAt(currentPos),
                    currentTry,
                    this.intoVoxels,
                    this.intoAABBs,
                    rootEntity.getStepHeight(),
                    rootEntity.isOnGround());
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
            //            rootEntity.setPosition(originRoot);
            return simu;
        }

        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec, boolean checkLiquid) {
            return MovTasks.checkEnvironmentCollision(entity, vec, checkLiquid, this.ignoreChunkBorder);
        }

        @Override
        public List<Vec3d> generateTpSequence(
                Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment) {
            return CollisionContext.super.generateTpSequence(current, target, command, farawayTp, considerEnvironment);
        }
    }

    public static boolean validMoveTo(CollisionContext engin, Vec3d currentPos, Vec3d currentTry) {
        // fixme: currentPos may not be a suitable place for player to stay
        if (engin.checkEnvironmentCollision(mc.player, currentPos, false)) {
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if (validMovementAsServer(currentTry, testMov)) {
            Vec3d currentForward = currentPos.add(currentTry);
            // check collision for safety
            if (!engin.checkEnvironmentCollision(mc.player, currentForward, false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validMoveToAndBack(CollisionContext engin, Vec3d currentPos, Vec3d currentTry) {
        // fixme: currentPos may not be a suitable place for player to stay
        if (engin.checkEnvironmentCollision(mc.player, currentPos, false)) {
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if (validMovementAsServer(currentTry, testMov)) {
            // use expected position as server success position
            Vec3d currentForward = currentPos.add(currentTry);
            // check collision for safety
            if (!engin.checkEnvironmentCollision(mc.player, currentForward, false)) {
                Vec3d backMov = Vec3d.ZERO.subtract(currentTry);
                Vec3d testBackMov = engin.simulateMovement(mc.player, currentForward, backMov);
                if (validMovementAsServer(backMov, testBackMov)) {
                    return true;
                }
            }
        }
        return false;
    }

    // copied from wurst
    // seems not work
    // shit
    public void onSpeedUp(ClientPlayerEntity player) {
        // return if sneaking or not walking
        if (player.isSneaking() || player.forwardSpeed == 0 && player.sidewaysSpeed == 0) return;

        // activate sprint if walking forward
        if (player.forwardSpeed > 0 && !player.horizontalCollision) player.setSprinting(true);

        // activate mini jump if on ground
        if (!player.isOnGround()) return;

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 1.8, v.y + 0.1, v.z * 1.8);

        v = player.getVelocity();
        double currentSpeed = Math.sqrt(Math.pow(v.x, 2) + Math.pow(v.z, 2));

        // limit speed to highest value that works on NoCheat+ version
        // 3.13.0-BETA-sMD5NET-b878
        // UPDATE: Patched in NoCheat+ version 3.13.2-SNAPSHOT-sMD5NET-b888
        double maxSpeed = 0.66F;

        if (currentSpeed > maxSpeed)
            player.setVelocity(v.x / currentSpeed * maxSpeed, v.y, v.z / currentSpeed * maxSpeed);
    }

    private static boolean noBlocksAround(Entity entity) {
        // Paper start - stop using streams, this is already a known fixed problem in Entity#move
        Box box = entity.getBoundingBox().expand(0.0625D).stretch(0.0D, -0.55D, 0.0D);
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    pos.set(x, y, z);
                    BlockState type = mc.world.getBlockState(pos);

                    if (type != null && !type.isAir()) {

                        return false;
                    }
                }
            }
        }

        return true;
        // Paper end - stop using streams, this is already a known fixed problem in Entity#move
    }

    public static boolean seenAsFloating(boolean fakeGilding) {
        // add serverPacket result, if toggle flight at server, stop seen as floating, no need to antiKick
        return !creativeFlight.serverSideCanFly
                && mc.player.getVelocity().y >= -0.03125D
                && mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && (fakeGilding || !mc.player.isFallFlying())
                && !mc.player.isUsingRiptide()
                && !mc.player.isSleeping()
                && !mc.player.isRiding()
                && !mc.player.isDead()
                && noBlocksAround(mc.player);
    }

    //    private static boolean serverPacketAllowFlight = false;

    //    public static boolean onPacketFlyToggle(PlayerAbilitiesS2CPacket packet1){
    //        Tasks.scheduleDelayed(()->{
    //
    //            serverPacketAllowFlight = packet1.allowFlying();
    //            if(mc.player != null){
    //                PlayerAbilities abilities = mc.player.getAbilities();
    //                //abilities.allowFlying = abilities.allowFlying;
    //                abilities.creativeMode = packet1.isCreativeMode();
    //                abilities.invulnerable = packet1.isInvulnerable();
    //                if(! HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
    //                    abilities.allowFlying = packet1.allowFlying();
    //                }
    //                if(!overrideFly.get()){
    //                    abilities.setFlySpeed(packet1.getFlySpeed());
    //                }
    //                abilities.setWalkSpeed(packet1.getWalkSpeed());
    //                //mc.player.getAbilities().flying = isFly;
    //            }
    //
    //        },1);
    //        return false;
    //    }

    //    public static boolean onPacketFly(UpdatePlayerAbilitiesC2SPacket packet){
    //
    //        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT) && !serverPacketAllowFlight){
    //            return false;
    //        }
    //        return true;
    //
    //    }

    //    public static LegalMovementManager.MovementModifier configureCreativeFlyAbility(){
    //        return new LegalMovementManager.MovementModifier() {
    //            @Override
    //            public boolean mayModifyPos() {
    //                return false;
    //            }
    //
    //            @Override
    //            public boolean mayModifyRotation() {
    //                return false;
    //            }
    //
    //            @Override
    //            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
    //                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
    //                if( HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
    //                    if( !player.getAbilities().allowFlying ){
    //                        player.getAbilities().allowFlying = true;
    //                    }
    //                    antiKick(player);
    //                }else {
    //                    player.getAbilities().allowFlying = creativeFlight.serverSideCanFly;
    //                }
    //            }
    //
    //            @Override
    //            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
    //                return true;
    //            }
    //        };
    //    }

    // fixme: fake flight causes fallflying fly

    @ApiMethod
    public static void setupAutoResync(Vec3d pos) {
        getAutoResync().setAutoResyncSchedule(Optional.of(pos));
    }

    @ApiMethod
    public static void setupAutoResync() {
        getAutoResync().setAutoResyncSchedule(Optional.empty());
    }

    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_0 =
            new LegalMovementManager.ModifierPipeline(0) {
                @Override
                public int priority() {
                    return 0;
                }

                @Override
                public boolean mayModifyPos() {
                    return false;
                }

                @Override
                public boolean mayModifyRotation() {
                    return false;
                }
            };
    // This pipeline will modify rot not pos
    //    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_ROT =
    //            new LegalMovementManager.ModifierPipeline(1_000_000) {
    //                @Override
    //                public int priority() {
    //                    return 1_000_000;
    //                }
    //
    //                @Override
    //                public boolean mayModifyPos() {
    //                    return false;
    //                }
    //            };
    // This pipeline will modify pos not rot
    //    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_POS =
    //            new LegalMovementManager.ModifierPipeline(1_000) {
    //                @Override
    //                public int priority() {
    //                    return 1_000;
    //                }
    //
    //                @Override
    //                public boolean mayModifyRotation() {
    //                    return false;
    //                }
    //            };

    // Removed
    // move to NoFallModule.class
    // avoid player
    //    public static void onSetBackResponseAction(Event<MovInfo> event){
    //        afterSetbackFlag = true;
    //        nofallWaitSetbackFlag = false;
    //        if(noFallMode.getValue() == Configs.NofallBypassMode.NO_BYPASS && (event.context.oGroundOverride == null
    // || noFallSetbackResponse != (boolean)event.context.oGroundOverride)){
    //            var info = event.context();
    //            event.context(new MovInfo(info.vec3d, noFallSetbackResponse, false, info.rotationOverride));
    //        }
    //        noFallSetbackResponse = false;
    //    }
    //    private boolean canStartSprinting(ClientPlayerEntity player) {
    //        return !player.isSprinting() && player.isWalking() && this.canSprint() && !this.isUsingItem() &&
    // !this.hasStatusEffect(StatusEffects.BLINDNESS) && (!this.hasVehicle() ||
    // this.canVehicleSprint(this.getVehicle())) && !this.isFallFlying();
    //    }
    //    private static LegalMovementManager.MovementModifier configureNoFall(){
    //        return new LegalMovementManager.MovementModifier() {
    //            double lastOnGroundHeight = Integer.MIN_VALUE;
    //            double lastHeight;
    //            int counter = 0;
    //            boolean holdingMace  = false;
    //            boolean runningThisTick = false;
    //            @Override
    //            public int priority() {
    //                //lower than tp mask ,should run like "background tasks"
    //                return 1;
    //            }
    //            @Override
    //            public boolean mayModifyPos() {
    //                //it will not modify pos in default mode
    //                //if it is configurated to be a legal mode or something, then it may need a modify
    //                return false;
    //            }
    //            boolean canDoJump = false;
    //            boolean doJump = false;
    //            Boolean shouldApplyOnGroundReverseNextTick = null;
    //
    //            int waitTimeout = 0;
    //            int noFallCnt = -1;
    //
    //            public void preTick(Event<LegalMovementManager> movementManagerEvent){
    //
    //            }
    //            @Override
    //            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
    //                ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
    //                //filter creative playerGaming
    //                if(args.getAbilities().invulnerable){
    //                    return;
    //                }
    //                if(noFallCnt >= 0){
    //                    noFallCnt -= 1;
    //                    args.input.movementSideways = 0.0F;
    //                    args.input.movementForward = 0.0F;
    //                }
    //                holdingMace = args.getMainHandStack().getItem() instanceof MaceItem;
    //                Vec3d pos = args.getPos();
    //                if(pos == null)return;
    //                if(canDoJump){
    ////                    mc.player.addVelocityInternal(new Vec3d(0, 8, 0));
    //                    if(!nofallWaitSetbackFlag){
    //                        //a nofall packet comes
    //                        doJump = true;
    //                        canDoJump =false;
    ////                        Debug.info("trigger jump tick");
    //                        mc.player.setOnGround(true);
    //                        //TODO 1.21.2+ may need this, check code then
    //                        mc.options.jumpKey.setPressed(true);
    ////                        Vec3d vc = mc.player.getVelocity();
    ////                        mc.player.setVelocity(vc.x, 0.1, vc.z);
    //                    }else{
    //                        //should not send onGround
    //                        //do not send pos
    //                        waitTimeout += 1;
    //                        if(waitTimeout >= 2){
    //                            waitTimeout = 0;
    //                            canDoJump = false;
    //                            nofallWaitSetbackFlag = false;
    //                            mc.player.setOnGround(true);
    //                        }else{
    //                            mc.player.setOnGround(false);
    //                        }
    //
    //                    }
    //
    //                }
    ////                Vec3d pos2 = args.getVelocity();
    ////                if(pos2 == null)return;
    ////                if(pos2.y < -0.67){
    ////                    args.setVelocity(pos2.x, -0.67, pos2.z);
    ////                }
    ////                if(doJump){
    ////                    args.setOnGround(true);
    ////                }
    //                boolean forceNoFall = ClientPlayerAccess.of( args).isForceNoFall();
    //                lastHeight = args.getY();
    //                //reset onground height when in water
    //                if(args.isOnGround() || args.isInsideWaterOrBubbleColumn()){
    //                    lastOnGroundHeight = lastHeight;
    //                }
    //                if((noFall.get()) || forceNoFall){
    //
    //                    // lastOnGround = args.isOnGround();
    //                    double safeDistance = args.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) ;
    //
    //                    if( forceNoFall ||  lastHeight <= lastOnGroundHeight -  safeDistance ){
    //                        //this is a signal from other functional
    //
    //                        var bypassMode = noFallMode.getValue();
    //
    //                        if(bypassMode == Configs.NofallBypassMode.NO_BYPASS){
    //                            if(!holdingMace){
    //                                //TODO Optimize this calculation NO_BYPASS
    //                                runningThisTick = true;
    //                                //TODO LAZY MODE, only if we trigger not onground -> onground should we reset
    //                                counter = 0;
    //                                lastOnGroundHeight = args.getY();
    //
    //                                args.setPosition(args.getPos().add(0, + 1E-8, 0));
    //                                mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(args.getX(),
    // args.getY() , args.getZ(), !forceNoFall && args.isOnGround()));
    //                                noFallSetbackResponse = true;
    //
    //                            }
    //
    //                        } else if(bypassMode == Configs.NofallBypassMode.LAZY_MODE){
    //                            if(forceNoFall){
    //                                runningThisTick = true;
    //                                //TODO LAZY MODE, only if we trigger not onground -> onground should we reset
    //                                counter = 0;
    //                                lastOnGroundHeight = args.getY();
    //
    //                                args.setPosition(args.getPos().add(0, + 1E-8, 0));
    //                                mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(args.getX(),
    // args.getY() , args.getZ(), false));
    //                                noFallSetbackResponse = true;
    //                            }
    //                        }
    //                        else if(bypassMode == Configs.NofallBypassMode.BYPASS_GRIM){
    //                            if(!args.isOnGround()){
    //                                runningThisTick = true;
    //                            }
    //                            //resync lastOnGroundHeigth in this method
    //                            counter = 0;
    //                        }
    //                        //todo implement other mode
    //                        if(forceNoFall){
    //                            ClientPlayerAccess.of(args).setForceNoFall(false);
    //                        }
    //                        //args.setOnGround(true);
    //                    }else if(lastHeight > lastOnGroundHeight){
    //                        lastOnGroundHeight = lastHeight;
    //                        counter = 0;
    //                    }
    //                    else if(args.isOnGround()){
    //                        //todo check if this is at risk
    //                        if(noFallMode.getValue() == Configs.NofallBypassMode.BYPASS_GRIM){
    //                            lastOnGroundHeight = lastHeight;
    //                        }
    //                    }
    //                    else {
    //                        counter ++;
    //                    }
    //                    if(counter > 100){
    //                        //whatever , reset this flag
    //                        noFallSetbackResponse = false;
    //                    }
    //                }
    //            }
    //
    //            @Override
    //            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
    //                if(canDoJump){
    //                    //wait for set back packets to do jump
    //                    movementManagerEvent.cancel();
    //                    //restore pos
    //                    movementManagerEvent.context.playerStatus.restorePos();
    //                    return;
    //                }
    //                if(noFall.get()){
    //                    //todo: how do it pass grimac?? I don't understand
    //                    if(noFallMode.getValue() == Configs.NofallBypassMode.LAZY_MODE){
    //                        double safeDistance =
    // movementManagerEvent.context.playerStatus.entity.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) ;
    //                        var entity = movementManagerEvent.context.playerStatus;
    //                        // LAZY MODE, only if we trigger not onground -> onground should we reset fall height
    //                        if(afterSetbackFlag || (entity.entity.getY() <= lastOnGroundHeight -  safeDistance)){
    //                            if(!runningThisTick){
    //                                //apply only once
    //
    //                                if(!entity.onGround && entity.entity.isOnGround()){
    //                                    afterSetbackFlag = false;
    //                                    runningThisTick = true;
    //                                    counter = 0;
    //                                    lastOnGroundHeight = entity.pos.getY();
    //// todo: try send it eariler
    //
    // mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(entity.pos.getX(), entity.pos.getY() + 1E-8,
    // entity.pos.getZ(), false));
    //                                    //todo: 测试终止横向动量 减少grimac发包
    ////                                    entity.entity.setPos(entity.pos.getX(), entity.entity.getY() ,
    // entity.pos.getZ());
    //                                    entity.entity.input.movementSideways = 0.0F;
    //                                    entity.entity.input.movementForward = 0.0F;
    //                                    noFallSetbackResponse = true;
    //                                    noFallCnt = 2;
    //                                    return;
    //                                }
    //                            }
    //                        }
    //                    } else if(noFallMode.getValue() == Configs.NofallBypassMode.BYPASS_GRIM && runningThisTick){
    ////                    movementManagerEvent.cancel();
    ////                    movementManagerEvent.context().playerStatus.restorePos();
    //                        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
    ////                    if(waitingForSetback && waitForSetbackId == waitForSetBack){
    ////                        waitTimeout += 1;
    ////                        if(waitTimeout >= 5){
    ////                            waitingForSetback = false;
    ////                            player.fallDistance = 0.0f;
    ////                            lastOnGroundHeight = player.getY();
    ////                            return;
    ////                        }else{
    ////                            movementManagerEvent.cancel();
    ////                            movementManagerEvent.context.playerStatus.restorePos();
    ////                            return;
    ////                        }
    ////                    }
    //                        if(player.isOnGround()){
    //                            //onGround
    //                            //collide on ground should be
    //                            runningThisTick = true;
    //
    ////                        player.setPos(player.getX(), player.getY() + 5E-2, player.getZ());
    ////                        Debug.info(player.getVelocity());
    ////                        player.setPos(player.getX(), player.getY() + 1E-8, player.getZ());
    //
    ////                        player.setOnGround(false);
    //                            //cancel , do not restore pos
    //                            movementManagerEvent.cancel();
    ////                        movementManagerEvent.context.playerStatus.restorePos();
    ////                        player.setPosition(player.getX(), player.getY() + 1E-8, player.getZ());
    //                            //** must be OnGroundOnly(true) **
    //                            //在grimac的预测中, 当前状态应该即将着地, 若使用Onground = false 则会触发onGround不匹配
    //                            //最终结果:
    //                            //client:  onGround(true)  jump() .............(............(............(  client
    // resync on ground
    //                            //                    |       |
    //                            //grimac:   predict at ground, (accepted predict) let client resync to ground /
    // accept resync tp
    //                            //                    x       √                √             v                     √
    //                            //server   do not reset     reset fall distance
    // ...............................
    //                            //为什么是onground = true
    //                            //grim的不同setback模式
    //                            //onground = true会导致resync = true, simulate = true
    //                            //对方将resync packets传输到咱们这里 是(a, b + 1E-7, c, false)
    //                            //咱们设置为了false
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.459339812018335 78.59016863897678 28.941366735922244 false
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.459339812018335 75.46476221959249 28.941366735922244 false
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move OnGroundOnly 0.0 0.0
    // 0.0 false
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) trigger jump tick
    //// [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.459339812018335
    // 74.41999998688698 28.941366735922244 false
    ////                            [04:06:06] [Netty Client IO #8/INFO] (Minecraft) [CHAT] Pos Resync
    // [51.46,74.00,28.94]
    //// [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] Grim » matl114 触发了 GroundSpoof (x2) claimed false
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending packet
    // TeleportConfirmC2SPacket
    //// [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] [anti-grim] 检测到反作弊回弹! tp号:-1034761362
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move Full
    // 51.459339812018335 74.0000001 28.941366735922244 false
    ////                            [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] Grim » matl114 触发了 Simulation
    // (x2) .420000 /gl 82 <-这里 他认为我们是从75.46476221959249 移动到74.0000001, 这是不合法的
    ////                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.459339812018335 74.0000001 28.941366735922244 true
    ////                            [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] matl114从高处摔了下来
    //                            //正常情况是
    ////                        [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 84.79200176125546 29.17552569387324 false
    ////                            [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 81.69935880076793 29.17552569387324 false
    ////                            [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 78.59016863897678 29.17552569387324 false
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 75.46476221959249 29.17552569387324 false
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move OnGroundOnly 0.0 0.0
    // 0.0 true
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) trigger jump tick
    //                            //[04:08:02] [Netty Client IO #8/INFO] (Minecraft) [CHAT] Pos Resync
    // [51.05,74.00,29.18]
    //                            //[04:08:02] [Render thread/INFO] (SlimefunHelper) sending packet
    // TeleportConfirmC2SPacket
    //                            //[04:08:02] [Render thread/INFO] (Minecraft) [CHAT] [anti-grim] 检测到反作弊回弹!
    // tp号:-2093209308
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move Full 51.04938473524123
    // 74.0000001 29.17552569387324 false
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 74.42000008688697 29.17552569387324 false
    //                            //        <-这里 他认为我们是从75.46476221959249 移动到74.0000001, 但是 由于上面触发的非常巧妙,是resync packets,
    // 这里的运动偏差会被直接无视
    //                            //<- 同时 这里的movement会被认为是knockback， 可以通过后续的resync，？？？？？？？？？
    //                            //todo: 需要进一步查看 这也太离谱了
    //
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 74.7532000805212 29.17552569387324 false
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 75.00133607911214 29.17552569387324 false
    ////                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround
    // 51.04938473524123 75.16610936093821 29.17552569387324 false
    //                            mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(true));
    //                            //包吃住 不要过
    //                            noFallSetbackResponse = false;
    //                            ClientPlayerAccess.of(player).setForceNoFall(false);
    //                            lastOnGroundHeight = player.getY();
    //
    //                            nofallWaitSetbackFlag = true;
    //                            canDoJump = true;
    //                            waitTimeout = 0;
    //                            runningThisTick = false;
    ////                        mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(player.getX(),
    // player.getY(), player.getZ(),false));
    //
    //
    //                        }else {
    //                            runningThisTick = false;
    //                        }
    //                    }
    //                }
    //
    //            }
    //
    //            @Override
    //            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
    //                //skip and kept working
    //                if(shouldApplyOnGroundReverseNextTick != null){
    //                    if(!runningThisTick){
    //
    // movementManagerEvent.context.playerStatus.entity.setOnGround(shouldApplyOnGroundReverseNextTick);
    //                    }
    //                    shouldApplyOnGroundReverseNextTick = null;
    //                }
    //                if(runningThisTick && noFallMode.get() == Configs.NofallBypassMode.LAZY_MODE){
    //                   // shouldApplyOnGroundReverseNextTick =
    // movementManagerEvent.context.playerStatus.entity.isOnGround();
    //                }
    //                if(canDoJump)return true;
    //                ClientPlayerEntity player = movementManagerEvent.context().playerStatus.entity;
    //                if(doJump){
    //
    //                    mc.options.jumpKey.setPressed(false);
    //                    doJump = false;
    //                    player.setOnGround(false);
    //                }
    //
    //                runningThisTick = false;
    //
    //                return true;
    //            }
    //        };
    //    }

    // This pipeline will not modify pos or rot

    // TODO I believe we can gain more advantage from grimac

    public static void configurePipelinesForPlayer(Event<ClientPlayerEntity> playerEvent) {
        var player = playerEvent.context();
        var legalMovement = ClientPlayerAccess.of(player).getLegalMovementManager();
        PLAYER_PIPELINE_0.resetForNewPlayer(player);
        legalMovement.addMovementModifier(PLAYER_PIPELINE_0);
        //        PLAYER_PIPELINE_ROT.resetForNewPlayer(player);
        //        legalMovement.addMovementModifier(PLAYER_PIPELINE_ROT);
        //        PLAYER_PIPELINE_POS.resetForNewPlayer(player);
        //        legalMovement.addMovementModifier(PLAYER_PIPELINE_POS);
    }

    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Move");

    @Getter
    public static MovExtra movExtra;

    @Getter
    public static PlayerStateManager playerStateManager;

    @Getter
    public static LegacySnapRotManager legacySnapRotManager;

    @Getter
    public static ForwardTp forwardTp;

    @Getter
    public static NoSlowDown noSlowDown;

    @Getter
    public static NoFall noFall;

    @Getter
    public static SetBackLog setBackLog;

    @Getter
    public static AutoResync autoResync;

    @Getter
    public static CreativeFlight creativeFlight;

    @Getter
    public static Sprint sprint;

    @Getter
    public static MoveTimer moveTimer;

    @Getter
    public static StepHeight stepHeight;

    @Getter
    public static ElytraExtra elytraExtra;

    @Getter
    public static ElytraFlight elytraFlight;

    @Getter
    public static ElytraFlightLegit elytraFlightLegit;

    @Getter
    public static ElytraGrimAccelerate elytraGrimAccelerate;

    @Getter
    public static Velocity velocity;

    @Getter
    public static FloatingUtils floatingUtils;

    @Getter
    public static MovTest movTest;

    @Getter
    public static TpaCommand tpaCommand;

    @Getter
    public static TargetCommand targetCommand;

    @Getter
    public static TravellingControl travellingControl;

    @ApiStatus.Experimental
    @Getter
    public static ElytraFinder elytraFinder;

    private static void initModules(ModuleManager m) {
        // move
        movExtra = new MovExtra().register(m);
        //fallDistanceManager = new FallDistanceManager().register(m);
        legacySnapRotManager = new LegacySnapRotManager().register(m);
        forwardTp = new ForwardTp().register(m);
        noSlowDown = new NoSlowDown().register(m);
        noFall = new NoFall().register(m);

        setBackLog = new SetBackLog().register(m);
        autoResync = new AutoResync().register(m);
        creativeFlight = new CreativeFlight().register(m);
        sprint = new Sprint().register(m);
        moveTimer = new MoveTimer().register(m);
        stepHeight = new StepHeight().register(m);
        elytraExtra = new ElytraExtra().register(m);
        elytraFlight = new ElytraFlight().register(m);
        elytraGrimAccelerate = new ElytraGrimAccelerate().register(m);

        // elytraFlightLegit = new ElytraFlightLegit().register(m);
        velocity = new Velocity().register(m);
        floatingUtils = new FloatingUtils().register(m);
        movTest = new MovTest().register(m);
        tpaCommand = new TpaCommand().register(m);
        targetCommand = new TargetCommand().register(m);
        travellingControl = new TravellingControl().register(m);
        elytraFinder = new ElytraFinder().register(m);
    }

    static {
        // basic structure
        Listener.getPlayerInitConfiguration().registerHandler(MovTasks::configurePipelinesForPlayer);
        Listener.getPlayerInitConfiguration().registerHandler(MovTasks::configureTpMaskPlayer);

        //        //creative fly bad packets
        //        Listener.registerSinglePacketListener(UpdatePlayerAbilitiesC2SPacket.class, MovTasks::onPacketFly);
        //        //block server ability resync about flying
        //        Listener.registerSinglePacketListener(PlayerAbilitiesS2CPacket.class, MovTasks::onPacketFlyToggle);
        // force set ability flight
        //        PLAYER_PIPELINE_0.addMovementModifierFactory(MovTasks::configureCreativeFlyAbility);
        // PLAYER_PIPELINE_0.addMovementModifierFactory(MovTasks::configureFakeSprint);

        // watch setback packets
        //  Listener.registerSinglePacketListener(TeleportConfirmC2SPacket.class, MovTasks::listenAntiCheatSetBack);

        // teleport management

        // nofall
        // Listener.getTeleportConfirmResponsePoint().registerHandler(MovTasks::onSetBackResponseAction);
        // PLAYER_PIPELINE_POS.addMovementModifierFactory(MovTasks::configureNoFall);
        // sprint
        // PLAYER_PIPELINE_ROT.addMovementModifierFactory(MovTasks::configureLegalDirectionalSprint);
        // stepheight
        // PLAYER_PIPELINE_POS.addMovementModifierFactory(MovTasks::configureEnhancedStepheight);
        //        Listener.getPlayerNotFlyJumpPoint().registerHandler(MovTasks::listenJump);
        // setback function
        //
        // Listener.getTeleportConfirmVelocityUpdatePoint().registerHandler(MovTasks::configurateTeleportBackVelocityUpdate);

        //        Listener.registerSinglePacketListener(PlayerPositionLookS2CPacket.class,
        // MovTasks::listenPositionResync);
        Listener.registerSinglePacketListener(PlayerMoveC2SPacket.class, MovTasks::doIntercepteMovingPacketsWhileTp);
        Listener.getClientPlayerSendMovementPoint().registerHandler(MovTasks::doStopPlayerSendMovementPackets);

        moduleManager.registerFactories(MovTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }

    public static Optional<Vec3d> getTpaCommandResult(
            CommandExecution p, List<InputArgument<?>> args, ArgumentReader reader, Consumer<Text> errMsg) {
        TpaCommandEvent event = new TpaPositionResolver(reader, p, args, errMsg);
        EventContainer<TpaCommandEvent> container = new EventContainer<>(TpaCommandEvent.class, event);
        Listener.getCustomListener().broadcast(container);
        return container.getValue() instanceof TpaPositionResolver resolver && resolver.hasResolved()
                ? resolver.resolve
                : null;
    }

    public static List<String> getTpaCommandTabResult(CommandExecution p, List<InputArgument<?>> args) {
        TpaCommandEvent event = new TpaTabCompletor(p, args);
        EventContainer<TpaCommandEvent> container = new EventContainer<>(TpaCommandEvent.class, event);
        Listener.getCustomListener().broadcast(container);
        return container.getValue() instanceof TpaTabCompletor tabCompletor ? tabCompletor.tab : List.of();
    }

    @AllArgsConstructor
    public static class TpaCommandEvent {
        public EventMode mode;
        public CommandExecution player;
        public List<InputArgument<?>> inputs;

        public static enum EventMode {
            RESOLVE(TpaPositionResolver.class),
            TAB(TpaTabCompletor.class);
            public final Class<? extends TpaCommandEvent> clazz;

            EventMode(Class<? extends TpaCommandEvent> clazz) {
                this.clazz = clazz;
            }
        }
    }

    public static class TpaPositionResolver extends TpaCommandEvent {
        public Optional<Vec3d> resolve = null;

        public boolean hasResolved() {
            return resolve != null;
        }

        public final Consumer<Text> errMsg;
        public ArgumentReader arguments;

        public TpaPositionResolver(
                ArgumentReader arguments,
                CommandExecution player,
                List<InputArgument<?>> inputs,
                Consumer<Text> errMsg) {
            super(EventMode.RESOLVE, player, inputs);
            this.arguments = arguments;
            this.errMsg = errMsg;
        }
    }

    public static class TpaTabCompletor extends TpaCommandEvent {
        public List<String> tab = new ArrayList<>();

        public TpaTabCompletor(CommandExecution player, List<InputArgument<?>> arguments) {
            super(EventMode.TAB, player, arguments);
        }
    }

    public static final TabResult specialTypeTabResult =
            TabResult.ofStreamSupplier(MovTasks::commandSpecialPositionType);

    public static final ArgumentType<String> specialTypeArgumentType = SimpleCommandArgs.argumentBuilder()
            .name("special_type")
            .tabCompletor(specialTypeTabResult)
            .build();

    public static void resolveSpecialType(Event<EventContainer<TpaCommandEvent>> tpaRequest) {
        TpaCommandEvent event = tpaRequest.context.getValue();
        switch (event.mode) {
            case TAB -> {
                var tabResult = specialTypeArgumentType.getTab(event.player, event.inputs);
                if (tabResult != null) {
                    tabResult.forEach(((TpaTabCompletor) event).tab::add);
                }
            }
            case RESOLVE -> {
                TpaPositionResolver eventResolver = (TpaPositionResolver) event;
                if (eventResolver.hasResolved()) return;
                ArgumentReader reader = eventResolver.arguments;
                if (reader.hasNext()) {
                    String type = reader.peek();
                    if (type.startsWith("#")) {
                        reader.step();
                        String val = type.substring(1);
                        if (specialPositionRegistry.containsKey(val)) {
                            SpecialPositionResolver resolver = specialPositionRegistry.get(val);
                            eventResolver.resolve = Optional.ofNullable(
                                    resolver.resolvePosition(reader, event.player.getExecutor(), eventResolver.errMsg));
                        } else {
                            eventResolver.errMsg.accept(
                                    Text.literal("不存在这样的特殊位置: " + type).formatted(Formatting.RED));
                            eventResolver.resolve = Optional.empty();
                        }
                    }
                }
            }
        }
    }

    public static final TabResult playerName = TabResult.ofStreamSupplier(
            () -> mc.world != null ? EntityUtils.getWorldPlayerNames(false) : Stream.empty());

    public static final TabResult crossHairTarget = TabResult.ofStreamSupplier(() -> (mc.crosshairTarget != null
                    && mc.crosshairTarget.getType() == HitResult.Type.ENTITY)
            ? Stream.of(((EntityHitResult) (mc.crosshairTarget)).getEntity().getUuidAsString())
            : Stream.empty());

    public static final ArgumentType<String> entityTargetArgumentType = SimpleCommandArgs.argumentBuilder()
            .name("target")
            .tabCompletor(playerName)
            .tabCompletor(crossHairTarget)
            .build();

    public static void resolveEntityTarget(Event<EventContainer<TpaCommandEvent>> tpaRequest) {
        TpaCommandEvent event = tpaRequest.context.getValue();
        switch (event.mode) {
            case TAB -> {
                var result = entityTargetArgumentType.getTab(event.player, event.inputs);
                if (result != null) {
                    result.forEach(((TpaTabCompletor) event).tab::add);
                }
            }
            case RESOLVE -> {
                TpaPositionResolver resolver = (TpaPositionResolver) event;
                if (resolver.hasResolved()) return;
                ArgumentReader reader = resolver.arguments;
                if (reader.hasNext()) {
                    String target = reader.peek();
                    Entity entity = null;
                    if (target.length() > 16) {
                        try {
                            UUID uid = UUID.fromString(target);
                            entity = mc.world.getEntityLookup().get(uid);
                        } catch (Throwable e) {
                        }
                    }
                    if (entity == null) {
                        entity = EntityUtils.getPlayerByName(target);
                    }
                    if (entity == null) {
                        // can not parse
                        return;
                    }
                    // consume
                    reader.step();
                    resolver.resolve = Optional.ofNullable(entity.getPos());
                }
            }
        }
    }

    public static interface SpecialPositionResolver {
        public Vec3d resolvePosition(ArgumentReader re, PlayerEntity var1, Consumer<Text> errMsg);
    }

    public static Map<String, SpecialPositionResolver> specialPositionRegistry = new LinkedHashMap<>();
    public static Vec3d MARK = null;

    static {
        specialPositionRegistry.put("this", (re, var1, errMsg) -> var1.getPos());
        specialPositionRegistry.put("near", (re, var1, errMsg) -> {
            var player = mc.world.getPlayers().stream()
                    .filter(m -> m != var1)
                    .sorted(Comparator.comparingDouble(m -> m.getPos().squaredDistanceTo(var1.getPos())))
                    .findFirst()
                    .orElse(null);
            if (player == null) {
                errMsg.accept(Text.literal("附近没有其他玩家!").formatted(Formatting.RED));
                return null;
            } else {
                errMsg.accept(Text.literal("找到附近的玩家: " + player.getName()).formatted(Formatting.GREEN));
            }
            return player.getPos();
        });
        specialPositionRegistry.put("mark", (re, var1, errMsg) -> {
            if (MovTasks.MARK != null) {
                Vec3d pos = Vec3d.ZERO.add(MovTasks.MARK);
                errMsg.accept(Text.literal("使用记录坐标： ").append(ChatUtils.getDisplayedLocationDouble(pos)));
                return pos;
            } else {
                errMsg.accept(Text.literal("暂未记录坐标!"));
                return null;
            }
        });
        specialPositionRegistry.put("back", (re, var1, errMsg) -> {
            if (MovTasks.LAST_TP_FROM != null) {
                errMsg.accept(
                        Text.literal("使用上一个位置: ").append(ChatUtils.getDisplayedLocationDouble(MovTasks.LAST_TP_FROM)));
                return MovTasks.LAST_TP_FROM;
            }
            errMsg.accept(Text.literal("找不到上一个位置"));
            return null;
        });
        specialPositionRegistry.put("desync", (re, var1, errMsg) -> {
            if (MovTasks.setBackLog.lastDesyncPos != null) {
                errMsg.accept(Text.literal("使用上次客户端同步之前的位置")
                        .append(ChatUtils.getDisplayedLocationDouble(MovTasks.setBackLog.lastDesyncPos)));
                return MovTasks.setBackLog.lastDesyncPos;
            }
            errMsg.accept(Text.literal("找不到上一次的客户端同步记录"));
            return null;
        });
        specialPositionRegistry.put("lasttp", (re, var1, errMsg) -> {
            if (MovTasks.LAST_TP_REQUEST != null) {
                errMsg.accept(Text.literal("使用上一个TP请求: ")
                        .append(ChatUtils.getDisplayedLocationDouble(MovTasks.LAST_TP_REQUEST)));
                return MovTasks.LAST_TP_REQUEST;
            }
            errMsg.accept(Text.literal("找不到上一个TP请求"));
            return null;
        });
        specialPositionRegistry.put("death", (re, var1, errMsg) -> {
            var b0 = var1.getLastDeathPos();
            if (b0.isPresent()) {
                if (Objects.equals(b0.get().dimension(), mc.world.getRegistryKey())) {
                    return b0.get().pos().toBottomCenterPos();
                } else {
                    errMsg.accept(Text.literal("上次死亡位置不在该世界"));
                }
            } else {
                errMsg.accept(Text.literal("暂未死亡历史记录"));
            }
            return null;
        });
        specialPositionRegistry.put("camera", (re, var1, errMsg) -> {
            return RenderUtils.getCameraEntityPos();
        });
    }

    public static Vec3d resolveCommandSpecialPositions(
            String type, ArgumentReader reader, PlayerEntity var1, Consumer<Text> errMsg) {
        if (type.startsWith("#")) {
            String val = type.substring(1);
            if (specialPositionRegistry.containsKey(val)) {
                SpecialPositionResolver resolver = specialPositionRegistry.get(val);
                return resolver.resolvePosition(reader, var1, errMsg);
            } else {
                errMsg.accept(Text.literal("不存在这样的特殊位置: " + type).formatted(Formatting.RED));
            }
        }
        return null;
    }

    public static Stream<String> commandSpecialPositionType() {
        return specialPositionRegistry.keySet().stream().map(s -> "#" + s);
        // return List.of("#mark", "#near", "#this", "#back", "#death", "#desync", "#lasttp", "#warp");
    }

    static {
        Listener.getCustomListener()
                .<EventContainer<TpaCommandEvent>>getChannel(TpaCommandEvent.class)
                .registerHandler(MovTasks::resolveSpecialType, 0);
        // lastly resolve entity
        Listener.getCustomListener()
                .<EventContainer<TpaCommandEvent>>getChannel(TpaCommandEvent.class)
                .registerHandler(MovTasks::resolveEntityTarget, 2147483646);
    }

    //    public static final TabResult XResult = TabResult.ofStreamFunction(
    //        p -> Stream.of("%.2f %.2f %.2f".formatted(p.getExecutePos().x, p.getExecutePos().y, p.getExecutePos().z),
    // "~ ~ ~", "^ ^ ^"));
    //
    //    public static List<TabResult> xResultList = new ArrayList<>();
    //    static{
    //        xResultList.add(XResult);
    //    }
    //    public static TabResult createXResult() {
    //        return TabResult.ofAll(xResultList);
    //    }
    //
    //
    //    public static TabResult YResult = TabResult.ofStreamFunction(
    //            p -> Stream.of("%.2f".formatted(p.getExecutePos().y), "~"))
    //        .orElse(
    //            s -> {
    //                return !s.isEmpty()
    //                    && !s.get(s.size() - 1).nonnullResult().startsWith("^");
    //            },
    //            TabResult.ofStreamSupplier(() -> Stream.of("^")));
    //    public static List<TabResult> yResultList = new ArrayList<>();
    //    static{
    //        yResultList.add(YResult);
    //    }
    //    public static TabResult createYResult() {
    //        return TabResult.ofAll(yResultList);
    //    }
    //    public static TabResult ZResult = TabResult.ofStreamFunction(
    //            p -> Stream.of("%.2f".formatted(p.getExecutePos().z), "~"))
    //        .orElse(
    //            s -> {
    //                return !s.isEmpty()
    //                    && !s.get(s.size() - 1).nonnullResult().startsWith("^");
    //            },
    //            TabResult.ofStreamSupplier(() -> Stream.of("^")));
    //    public static List<TabResult> zResultList = new ArrayList<>();
    //    static{
    //        zResultList.add(ZResult);
    //    }
    //
    //    public static TabResult createZResult() {
    //        return TabResult.ofAll(zResultList);
    //    }

    //    public static SimpleCommandArgs coordinateArguments = new SimpleCommandArgs(
    //        SimpleCommandArgs.argumentBuilder()
    //            .name("x")
    //            .tabCompletor(createXResult())
    //            .defaultValue("~")
    //            .build(),
    //        SimpleCommandArgs.argumentBuilder()
    //            .name("y")
    //            .tabCompletor(createYResult())
    //            .defaultValue("~")
    //            .build(),
    //        SimpleCommandArgs.argumentBuilder()
    //            .name("z")
    //            .tabCompletor(createZResult())
    //            .defaultValue("~")
    //            .build()
    //    );

    private static Vec3d resolveCoord(Entity entity, InputArgument argx, InputArgument argy, InputArgument argz) {
        Vec3d parsedCoord;
        if (argx.nonnullResultAsString().startsWith("^")) {
            // use polar coord
            if (!(argy.nonnullResultAsString().startsWith("^")
                    && argz.nonnullResultAsString().startsWith("^"))) {
                throw new LogicalError("Illegal format of look coordinate");
            }
            String xcoord = argx.nonnullResultAsString();
            String ycoord = argy.nonnullResultAsString();
            String zcoord = argz.nonnullResultAsString();
            double x = xcoord.length() == 1 ? 0 : CommandUtils.gdouble(xcoord.substring(1), argx.getType());
            double y = ycoord.length() == 1 ? 0 : CommandUtils.gdouble(ycoord.substring(1), argy.getType());
            double z = zcoord.length() == 1 ? 0 : CommandUtils.gdouble(zcoord.substring(1), argz.getType());
            parsedCoord = EntityUtils.lookCoordToAbsolutePos(entity, x, y, z);
        } else {
            // use simple coord
            Vec3d pos = entity.getPos();
            double x = 0;
            double y = 0;
            double z = 0;
            String xcoord = argx.nonnullResultAsString();
            String ycoord = argy.nonnullResultAsString();
            String zcoord = argz.nonnullResultAsString();
            if (xcoord.startsWith("~")) {
                x = pos.x;
                xcoord = xcoord.substring(1);
            }
            if (!xcoord.isEmpty()) {
                x += CommandUtils.gdouble(xcoord, argx.getType());
            }
            if (ycoord.startsWith("~")) {
                y = pos.y;
                ycoord = ycoord.substring(1);
            }
            if (!ycoord.isEmpty()) {
                y += CommandUtils.gdouble(ycoord, argy.getType());
            }
            if (zcoord.startsWith("~")) {
                z = pos.z;
                zcoord = zcoord.substring(1);
            }
            if (!zcoord.isEmpty()) {
                z += CommandUtils.gdouble(zcoord, argz.getType());
            }

            parsedCoord = new Vec3d(x, y, z);
        }
        return parsedCoord;
    }

    public static class TpaArgumentType extends AbstractArgumentType<ExecutePos> {

        public TpaArgumentType(String argsName) {
            super(argsName);
        }

        public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
            return Stream.concat(super.getTab(sender, args), tabCompleteTpaResult(sender, args));
        }

        @Nullable
        @Override
        public InputArgument<ExecutePos> consume(
                CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
            if (reader.hasNext()) {
                int startIndex = reader.cursor();
                Optional<Vec3d> parsePos = getTpaCommandResult(execution, args, reader, Consumers.nop());
                if (parsePos != null) {
                    // parsed success, maybe null
                    // output.forEach(execution::sendMessage);
                    return new PosArgumentResult(parsePos.map(ExecutePos::of), this, reader, startIndex);
                } else {
                    // parsed failure
                    return new PosArgumentResult(null, this, reader, startIndex);
                }
            } else {
                return new PosArgumentResult(null, this, reader, reader.cursor());
            }
        }

        public Stream<String> tabCompleteTpaResult(CommandExecution sender, List<InputArgument<?>> args) {
            if (args.isEmpty()) return Stream.empty();
            return filterTab(getTpaCommandTabResult(sender, args).stream(), args);
        }
    }

    public static class TpaAndPosArgumentType extends PosArgumentType {

        public TpaAndPosArgumentType(String argsName) {
            super(argsName);
        }

        @Override
        public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
            // first coord, then tpa result
            return Stream.concat(super.getTab(sender, args), tabCompleteTpaResult(sender, args));
        }

        public Stream<String> tabCompleteTpaResult(CommandExecution sender, List<InputArgument<?>> args) {
            if (args.isEmpty()) return Stream.empty();
            return filterTab(getTpaCommandTabResult(sender, args).stream(), args);
        }

        @Override
        public @Nullable InputArgument<ExecutePos> consume(
                CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
            if (reader.hasNext()) {
                int startIndex = reader.cursor();
                Optional<Vec3d> parsePos = getTpaCommandResult(execution, args, reader, Consumers.nop());
                if (parsePos != null) {
                    // parsed success, maybe null
                    //  output.forEach(execution::sendMessage);
                    return new PosArgumentResult(parsePos.map(ExecutePos::of), this, reader, startIndex);
                } else {
                    // parsed failure
                    // reset cursor
                    reader.setCursor(startIndex);
                    return super.consume(execution, args, reader);
                }
            } else {
                return new PosArgumentResult(null, this, reader, reader.cursor());
            }
        }
    }
}
