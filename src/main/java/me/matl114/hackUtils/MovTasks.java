package me.matl114.hackUtils;

import me.matl114.access.*;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.EntityMovementStatus;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LegalMovementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.stream.Streams;
import org.spongepowered.asm.mixin.Unique;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static me.matl114.utils.CollisionUtil.*;
import static me.matl114.hackUtils.RenderTasks.*;

//todo: add more Functional Method as API
public class MovTasks {
    public static void init(){

    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Config.DoubleRef maxDistance = Configs.MOV_CONFIG.getDouble(Configs.MOV_MAX_DISTANCE);
    private static final double distance = 0.1;

    public static boolean quickMovFront(){
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d vec3d = player.getPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateAvailableMovPlace(mc.player, vec3d, lookat, distance, maxDistance.get());
//        DEBUG_RENDER_COLLISION_RENDERING = true;
//        STATIC_DEBUG_COLOR = Color.RED;
//        RenderTasks.debugBox(mc.player.dimensions.getBoxAt(lastAvailablePos));
//        STATIC_DEBUG_COLOR = Color.YELLOW;
//        collide(mc.player, lastAvailablePos.subtract(vec3d));
//        DEBUG_RENDER_COLLISION_RENDERING = false;

        if(lastAvailablePos !=  vec3d){
            if(ignoreCheck.get()){
                executeTp(lastAvailablePos, 2147483647, false, true);
            }else {
                farawayMoveTo(lastAvailablePos, true);
            }
            return true;
        }else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }
    public static boolean quickMovTowardsWall(){
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d vec3d = player.getPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateNextWallPosition(mc.player, vec3d, lookat, distance, maxDistance.get());
        if(lastAvailablePos !=  vec3d){
            farawayMoveTo(lastAvailablePos, true);
            return true;
        }else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }
    //todo more optimize
    public static Vec3d calculateAvailableMovPlace(Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max){
//        double stepHeight = executor.getStepHeight();
//        boolean onGround = executor.isOnGround();
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
        //fixme: when max too high , creating cache costs too much
        //fixme: should in lower case and higher case when searching i
        //fixme: most of case we move less than 100
        CollisionContext engin = new CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);
//        Box originalBox = executor.dimensions.getBoxAt(curPose);
//        Box originalBox = executor.getBoundingBox();
//        Box bigBox = makeCollectorBoxInvolvingCollision(originalBox, maxinumMovement, stepHeight, onGround);
//        List<VoxelShape> involvedVoxel = new ArrayList<>();
//        List<Box> involvedAABB = new ArrayList<>();
//        CollisionUtil.getCollisions(
//            executor.getWorld(), executor, bigBox, involvedVoxel, involvedAABB,
//            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
//            null, null, null
//        );

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos  = curPose;
        boolean hasWall = false;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);
//            BlockPos pos1= BlockPos.ofFloored(vec3d);
//            BlockPos pos2 = pos1.up();
            //check
            boolean noCollision = ignoreCheck.get();
            boolean value;
            if(noCollision){
                value = true;
            }else {
                Vec3d totalMovement = curPose.subtract(originalPos);
                Vec3d sim = engin.simulateMovement(executor, originalPos, totalMovement);// collideWithTrustedList(originalBox, totalMovement, involvedVoxel, involvedAABB, stepHeight, onGround);
                value = validMovementAsServer(totalMovement, sim);
            }

            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose);

            if(pass){
                lastAvailablePos = curPose;
                if(hasWall){
                    break;
                }else {
                    continue;
                }
            }else {
                hasWall = true;
            }
        }
        return lastAvailablePos;
    }

    public static Vec3d calculateNextWallPosition(Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max){
//        double stepHeight = executor.getStepHeight();
//        boolean onGround = executor.isOnGround();
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
//        Box originalBox = executor.getBoundingBox();
//        Box bigBox = makeCollectorBoxInvolvingCollision(originalBox, maxinumMovement, stepHeight, onGround);
//        List<VoxelShape> involvedVoxel = new ArrayList<>();
//        List<Box> involvedAABB = new ArrayList<>();
//        CollisionUtil.getCollisions(
//            executor.getWorld(), executor, bigBox, involvedVoxel, involvedAABB,
//            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
//            null, null, null
//        );
        CollisionContext engin = new CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos  = curPose;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);
