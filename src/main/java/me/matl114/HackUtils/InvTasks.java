package me.matl114.HackUtils;

import lombok.Getter;
import me.matl114.Access.ClientPlayerAccess;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.ConfigureScreen;
import me.matl114.ManageUtils.SelectScreen;
import me.matl114.Utils.UtilClass.LimitedSpeedExecutor;
import me.matl114.Utils.ItemStackUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InvTasks {
    public static void init(){

    }
    private static MinecraftClient mc = MinecraftClient.getInstance();
    public static void clearKeepedInv(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            player.sendMessage(Text.literal("已清除界面历史记录"));
        }
    }

    public static void openSelectScreen(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            HashMap<String,Runnable> map = new LinkedHashMap<>();
            for(Config config:Config.getConfigs()){
                map.put(config.getConfigName(),()->openConfigScreen(config));
            }
            MinecraftClient.getInstance().setScreen(new SelectScreen(map,4,Text.of("")));
        }
    }
    public static void openConfigScreen(Config config){
        MinecraftClient.getInstance().setScreen(new ConfigureScreen(config,Text.of("")));
    }
    public static Screen getCurrentServerScreen(PlayerEntity player){
        if(player==null){
            return null;
        }
        Screen nowScreen= null;
        if(player instanceof ClientPlayerAccess access){
            nowScreen = access.getServerHandledScreen();
        }else{
            nowScreen= MinecraftClient.getInstance().currentScreen;
        }
        return nowScreen;
    }
    public static boolean dropAllSelectedItem(){
        PlayerEntity player = mc.player;
        Screen nowScreen= getCurrentServerScreen(player);
        if( nowScreen instanceof HandledScreen<?> handled){
            ScreenHandler handler= handled.getScreenHandler();
            if(handler.getCursorStack()!=null&&handler.getCursorStack().getItem()!= Items.AIR){
                ItemStack cleanedCursor= ItemStackUtils.getCleanedItem( handler.getCursorStack(),false,false);
                clickExecutor.execute(()->{
                    MinecraftClient.getInstance().interactionManager.clickSlot(handler.syncId,-999,0,SlotActionType.PICKUP,player );
                });
                for (int i=0;i<handler.slots.size();i++){
                    ItemStack slot=handler.getSlot(i).getStack();
                    if(!ItemStack.areItemsEqual(slot,cleanedCursor)){
                        continue;
                    }
                    ItemStack cleaned= ItemStackUtils.getCleanedItem( slot,false,false);
                    if(ItemStack.canCombine(cleaned,cleanedCursor)){
                        final  int index=i;
                        clickExecutor.execute(()->{
                            MinecraftClient.getInstance().interactionManager.clickSlot(handler.syncId,index,1,SlotActionType.THROW,player );
                        });

                    }
                }
                return true;
            }

        }
        return false;
    }
    public static void takeAllContainerItem(){
        Screen nowScreen= getCurrentServerScreen(mc.player);
        if(nowScreen instanceof HandledScreen<?> handled){
            ScreenHandler handler= handled.getScreenHandler();
            for (int i=0;i<handler.slots.size();i++){
                Slot slot=handler.getSlot(i);
                if(!(slot.inventory instanceof PlayerInventory)){
                    final int index=i;
                    clickExecutor.execute(()->{
                        mc.interactionManager.clickSlot(handler.syncId,index,1,SlotActionType.QUICK_MOVE,mc.player);
                    });
                }
            }
        }
    }
    public static void saveAllPlayerInvItem(){
        Screen nowScreen= getCurrentServerScreen(mc.player);
        if(nowScreen instanceof HandledScreen<?> handled){
            ScreenHandler handler= handled.getScreenHandler();
            for (int i=0;i<handler.slots.size();i++){
                Slot slot=handler.getSlot(i);
                if(slot.inventory instanceof PlayerInventory){
                    final int index=i;
                    clickExecutor.execute(()->{
                        mc.interactionManager.clickSlot(handler.syncId,index,1,SlotActionType.QUICK_MOVE,mc.player);
                    });
                }
            }
        }
    }
    private static final AtomicInteger SPEED= Configs.INV_CONFIG.getInt(Configs.INV_CLICK_LIMIT);
    @Getter
    private static final LimitedSpeedExecutor clickExecutor=new LimitedSpeedExecutor(SPEED);
    static{
        Tasks.registerGameTask(r->{
            clickExecutor.reset();
        });
    }
}
