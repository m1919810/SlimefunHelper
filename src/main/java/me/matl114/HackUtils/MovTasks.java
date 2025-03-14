package me.matl114.HackUtils;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.Access.KeyBindAccess;
import me.matl114.ListenerUtils.Listener;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class MovTasks {
    public static void init(){

    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final AtomicDouble maxDistance = Configs.MOV_CONFIG.getDouble(Configs.MOV_MAX_DISTANCE);
    private static final double distance = 0.1;
    public static boolean quickMovFront(){
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d vec3d = player.getPos();
        Vec3d lookat = player.getRotationVector().normalize().multiply(distance);
        Vec3d lastAvailablePos  = vec3d;
        boolean hasWall = false;
        boolean differentPos = false;
        for (double i = 0; i < maxDistance.get(); i += distance) {
            vec3d = vec3d.add(lookat);
//            BlockPos pos1= BlockPos.ofFloored(vec3d);
//            BlockPos pos2 = pos1.up();
            if(isPlayerPassable(vec3d)){
                differentPos = true;
                lastAvailablePos = vec3d;
                if(hasWall){
                    break;
                }else {
                    continue;
                }
            }else {
                hasWall = true;
            }
        }
        if(differentPos){
            move(lastAvailablePos, false);
            return true;
        }else {
            Debug.chat("no available position in front of you!");
            return false;
        }
    }
    public static boolean toggleSpeedOverride(){
        overrideFly.set(!overrideFly.get());
        return true;
    }
    private static void modifyVelocity(double x,double y,double z){
        applyVelocity(mc.player.getVelocity().add(x, y, z));
    }
    private static void applyVelocity(Vec3d vec3d){
        mc.player.setVelocity(vec3d);
        mc.player.setPosition(mc.player.getPos().add(vec3d));
        sendUpdatePositionPacket();
    }
    private static void move(Vec3d vec3d, boolean alignToBlock){
        if(alignToBlock){
            vec3d = BlockPos.ofFloored(vec3d).toCenterPos();
        }
        mc.player.setPosition(vec3d.x, vec3d.y , vec3d.z);
        sendUpdatePositionPacket();
    }
    private static void sendUpdatePositionPacket(){
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(),mc.player.getY(),mc.player.getZ(),true);
        mc.getNetworkHandler().sendPacket(packet);
    }
    private static boolean isPassMovement(){
        ClientPlayerInteractionManager manager = mc.interactionManager;
        return ignoreCheck.get() || manager.getCurrentGameMode().isCreative() || manager.getCurrentGameMode()== GameMode.SPECTATOR;
    }
    private static boolean checkBlockCollision(Vec3d  pos){
        //set position for bounding box update!
        mc.player.setPosition(pos);
        Iterator var3 = mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator();

        while(var3.hasNext()) {
            VoxelShape voxelShape = (VoxelShape)var3.next();
            if (!voxelShape.isEmpty()) {

                return false;
            }
        }
        return true;
    }
    private static final AtomicBoolean ignoreCheck = Configs.MOV_CONFIG.getBoolean(Configs.QUICK_MOVE_IGNORE_COLLISION);
    private static boolean isPlayerPassable(Vec3d pos){
        boolean passMoveTest = false;
        Vec3d vecpos = mc.player.getPos();
        Vec3d savedPosition = new Vec3d(vecpos.x, vecpos.y, vecpos.z);
        try{
            if(isPassMovement()){
                passMoveTest = true;
            }else {
                mc.player.move(MovementType.PLAYER, pos.subtract(savedPosition));
                if(mc.player.getPos().squaredDistanceTo(pos) < 0.0625){
                    passMoveTest = true;
                }
            }
            return passMoveTest && checkBlockCollision(pos);//!isPlayerCollidingWithAnythingNew(mc.world, mc.player.getBoundingBox(), pos.x, pos.y, pos.z) ;// mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox()))
        }finally {
            mc.player.setPosition(savedPosition);
        }

    }
//    private static boolean isPlayerCollidingWithAnythingNew(World world, Box box, double newX, double newY, double newZ) {
//        Box box2 = mc.player.getBoundingBox().offset(newX - mc.player.getX(), newY - mc.player.getY(), newZ - mc.player.getZ());
//        Iterable<VoxelShape> iterable = world.getCollisions(mc.player, box2.contract(9.999999747378752E-6));
//        VoxelShape voxelShape = VoxelShapes.cuboid(box.contract(9.999999747378752E-6));
//        Iterator var12 = iterable.iterator();
//
//        VoxelShape voxelShape2;
//        do {
//            if (!var12.hasNext()) {
//                return false;
//            }
//
//            voxelShape2 = (VoxelShape)var12.next();
//        } while(VoxelShapes.matchesAnywhere(voxelShape2, voxelShape, BooleanBiFunction.AND));
//
//        return true;
//    }
    //copied from wurst
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
        KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
        KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
    }

    private static final AtomicBoolean overrideFly = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_FLY);
    private static boolean serverPacketAllowFlight = false;
    public static void onMoving(ClientPlayerEntity player){
        if( HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
            if( !player.getAbilities().allowFlying ){
                player.getAbilities().allowFlying = true;
            }
            antiKick(player);
        }
    }
    //    @Unique
//    private static final AtomicBoolean overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    static{
//        Tasks.registerGameTask((player -> {
//
//        }));
        Listener.registerPacketListener(packet ->{
            if(packet instanceof PlayerAbilitiesS2CPacket packet1 ){
                //Debug.info("update ability",packet1);
                //keep ability\
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
                   // boolean isFly = mc.player.getAbilities().flying;
//                    Tasks.scheduleDelayed(()->{
//                        if(mc.player != null){
//                            mc.player.getAbilities().flying = isFly;
//                        }
//                    },1);

                return false;

            }
            return true;
        },true);
//        Listener.registerPacketListener(packet -> {
//            Debug.info("acc ?",packet.getClass().getSimpleName());
//            if(mc.world !=null && packet instanceof EntityS2CPacket pack && pack.getEntity(mc.world) == mc.player){
//                Debug.info("player " ,pack);
//                if(waitingForServerResponse ){
//                    waitingForServerResponse = false;
//                }
//            }
////            if(packet instanceof PlayerPositionLookS2CPacket confirmC2SPacket){
////
////                waitingForServerResponse = false;
////            }
//            return true;
//        },true);
    }

}
