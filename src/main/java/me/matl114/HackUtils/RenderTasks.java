package me.matl114.HackUtils;

import me.matl114.ListenerUtils.Listener;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.Utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashSet;

public class RenderTasks {
    public static void init(){

    }
    private static HashSet<EntityType<?>> entityTypes = new HashSet<>();
    public static Config.StringRef RENDER_DETECT_WHITELIST= Configs.RENDER_CONFIG.getString(Configs.RENDER_DETECT_SPAWN_WHITELIST);
    private static HashSet<EntityType<?>> getWhitelisted(){

        return entityTypes;
    }
    private static double calculateDistance(double x1,double y1,double z1){
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerEntity player=MinecraftClient.getInstance().player;
            return Math.sqrt( player.getPos().squaredDistanceTo(x1,y1,z1));
        }
        return -1.0f;
    }
    private static boolean noPlayerSpawnPacket=false;
    public static void onDetect(Packet<?> packet){
        MinecraftClient.getInstance().executeSync(()->{
            if(packet instanceof EntitySpawnS2CPacket spawn){
                if(HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
                    RenderTasks.detectEntitySpawn(spawn);
                }
            }
            if(packet instanceof EntitiesDestroyS2CPacket spawn){
                if(HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
                    RenderTasks.detectEntityDestory(spawn);
                }
            }
            if(!noPlayerSpawnPacket){
                //todo add this to EntityListener
                //todo or change to world.addEntity()
//                try{
//                    if(packet instanceof PlayerSpawnS2CPacket spawn){
//                        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
//                            RenderTasks.detectPlayerSpawn(spawn);
//                        }
//                    }
//                }catch (NoClassDefFoundError e){
//                    noPlayerSpawnPacket=true;
//                }
            }
        });
    }
    public static Text getDisplayedLocation(double x,double y ,double z){
        return Text.literal("[%d,%d,%d]".formatted((int)x, (int)y, (int)z)).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,"%.2f %.2f %.2f".formatted(x,y,z))).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("click to copy coord")))).formatted(Formatting.GREEN);
    }
    public static void detectEntitySpawn(EntitySpawnS2CPacket packet){
        HashSet<EntityType<?>> whitelisted=getWhitelisted();
        if(whitelisted.contains(packet.getEntityType())){
            EntityType<?> type=packet.getEntityType();
            if(type==EntityType.PLAYER){
                Text text=null;
                if(MinecraftClient.getInstance().world!=null){
                    PlayerListEntry entry= MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(packet.getUuid());
                    if(entry!=null){
                        text=Text.literal(entry.getProfile().getName()).formatted(Formatting.GREEN);
                    }
                }
                Debug.chat("Player ",text==null?"":text,"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
            }else{
                Debug.chat("Entity",packet.getEntityType().getName(),"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
            }
            //Debug.info(packet.getEntityData(),packet.getUuid());
        }
    }
//    public static void detectPlayerSpawn(PlayerSpawnS2CPacket packet){
//        HashSet<EntityType<?>> whitelisted=getWhitelisted();
//        if(whitelisted.contains(EntityType.PLAYER)){
//            Text name=null;
//            if(MinecraftClient.getInstance().world!=null){
//                PlayerListEntry entry= MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(packet.getPlayerUuid());
//                if(entry!=null){
//                    name=Text.literal( entry.getProfile().getName()).formatted(Formatting.GREEN);
//                }
//            }
//
//            Debug.chat("Player",name==null?"":name,"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
//        }
//    }


    public static void detectEntityDestory(EntitiesDestroyS2CPacket packet){
        HashSet<EntityType<?>> whitelisted=getWhitelisted();
        World clientWorld=MinecraftClient.getInstance().world;
        if(clientWorld!=null){
            for(int i:packet.getEntityIds()){
                Entity entity=clientWorld.getEntityById(i);
                if(entity==null)continue;
                if(whitelisted.contains(entity.getType())){
                    Debug.chat("Entity",entity.getType().getName(),entity instanceof PlayerEntity pl? pl.getName():(entity.hasCustomName()? entity.getCustomName():""),"disappear at position ",getDisplayedLocation(entity.getX(),entity.getY(),entity.getZ()),",distance: %.2f".formatted(calculateDistance(entity.getX(),entity.getY(),entity.getZ())));
                    Debug.info(entity.getDisplayName(),entity.getUuid());
                }
            }
        }


    }
    //todo add rayTrace render Func
    static {
        EntityUtils.parseEntityWhiteList(RENDER_DETECT_WHITELIST.get().replace(',','|'),entityTypes);
        RENDER_DETECT_WHITELIST.addUpdateListener((str)->{
            EntityUtils.parseEntityWhiteList(str.replace(',','|'),entityTypes);
        });
        Listener.registerPacketListener(RenderTasks::onDetect,true);
    }
}
