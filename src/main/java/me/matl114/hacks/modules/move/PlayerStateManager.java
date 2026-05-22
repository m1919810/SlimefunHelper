package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class PlayerStateManager extends BaseModule {
    public static PlayerStateManager INSTANCE;
    double startFallingY;
    public double fallDistance;
    public double lastX;
    public double lastZ;
    public double lastY;
    public float lastPitch;
    public float lastYaw;
    public boolean lastOnGround;
    public boolean lastSprint;
    public Vec3d lastKnownMovementSpeed = Vec3d.ZERO;
    public Vec3d lastAverageMovementSpeed = Vec3d.ZERO;
    public Vec3d lastSetBackPosition = Vec3d.ZERO;
    boolean lastTickHasMovement = false;
    public boolean lastClimbing;
    public boolean lastInLava;
    public boolean lastInWater;
    public boolean lastInWeb;
    private boolean inWeb;
    public boolean lastInWall;
    public boolean serverSideCanFly;
    public Deque<Vec3d> last40Positions = new ArrayDeque<>();
    private static final int MAX_SIZE = 20;
    {
        for (int i= 0 ; i < MAX_SIZE; ++i) {
            last40Positions.add(Vec3d.ZERO);
        }
    }

    public PlayerStateManager() {
        INSTANCE = this;
    }


    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPostSendPoint().getChannel(PlayerMoveC2SPacket.class), this::onMove);
        registerListener(Listener.getPlayerWebSlowPoint(), this::handleInWeb);
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityAttackEvent);
        registerListener(Listener.getPacketPostSendPoint().getChannel(ClientCommandC2SPacket.class), this::onPlayerCommand);
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInitialize);
        registerListener(Listener.getPacketPostSendPoint().getChannel(ClientTickEndC2SPacket.class), this::onTickEnd);
    }

    public void onMove(Event<PlayerMoveC2SPacket> event){
        PlayerMoveC2SPacket packet = event.context;
        if(PlayerMoveC2SPacketAccess.of(packet).getCause() != PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION){
            // will not be intercepted by antiCheat
            Vec3d oldMove = new Vec3d(lastX, lastY, lastZ);

            if(!packet.changesPosition()){
                if(packet.isOnGround()){
                    handleOnGroundFlag();
                }
            }else {
                Vec3d vec3d = new Vec3d( packet.getX(lastX), packet.getY(lastY), packet.getZ(lastZ));
                if(!containsInvalidValues(vec3d.x, vec3d.y, vec3d.z)){
                    handleMove(vec3d, packet.isOnGround());
                }
            }
            lastOnGround = packet.isOnGround();
            if(packet.changesLook()){
                lastPitch = packet.getPitch(lastPitch);
                lastYaw = packet.getYaw(lastYaw);
            }
            lastKnownMovementSpeed = new Vec3d(lastX - oldMove.x, lastY - oldMove.y, lastZ - oldMove.z);
            lastTickHasMovement = true;
        }
    }

    public void onPlayerInitialize(Event<ClientPlayerEntity> event){
        onPlayerReset();
    }

    private static boolean containsInvalidValues(double x, double y, double z) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ;
    }

    public void handleY(double y, boolean onGround) {
        // handle water
        if(!mc.player.isTouchingWater()){
            if(mc.player.updateMovementInFluid(FluidTags.WATER, 0.014)){
                fallDistance = 0.0;
            }
        }else {
            fallDistance = 0.0;
        }
        if(lastY > y){
            if(!mc.player.isTouchingWater()){
                fallDistance += lastY - y;
            }
        }
        if(onGround){
            handleOnGroundFlag();
        }
        // handle reset
        if(lastY < y){
            startFallingY = y;
            fallDistance = 0;
        }
        handleFallDistanceEnvironmentCheck();
    }

    public void onLand(){

    }

    public void handleFallDistanceEnvironmentCheck(){
        if(fallDistance < 0){
            fallDistance = 0;
        }
        if(fallDistance > 0){
            // check water
        }
    }

    public void handleOnGroundFlag(){
        //fall on
        if(!lastOnGround){
            onLand();
            lastOnGround = true;
        }
        fallDistance = 0.0;
    }

    public void handleMove(Vec3d pos, boolean onGround){
        handleY(pos.getY(), onGround);
        lastY = pos.getY();
        lastX = pos.getX();
        lastZ = pos.getZ();
        lastOnGround = onGround;
    }

    public void handleInWeb(Event<Vec3d> vec3dEvent){
        fallDistance = 0.0;
        lastInWeb = true;
        inWeb = true;
    }

    public void onPreGameTick(Event<ClientPlayerEntity> event){
        handleTick();
    }

    public void handleTick(){
        // base flag ticks;
        lastInLava = mc.player.isInLava();
        lastInWater = mc.player.isTouchingWater();
        lastClimbing = mc.player.isClimbing();
        lastInWeb = inWeb;
        inWeb = false;
        lastInWall = MovTasks.isCollidingWithEnvironment(mc.player);
        //push vec3d
        Vec3d nowPos = new Vec3d(lastX, lastY, lastZ);
        last40Positions.addLast(nowPos);
        Vec3d last1MinPos = null;
        while (last40Positions.size() > MAX_SIZE){
            last1MinPos = last40Positions.removeFirst();
        }
        if(last1MinPos != null){
            lastAverageMovementSpeed = nowPos.subtract(last1MinPos).multiply(1D/MAX_SIZE);
        }

        // falldistance tick
        if(lastInLava){
            fallDistance *= 0.5;
        }
        if(lastInWater){
            fallDistance = 0.0;
        }
        if(mc.player.isRiding()){
            fallDistance = 0.0;
        }
        if(mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) || mc.player.hasStatusEffect(StatusEffects.LEVITATION)){
            fallDistance = 0.0;
        }
        if(lastClimbing){
            fallDistance = 0.0;
        }
    }

    public void onEntityAttackEvent(Event<EntityDamageS2CPacket> eventS2C){
        if(mc.player != null && eventS2C.context.sourceCauseId() == mc.player.getId()){
            //me attack them
            var source = eventS2C.context.sourceType().getKey().orElse(null);
            if(Objects.equals(source, DamageTypes.MACE_SMASH)){
                //we trigger a mace smash
                handleMaceSmash();
            }
            if(Objects.equals(source, DamageTypes.ENDER_PEARL)){
                handlePearlTeleport();
            }
        }
    }

    public void onPlayerCommand(Event<ClientCommandC2SPacket> event){
        switch (event.context.getMode()){
            case START_SPRINTING -> {lastSprint = true;}
            case STOP_SPRINTING -> {lastSprint = false;}
        }
    }


    public void handleMaceSmash(){
        if(fallDistance > 1.5){
            fallDistance = 0;
        }
    }

    public void handlePearlTeleport(){
        fallDistance = 0;
    }

    public void onPlayerReset(){
        startFallingY = Double.MIN_VALUE;
        fallDistance = 0;
        lastKnownMovementSpeed = new Vec3d(0, 0, 0);
        lastAverageMovementSpeed = new Vec3d(0, 0, 0);
        lastSetBackPosition = new Vec3d(0, 0, 0);
        last40Positions.clear();
        for (int i= 0 ; i < MAX_SIZE; ++i) {
            last40Positions.add(Vec3d.ZERO);
        }
    }

    public void onTickEnd(Event<ClientTickEndC2SPacket> tickEndPacket){
        if(!lastTickHasMovement){
            lastKnownMovementSpeed = Vec3d.ZERO;
        }
        lastTickHasMovement = false;
    }

    // api methods

    public boolean isRotationDifferent() {
        return EntityUtils.isRotationDifferent(lastPitch, mc.player.getPitch(), lastYaw, mc.player.getYaw());
    }

    public boolean isRotationDifferent(float pitch, float yaw) {
        return EntityUtils.isRotationDifferent(lastPitch, pitch, lastYaw, yaw);
    }

    public void sendSprintStatus(boolean sprint){
        if (sprint != lastSprint) {
            if (sprint) {
                mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            } else {
                mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }

}