//            BlockPos pos1= BlockPos.ofFloored(vec3d);
//            BlockPos pos2 = pos1.up();
            //check

            boolean value;

            Vec3d totalMovement = curPose.subtract(originalPos);
            Vec3d sim = engin.simulateMovement(executor, originalPos, totalMovement); //collideWithTrustedList(originalBox, totalMovement, involvedVoxel, involvedAABB, stepHeight, onGround);
            value = validMovementAsServer(totalMovement, sim);


            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose);

            if(pass){
                lastAvailablePos = curPose;
            }else {
                break;
            }
        }
        return lastAvailablePos;
    }

    //teleport module
    @ApiMethod(deprecate = true)
    public static void farawayMoveTo(Vec3d vec3d, boolean updatePlayer){
        farawayMoveFromTo(mc.player.getPos(), vec3d, null, updatePlayer);
    }
    @ApiMethod(deprecate = true)
    public static void farawayMove(Vec3d vec3d, boolean updatePlayer){
        farawayMoveFromTo(mc.player.getPos(), mc.player.getPos().add(vec3d), null, updatePlayer);
    }
    @ApiMethod(deprecate = true)
    public static void farawayMoveFromTo(Vec3d from , Vec3d to, Boolean onGroundOverride, boolean updatePlayer){
        Vec3d vec3d = to.subtract(from);
        double len = vec3d.length();
        //tiny movements considered as a method to reset falldistance
        //todo: marge this method to scheduleMove
//        if(len  < 1E-7){
//            if(updatePlayer){
//                mc.player.setPosition(to);
//            }
//            return;
//        }
        if(mc.player.hasVehicle()){
            Entity vehicle = mc.player.getVehicle();
            double maxOnceLen = 10 - 1E-2;
            if(len >= maxOnceLen){
                double speedArg = 10;
                int packetNum = (int) (((len + 1)/speedArg));
                //calculate when elytra
                //boolean mc.world.getGameRules().get()
                if(packetNum > 0)
                    for (var p = 0 ; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(vehicle));
            }
            double deltaY = vehicle.getY() - mc.player.getY();
            vehicle.setPosition(to.add(0, deltaY, 0));
            mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(vehicle));
            if(updatePlayer){
                mc.player.setPosition(to);
            }
        }else{
            double maxOnceLen = (mc.player.isFallFlying()? (10 * Math.sqrt(3)) : 10) - 1E-2;

            if(len >= maxOnceLen){
                double speedArg = 10;
                int packetNum = (int) (((len + 1)/speedArg));
                //calculate when elytra
                //boolean mc.world.getGameRules().get()
                if(packetNum > 0)
                    for (var p = 0 ; p <= packetNum; ++p)
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
            }

            if(onGroundOverride != null){
                mc.player.setOnGround(onGroundOverride);
            }
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(to.getX(), to.getY(), to.getZ(), mc.player.isOnGround()));
            if(updatePlayer)
                mc.player.setPosition(to);
        }
    }
    public static void moveToWithPackets(Vec3d to, Boolean onGroundOverride){
        Entity entity = mc.player.getRootVehicle();
        if(Objects.equals(to, mc.player.getPos())){
            if(mc.player.hasVehicle()){
                mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(entity));
            }else{
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(onGroundOverride != null ? onGroundOverride :   mc.player.isOnGround()));
            }
        }else{
            double deltaY = entity.getY() - mc.player.getY();
//            Vec3d curr = entity.getPos();
//            Vec3d currPlayer = mc.player.getPos();
            entity.setPosition(to.add(0, deltaY, 0));
            mc.player.setPosition(to);
            if(mc.player.hasVehicle()){
                mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(entity));
            }else{
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(to.getX(), to.getY(), to.getZ(),onGroundOverride != null ? onGroundOverride :   mc.player.isOnGround()));
            }
        }

    }

    public static record MovInfo(Vec3d vec3d, Boolean oGroundOverride, boolean updatePlayer, Vec2f rotationOverride){
        public static MovInfo create(Vec3d to){
            return new MovInfo(to, null, true, null);
        }
        public static MovInfo createNotOnGround(Vec3d to){
            return new MovInfo(to, Boolean.FALSE, true, null);
        }
        public static MovInfo createNoUpdate(Vec3d to){
            return new MovInfo(to, null, false, null);
        }
    }
    public static record MovingContext(MutableObject<Vec3d> from, MutableObject<Vec3d> tickFirstGoodVec,  AtomicInteger currentTokenLimit, AtomicInteger currentTokenInTick){
        public static MovingContext create(Vec3d from){
            return new MovingContext(new MutableObject<>(from), new MutableObject<>(from), new AtomicInteger(0), new AtomicInteger(0));
        }

        public MovingContext resetTick(){
            currentTokenInTick.set(0);

            currentTokenLimit.set(0);
            tickFirstGoodVec.setValue(from.getValue());
            return this;
        }

    }
    @ApiMethod
    public static MovingContext createMovContext(double a1, double b1, double c1){
        return MovingContext.create(new Vec3d(a1, b1, c1));
    }
    @ApiMethod
    public static MovingContext createPlayerMovContext(){
        return MovingContext.create(mc.player.getPos());
    }
    @ApiMethod
    public static MovInfo createNotOnGround(Vec3d vec3){
        return MovInfo.createNotOnGround(vec3);
    }
    @ApiMethod
    public static MovInfo createMovInfo(Vec3d to){
        return MovInfo.create(to);
    }
    @ApiMethod
    public static MovInfo createMovInfo(Vec3d to, Boolean onGround, boolean updatePlayerPos, Vec2f rotationOverride){
        return new MovInfo(to, onGround, updatePlayerPos, rotationOverride);
    }


    public static void scheuleFarawayMove(Vec3d from, List<MovInfo> deltaMovements, boolean allowNextTick, boolean considerNoFall){

        scheduleFarawayMoveInternal(deltaMovements, allowNextTick, MovingContext.create(from), considerNoFall);
    }
    @ApiMethod
    public static void scheduleMoveSequence(MovingContext context, List<MovInfo> deltaMovements, boolean allowNextTick, boolean noFall){
        scheduleFarawayMoveInternal(deltaMovements, allowNextTick, context, noFall);
    }

    public static void scheduleFarawayMoveInternal(List<MovInfo> deltaMovements, boolean allowNextTick, MovingContext context, boolean considerNoFall){
//        boolean shouldCheckSpeed = ! mc.player.isFallFlying() || (! mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
//        double maxOnceLen = (mc.player.isFallFlying()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        boolean firstMove = true;
        Vec3d originalPositionTick = context.tickFirstGoodVec.getValue();
        boolean riding = mc.player.hasVehicle();
        Entity rootEntity = mc.player.getRootVehicle();
        double deltaY = rootEntity.getY() - mc.player.getY();
        double minY = context.from.getValue().y;
        double maxY = minY;
        if(context.currentTokenInTick.get() == 0){
            //gain tokens
            int maxTokenNeeded = 0;
            Vec3d lastPos = originalPositionTick;
            int packetCount = 0;
            for (var move: deltaMovements){
                packetCount += 1;
                Vec3d movingPos = move.vec3d();
                Vec3d movement = movingPos.subtract(lastPos);
                lastPos = movingPos;
                double d7 = movement.length();
                double d8 = movingPos.subtract(originalPositionTick).length();
                double d10  = Math.max(d7, d8);
                //比如
                //使用 9 9 9 9 9作为移动的， 每次packet + 1
                //即使不用token,d10也不会超过packetNum * (...)

                int tokenNeeded = ((int) Math.ceil (d10/ 9.9)) - packetCount;
                maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            }
//            Debug.info("check ", maxTokenNeeded);
            if(maxTokenNeeded > 0){
                //粗略估计
                if(maxTokenNeeded > 20 - deltaMovements.size()){
                    //unable to reach so faraway
//                    Debug.info("UnReachable ");
                    return;
                }
                for (int  i = 0; i < maxTokenNeeded; ++i){
                    context.currentTokenLimit.set(20);
                    context.currentTokenInTick.incrementAndGet();
                    //  Debug.chat("send zero packet !",context.currentTokenInTick.get(), context.currentTokenLimit.get());
                    if(riding){
                        mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(rootEntity));
                    }else{
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
                    }
                }
            }
        }
        var iter = deltaMovements.iterator();
        while (iter.hasNext()){
            MovInfo info = iter.next();
            Vec3d fromNow = context.from.getValue();
            Vec3d vec3d = info.vec3d().subtract(fromNow);
            maxY = Math.max(maxY, info.vec3d().y);
            minY = Math.min(minY, info.vec3d().y);
            double len = vec3d.length();
            double len2 = originalPositionTick.subtract(info.vec3d()).length();
            double speedArg = 9.8;
            if(len == 0 && len2 == 0){
                //d10 is 0 server side, can regain token
                context.currentTokenLimit.set(20);
                context.currentTokenInTick.incrementAndGet();
                if(info.oGroundOverride != null){
                    mc.player.setOnGround(info.oGroundOverride);
                }
                boolean hasRot = info.rotationOverride != null;
                if(hasRot){
                    mc.player.setPitch(info.rotationOverride.x);
                    mc.player.setYaw(info.rotationOverride.y);
                }
                if(riding){
                    mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(rootEntity));
                }else{
                    if(hasRot){
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
                    }else{
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
                    }

                }
            }else{
                int ExtraTokenNeeded = (int)Math.ceil (((Math.max(len, len2))/speedArg));;
                //+1代表这个包发出去之后的结果
                int tokenNow = context.currentTokenInTick.get() + 1;
//            tokenLimit = Math.max(tokenLimit, 1);
                //超出了tokenLimit, 会被强制重置为1（server side)
                //需要把剩下的移动到下一个tick执行
                if((Math.min( ExtraTokenNeeded, tokenNow) >= Math.max(5, context.currentTokenLimit.get()))){
                    if(allowNextTick && context.currentTokenInTick.get() > Math.max(5, context.currentTokenLimit.get())){
                        //
                        if(firstMove)return;
                        List<MovInfo> leftTasks = Stream.concat(Stream.of(info), Streams.of(deltaMovements)).toList();
                        Tasks.scheduleDelayed(()->{
                            scheduleFarawayMoveInternal(leftTasks, allowNextTick, context.resetTick(), considerNoFall);
                        }, 1);
                        if(considerNoFall){
                            if(Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1){
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
//                                mc.player.fallDistance = MovTasks. FORCE_RESET_DISTANCE;
                                //in case that resync packet cause OnGround falldamage
                                mc.player.setOnGround(false);
                            }
                        }
                        return;
                    }
                }
                context.currentTokenLimit.decrementAndGet();
                context.currentTokenInTick.incrementAndGet();
                //  Debug.chat("before move", context.currentTokenInTick.get(), context.currentTokenLimit.get());
                if(info.oGroundOverride() != null){
                    mc.player.setOnGround(info.oGroundOverride());
                }
                boolean hasRot = info.rotationOverride != null;
                if(hasRot){
                    mc.player.setPitch(info.rotationOverride.x);
                    mc.player.setYaw(info.rotationOverride.y);
                }
                Vec3d to = info.vec3d();
                if(riding){
                    rootEntity.setPosition(to.add(0, deltaY, 0));
                    var packet = new VehicleMoveC2SPacket(rootEntity);
                    mc.getNetworkHandler().sendPacket(packet);
                    context.from.setValue(to);
                    if(info.updatePlayer()){
                        mc.player.setPosition(to);
                    }
                }else{
                    var packet = hasRot ? new PlayerMoveC2SPacket.Full(to.getX(), to.getY(), to.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()): new PlayerMoveC2SPacket.PositionAndOnGround(to.getX(), to.getY(), to.getZ(), mc.player.isOnGround());
                    mc.getNetworkHandler().sendPacket(packet);
                    //Debug.info("send packet schedule", packet.getX(0), packet.getY(0), packet.getZ(0), packet.isOnGround());
                    context.from.setValue(to);
                    if(info.updatePlayer())
                        mc.player.setPosition(to);
                }

                //real first move
                //zero packets is not seen as movement
                firstMove = false;

            }
        }
        if(considerNoFall){
            if(Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1){
               ClientPlayerAccess.of( mc.player).setForceNoFall(true);// = MovTasks. FORCE_RESET_DISTANCE;
                //in case that resync packet cause OnGround falldamage
                mc.player.setOnGround(false);
            }
        }
    }


    private static boolean isPassMovement(){
        ClientPlayerInteractionManager manager = mc.interactionManager;
        return ignoreCheck.get() || manager.getCurrentGameMode().isCreative() || manager.getCurrentGameMode()== GameMode.SPECTATOR;
    }
    public static double searchFirstNoCollisionSpaceYHeight(Vec3d origin, double min, double max, boolean upOrDown){
        Box boundingBox = mc.player.dimensions.getBoxAt(origin);
        double tmin = upOrDown ? min: -max;
        double tmax = upOrDown ? max: -min;
        Box involved = CollisionUtil.resetY(boundingBox, origin.y + tmin, origin.y + tmax);//  boundingBox.stretch(0, max * (upOrDown? 1.0D : -1.0D), 0).stretch();
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.RED;
        debugBox(involved);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        CollisionUtil.getCollisions(
            mc.player.getWorld(), mc.player, involved, collisionsVoxel, collisionsBB,
            //CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
            null, null, null
        );
        return searchFirstNoYConflictYHeight(origin.y, collisionsBB, collisionsVoxel, min, max, upOrDown);
    }
    public static double searchFirstNoYConflictYHeight(double originY, List<Box> collisionsBB, List<VoxelShape> collisionShapes, double min, double max, boolean upOrDown){
        List<Box> allBoxes = new ArrayList<>(collisionsBB);
        for (VoxelShape shape: collisionShapes){
            allBoxes.addAll(shape.getBoundingBoxes());
        }
        allBoxes.sort(Comparator.comparingDouble(b->b.minY * (upOrDown? 1.0D: -1.0D)));
        DEBUG_RENDER_COLLISION_RENDERING = true;
        STATIC_DEBUG_COLOR = Color.BLUE;
        for (var box: allBoxes){
            debugBox(box);
        }
        DEBUG_RENDER_COLLISION_RENDERING = false;
        if(upOrDown){
            //up sort minY from small to big
            double levelY = originY + min;
            double playerBoxHeight = mc.player.dimensions.height();
            //double lastStableY;
            for (var re: allBoxes){
                //高度差上已经有碰撞了
                if(levelY + playerBoxHeight > re.minY && levelY < re.maxY){
                    if(re.maxY < originY + max){
                        levelY = re.maxY;
                    }else{
                        //new Y out of bound
                        break;
                    }
                }else if(levelY + playerBoxHeight <= re.minY){
                    //搜索结束
                    //因为按re.minY从小到大排,这个位置将不会和任意后面的碰撞
                    break;
                }
            }
            //fixme returning y value out of bound (? need test, havn't seen problem )
            return levelY - originY;
        }else {
            double levelY = originY - min;
            double playerBoxHeight = mc.player.dimensions.height();
            //由大到小排
            for (var re: allBoxes){
                //高度差上已经有碰撞了
                if(levelY  < re.maxY && levelY + playerBoxHeight > re.minY){
                    double newLevelY = re.minY - playerBoxHeight;
                    if(newLevelY > originY - max){
                        levelY = newLevelY;
                    }else{
                        //new Y out of bound
                        break;
                    }
                }else if(levelY >= re.maxY){
                    //搜索结束
                    break;
                }
            }
            return levelY - originY;
        }
    }
    @ApiMethod
    private static boolean checkEnvironmentCollision(Entity entity, Vec3d pos, boolean ignoreChunkBorder){
        // should also consider entity collision, shit shulker
        //set position for bounding box update!
        final List<Box> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        Box oldBox = entity.getBoundingBox();
        CollisionUtil.getCollisions(
            entity.getWorld(), entity, entity.dimensions.getBoxAt(pos), collisionsVoxel, collisionsBB,
            //may cancel unloaded chunks?
            //COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS |
            ignoreChunkBorder ?
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER
                : (COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS | CollisionUtil.COLLISION_FLAG_CHECK_BORDER)
            ,
            null, null, null
        );

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
    private static final Config.FlagRef ignoreCheck = Configs.MOV_CONFIG.getBoolean(Configs.QUICK_MOVE_IGNORE_COLLISION);
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
//            return  validMovementAsServer(simu, movement) && !checkEnvironmentCollision(mc.player, pos, );//!isPlayerCollidingWithAnythingNew(mc.world, mc.player.getBoundingBox(), pos.x, pos.y, pos.z) ;// mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox()))
//        }finally {
//            mc.player.setPosition(savedPosition);
//        }
//
//    }
    private static final double maxYDelta = 190;
    public static boolean doingTp = false;
    public static Vec3d LAST_TP_REQUEST ;
    public static Vec3d LAST_TP_FROM;
    @ApiMethod
    public static void executeTp(Vec3d target, double farawayTp, boolean command, boolean considerNoFall){
        if(mc.player == null)return;
        LAST_TP_FROM = mc.player.getPos();
        LAST_TP_REQUEST = Vec3d.ZERO.add(target);
        scheduleTpInternal(MovingContext.create(mc.player.getPos()), target, farawayTp, command, false, considerNoFall);
    }
    @ApiMethod
    public static List<Vec3d> generateTpSequence(Vec3d current, Vec3d target, boolean command, double farawayTp, boolean considerEnvironment){
        boolean collideAtTarget = checkEnvironmentCollision(mc.player, target, true);
        if(collideAtTarget){
            if(command){
                Debug.chat("目标位置存在方块碰撞冲突, 无法执行tp");
            }
            return List.of();
        }

        Vec3d movement0 = target.subtract(current);
        Vec3d sim = simulateMovement(mc.player, current, movement0, true);
        if(validMovementAsServer(movement0, sim)){
            return List.of(current, current.add(movement0));
        }
        Vec3d horizontalMovement ;
        horizontalMovement = new Vec3d(movement0.x, 0, movement0.z);

        double len = horizontalMovement.length();
//        Box tpHorizontalPlate = mc.player.dimensions.getBoxAt(current).stretch(horizontalMovement);
        Box tpSmallerAxisPlate;
        Box tpLargerAxisPlate;
        if(Math.abs(movement0.x ) > Math.abs(movement0.z)){
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch(movement0.x, 0, 0);
            tpLargerAxisPlate = mc.player.dimensions.getBoxAt(current).offset(movement0.x, 0, 0).stretch(0,0,movement0.z);
        }else {
            tpSmallerAxisPlate = mc.player.dimensions.getBoxAt(current).stretch( 0, 0, movement0.z);
            tpLargerAxisPlate = mc.player.dimensions.getBoxAt(current).offset( 0, 0, movement0.z).stretch(movement0.x, 0,0);
        }

        double currentY = current.y;
        double targetY = target.y;
        World world = mc.player.getWorld();
        double horizontalY;


        if(len <=  farawayTp){
            if(currentY >= world.getBottomY() && currentY <= world.getTopY()){
                Box boundariesFromBox = mc.player.dimensions.getBoxAt(current);
                Box boundariesToBox = mc.player.dimensions.getBoxAt(target);
                Box boundariesSmallAxis ;
                Box boundariesLargeAxis;
                boundariesSmallAxis = CollisionUtil.resetY(tpSmallerAxisPlate, world.getBottomY(), world.getTopY());
                boundariesLargeAxis = CollisionUtil.resetY(tpLargerAxisPlate, world.getBottomY(), world.getTopY());
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
                //should add LAVA with current/target xz coord into to avoid searching TP into them
                BiFunction<BlockState, BlockPos, Box>  currentFromToPredicateFilter = considerEnvironment? (blockstate, blockpos)->{
                    if(blockstate != null){
                        Block block = blockstate.getBlock();
                        //I DO NOT WANT BY TP SEQUENCE ENTER ANY OF THESE FIRE BLOCKS BECAUSE IT MAY LIT ME UP!
                        if(block == Blocks.LAVA || block == Blocks.SOUL_FIRE || block == Blocks.FIRE){
                            //filter lava blocks, we should be careful
                            Box lavaBlockBox = Box.enclosing(blockpos, blockpos);
                            if(CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, boundariesFromBox) || CollisionUtil.voxelShapeIntersectHorizontal(lavaBlockBox, boundariesToBox)){
                                return lavaBlockBox;
                            }
                        }
                    }
                    return null;
                } : null;
                CollisionUtil.getCollisions(world, mc.player, boundariesLargeAxis, intoVoxels, intoAABB, COLLISION_FLAG_CHECK_BORDER, null, null, currentFromToPredicateFilter);
                CollisionUtil.getCollisions(world, mc.player, boundariesSmallAxis, intoVoxels, intoAABB, COLLISION_FLAG_CHECK_BORDER, null, null, currentFromToPredicateFilter);
                debug0 = DEBUG_RENDER_COLLISION_RENDERING;
                DEBUG_RENDER_COLLISION_RENDERING = true;
                STATIC_DEBUG_COLOR = Color.BLUE;
                for (var box: intoAABB){
                    if(box.minY > 20)
                        debugBox(box);
                }
                if(considerEnvironment){
                    //some blocks are dangerous , so we should consider them as not passable
                    //for example LAVA , shit LAVA
                }
                DEBUG_RENDER_COLLISION_RENDERING = debug0;
                STATIC_DEBUG_COLOR = Color.CYAN;
                if(!intoAABB.isEmpty() || !intoVoxels.isEmpty()){
//                        Box startPlace;
//                        Vec3d movement ;
                    Vec3d result;
//                        Box lastPlace;
                    double height1 = searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180,true);
                    double height2= searchFirstNoYConflictYHeight(currentY, intoAABB, intoVoxels, 0, 180,false);
//                        Debug.chat(height1, height2);
                    double height;
                    if(Math.abs(height1) > Math.abs(height2)){
                        height = height2;
                    }else {
                        height = height1;
                    }
                    result = new Vec3d(0, height,0);
                    boolean debug = DEBUG_RENDER_COLLISION_RENDERING;
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
                    RenderTasks.STATIC_DEBUG_COLOR = Color.WHITE;
                    debugBox(tpSmallerAxisPlate.offset(0, height, 0));
                    debugBox(tpLargerAxisPlate.offset(0, height, 0));
                    RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
                    horizontalY = height + currentY;

                    //tp
                }else{
                    horizontalY = currentY;
                }
                //small use
                //

            }else{
                horizontalY = currentY;
            }
        }else{
            if(command)
                Debug.chat("水平差距过大,当前tp模式无法完成");
            return List.of();

        }
        if(Math.abs(horizontalY - currentY) > maxYDelta || Math.abs(horizontalY - targetY) > maxYDelta){
            if(command)
                Debug.chat("y 差距过大,当前tp模式无法完成");
            return List.of();
        }
        Vec3d vec3d1 = current;
        Vec3d vec3d2 = vec3d1.add(new Vec3d(0, horizontalY - currentY, 0));
        Vec3d vec3d3 = vec3d2.add(horizontalMovement);
        Vec3d vec3d4 = vec3d3.add(new Vec3d(0, targetY - horizontalY, 0));
        return List.of(vec3d1, vec3d2, vec3d3, vec3d4);
    }


    public static void scheduleTpInternal(MovingContext context, Vec3d target,  double farawayThreshold, boolean command, boolean fastMode, boolean considerNoFall){
        if(mc.player == null)return;
        if(command)
            Debug.chat("正在向", ChatUtils.getDisplayedLocationDouble(target),"执行tp行为");
        Vec3d current = context.from.getValue();
        //simulate direct move
        Vec3d movement0 = target.subtract(current);
        //flying into lava fuck, so we consider Environment
        List<Vec3d> vc3d0 = generateTpSequence(current, target, command, farawayThreshold, true);
        if(vc3d0.size() == 2){
            boolean downward = movement0.y < -4;
            Vec3d target0 = vc3d0.get(1).add(0, 9E-8,0);
            MovInfo mainMove = new MovInfo(target0, downward?Boolean.FALSE: null ,true, null);
            List<MovInfo> movements =
                //downward ? List.of(mainMove, MovInfo.create(target0.add(0, 9E-8,0))):
                    List.of(mainMove);
            // direct tp should also consider about setBack falldistance, passing considerNoFall arguments to do that
            scheduleFarawayMoveInternal(movements, true, context, considerNoFall);
        }else if(vc3d0.size() == 4){
            Debug.chat("执行TP序列");
            Vec3d vec3d1 = vc3d0.get(0);
            Vec3d vec3d2 = vc3d0.get(1);
            Vec3d vec3d3 = vc3d0.get(2);
            Vec3d vec3d4 = vc3d0.get(3);
            boolean downWard = vec3d1.y > vec3d2.y + 4;
            boolean downWard0 = vec3d3.y > vec3d4.y + 4;
            context.from.setValue(vec3d1);
//            boolean currentOnGround = mc.player.isOnGround();
            mc.player.setOnGround(false);
            if(fastMode){
                List<MovInfo> movingPositions = new ArrayList<>();
                movingPositions.add( MovInfo.createNotOnGround(vec3d2));
                if(downWard)
                    movingPositions.add(MovInfo.create(vec3d2.add(0, 9E-8, 0)));
                movingPositions.add(MovInfo.create(vec3d3
                  //  .add(0, downWard ? 9E-8: 0,0)
                ));
                movingPositions.add(MovInfo.createNotOnGround(vec3d4));
//                if(downWard0)
//                    movingPositions.add();
                if(downWard0)
                    movingPositions.add(MovInfo.create(vec3d4.add(0, 9E-8, 0)));
                scheduleFarawayMoveInternal(movingPositions, true, context, considerNoFall);
            }
           else{
                context.from.setValue(vec3d1);
                //it should be ignored consider No Fall
                scheduleFarawayMoveInternal(List.of(MovInfo.createNotOnGround(vec3d2)),false, context, true);
               // farawayMoveFromTo(vec3d1 , vec3d2, downWard? Boolean.FALSE: null, true);
                doingTp = true;

                Tasks.scheduleDelayed(()->{
                    doingTp = false;
                    scheduleFarawayMoveInternal(List.of(MovInfo.create(vec3d3)), false, context.resetTick(), false);
                   // farawayMoveFromTo(vec3d2, vec3d3, null, true);
                    doingTp = true;
                }, 2);
                ;
                Tasks.scheduleDelayed(()->{
                    doingTp = false;
                    scheduleFarawayMoveInternal(List.of(downWard0?MovInfo.createNotOnGround(vec3d4): MovInfo.create(vec3d4)), false, context.resetTick(), false);
                    // reset fall distance after scheduleTpInternal
                    if(considerNoFall && downWard0 && mc.player != null){
                        ClientPlayerAccess.of( mc.player).setForceNoFall(true);
                        mc.player.setOnGround(false);
                    }
//                    farawayMoveFromTo(vec3d3, vec3d4,  downWard0? Boolean.FALSE: null,  true);
//                    if(downWard0)
//                        scheduleFarawayMoveInternal(Iterators.singletonIterator(MovInfo.create(vec3d4.add(0, 9E-8, 0))), false, context);
                }, 3);
           }

        }else{
            return;
        }

        if(command)
            Debug.chat("tp行为已经执行, 若出现回弹或者位置不变,则目标位置不可达");

//        if(currentY > world.getBottomY() + 64){
//            //most likely
//            tpHorizontalPlate = world.getTopY()
//        }
    }
    private static boolean doIntercepteMovingPacketsWhileTp(PlayerMoveC2SPacket packet){
        if(doingTp){
           // capture no-tp packets
            return false;
        }
        return true;
    }
    private static boolean doIntercepteMoveVehiclePacketsWhileTp(VehicleMoveC2SPacket packet){
        if(doingTp){
            return false;

        }
        return true;
    }
    private static void doStopPlayerSendMovementPackets(Event<ClientPlayerEntity> entity){

        if(doingTp){
            entity.cancel();
        }
    }

    private static void configureTpMaskPlayer(ClientPlayerEntity plaayer){
        //remove tick Movement packets when doing tp
        ClientPlayerAccess.of(plaayer).getLegalMovementManager().addMovementModifier(
            new LegalMovementManager.MovementModifier() {
                boolean resetThisTick;

                @Override
                public boolean mayModifyPos() {
                    //only collide with other when doing Tp
                    return doingTp;
                }

                @Override
                public int priority() {
                    //every negative can override this
                    //it can override nofall or something
                    return 0;
                }

                @Override
                public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                    if(doingTp){
                        resetThisTick = true;
                    }
                }

                @Override
                public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                    if(resetThisTick){
                        movementManagerEvent.cancel();
                    }
                }

                @Override
                public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                    if(resetThisTick){
                        movementManagerEvent.cancel();
                    }
                }

                @Override
                public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                    if(resetThisTick){
                        resetThisTick = false;
                        movementManagerEvent.context().playerStatus.restorePos();
                        movementManagerEvent.context().playerStatus.restoreOnGround();
                    }
                    return true;
                }
            }
        );
    }

    //movement simulation
    public static boolean hasCollidedSoftly(Vec3d adjustedMovement) {
        float f = mc.player.getYaw() * 0.017453292F;
        double d = (double)MathHelper.sin(f);
        double e = (double)MathHelper.cos(f);
        double g = (double)mc.player.sidewaysSpeed * e - (double)mc.player.forwardSpeed * d;
        double h = (double)mc.player.forwardSpeed * e + (double)mc.player.sidewaysSpeed * d;
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

    public static EntityMovementStatus<ClientPlayerEntity> startSimulation(){
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
        final double stepHeight = (double)entity.getStepHeight();

        final Box currBoundingBox = entity.getBoundingBox();
        final List<Box> potentialCollisionsBB = new ArrayList<>();
        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        collectBoxInvolvingInMovements(entity, entity.getPos(), movement, potentialCollisionsBB, potentialCollisionsVoxel, true);
//        if (CollisionUtil.isEmpty(currBoundingBox)) {
//            return movement;
//        }
//        Box collisionBox = makeCollectorBoxInvolvingCollision(currBoundingBox, movement, stepHeight, onGround);
//
//        CollisionUtil.getCollisions(
//            world, entity, collisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
//            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
//            null, null, null
//        );
        return collideWithTrustedList(currBoundingBox, movement, potentialCollisionsVoxel, potentialCollisionsBB, stepHeight, entity.isOnGround());
        // Paper end - optimise collisions
    }
    private static void collectBoxInvolvingInMovements(Entity entity, Vec3d startPos, Vec3d movement, List<Box> intoAABB, List<VoxelShape> intoVoxels, boolean ignoreUnloadedChunk){
        final Box currBoundingBox = entity.dimensions.getBoxAt(startPos);
        if(CollisionUtil.isEmpty(currBoundingBox))return;
        Box collisionBox = makeCollectorBoxInvolvingCollision(currBoundingBox, movement, entity.getStepHeight(), entity.isOnGround());
        CollisionUtil.getCollisions(
            entity.getWorld(), entity, collisionBox, intoVoxels, intoAABB,
            ignoreUnloadedChunk ?  COLLISION_FLAG_CHECK_BORDER : (COLLISION_FLAG_CHECK_BORDER | COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS),
            null,null,null
        );
        if(DEBUG_RENDER_COLLISION_RENDERING){
            for (var coll: intoAABB){
                debugBox(coll);
            }
            for (var voxel : intoVoxels){
                for (var box : voxel.getBoundingBoxes()){

                    debugBox(box);
                }
            }
        }
    }
    public static Box makeCollectorBoxInvolvingCollision(Box currBoundingBox, Vec3d movement, double stepHeight, boolean onGround){

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
            //stepheight check 1.21.6
            if (!disableStepHeightFeature.get() && stepHeight > 0.0 && (onGround || (movement.y < 0.0))) {
                // don't bother getting the collisions if we don't need them.
                if (movement.y <= 0.0) {
                    collisionBox = CollisionUtil.expandUpwards(currBoundingBox.stretch(movement.x, movement.y, movement.z), stepHeight);
                } else {
                    collisionBox = currBoundingBox.stretch(movement.x, Math.max(stepHeight, movement.y), movement.z);
                }
            } else {
                collisionBox = currBoundingBox.stretch(movement.x, movement.y, movement.z);
            }
        }
        return collisionBox;
    }
    private static final Config.FlagRef disableStepHeightFeature = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_COMPATE_HIGHER_VERSION);
    private static final ThreadLocal<Boolean> INTERNAL_VALUE_USE_STEPHEIGHT = ThreadLocal.withInitial(()->Boolean.FALSE);
    public static Vec3d collideWithTrustedList(Box currBoundingBox, Vec3d movement, List<VoxelShape> potentialCollisionsVoxel, List<Box> potentialCollisionsBB, double stepHeight, boolean onGround){
        if (potentialCollisionsVoxel.isEmpty() && potentialCollisionsBB.isEmpty()) {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return movement;
        }

        STATIC_DEBUG_COLOR = Color.YELLOW;
        final Vec3d limitedMoveVector = CollisionUtil.performCollisions(movement, currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);

        if (!disableStepHeightFeature.get() && stepHeight > 0.0
            && (onGround || (limitedMoveVector.y != movement.y && movement.y < 0.0))
            && (limitedMoveVector.x != movement.x || limitedMoveVector.z != movement.z)) {
            // stepheight cause movement invalid in higher version
            // mark as not planned in 1.21.1; will do it in 1.21.6 version
            //auto jump to go across, do not move
            STATIC_DEBUG_COLOR = Color.BLUE;
            Vec3d vec3d2 = CollisionUtil.performCollisions(new Vec3d(movement.x, stepHeight, movement.z), currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);
            boolean debug = RenderTasks.DEBUG_RENDER_COLLISION_RENDERING;
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
            //speed up jumping head

            final Vec3d vec3d3 = CollisionUtil.performCollisions(new Vec3d(0.0, stepHeight, 0.0), currBoundingBox.stretch(movement.x, 0.0, movement.z), potentialCollisionsVoxel, potentialCollisionsBB);

            if (vec3d3.y < stepHeight) {
                final Vec3d vec3d4 = CollisionUtil.performCollisions(new Vec3d(movement.x, 0.0D, movement.z), currBoundingBox.offset(vec3d3), potentialCollisionsVoxel, potentialCollisionsBB).add(vec3d3);

                if (vec3d4.horizontalLengthSquared() > vec3d2.horizontalLengthSquared()) {
                    vec3d2 = vec3d4;
                }
            }
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = debug;
            if (vec3d2.horizontalLengthSquared() > limitedMoveVector.horizontalLengthSquared()) {
//                STATIC_DEBUG_COLOR = Color.PINK;
                INTERNAL_VALUE_USE_STEPHEIGHT.set(true);
                return vec3d2.add(CollisionUtil.performCollisions(new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D), currBoundingBox.offset(vec3d2), potentialCollisionsVoxel, potentialCollisionsBB));
            }
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        } else {
            INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
            return limitedMoveVector;
        }
    }


    public static boolean doMovementInvolveStepheight(Entity entity, Vec3d movement){
        boolean stepheightFlag = disableStepHeightFeature.get();
        //enable stepheight feature to simulate
        disableStepHeightFeature.set(false);
        INTERNAL_VALUE_USE_STEPHEIGHT.set(false);
        DEBUG_RENDER_COLLISION_RENDERING = true;
        Vec3d c = collide(entity, movement);
        DEBUG_RENDER_COLLISION_RENDERING = false;
        boolean useStepheight = INTERNAL_VALUE_USE_STEPHEIGHT.get();
        disableStepHeightFeature.set(stepheightFlag);
        return  useStepheight;
    }
    @ApiMethod
    public static Vec3d simulateMovement(Entity entity, Vec3d from, Vec3d vec3d, boolean serverMode){
       //var simu = startSimulation();
        Entity rootEnity = entity.getRootVehicle();
        double deltaY = rootEnity.getY() - entity.getY();
//        entity.setPosition(from);
        Vec3d rootVec = rootEnity.getPos();
        rootEnity.setPosition(from.add(0, deltaY, 0));
        //allow down velocity
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
//                    RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(box0.getMinPos(), box0.getMaxPos(), DEBUG_TICK, Color.YELLOW));
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
    public static boolean validMovementAsServer(Vec3d expect, Vec3d sim){
        return mc.interactionManager.getCurrentGameMode().isCreative() || MathUtils.s2(expect.x - sim.x) + MathUtils.s2(expect.z - sim.z) < 0.0625D;
    }
    private static final Random rand = Random.create();
    public static List<Vec3d> tpAttackSearch(Vec3d from, Box to, double availableRange, double maxAtOnce, int maxAttempt){
        double avRS = MathUtils.s2(availableRange);
        List<Vec3d> vec = new ArrayList<>();
        //store player information
        Vec3d playerVec = mc.player.getVelocity();
        Vec3d playerPos = mc.player.getPos();

        Vec3d currentPos = from;
        for_loop:
        for (int i=0; i< maxAttempt; ++i){
           // Debug.chat("on loop", i);
            if(!vec.isEmpty()){
                currentPos = vec.get(vec.size() - 1);
            }
            Vec3d currentTry =  to.getBottomCenter().subtract(currentPos);
            double len = Math.max(1.0F,  currentTry.length() - availableRange + 1.0F);
            currentTry = currentTry.normalize().multiply(Math.min(maxAtOnce, len));
            if(len >= maxAtOnce ){
                currentTry = currentTry.multiply(maxAtOnce/len);
            }
           // mc.player.move(MovementType.PLAYER, currentTry);
            switch (validMovToEntity(ENGIN, currentPos, currentTry, to, avRS, vec)){
                case 2:break for_loop;
                case 1: continue;
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
            //java.util.Random rand = new java.util.Random();
            //random select
//            for (int s = 0; s< 10; ++s ){
//                Vec3d currentFacingTry = EntityUtils.rotateVec(currentTry, rand.nextFloat(- 50, 50),rand.nextFloat(- 50, 50) );
//                switch (validMov(currentPos, currentFacingTry, to, avRS, vec)){
//                    case 2:break for_loop;
//                    case 1: continue for_loop;
//                }
//            }
            break for_loop;
            //can not continue move
            //back to currentPos
        }
        mc.player.setVelocity(playerVec);
        mc.player.setPosition(playerPos);
        return vec;
    }




    private static int validMovToEntity(CollisionContext engin, Vec3d currentPos, Vec3d currentTry, Box to, double avRS, List<Vec3d> vec){
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);
        Vec3d testPos = currentPos.add(testMov);

        //test back
        //can back
        Vec3d expectedBack  = Vec3d.ZERO.subtract(testMov);
        Vec3d backTry = engin.simulateMovement(mc.player, testPos, expectedBack );
        if(collisionDebugRender()){
            Vec3d backPos = testPos.add(backTry);
            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(testPos.add(new Vec3d(-0.5, 0, -0.5)), testPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(backPos.add(new Vec3d(-0.5, 0, -0.5)), backPos.add(new Vec3d(0.5, 2, 0.5)), DEBUG_TICK));
            Debug.chat("Boundback", backPos.squaredDistanceTo(currentPos));
        }

        // there should be bug, but it works well, that's because only the y is unlimited
        if(validMovementAsServer(expectedBack, backTry)){
            //distance available
            // check collision for safety
            if(to.squaredMagnitude(testPos) < avRS && !engin.checkEnvironmentCollision(mc.player, testPos)){
                vec.add(testPos);
              //  Debug.chat("add finish pos", RenderTasks.getDisplayedLocationDouble(testPos), "move", RenderTasks.getDisplayedLocationDouble(testMov));
                return 2;
            }
            //valid move
            //move available

            else if(validMovementAsServer(currentTry, testMov)){
                Vec3d nextPos = currentPos.add(currentTry);
                //fixme use expected Pos as target
                //fixme no need to check expected pos because of movement mech
                //check collision for safety
                if(!engin.checkEnvironmentCollision(mc.player, nextPos)){
                    vec.add(nextPos);
                    return 1;
                }
              //  Debug.chat("add avail pos", RenderTasks.getDisplayedLocationDouble(testPos), "move", RenderTasks.getDisplayedLocationDouble(testMov));
            }
        }
       // Debug.chat("fail check to ", RenderTasks.getDisplayedLocationDouble(testPos));
        return 0;
    }
    public static final CollisionContext ENGIN = new CollisionContext() {
    };
    public static final CollisionContext ENGIN_LOADED = new CollisionContext() {
        @Override
        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec) {
            return MovTasks.checkEnvironmentCollision(entity, vec, false);
        }
    };
    public static interface CollisionContext{
        default Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry){
            return MovTasks.simulateMovement(entity, currentPos, currentTry, true);
        }

        default boolean checkEnvironmentCollision(Entity entity, Vec3d vec){
            return MovTasks.checkEnvironmentCollision(entity, vec, true);
        }
    }
    public static class CollisionCache implements CollisionContext{
        Entity entity;
        Vec3d startPos;
        Vec3d endPos;
        List<Box> intoAABBs;
        List<VoxelShape> intoVoxels;
        List<Box> allBoxes;
        boolean ignoreChunkBorder;
        public CollisionCache(Entity entity, Vec3d startPos, Vec3d endPos, boolean ignoreChunkBorder){
            this.entity = entity;
            this.startPos = startPos;
            this.endPos = endPos;
            this.ignoreChunkBorder = ignoreChunkBorder;
            intoAABBs = new ArrayList<>();
            intoVoxels = new ArrayList<>();
            collectBoxInvolvingInMovements(entity, startPos, endPos.subtract(startPos), this.intoAABBs, this.intoVoxels, ignoreChunkBorder);
           // makeCollectorBoxInvolvingCollision(entity.dimensions.getBoxAt(startPos), endPos.subtract(startPos))
            //collected all aabbs
            allBoxes = new ArrayList<>();
            allBoxes.addAll(intoAABBs);
            for (var voxel: intoVoxels){
                allBoxes.addAll(voxel.getBoundingBoxes());
            }
        }

        public Vec3d simulateMovement(Entity entity, Vec3d currentPos, Vec3d currentTry){
            Entity rootEntity = entity.getRootVehicle();
          //  double y = entity.getY() - rootEntity.getY();
//            Vec3d originRoot = rootEntity.getPos();
//            rootEntity.setPosition(currentPos);
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = true;
            Vec3d simu = collideWithTrustedList(rootEntity.dimensions.getBoxAt(currentPos), currentTry, this.intoVoxels, this.intoAABBs, rootEntity.getStepHeight(), rootEntity.isOnGround());
            RenderTasks.DEBUG_RENDER_COLLISION_RENDERING = false;
//            rootEntity.setPosition(originRoot);
            return simu;
        }

        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec){
            return MovTasks.checkEnvironmentCollision(entity, vec, this.ignoreChunkBorder);
        }

        //todo: mechanism different , can not be cached!
