package me.matl114.hackUtils;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.SlimefunHelper;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.access.EntityAccess;
import me.matl114.access.KeyBindAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

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
        //bugfix when shift click in screen, this key is reset to fall
        if(mc.currentScreen == null){

            KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
            KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
        }

    }

    private static final AtomicBoolean overrideFly = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_FLY);
    private static final AtomicBoolean overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    private static boolean serverPacketAllowFlight = false;
    public static void onMoving(ClientPlayerEntity player){
        if( HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
            if( !player.getAbilities().allowFlying ){
                player.getAbilities().allowFlying = true;
            }
            antiKick(player);
        }else {
            player.getAbilities().allowFlying = serverPacketAllowFlight;
        }
    }


    public static Optional<BlockPos> rayTraceSpecificBlock(Predicate<Block> blockPredicate){
        if(mc.world == null || mc.player == null)return Optional.empty();
        if(mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK){
            BlockHitResult blockHitResult = (BlockHitResult)mc.crosshairTarget;
            Block block = mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
            if(blockPredicate.test(block)){
                return Optional.of(blockHitResult.getBlockPos());
            }
        }
        Vec3d lookat = mc.player.getRotationVector().normalize().multiply(distance);
        Vec3d cameraPose = mc.player.getCameraPosVec(1.0f);
        BlockPos.Mutable mutable = new BlockPos.Mutable(cameraPose.x, cameraPose.y, cameraPose.z);
        for (int i= 0; i< 75; ++i){
            int x = (int) (lookat.x *i + cameraPose.x );
            int y = (int) (lookat.y *i + cameraPose.y );
            int z = (int) (lookat.z *i + cameraPose.z );
            if(x != mutable.getX() || y != mutable.getY() || z != mutable.getZ()){
                mutable.set(x,y,z);
                BlockPos pos = mutable.toImmutable();
                Block block = mc.world.getBlockState(pos).getBlock();
                if(blockPredicate.test(block)){
                    return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }

    public static BlockHitResult createHitResult(BlockPos pos){
        if(mc.player == null)return null;
        Vec3d startVec = mc.player.getCameraPosVec(1.0f);
        Vec3d endVec = pos.toCenterPos();
        Vec3d ray = startVec.subtract(endVec);
        Direction dir = Direction.getFacing(ray.x, ray.y, ray.z);
        Vec3d crossTargetPose =  ray.lengthSquared() > 0.25 ?
            switch (dir){
                case DOWN -> startVec.subtract(ray.multiply((startVec.y - (endVec.y - 0.5))/ray.y ));
                case UP -> startVec.subtract(ray.multiply((startVec.y - (endVec.y + 0.5))/ray.y ));
                case NORTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z - 0.5))/ray.z ));
                case SOUTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z + 0.5))/ray.z ));
                case WEST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x - 0.5))/ray.x ));
                case EAST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x + 0.5))/ray.x ));
        }: endVec;
        return new BlockHitResult(crossTargetPose, dir, pos, false);
    }

    public static boolean fakeFlightEnabled = false;

    public void onPlayerUpdate(Entity entity){
        if(mc.player == entity){
            onMCPlayerMovement();
        }
    }
    public void onMCPlayerMovement(){
        if(fakeFlightEnabled){

        }
    }

    public static void onFakeFlightEnabled(){
        fakeFlightEnabled = true;
        if(false)
            EntityAccess.of(mc.player).addTickWrapper(
            new ProgressWrapper<ClientPlayerEntity>() {
                BlockPos pos;
                BlockState cacheBlockState;
                @Override
                public void preProgress(ClientPlayerEntity args) {
                    //略微减去一点 保证指向他脚下的方块
                    if(Screen.hasShiftDown()){
                        cacheBlockState = null;
                        return;
                    }
                    pos = BlockPos.ofFloored(args.getPos().add(0,-1 + 1e-4,0));
                    BlockState blockState = mc.world.getBlockState(pos);
                    if(blockState.isAir()){
                         cacheBlockState = blockState;
                         mc.world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
                    }
                }

                @Override
                public void postProgress(ClientPlayerEntity args) {
                    if(cacheBlockState != null){
                        mc.world.setBlockState(pos, cacheBlockState);
                        cacheBlockState = null;
                    }
                }

                @Override
                public boolean stillWrap(ClientPlayerEntity args) {
                    return HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FAKE_FLIGHT);
                }
            }
        );
    }
    private static final AtomicBoolean shouldCheckSetback = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_CHECK_SETBACK);
    private static void listenAntiCheatSetBack(TeleportConfirmC2SPacket packet1){
        //WHEN IN hack version, detect grimac backteleport with negative random teleport id
        if(SlimefunHelper.HACK_VERSION && shouldCheckSetback.get() &&  packet1.getTeleportId() <  -10){
//            Debug.info("apply confirm", packet1.getTeleportId());
            if(mc.player != null){
                Debug.chat(Text.literal("[anti-grim] 检测到反作弊回弹! tp号:" + packet1.getTeleportId()).formatted(Formatting.RED));
            }
        }
    }

    private static final AtomicBoolean noFall = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_NOFALL);

    private static void configurateNoFall(ClientPlayerEntity entity){
        ClientPlayerAccess.of(entity).addMovementPacketWrapper(new ProgressWrapper<ClientPlayerEntity>() {
            double lastOnGroundHeight = Integer.MIN_VALUE;
            double lastHeight;
            boolean lastOnGround = false;
            int counter = 0;
            boolean holdingMace  = false;
            @Override
            public void preProgress(ClientPlayerEntity args) {
                holdingMace = args.getMainHandStack().getItem() instanceof MaceItem;
                if(!holdingMace && noFall.get() ){
                    lastHeight = args.getY();
                    lastOnGround = args.isOnGround();
                    if(lastHeight < lastOnGroundHeight - 3.0f || counter > 10){
                        counter = 0;
                        lastOnGroundHeight = args.getY();
                        args.setOnGround(true);
                    }else if(lastHeight > lastOnGroundHeight){
                        lastOnGroundHeight = lastHeight;
                        counter = 0;
                    }else{
                        counter ++;
                    }
                }
            }

            @Override
            public void postProgress(ClientPlayerEntity args) {
                //Debug.info("current : ", args.isOnGround());
                if(!holdingMace && noFall.get()){
                    args.setOnGround(lastOnGround);
                }
            }

            @Override
            public boolean stillWrap(ClientPlayerEntity args) {
                return true;
            }
        });
    }

    //    @Unique
//    private static final AtomicBoolean overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    static{
//        Tasks.registerGameTask((player -> {
//
//        }));
        Listener.registerSinglePacketListener(PlayerAbilitiesS2CPacket.class, packet1 ->{

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
        });
        Listener.registerSinglePacketListener(TeleportConfirmC2SPacket.class, MovTasks::listenAntiCheatSetBack);
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
        Listener.getPlayerInitConfiguration().registerHandler(MovTasks::configurateNoFall);
    }

}
