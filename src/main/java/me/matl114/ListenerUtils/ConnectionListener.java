package me.matl114.ListenerUtils;

import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class ConnectionListener {
    public static void init(){

    }
    public static void onPacketReceive(Packet<?> packet){
        checkAcceptedPacketDebug(packet);
        checkAcceptedContainer(packet);
    }
    public static void onPacketSend(Packet<?> packet){
//        if(packet instanceof ResourcePackStatusC2SPacket packet2){
//            Debug.info("send status packet",packet2.status());
//        }
//        if(packet instanceof PlayerInteractItemC2SPacket interactItemC2SPacket){
//            Debug.info("send interact packet");
//        }
//        if(packet instanceof PlayerActionC2SPacket playerActionC2SPacket){
//            Debug.info("send player action packet",playerActionC2SPacket.getAction());
//        }

    }
    private static void checkAcceptedContainer(Packet<?> packet){
//        if(packet instanceof CloseScreenS2CPacket close){
//            Debug.info("accept close!");
//        }
    }
    private static void checkAcceptedPacketDebug(Packet<?> packet){
        //
//        if(packet.getClass().getSimpleName().startsWith("Resource")){
//            Debug.info("accept packet",packet.getClass().getSimpleName());
//            if(packet instanceof ResourcePackSendS2CPacket send){
//                Debug.info("send packet",send.id(),send.hash(),send.url(),send.prompt(),send.required());
//            }else if(packet instanceof ResourcePackRemoveS2CPacket remove){
//                Debug.info("remove packet",remove.id());
//            }
//        }

//                if(true){
//            return;
//        }
//        if(packet instanceof RemoveMessageS2CPacket remove){
//            Debug.info();
//        }
//        if(packet instanceof PlayerPositionLookS2CPacket position){
//            Debug.info("accept position!",position.getX(),position.getY(),position.getZ(),position.getTeleportId());
//        }
//        if(packet instanceof PlayerSpawnPositionS2CPacket position){
//            Debug.info("accept spawn ",position.getPos());
//        }
//        if(packet instanceof InventoryS2CPacket inventory){
//            Debug.info("accept inventory",inventory);
//        }
//        if(packet instanceof ParticleS2CPacket particleS2CPacket){
//            return;
//        }
//
//        if(packet instanceof WorldTimeUpdateS2CPacket ||packet.getClass().getSimpleName().startsWith("ScoreBoard")) {
//            return;
//        }
//        if(packet instanceof ChunkDataS2CPacket chunk){
//            Debug.info("check chunk data "+chunk.getChunkX()+" "+chunk.getChunkZ());
//        }
       // Debug.info("Received packet: " + packet.getClass().getSimpleName());
//        if(packet instanceof EntitiesDestroyS2CPacket des) {
//            Debug.info("destroy",des.getEntityIds());
//            for (int id: des.getEntityIds()) {
//                if(MinecraftClient.getInstance().world!=null){
//                    var a=MinecraftClient.getInstance().world.getEntityById(id);
//                    if(a!=null)
//                        Debug.info("entity info",a.getType(),a.getPos());
//                }
//            }
//        }
//        if(packet instanceof EntityTrackerUpdateS2CPacket tracker) {
//            if(MinecraftClient.getInstance().world!=null){
//                Entity entity=MinecraftClient.getInstance().world.getEntityById(tracker.id());
//                if(entity!=null&&entity.getType()== EntityType.PLAYER) {
//                    Debug.info("tracker ",tracker.id(),tracker.trackedValues());
//                    for(DataTracker.SerializedEntry entity1 : tracker.trackedValues()) {
//                        Debug.info("tracker value",entity1.id(),entity1.value());
//                    }
//                }
//            }
//
//        }
//        ClientPlayerEntity player = MinecraftClient.getInstance().player;
//        player.attack();
//        if(packet instanceof EntitySpawnS2CPacket spawn) {
//            Debug.info("spawn",spawn.getId(),spawn.getEntityType(),spawn.getEntityData(),spawn.getX(),spawn.getY(),spawn.getZ());
//        }
//        //Debug.info("accept ",packet.getClass().getSimpleName());
//        if(packet instanceof EntityDamageS2CPacket damage){
//            Debug.info("accept damage packet",damage.entityId(),damage.sourceTypeId(),damage.sourceDirectId(),damage.sourceCauseId(),damage.sourcePosition().orElse(null));
//
//        }
//        if(packet instanceof PlayerListS2CPacket packet1){
//            Debug.info(packet1.getEntries(),packet1.getPlayerAdditionEntries(),packet1.getActions());
//
//        }

    }
    //@Inject(method = "sendInternal",at=@At("HEAD"))
    private void sendPacket(Packet<?> packet, @Nullable PacketCallbacks callbacks, NetworkState packetState, NetworkState currentState, CallbackInfo ci) {
//        Debug.info("send packet ",packet.getClass().getSimpleName());

    }
    static {
        Listener.registerPacketListener(ConnectionListener::onPacketReceive,true);
        Listener.registerPacketListener(ConnectionListener::onPacketSend,false);
    }
}