//        public boolean checkEnvironmentCollision(Entity entity, Vec3d vec){
//            Box oldBox = entity.dimensions.getBoxAt(vec);
//            for (int i = 0, len = this.intoAABBs.size(); i < len; ++i) {
//                final Box box = this.intoAABBs.get(i);
//                if (!CollisionUtil.voxelShapeIntersect(box, oldBox)) {
//                    return true;
//                }
//            }
//
//            for (int i = 0, len = this.intoVoxels.size(); i < len; ++i) {
//                final VoxelShape voxel = this.intoVoxels.get(i);
//                if (!CollisionUtil.voxelShapeIntersectNoEmpty(voxel, oldBox)) {
//                    return true;
//                }
//            }
//            return false;
//        }
    }

    public static boolean validMoveTo(CollisionContext engin, Vec3d currentPos, Vec3d currentTry){
        //fixme: currentPos may not be a suitable place for player to stay
        if(engin.checkEnvironmentCollision(mc.player, currentPos)){
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if(validMovementAsServer(currentTry, testMov) ){
            Vec3d currentForward = currentPos.add(currentTry);
            //check collision for safety
            if(!engin.checkEnvironmentCollision(mc.player, currentForward)){
                return true;
            }
        }
        return false;
    }
    public static boolean validMoveToAndBack(CollisionContext engin, Vec3d currentPos, Vec3d currentTry){
        //fixme: currentPos may not be a suitable place for player to stay
        if(engin.checkEnvironmentCollision(mc.player, currentPos)){
            return false;
        }
        Vec3d testMov = engin.simulateMovement(mc.player, currentPos, currentTry);

        if(validMovementAsServer(currentTry, testMov) ){
            //use expected position as server success position
            Vec3d currentForward = currentPos.add(currentTry);
            //check collision for safety
            if(!engin.checkEnvironmentCollision(mc.player, currentForward)){
                Vec3d backMov = Vec3d.ZERO.subtract(currentTry);
                Vec3d testBackMov = engin.simulateMovement(mc.player, currentForward, backMov);
                if(validMovementAsServer(backMov, testBackMov)){
                    return true;
                }
            }

        }
        return false;
    }




    public static boolean toggleSpeedOverride(){
        if(mc.player == null)return false;
        if(mc.player.getAbilities().flying){
            overrideFly.set(!overrideFly.get());
            Debug.chat("toggle fly speed override",overrideFly.get());
        }else{
            overrideWalk.set(!overrideWalk.get());
            Debug.chat("toggle walk speed override",overrideWalk.get());
        }
        //fix config not save
        Configs.MOV_CONFIG.save();
        return true;
    }

    //copied from wurst
    //seems not work
    //shit
    public void onSpeedUp(ClientPlayerEntity player)
    {
        // return if sneaking or not walking
        if(player.isSneaking()
            || player.forwardSpeed == 0 && player.sidewaysSpeed == 0)
            return;

        // activate sprint if walking forward
        if(player.forwardSpeed > 0 && !player.horizontalCollision)
            player.setSprinting(true);

        // activate mini jump if on ground
        if(!player.isOnGround())
            return;

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 1.8, v.y + 0.1, v.z * 1.8);

        v = player.getVelocity();
        double currentSpeed = Math.sqrt(Math.pow(v.x, 2) + Math.pow(v.z, 2));

        // limit speed to highest value that works on NoCheat+ version
        // 3.13.0-BETA-sMD5NET-b878
        // UPDATE: Patched in NoCheat+ version 3.13.2-SNAPSHOT-sMD5NET-b888
        double maxSpeed = 0.66F;

        if(currentSpeed > maxSpeed)
            player.setVelocity(v.x / currentSpeed * maxSpeed, v.y,
                v.z / currentSpeed * maxSpeed);
    }
    //antikick module
    private static int antiKickCount = 0;
    private static final int antiKickPeriod = 60;
    private static final double antiKickOffset = 0.032D;
    private static double antiKickOffset0 ;
    private static boolean escapeMotionReset = false;
    private static double preservedLastMotion = 0.0D;
    private static boolean waitingForServerResponse;

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
    public static boolean seenAsFloating(){
        //add serverPacket result, if toggle flight at server, stop seen as floating, no need to antiKick
        return  !serverPacketAllowFlight && mc.player.getVelocity().y >= -0.03125D && mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR  && !mc.player.hasStatusEffect(StatusEffects.LEVITATION) && !mc.player.isFallFlying() && !mc.player.isUsingRiptide() && !mc.player.isSleeping() && !mc.player.isRiding() && !mc.player.isDead() && noBlocksAround(mc.player) ;
    }
    public static void antiKick(ClientPlayerEntity player){
        if(seenAsFloating()){
            antiKickCount++;
        }else {
            antiKickCount = 0;
        }
        if(antiKickCount > antiKickPeriod){
            antiKickCount = 0;
            escapeMotionReset = false;
            preservedLastMotion = player.getVelocity().y;
            setMotionY(- antiKickOffset);
            //randomly fall down twice
            waitingForServerResponse = true; //Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return;
        }
       // int tickCounter = Tasks.getTick()%antiKickPeriod;
//        if(tickCounter == 0){
//            if(mc.options.sneakKey.isPressed()
//                && !mc.options.jumpKey.isPressed()){
//                escapeMotionReset = true;
//            }
//            else
//            if( !seenAsFloating() ){
//                escapeMotionReset = true;
//            }
//            else{
//                escapeMotionReset = false;
//
//            }
//        }else if(tickCounter < 5 ){
        if( !escapeMotionReset){
            if(waitingForServerResponse){
                setMotionY(- antiKickOffset);
                antiKickOffset0 += antiKickOffset - 0.008;
                //there is no fucking packet for response
                waitingForServerResponse = false;

                //continue fall down til server respond
            }else {
                setMotionY( antiKickOffset0 + preservedLastMotion - 0.0);
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                Tasks.scheduleDelayed(MovTasks::restoreKeyPresses,1);
                //set end
                escapeMotionReset = true;
            }
        }

//        }

    }
    private static void setMotionY(double motionY)
    {

        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, motionY, velocity.z);
    }

    private static void restoreKeyPresses()
    {
        //bugfix when shift click in screen, this key is reset to fall
        if(mc.currentScreen == null){

            KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
            KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
        }

    }

    private static final Config.FlagRef overrideFly = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_FLY);
    private static final Config.FlagRef overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    private static boolean serverPacketAllowFlight = false;

    public static boolean onPacketFlyToggle(PlayerAbilitiesS2CPacket packet1){
        Tasks.scheduleDelayed(()->{

            serverPacketAllowFlight = packet1.allowFlying();
            if(mc.player != null){
                PlayerAbilities abilities = mc.player.getAbilities();
                //abilities.allowFlying = abilities.allowFlying;
                abilities.creativeMode = packet1.isCreativeMode();
                abilities.invulnerable = packet1.isInvulnerable();
                if(! HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
                    abilities.allowFlying = packet1.allowFlying();
                }
                if(!overrideFly.get()){
                    abilities.setFlySpeed(packet1.getFlySpeed());
                }
                abilities.setWalkSpeed(packet1.getWalkSpeed());
                //mc.player.getAbilities().flying = isFly;
            }

        },1);
        return false;
    }

    public static boolean onPacketFly(UpdatePlayerAbilitiesC2SPacket packet){

        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT) && !serverPacketAllowFlight){
            return false;
        }
        return true;

    }

    public static LegalMovementManager.MovementModifier configureCreativeFlyAbility(){
        return new LegalMovementManager.MovementModifier() {
            @Override
            public boolean mayModifyPos() {
                return false;
            }

            @Override
            public boolean mayModifyRotation() {
                return false;
            }

            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                if( HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
                    if( !player.getAbilities().allowFlying ){
                        player.getAbilities().allowFlying = true;
                    }
                    antiKick(player);
                }else {
                    player.getAbilities().allowFlying = serverPacketAllowFlight;
                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                return false;
            }
        };
    }

    //fixme: fake flight causes fallflying fly

    private static final Config.FlagRef shouldCheckSetback = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_CHECK_SETBACK);
    private static void listenAntiCheatSetBack(TeleportConfirmC2SPacket packet1){
        //WHEN IN hack version, detect grimac backteleport with negative random teleport id
        if(shouldCheckSetback.get() &&  packet1.getTeleportId() <  -10){
//            Debug.info("apply confirm", packet1.getTeleportId());
            if(mc.player != null){
                Debug.chat(Text.literal("[AC] 检测到反作弊回弹! tp号:" + packet1.getTeleportId()).formatted(Formatting.RED));
                //fixme : list possible reasons like sprinting in hungry
            }
        }

    }
    public static void setupAutoResync(Vec3d pos, int timeoutTick){
        autoResyncTillTick = Tasks.getTick() + timeoutTick;
        AUTO_RESYNC_POS = pos;
    }
    public static long autoResyncTillTick = 0;
    public static Vec3d AUTO_RESYNC_POS;
    public static Vec3d LAST_RESYNC_POS;
    //need test
    public static int MAXINUM_TP_ID = 0;
    private static final Config.FlagRef LOG_RESYNC = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_LOG_RESYNC_PACKETS);
    private static boolean listenPositionResync(PlayerPositionLookS2CPacket packet1){
      //  if(packet1.getTeleportId() > 1){
        //most probably the packet is from vanilla if the tp id is positive
        //todo need test
        MAXINUM_TP_ID = Math.max(packet1.getTeleportId(), MAXINUM_TP_ID);
        //debug:
        //fix: log before player enter
        if(LOG_RESYNC.get()){
            Debug.chat("Pos Resync", ChatUtils.getDisplayedLocationDouble(packet1.getX(), packet1.getY(), packet1.getZ()));
        }
        if(mc.player != null){
            LAST_RESYNC_POS = mc.player.getPos();

            //execute auto resync
            if(autoResyncTillTick > Tasks.getTick() && AUTO_RESYNC_POS != null){
                //auto resync
                Vec3d resyncPos = new Vec3d(packet1.getX(), packet1.getY(), packet1.getZ());
                double sqdistance = resyncPos.squaredDistanceTo(mc.player.getPos());
                double sqdistance2 = resyncPos.squaredDistanceTo(AUTO_RESYNC_POS);
                if(sqdistance > 1E-4 && sqdistance < MathUtils.s2(128) && sqdistance2 > 1E-4 && sqdistance2 < MathUtils.s2(128)){
                    //don't so far, it may be a real teleport
                    Debug.chat("Auto Resync triggered!");
                    mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet1.getTeleportId()));
                    mc.player.setPosition(resyncPos);
//                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                    executeTp(AUTO_RESYNC_POS, 180, false, true);
                    autoResyncTillTick = -1;
                    AUTO_RESYNC_POS = null;
                    return false;
                }
            }
        }

        return true;



        //}
    }
    private static boolean fixPositionSetBackFallDamage(Event<Packet<?>> packet){
        if(packet.context() instanceof PlayerPositionLookS2CPacket setBackPackets){
            //real setback , not a tp
            Vec3d target = new Vec3d(setBackPackets.getX(), setBackPackets.getY(), setBackPackets.getZ());
            //len < 200, may be the set back of a single movement
            double lenSqr = mc.player.getPos().squaredDistanceTo(target);
            // len > 3, not be setback packets of anticheat
            if (lenSqr < 60000 && lenSqr > 10) {
                double deltaY = target.y -  mc.player.getY();
                if(deltaY < 0  && Math.abs(deltaY) > mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE)){
                    //fix setback packets cause fallDistance
                    mc.player.setOnGround(false);
                    ClientPlayerAccess.of( mc.player).setForceNoFall(true);
                }
            }
        }
        return true;
    }

    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_0 = new LegalMovementManager.ModifierPipeline(0){
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
    //This pipeline will modify rot not pos
    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_ROT = new LegalMovementManager.ModifierPipeline(1_000_000){
        @Override
        public int priority() {
            return 1_000_000;
        }

        @Override
        public boolean mayModifyPos() {
            return false;
        }
    };
    //This pipeline will modify pos not rot
    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_POS = new LegalMovementManager.ModifierPipeline(1_000){
        @Override
        public int priority() {
            return 1_000;
        }

        @Override
        public boolean mayModifyRotation() {
            return false;
        }
    };
    //copied from BMW
    public static final Config.FlagRef MOVEMENT_CORRECTION = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_LEGAL_MODE_MOVE_CORRECTION);

    public static void onVelocityUpdatePlayer(Event<Vec3d> event){
        if(event.isCancelled())return;
//        ClientPlayerAccess access = ClientPlayerAccess.of(mc.player);
//        if(access.getLegalMovementManager().yawModified()){
//            event.context(EntityUtils.movementInputToVelocity())
//        }
    }


    //FIX: do not use falldistance as flag anymore

