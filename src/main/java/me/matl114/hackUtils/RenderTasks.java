package me.matl114.hackUtils;

import me.matl114.access.PlayerInteractionAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.renders.RenderMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderTasks {
    public static void init(){

    }
    private static HashSet<EntityType<?>> entityTypes = new HashSet<>();
    private static MinecraftClient mc = MinecraftClient.getInstance();
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
                Debug.chat("Player Entity Id ",packet.getId());
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
//                    Debug.info(entity.getDisplayName(),entity.getUuid());
                }
            }
        }


    }
    public static void drawRecipeHistory(DrawContext context, TextRenderer textRenderer, int x, int y, int atX, int atY){
        RecipeEntry<?> entry = PlayerInteractionAccess.of( MinecraftClient.getInstance().interactionManager).getLastlyCrafted();
        ItemStack tobeRendered;
        if(entry != null){
            tobeRendered = entry.value().getResult(MinecraftClient.getInstance().world.getRegistryManager());
        }else{
            tobeRendered = new ItemStack(Items.BARRIER);
        }
        context.getMatrices().push();
        context.getMatrices().translate((float)x, (float)y, 0.0F);
        RenderMain.drawSlotLikeItemAt(context,textRenderer,tobeRendered,atX,atY,0,1.0F,666);
        //render lock
        boolean lock = PlayerInteractionAccess.of(MinecraftClient.getInstance().interactionManager).getRecipeLock();
        if(lock){
            RenderMain.drawSlotLikeItemAt(context,textRenderer,new ItemStack(Items.BARRIER),atX     -4,atY + 4,50,0.4F,999);
        }
        context.getMatrices().pop();
    }
    private static final AtomicBoolean disableServerResourcePack = Configs.RENDER_CONFIG.getBoolean(Configs.RESOURCE_IGNORE_SERVER);
    public static boolean denyServerPacket(ClientConnection connection, Packet packet){
        if(packet instanceof ResourcePackSendS2CPacket sendPacket && disableServerResourcePack.get()){
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            Debug.chat(Text.literal("Successfully reject server resourcepack").formatted(Formatting.GREEN),sendPacket.id());
            Debug.chat(Text.literal("Download url:").formatted(Formatting.GREEN),Text.literal( sendPacket.url()).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, sendPacket.url()))).formatted(Formatting.YELLOW));
            return false;
        }
        return true;
    }

    //todo add rayTrace render Func
    static {
        EntityUtils.parseEntityWhiteList(RENDER_DETECT_WHITELIST.get().replace(',','|'),entityTypes);
        RENDER_DETECT_WHITELIST.addUpdateListener((str)->{
            EntityUtils.parseEntityWhiteList(str.replace(',','|'),entityTypes);
        });
        Listener.registerPacketListener(RenderTasks::onDetect,true);
        Listener.registerPacketListener(RenderTasks::denyServerPacket,true);
    }
}
