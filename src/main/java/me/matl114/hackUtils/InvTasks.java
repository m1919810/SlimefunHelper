package me.matl114.hackUtils;

import lombok.Getter;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.access.HandledScreenAccess;
import me.matl114.access.PlayerInteractionAccess;
import me.matl114.managers.*;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.UtilClass.LimitedSpeedExecutor;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
        //ignore Creative screen as it is not handled by server
        if(nowScreen instanceof CreativeInventoryScreen){
            return null;
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
    private static final AtomicBoolean applyShiftMove = Configs.INV_CONFIG.getBoolean(Configs.FAST_INV_DO_SHIFT);
    public static boolean quickMoveAllSelectedItem(){
        //fixme distinguish backpack move-from-hotbar-to-backpack, add inv check
        if(HotKeys.getButtonToggleManager().getState(HotKeys.FAST_INV) && applyShiftMove.get()){
            PlayerEntity player = mc.player;
            Screen nowScreen= getCurrentServerScreen(player);
            //filter inventory screen
            if( nowScreen instanceof HandledScreen<?> handled && !(nowScreen instanceof InventoryScreen)){
                ScreenHandler handler= handled.getScreenHandler();
                Pair<Integer,Integer> mouseCoord= ScreenUtils.getMouseCoord(mc);
                Slot slot= HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.getLeft(),mouseCoord.getRight());
                if(slot != null ){
                    ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(),false,false);
                    boolean isPlayerInventory = slot.inventory instanceof PlayerInventory;

                    for(int i=0;i<handler.slots.size();i++){
                        Slot slot2=handler.getSlot(i);
                        if(((slot2.inventory instanceof PlayerInventory)==isPlayerInventory) &&ItemStack.areItemsEqual(cleanedStack,slot2.getStack()) &&  ItemStack.canCombine(cleanedStack,ItemStackUtils.getCleanedItem(slot2.getStack(),false,false))){
                            final int index = i;
                            clickExecutor.execute(()->{
                                mc.interactionManager.clickSlot(handler.syncId,index,1,SlotActionType.QUICK_MOVE,mc.player);
                            });
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
    private static final AtomicBoolean applyShiftDrop = Configs.INV_CONFIG.getBoolean(Configs.FAST_INV_DO_DROP);
    public static boolean quickDropAllSelectedItem(){
        if(HotKeys.getButtonToggleManager().getState(HotKeys.FAST_INV) && applyShiftDrop.get()) {
            PlayerEntity player = mc.player;
            Screen nowScreen = getCurrentServerScreen(player);
            if (nowScreen instanceof HandledScreen<?> handled) {
                ScreenHandler handler= handled.getScreenHandler();
                Pair<Integer,Integer> mouseCoord= ScreenUtils.getMouseCoord(mc);
                Slot slot= HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.getLeft(),mouseCoord.getRight());
                if (slot!=null && slot.getStack() != null && slot.getStack().getItem() != Items.AIR) {
                    ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(), false, false);
                    for(int i=0;i<handler.slots.size();i++){
                        Slot slot2=handler.getSlot(i);
                        if(ItemStack.areItemsEqual(cleanedStack,slot2.getStack()) &&  ItemStack.canCombine(cleanedStack,ItemStackUtils.getCleanedItem(slot2.getStack(),false,false))){
                            final int index = i;
                            clickExecutor.execute(()->{
                                mc.interactionManager.clickSlot(handler.syncId, index, 1, SlotActionType.THROW, player);
                            });
                        }
                    }
                    return true;
                }

            }
        }
        return false;
    }
    public static void placeLastCraftingRecipe(HandledScreen<? extends AbstractRecipeScreenHandler> craftingScreen, boolean doCraft){
       // var recipeBook = craftingScreen.getRecipeBookWidget();
        RecipeEntry<?> last = PlayerInteractionAccess.of(mc.interactionManager).getLastlyCrafted();
        if(last != null){
            mc.interactionManager.clickRecipe(craftingScreen.getScreenHandler().syncId, last,true);
            //Debug.info("What's wrong?",doCraft);
            if(doCraft){
                boolean dropCraft = HotKeys.getSimpleToggleManager().getState(HotKeys.DROP_CRAFT);
                //Debug.info("Drop craft?",dropCraft);
                if(dropCraft){
                    int maxCraft = 64;
                    for (Ingredient material:last.value().getIngredients()){
                        for (ItemStack val:material.getMatchingStacks()){
                            maxCraft = Math.min(maxCraft, val.getMaxCount());
                        }
                    }
                    for (int i=0;i<maxCraft;++i){
                        clickExecutor.execute(()->{
                            mc.interactionManager.clickSlot(craftingScreen.getScreenHandler().syncId,craftingScreen.getScreenHandler().getCraftingResultSlotIndex(),0,SlotActionType.THROW,mc.player);});
                    }
                }else {
                    clickExecutor.execute(()->{
                        mc.interactionManager.clickSlot(craftingScreen.getScreenHandler().syncId,craftingScreen.getScreenHandler().getCraftingResultSlotIndex(),1,SlotActionType.QUICK_MOVE,mc.player);
                    });
                }
            }
        }else {
            Debug.chat("Crafting History Is Empty");
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