//    public static final float FORCE_RESET_DISTANCE = 13495702F;
    private static final Config.FlagRef noFall = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_NOFALL);
    private static final Config.EnumRef<Configs.BypassMode> noFallMode = Configs.MOV_CONFIG.getEnum(Configs.MOVE_NOFALL_MODE);
    public static boolean noFallSetbackResponse = false;
    public static boolean nofallWaitSetbackFlag;
    private static final Config.FlagRef setBackDisableVelocity = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_DISABLE_SETBACK_VELOCITY_RESET);
    public static void configurateTeleportBackVelocityUpdate(Event<Vec3d> vcUpdate){
        if(setBackDisableVelocity.get()){
            vcUpdate.cancel();
            return;
        }
    }
    //avoid player
    public static void onSetBackResponseAction(Event<MovInfo> event){
        nofallWaitSetbackFlag = false;
        if(noFallMode.getValue() == Configs.BypassMode.NO_BYPASS && (event.context.oGroundOverride == null || noFallSetbackResponse != (boolean)event.context.oGroundOverride)){
            var info = event.context();
            event.context(new MovInfo(info.vec3d, noFallSetbackResponse, false, info.rotationOverride));
        }
        noFallSetbackResponse = false;
    }
//    private boolean canStartSprinting(ClientPlayerEntity player) {
//        return !player.isSprinting() && player.isWalking() && this.canSprint() && !this.isUsingItem() && !this.hasStatusEffect(StatusEffects.BLINDNESS) && (!this.hasVehicle() || this.canVehicleSprint(this.getVehicle())) && !this.isFallFlying();
//    }
    private static LegalMovementManager.MovementModifier configureNoFall(){
        return new LegalMovementManager.MovementModifier() {
            double lastOnGroundHeight = Integer.MIN_VALUE;
            double lastHeight;
            int counter = 0;
            boolean holdingMace  = false;
            boolean runningThisTick = false;
            @Override
            public int priority() {
                //lower than tp mask ,should run like "background tasks"
                return 1;
            }
            @Override
            public boolean mayModifyPos() {
                //it will not modify pos in default mode
                //if it is configurated to be a legal mode or something, then it may need a modify
                return false;
            }
            boolean canDoJump = false;
            boolean doJump = false;

            int waitTimeout = 0;

            public void preTick(Event<LegalMovementManager> movementManagerEvent){

            }
            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                //filter creative playerGaming
                if(mc.player.getAbilities().invulnerable){
                    return;
                }
                holdingMace = args.getMainHandStack().getItem() instanceof MaceItem;
                Vec3d pos = args.getPos();
                if(pos == null)return;
                if(canDoJump){
//                    mc.player.addVelocityInternal(new Vec3d(0, 8, 0));
                    if(!nofallWaitSetbackFlag){
                        //a nofall packet comes
                        doJump = true;
                        canDoJump =false;
//                        Debug.info("trigger jump tick");
                        mc.player.setOnGround(true);
                        //TODO 1.21.2+ may need this, check code then
                        mc.options.jumpKey.setPressed(true);
//                        Vec3d vc = mc.player.getVelocity();
//                        mc.player.setVelocity(vc.x, 0.1, vc.z);
                    }else{
                        //should not send onGround
                        //do not send pos
                        waitTimeout += 1;
                        if(waitTimeout >= 2){
                            waitTimeout = 0;
                            canDoJump = false;
                            nofallWaitSetbackFlag = false;
                            mc.player.setOnGround(true);
                        }else{
                            mc.player.setOnGround(false);
                        }

                    }

                }
//                Vec3d pos2 = args.getVelocity();
//                if(pos2 == null)return;
//                if(pos2.y < -0.67){
//                    args.setVelocity(pos2.x, -0.67, pos2.z);
//                }
//                if(doJump){
//                    args.setOnGround(true);
//                }
                boolean forceNoFall = ClientPlayerAccess.of( mc.player).isForceNoFall();
                if((!holdingMace && noFall.get()) || forceNoFall){
                    lastHeight = args.getY();

                    // lastOnGround = args.isOnGround();
                    double safeDistance = args.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) ;

                    if( forceNoFall ||  lastHeight <= lastOnGroundHeight -  safeDistance ){
                        //this is a signal from other functional

                        var bypassMode = noFallMode.getValue();

                        if(bypassMode == Configs.BypassMode.NO_BYPASS){
                            //TODO Optimize this calculation NO_BYPASS
                            runningThisTick = true;

                            counter = 0;
                            lastOnGroundHeight = args.getY();

                            args.setPosition(args.getPos().add(0, + 1E-8, 0));
                            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(args.getX(), args.getY() , args.getZ(), !forceNoFall && args.isOnGround()));
                            noFallSetbackResponse = true;


                        }else if(bypassMode == Configs.BypassMode.BYPASS_GRIM){
                            if(!args.isOnGround()){
                                runningThisTick = true;
                            }
                            //resync lastOnGroundHeigth in this method
                            counter = 0;
                        }
                        //todo implement other mode
                        if(forceNoFall){
                            ClientPlayerAccess.of(args).setForceNoFall(false);
                        }
                        //args.setOnGround(true);
                    }else if(lastHeight > lastOnGroundHeight){
                        lastOnGroundHeight = lastHeight;
                        counter = 0;
                    }
                    else if(args.isOnGround()){
                        //todo check if this is at risk
                        if(noFallMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
                            lastOnGroundHeight = lastHeight;
                        }
                    }
                    else {
                        counter ++;
                    }
                    if(counter > 100){
                        //whatever , reset this flag
                        noFallSetbackResponse = false;
                    }
                }
            }

            @Override
            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                if(canDoJump){
                    //wait for set back packets to do jump
                    movementManagerEvent.cancel();
                    //restore pos
                    movementManagerEvent.context.playerStatus.restorePos();
                    return;
                }
                if(noFallMode.getValue() == Configs.BypassMode.BYPASS_GRIM && runningThisTick){
//                    movementManagerEvent.cancel();
//                    movementManagerEvent.context().playerStatus.restorePos();
                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
//                    if(waitingForSetback && waitForSetbackId == waitForSetBack){
//                        waitTimeout += 1;
//                        if(waitTimeout >= 5){
//                            waitingForSetback = false;
//                            player.fallDistance = 0.0f;
//                            lastOnGroundHeight = player.getY();
//                            return;
//                        }else{
//                            movementManagerEvent.cancel();
//                            movementManagerEvent.context.playerStatus.restorePos();
//                            return;
//                        }
//                    }
                    if(player.isOnGround()){
                        //onGround
                        //collide on ground should be
                        runningThisTick = true;

//                        player.setPos(player.getX(), player.getY() + 5E-2, player.getZ());
//                        Debug.info(player.getVelocity());
//                        player.setPos(player.getX(), player.getY() + 1E-8, player.getZ());

//                        player.setOnGround(false);
                        //cancel , do not restore pos
                        movementManagerEvent.cancel();
//                        movementManagerEvent.context.playerStatus.restorePos();
//                        player.setPosition(player.getX(), player.getY() + 1E-8, player.getZ());
                        //** must be OnGroundOnly(true) **
                        //在grimac的预测中, 当前状态应该即将着地, 若使用Onground = false 则会触发onGround不匹配
                        //最终结果:
                        //client:  onGround(true)  jump() .............(............(............(  client resync on ground
                        //                    |       |
                        //grimac:   predict at ground, (accepted predict) let client resync to ground /    accept resync tp
                        //                    x       √                √             v                     √
                        //server   do not reset     reset fall distance           ...............................
                        //为什么是onground = true
                        //grim的不同setback模式
                        //onground = true会导致resync = true, simulate = true
                        //对方将resync packets传输到咱们这里 是(a, b + 1E-7, c, false)
                        //咱们设置为了false
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.459339812018335 78.59016863897678 28.941366735922244 false
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.459339812018335 75.46476221959249 28.941366735922244 false
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move OnGroundOnly 0.0 0.0 0.0 false
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) trigger jump tick
//[04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.459339812018335 74.41999998688698 28.941366735922244 false
//                            [04:06:06] [Netty Client IO #8/INFO] (Minecraft) [CHAT] Pos Resync [51.46,74.00,28.94]
//[04:06:06] [Render thread/INFO] (Minecraft) [CHAT] Grim » matl114 触发了 GroundSpoof (x2) claimed false
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending packet TeleportConfirmC2SPacket
//[04:06:06] [Render thread/INFO] (Minecraft) [CHAT] [anti-grim] 检测到反作弊回弹! tp号:-1034761362
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move Full 51.459339812018335 74.0000001 28.941366735922244 false
//                            [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] Grim » matl114 触发了 Simulation (x2) .420000 /gl 82 <-这里 他认为我们是从75.46476221959249 移动到74.0000001, 这是不合法的
//                            [04:06:06] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.459339812018335 74.0000001 28.941366735922244 true
//                            [04:06:06] [Render thread/INFO] (Minecraft) [CHAT] matl114从高处摔了下来
                        //正常情况是
//                        [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 84.79200176125546 29.17552569387324 false
//                            [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 81.69935880076793 29.17552569387324 false
//                            [04:08:01] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 78.59016863897678 29.17552569387324 false
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 75.46476221959249 29.17552569387324 false
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move OnGroundOnly 0.0 0.0 0.0 true
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) trigger jump tick
                            //[04:08:02] [Netty Client IO #8/INFO] (Minecraft) [CHAT] Pos Resync [51.05,74.00,29.18]
                            //[04:08:02] [Render thread/INFO] (SlimefunHelper) sending packet TeleportConfirmC2SPacket
                            //[04:08:02] [Render thread/INFO] (Minecraft) [CHAT] [anti-grim] 检测到反作弊回弹! tp号:-2093209308
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move Full 51.04938473524123 74.0000001 29.17552569387324 false
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 74.42000008688697 29.17552569387324 false
                //        <-这里 他认为我们是从75.46476221959249 移动到74.0000001, 但是 由于上面触发的非常巧妙,是resync packets, 这里的运动偏差会被直接无视
                        //<- 同时 这里的movement会被认为是knockback， 可以通过后续的resync，？？？？？？？？？
                        //todo: 需要进一步查看 这也太离谱了

//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 74.7532000805212 29.17552569387324 false
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 75.00133607911214 29.17552569387324 false
//                            [04:08:02] [Render thread/INFO] (SlimefunHelper) sending move PositionAndOnGround 51.04938473524123 75.16610936093821 29.17552569387324 false
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                        //包吃住 不要过
                        noFallSetbackResponse = false;
                        ClientPlayerAccess.of(player).setForceNoFall(false);
                        lastOnGroundHeight = player.getY();

                        nofallWaitSetbackFlag = true;
                        canDoJump = true;
                        waitTimeout = 0;
                        runningThisTick = false;
//                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(player.getX(), player.getY(), player.getZ(),false));


                    }else {
                        runningThisTick = false;
                    }
                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                //skip and kept working

                if(canDoJump)return true;
                ClientPlayerEntity player = movementManagerEvent.context().playerStatus.entity;
                if(doJump){

                    mc.options.jumpKey.setPressed(false);
                    doJump = false;
                    player.setOnGround(false);
                }

                runningThisTick = false;

                return true;
            }
        };
//        EntityAccess.of(entity).addTickWrapper(new ProgressWrapper<ClientPlayerEntity>() {
//
//            @Override
//            public void preProgress(ClientPlayerEntity args) {
//
//            }
//
//            @Override
//            public void postProgress(ClientPlayerEntity args) {
//                //Debug.info("current : ", args.isOnGround());
//
//            }
//
//            @Override
//            public boolean stillWrap(ClientPlayerEntity args) {
//                return true;
//            }
//        });
    }
    private static final Config.FlagRef enhanceStepheight = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_ENHANCED_STEPHEIGHT);
    //todo add trigger condition
    private static Runnable jumpTriggerStepHeight;
    private static void listenJump(Event<Integer> jumpEvent){
        if(jumpTriggerStepHeight != null){
            jumpTriggerStepHeight.run();
        }
    }

    private static LegalMovementManager.MovementModifier configureEnhancedStepheight(){
        return new LegalMovementManager.MovementModifier() {
            @Override
            public int priority() {
                return 0;
            }
            public void triggerJump(){
                if(!enhanceStepheight.get()){
                    return;
                }
                double jumpStrength = LivingEntityAccess.of(mc.player).getJumpUpwardSpeed(1.0F);
                double gravity = mc.player.getFinalGravity();
                int ticksNeeded = (int) ((jumpStrength - 1E-5) / gravity);
                ticksEnd = ticksNeeded + 1;
                toggleRunning = true;
                runTicks = 0;
            }
            {
                jumpTriggerStepHeight = this::triggerJump;
            }

            boolean toggleRunning;
            int runTicks ;
            double y;
            int ticksEnd = 6;

            @Override
            public boolean mayModifyPos() {
                return enhanceStepheight.get() && runTicks >= ticksEnd - 1;
            }

            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
//                Vec3d vec0 = movementManagerEvent.context.playerStatus.vec;
//                if(vec0.getY() > 0){
//                    Debug.info(vec0);
//                }
                if(enhanceStepheight.get()){
//                    if(!toggleRunning){
//                        if(mc.options.jumpKey.isPressed()){
//                            //fixme : double jump not work
//                            toggleRunning = true;
//                            runTicks = 0;
////                            y = movementManagerEvent.context.playerStatus.entity.getY();
//                        }
//                    }
                    if(toggleRunning){

                        if(runTicks < 0){
                            runTicks = - 114514;
//                            Debug.info("check out");
//                            mc.options.jumpKey.setPressed(true);
                            //In case setback packets set false
                            movementManagerEvent.context.playerStatus.entity.setOnGround(true);
//                            Vec3d vec = movementManagerEvent.context.playerStatus.entity.getVelocity();
//                            movementManagerEvent.context.playerStatus.entity.addVelocityInternal(new Vec3d(0, 0.4,0));
                            //
                           // LivingEntityAccess.of(movementManagerEvent.context.playerStatus.entity).setJumpingCooldown(0);
                        }
                    }
                }


            }

            @Override
            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
//                Vec3d vec3 = movementManagerEvent.context.playerStatus.entity.getPos();
////                if(vec3.getY() != 0){
////                    Debug.info(vec3);
////                }
//                if(vec3.getY() > 86){
//                    Debug.info(vec3);
//                }

                if(enhanceStepheight.get()){
                    if(toggleRunning ){
                        //5刻后达到最高点
                        runTicks += 1;
                        if(runTicks >= ticksEnd){

                              ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                            if(!player.isOnGround()){
                                Vec3d vec3d2 = new Vec3d((double)player.input.movementSideways, 0.0, (double)player.input.movementForward);
                                Vec3d vec3d0 = player.getVelocity();
                                Vec3d movement = //new Vec3d(vec3d0.x, 0, vec3d0.z)
                                    vec3d0.add( EntityUtils.movementInputToVelocity(vec3d2, player.getMovementSpeed(), player.getYaw()));
                                //important simulation
                                player.setOnGround(true);
                                boolean useStepHeightFeature = doMovementInvolveStepheight(player, movement);
                                if(useStepHeightFeature){
                                    //Debug.chat("pass stepheight");
                                    runTicks = -200;
                                    movementManagerEvent.cancel();
//                        movementManagerEvent.context.playerStatus.restorePos();
                                    //fixme: optimize this check
                                    movementManagerEvent.context.playerStatus.entity.setOnGround(true);
                                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                                    return;
                                }player.setOnGround(false);
                            }

                                runTicks = -11451;



                        }
                        //mc.options.jumpKey.setPressed(false);
                    }
                }

            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                if(enhanceStepheight.get()){
                    if(runTicks < -1145){
//                        if(runTicks == -114514){
//                            mc.options.jumpKey.setPressed(false);
//                        }
                        runTicks = 0;
                        toggleRunning = false;
                    }
                }

                return true;
            }
        };
    }
    //This pipeline will not modify pos or rot

    //TODO I believe we can gain more advantage from grimac


    public static void configurePipelinesForPlayer(ClientPlayerEntity player){
        var legalMovement = ClientPlayerAccess.of(player).getLegalMovementManager();
        PLAYER_PIPELINE_0.resetForNewPlayer();
        legalMovement.addMovementModifier(PLAYER_PIPELINE_0);
        PLAYER_PIPELINE_ROT.resetForNewPlayer();
        legalMovement.addMovementModifier(PLAYER_PIPELINE_ROT);
        PLAYER_PIPELINE_POS.resetForNewPlayer();
        legalMovement.addMovementModifier(PLAYER_PIPELINE_POS);
    }


    private static final Config.FlagRef legalSprint = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_AUTO_TOGGLE_SPRINT);
    private static final Config.FlagRef sprintDirectional = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_ALL_DIRECTION_SPRINT);
    private static final Config.EnumRef<Configs.BypassMode> sprintBypassMode = Configs.MOV_CONFIG.getEnum(Configs.MOVE_SPRINT_BYPASS_MODE);
    public static boolean enableSprintDirectionalThisTick = false;
    //todo add pitchyaw pipeline-- do it later
    //check if we can speedup using moveFoward+sideway
    //no use: sidewaywalk no faster than foward, but jump-sprint much faster
    private static LegalMovementManager.MovementModifier configureLegalDirectionalSprint(){
        //todo test
       return new LegalMovementManager.MovementModifier() {
            boolean workRotationThisTick;
            @Override
            public int priority() {
                //the least important shit
                return 10000000;
            }

            @Override
            public boolean mayModifyRotation() {
                return sprintDirectional.get() && !mc.player.input.hasForwardMovement() && sprintBypassMode.getValue() != Configs.BypassMode.NO_BYPASS;
            }

            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
//                if(player.getVelocity().horizontalLength() > 0.05)
//                Debug.info("check vc", player.getVelocity().horizontalLength());

               // Debug.info(player.input.movementForward);
                if(sprintDirectional.get() && (player.input.movementForward < -1E-5) && !(player.isTouchingWater() && !player.isSubmergedInWater()) && !(player.horizontalCollision && !player.collidedSoftly)){
                    //give the ticket
                    enableSprintDirectionalThisTick = true;

                    //}

                }
            }

            @Override
            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                if(sprintDirectional.get() && (player.input.movementForward < -0.05) && player.isSprinting()){

                    if(sprintBypassMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
                        workRotationThisTick = true;
//                            float yaw = EntityUtils.rotationToYaw(walkingWay);
//                        Debug.info(walkingWay);
//                        Debug.info(yaw);
                        //turn around to bypass ,movingAround

                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    }

                    //}

                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                enableSprintDirectionalThisTick = false;
                if(!enabledThisTick)return true;
                if(workRotationThisTick){

                    movementManagerEvent.context.playerStatus.restoreRotation();
                }
                return true;
            }
        };
    }
    private static final Config.FlagRef fakeSprint =Configs.TEST_CONFIG.getBoolean(Configs.FAKE_SPRINT_TEST);
    private static LegalMovementManager.MovementModifier configureFakeSprint(){
        return new LegalMovementManager.MovementModifier() {
            @Override
            public int priority() {
                //the least important shit
                return 10000001;
            }


            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {

            }

            @Override
            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                if(fakeSprint.get() && mc.player.isSprinting()){
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                if(fakeSprint.get() && mc.player.isSprinting()){
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
                return true;
            }
        };
    }

    private static void runToggleLegalSprint(){
        if(legalSprint.get() && mc.currentScreen == null){
            if(!mc.options.sprintKey.isPressed()){
                Debug.chat("[Legal Sprint] toggle sprint on (ctrl)");
                mc.options.sprintKey.setPressed(true);
            }
        }
    }
    //fake elytra flight figure it out: NO USE, server player pose will not change
    //elytra unbreakable?

    public static boolean runElytraUnbreakable(Entity player){
        if(player == mc.player && elytraUnbreakable.get()){
            fakeGlideTime += 1;
            fakeGlidePoseTime += 1;
            if(mc.player != null && mc.player.isFallFlying()){
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
//                Debug.info("send stop glide");
            }
            Tasks.scheduleDelayed(()->{
                if(mc.player != null && mc.player.isFallFlying() && !mc.player.isOnGround()){
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
//                    Debug.info("send restart glide");
                }else{
//                    Debug.info("not glide anymore");
                }
            }, 3);

            return true;
        }
        return false;
    }
    private static int fakeGlideTime = 0;
    private static int fakeGlidePoseTime = 0;
    @Unique
    private static final Config.FlagRef elytraUnbreakable = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_UNBREAKABLE_ELYTRA);
    //may cause fake gliding !!! must be careful
    public static void handleEntityDataUpdate(Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject){
        if(serializedEntryMutableObject.isCancelled())return;
        //only when elytra unbreakable do
        if(elytraUnbreakable.get() && serializedEntryMutableObject.extraArgs().length > 0 && serializedEntryMutableObject.extraArgs()[0] instanceof ClientPlayerEntity player && player == mc.player && player.isFallFlying() && !player.isOnGround() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)){
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            //do all the checks to avoid ghost gliding
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                var val = serializedEntryMutableObject.context();

                if(val.id() == 0){
                    byte data = (byte) val.value();
                    if( (data & (1 << 7 )) == 0){
//                        Debug.info("[data]stop gliding");
                        if(fakeGlideTime > 0){
                            fakeGlideTime -= 1;
                            //cancel stop fallflying
                            // ;
                            serializedEntryMutableObject.context(new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)(data | (1 << 7))));
                        }

                    }else{
//                        Debug.info("[data]start gliding");
                    }
                }else if(val.id() == 6){
                    //standing pose
                    EntityPose pose = (EntityPose) val.value();
                    if(fakeGlidePoseTime >0 && pose != EntityPose.FALL_FLYING && player.getPose() == EntityPose.FALL_FLYING){
                        //cancel pose sync
                        fakeGlidePoseTime -= 1;

                    }
                }
            }

        }
    }
    //TODO: add speed boat, or stable boat(do not slip on ice
//    public static boolean handleFakeGlide(EntityTrackerUpdateS2CPacket trackerUpdateS2CPacket){
//        if(fakeGlide && mc.player != null && trackerUpdateS2CPacket.id() == mc.player.getId() && mc.player.isFallFlying()){
//            for (var track : trackerUpdateS2CPacket.trackedValues()){
//                if(track.id() == 0){
//                    byte data = (byte) track.value();
//                    if( (data & (1 << 7 )) == 0){
//                        //cancel stop fallflying
//                        return false;
//                    }
//                    //glide
//                }
//            }
//        }
//        return true;
//    }
    //    @Unique
//    private static final Config.FlagRef overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    static{
        //basic structure
        Listener.getPlayerInitConfiguration().registerHandler(MovTasks::configurePipelinesForPlayer);
        //creative fly bad packets
        Listener.registerSinglePacketListener(UpdatePlayerAbilitiesC2SPacket.class, MovTasks::onPacketFly);
        //block server ability resync about flying
        Listener.registerSinglePacketListener(PlayerAbilitiesS2CPacket.class, MovTasks::onPacketFlyToggle);
        //force set ability flight
        PLAYER_PIPELINE_0.addMovementModifierFactory(MovTasks::configureCreativeFlyAbility);
        PLAYER_PIPELINE_0.addMovementModifierFactory(MovTasks::configureFakeSprint);

        //watch setback packets
        Listener.registerSinglePacketListener(TeleportConfirmC2SPacket.class, MovTasks::listenAntiCheatSetBack);

        //teleport management
        Listener.getPlayerInitConfiguration().registerHandler(MovTasks::configureTpMaskPlayer);

        //nofall
        Listener.getTeleportConfirmResponsePoint().registerHandler(MovTasks::onSetBackResponseAction);
        PLAYER_PIPELINE_POS.addMovementModifierFactory(MovTasks::configureNoFall);
        //sprint
        PLAYER_PIPELINE_ROT.addMovementModifierFactory(MovTasks::configureLegalDirectionalSprint);
        //stepheight
        PLAYER_PIPELINE_POS.addMovementModifierFactory(MovTasks::configureEnhancedStepheight);
        Listener.getPlayerNotFlyJumpPoint().registerHandler(MovTasks::listenJump);
        //setback function
        Listener.getTeleportConfirmVelocityUpdatePoint().registerHandler(MovTasks::configurateTeleportBackVelocityUpdate);

        Listener.registerSinglePacketListener(PlayerPositionLookS2CPacket.class, MovTasks::listenPositionResync);
        Listener.registerSinglePacketListener(PlayerMoveC2SPacket.class, MovTasks::doIntercepteMovingPacketsWhileTp);
        Listener.getClientPlayerSendMovementPoint().registerHandler(MovTasks::doStopPlayerSendMovementPackets);
        Tasks.registerTickTask(MovTasks::runToggleLegalSprint);
    //        Listener.registerSinglePacketListener(EntityTrackerUpdateS2CPacket.class, MovTasks::handleFakeGlide);
        Listener.getEntityTrackDataUpdate().registerHandler(MovTasks::handleEntityDataUpdate);
        Listener.getMainThreadPacketPreApplyPoint().registerHandler(MovTasks::fixPositionSetBackFallDamage);
    }

}
