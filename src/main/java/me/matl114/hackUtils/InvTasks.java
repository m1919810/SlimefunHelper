package me.matl114.hackUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;
import me.matl114.access.*;
import me.matl114.gui.config.ConfigurateNewStyleScreen;
import me.matl114.gui.invcache.InventorySelectScreen;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.*;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.ItemStackSample;
import me.matl114.utils.UtilClass.LimitedSpeedExecutor;
import me.matl114.utils.UtilClass.MutableEntry;
import me.matl114.utils.UtilClass.Point;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public static final AtomicBoolean OPTIMIZE_SLOT_CLICK_PACKET = new AtomicBoolean(false);
    @ApiMethod
    public static void openConfigNewStyleScreen(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            ScreenAccess.of( new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList())).openFromCurrent();
        }
    }
    @ApiMethod
    public static void openConfigScreen(Config config){
        ConfigurateNewStyleScreen newStyleScreen = new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList());
        newStyleScreen.setConfig(config);
        ScreenAccess.of( newStyleScreen).openFromCurrent();
    }




    @ApiMethod
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

    @ApiMethod
    public static boolean dropAllCursorStack(){
        PlayerEntity player = mc.player;
        if(player == null)return false;
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
                    if(ItemStack.areItemsAndComponentsEqual(cleaned,cleanedCursor)){
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
    @ApiMethod
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
    @ApiMethod
    public static void saveAllPlayerItem(){
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

    private static final Config.FlagRef applyShiftMove = Configs.INV_CONFIG.getBoolean(Configs.FAST_INV_DO_SHIFT);
    @ApiMethod
    public static boolean isHandledScreen(Screen screen){
        return screen instanceof HandledScreen;
    }

    public static boolean quickMoveAllSelectedItem(){

        PlayerEntity player = mc.player;
        if(player == null)return false;
        Screen nowScreen= getCurrentServerScreen(player);
        //filter inventory screen
        if( nowScreen instanceof HandledScreen<?> handled && !(nowScreen instanceof InventoryScreen)){
            ScreenHandler handler= handled.getScreenHandler();
            Point mouseCoord= ScreenUtils.getMouseCoord(mc);
            Slot slot= HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x,mouseCoord.y);
            if(HotKeys.getButtonToggleManager().getState(HotKeys.FAST_INV) && applyShiftMove.get()){
                quickMoveSlotItem(handled, slot);
            } else if(HotKeys.getButtonToggleManager().getState(HotKeys.LEFT_ONE)){
                //FIXME: problem, clicking stacked, removing one
                if(slot != null){
                    //Debug.info("debug at ",slot.getIndex());
                    int index = handler.slots.indexOf(slot);
                    //Debug.info("index at", index);
                    if(index >= 0){
                        quickMoveSlot(handled, index);
                        return true;
                    }
                }
            }
        }
        return false;
    }
    @ApiMethod
    public static boolean quickMoveSlotItem(HandledScreen screen, int slotIndex){
        Slot slot = screen.getScreenHandler().getSlot(slotIndex);
        if(slot != null){
            return quickMoveSlotItem(screen, slot);
        }
        return false;
    }
    private static boolean quickMoveSlotItem(HandledScreen screen, Slot slot){
        if(slot != null ){
            ScreenHandler handler= screen.getScreenHandler();
            ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(),false,false);
            boolean isPlayerInventory = slot.inventory instanceof PlayerInventory;

            for(int i=0;i<handler.slots.size();i++){
                Slot slot2=handler.getSlot(i);
                if(((slot2.inventory instanceof PlayerInventory)==isPlayerInventory) &&ItemStack.areItemsEqual(cleanedStack,slot2.getStack()) &&  ItemStack.areItemsAndComponentsEqual(cleanedStack,ItemStackUtils.getCleanedItem(slot2.getStack(),false,false))){
                    quickMoveSlot(screen, i);
                }
            }
            return true;
        }return false;
    }
    @ApiMethod
    public static void quickMoveSlot(HandledScreen handler, int index, boolean ignoreConfig){
        quickMoveSlot(handler.getScreenHandler(), index, ignoreConfig);
    }
    @ApiMethod
    public static void quickMoveSlot(HandledScreen handler, int index){
        quickMoveSlot(handler.getScreenHandler(), index, false);
    }
    public static void quickMoveSlot(ScreenHandler handler, int index, boolean ignoreConfig){
        if(!ignoreConfig &&  handler.getSlot(index).getStack().getCount() > 1 && HotKeys.getButtonToggleManager().getState(HotKeys.LEFT_ONE)){
//            Debug.info("quick move 1");
            int syncId = handler.syncId;
            if(!handler.getCursorStack().isEmpty()){
                Debug.chat(Text.literal("[left 1] ").formatted(Formatting.RED).append(Text.literal("cursor stack needs to be empty to apply left-one quickMove")));
                return;
            }
            Slot slot = handler.getSlot(index);
            if(slot.getStack().isEmpty()){
                return;
            }
            boolean tryTake = slot.inventory instanceof PlayerInventory;
            boolean hasPlace = false;
            for (Slot s: handler.slots){
                //skip same-side inventory
                if((s.inventory instanceof PlayerInventory) == tryTake){
                    continue;
                }
                if(s.getStack().isEmpty() || (s.getStack().getCount() < s.getStack().getMaxCount() && ItemStack.areItemsAndComponentsEqual(slot.getStack(), s.getStack()))){
                    hasPlace = true;
                    break;
                }
            }
            //minimize packet amount sent
            if(!hasPlace){
                return;
            }
            mc.interactionManager.clickSlot(syncId, index, 0,SlotActionType.PICKUP,mc.player);
            mc.interactionManager.clickSlot(syncId, index, 1, SlotActionType.PICKUP, mc.player);
            ItemStack sample = handler.getCursorStack();
            if(sample.isEmpty()){
                return;
            }
            //save sample
            sample = sample.copy();
            for (int i = 0; i < handler.slots.size() ; ++i){
                Slot s = handler.slots.get(i);
                if((s.inventory instanceof PlayerInventory) == tryTake){
                    continue;
                }
                if(s.getStack().isEmpty() || (s.getStack().getCount() < s.getStack().getMaxCount() && ItemStack.areItemsAndComponentsEqual(slot.getStack(), s.getStack()))){
                    mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    if(handler.getCursorStack().isEmpty()){
                        return;
                    }
                }
            }
            if(!handler.getCursorStack().isEmpty()){
                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.PICKUP, mc.player);
            }
//            while (handler.getSlot(index).getStack().getCount() > 1){
//                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);
//                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
//                mc.interactionManager.clickSlot(syncId, index, 0,);
//            }
//                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);
//                int maxTry = 33;
//                while (handler.getCursorStack().getCount() > 1){
//                    mc.interactionManager.clickSlot(syncId, index, 1, SlotActionType.PICKUP, mc.player);
//                    if(-- maxTry <= 0){
//                        break;
//                    }
//                }
//                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
//                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);

        }else {
            if(handler.getCursorStack().isEmpty()){
                mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
            }
            mc.interactionManager.clickSlot(handler.syncId,index,0,SlotActionType.QUICK_MOVE,mc.player);

        }
    }

    private static final Config.FlagRef applyShiftDrop = Configs.INV_CONFIG.getBoolean(Configs.FAST_INV_DO_DROP);
    public static boolean quickDropAllSelectedItem(){
        if(mc.player == null)return false;
        if(HotKeys.getButtonToggleManager().getState(HotKeys.FAST_INV) && applyShiftDrop.get()) {
            PlayerEntity player = mc.player;
            Screen nowScreen = getCurrentServerScreen(player);
            if (nowScreen instanceof HandledScreen<?> handled) {

                Point mouseCoord= ScreenUtils.getMouseCoord(mc);
                Slot slot= HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x,mouseCoord.y);
                if (quickDropSlotItem(handled, slot)) {
                    return true;
                }

            }
        }
        return false;
    }
    @ApiMethod
    public static boolean quickDropSlotItem(HandledScreen handler, int index){
        Slot slot = handler.getScreenHandler().getSlot(index);
        if (slot != null){
            return quickDropSlotItem(handler, slot);
        }
        return false;

    }

    public static boolean quickDropSlotItem(HandledScreen handled, Slot slot){
        if(slot != null && slot.getStack() != null && slot.getStack().getItem() != Items.AIR){
            ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(), false, false);
            ScreenHandler handler= handled.getScreenHandler();
            for(int i=0;i<handler.slots.size();i++){
                Slot slot2=handler.getSlot(i);
                if(ItemStack.areItemsEqual(cleanedStack,slot2.getStack()) &&  ItemStack.areItemsAndComponentsEqual(cleanedStack,ItemStackUtils.getCleanedItem(slot2.getStack(),false,false))){
                    final int index = i;
                    clickExecutor.execute(()->{
                        mc.interactionManager.clickSlot(handler.syncId, index, 1, SlotActionType.THROW, mc.player);
                    });
                }
            }
            return true;
        }
        return false;
    }

    public static void placeLastCraftingRecipe(HandledScreen<? extends AbstractRecipeScreenHandler> craftingScreen, boolean doCraft){
       // var recipeBook = craftingScreen.getRecipeBookWidget();
        RecipeEntry<?> last = PlayerInteractionAccess.of(mc.interactionManager).getLastlyCrafted();
        if(last != null){
            mc.interactionManager.clickRecipe(craftingScreen.getScreenHandler().syncId, last,true);
            if(doCraft){
                int maxCraft = 64;
                for (Ingredient material:last.value().getIngredients()){
                    for (ItemStack val:material.getMatchingStacks()){
                        maxCraft = Math.min(maxCraft, val.getMaxCount());
                    }
                }
                int slot = craftingScreen.getScreenHandler().getCraftingResultSlotIndex();
                craftAtSlotIndex(craftingScreen, maxCraft, slot);
            }
        }else {
            Debug.chat("Crafting History Is Empty");
        }
    }
    public static void craftAtSlotIndex(HandledScreen<?> screen,  int maxCraft, int slot){
        //Debug.info("What's wrong?",doCraft);

        boolean dropCraft = HotKeys.getSimpleToggleManager().getState(HotKeys.DROP_CRAFT);
        //Debug.info("Drop craft?",dropCraft);
        if(dropCraft){
            for (int i=0;i<maxCraft;++i){
                clickExecutor.execute(()->{
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot,0,SlotActionType.THROW,mc.player);});
            }
        }else {
            clickExecutor.execute(()->{
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot,1,SlotActionType.QUICK_MOVE,mc.player);
            });
        }

    }

    public static boolean pickUpSelectingSlot(){
        PlayerEntity player = mc.player;
        if(player == null)return false;
        Screen nowScreen= getCurrentServerScreen(player);
        if( !player.isCreative() && nowScreen instanceof HandledScreen<?> handled){
            Debug.chat("run pickup");
            Point mouseCoord= ScreenUtils.getMouseCoord(mc);
            Slot slot= HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
            if(slot != null && slot.inventory instanceof PlayerInventory){
                if(slot.getIndex() >= 36){
                    Debug.chat("Invalid slot for player Inventory",slot.getIndex());
                }else {
                    mc.interactionManager.pickFromInventory(slot.getIndex());
                }
                return true;
            }
        }
        return false;
    }
    public static void autoStack(ClientPlayerEntity player, HandledScreen<?> handledScreen){
        if(HotKeys.getButtonToggleManager().getState(HotKeys.AUTO_STORE)){
            var handler = handledScreen.getScreenHandler();
            if( handledScreen instanceof CreativeInventoryScreen || handledScreen instanceof InventoryScreen){
                return;
            }

            IntList inputSlot = new IntArrayList();
            IntList outputSlot = new IntArrayList();
            for (int i = 0; i < handler.slots.size(); ++i){
                Slot slot = handler.slots.get(i);
                if(slot.inventory instanceof PlayerInventory){
                    inputSlot.add(i);
                }else {
                    outputSlot.add(i);
                }
            }

            for (int i : inputSlot){
                ItemStack stack = handler.slots.get(i).getStack();
                //left one is enough
                //left two please
                if(stack != null && !stack.isEmpty() && stack.getCount() >= 4){
                    //when trying to remove full stack, ensure that cursor is empty
                    if(!handler.getCursorStack().isEmpty()){
                        ItemStack stackt = handler.getCursorStack();
                        int slot = anyMatch(handler.slots, stackt, stackt.getCount(), outputSlot.toIntArray());
                        if(slot >= 0){
                            clickExecutor.execute(()->{
                                mc.interactionManager.clickSlot(handledScreen.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP , player);
                            });
                        }else {
                            clickExecutor.execute(()->{
                                mc.interactionManager.clickSlot(handledScreen.getScreenHandler().syncId, slot, 0, SlotActionType.THROW , player);
                            });
                        }
                        return;
                    }
                    //cursor is empty, we can execute transform
                    int toTransfer = (stack.getCount() + 1) / 2;
                    int slot = anyMatch(handler.slots, stack, toTransfer , outputSlot.toIntArray());
                    if(slot >= 0){

                        clickExecutor.execute(()->{
                            mc.interactionManager.clickSlot(handledScreen.getScreenHandler().syncId, i, 1, SlotActionType.PICKUP , player);
                            mc.interactionManager.clickSlot(handledScreen.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP , player);
                        });
                        return;
                    }
                }
            }
        }
    }

    private static int anyMatch(DefaultedList<Slot> slots, ItemStack stack, int amount, int... index){
        for (int i : index){
            ItemStack stack2 = slots.get(i).getStack();
            //can place stack with amount on it,
            if(stack2 != null && (stack2.isEmpty() || stack2.getCount() + amount <= stack2.getMaxCount() && ItemStack.areItemsAndComponentsEqual(stack, stack2))){
                return i;
            }
        }
        return -1;
    }

    public static void specialInventoryTick(ClientPlayerEntity player){
        Screen screen = getCurrentServerScreen(player);
        if(screen instanceof HandledScreen<?> inventory){
            autoStack(player, inventory);
        }
    }

    public static void setCreativeInventory(ItemStack itemStack, int slot){
        if(slot < 36){
            mc.player.getInventory().setStack(slot, itemStack.copy());
            mc.interactionManager.clickCreativeStack(itemStack, PLAYER_SLOTS[slot]);
        }
    }

    @ApiMethod
    public static ItemStack getHotbarStack(int hotbar){
        return hotbar == 40 ? mc.player.getInventory().offHand.get(0) : mc.player.getInventory().main.get(hotbar);
    }
    @ApiMethod
    public static int getTopInventorySize(){
        if(mc.currentScreen instanceof HandledScreen handledScreen && handledScreen.getScreenHandler() != null){
            int idx = 0;
            for (var slot : handledScreen.getScreenHandler().slots){
                if(slot.inventory instanceof PlayerInventory){
                    return idx;
                }else {
                    idx += 1;
                }
            }
            return idx;
        }else{
            return 0;
        }
    }

    private static final int[] PLAYER_SLOTS = new int[]{
        36, 37,38, 39,40,41,42,43,44,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,45, 5, 6, 7,8, 0, 1, 2, 3, 4
    };
    @ApiMethod
    public static void creativeGive(ItemStack itemStack, int count){
        if(mc.player != null && mc.interactionManager != null && mc.interactionManager.hasCreativeInventory()){
            PlayerScreenHandler inventoryView = mc.player.playerScreenHandler;
            int stackMax = itemStack.getMaxCount();
            for (int i: PLAYER_SLOTS){
                Slot slot0 = inventoryView.getSlot(i);
                int transfer = 0;
                ItemStack stackToSet = null;
                if(slot0.getStack().isEmpty()){
                    transfer = Math.min(stackMax, count);
                    stackToSet = itemStack.copyWithCount(transfer);
                }else if(slot0.getStack().getCount() < stackMax && ItemStack.areItemsAndComponentsEqual( slot0.getStack(), itemStack)){
                    transfer = Math.min(stackMax - slot0.getStack().getCount(), count);
                    stackToSet = itemStack.copyWithCount(slot0.getStack().getCount()+ transfer);
                }
                count -= transfer;
                if(stackToSet != null){
                    mc.interactionManager.clickCreativeStack(stackToSet, i);
                    slot0.setStack(stackToSet);
                }
                if(count <= 0)return;
            }
            ItemStack sample = itemStack.copy();
            while (count > 0 ){
                int transfer = Math.min(stackMax, count);
                count -= transfer;
                sample.setCount(transfer);
                mc.interactionManager.dropCreativeStack(sample);
            }
        }
    }
    @ApiMethod
    public static void creativeAddItem(ItemStack itemStack, int count){
        if(mc.player != null && mc.interactionManager != null && mc.interactionManager.hasCreativeInventory()){
            int slot = -1;

            PlayerScreenHandler inventoryView = mc.player.playerScreenHandler;
            int stackMax = itemStack.getMaxCount();
            int countA = count;
            for ( int i : PLAYER_SLOTS){
                Slot slot0 = inventoryView.getSlot(i);
                if(slot0.getStack().isEmpty()){
                    slot = i;
                    break;
                }else if(slot0.getStack().getCount() < stackMax && ItemStack.areItemsAndComponentsEqual( slot0.getStack(), itemStack)){
                    slot=  i;
                    countA = count + slot0.getStack().getCount();
                    break;
                }
            }
            ItemStack stackToGive  = itemStack.copyWithCount(Math.min(stackMax, countA));
            if(slot != -1) {
                inventoryView.getSlot(slot).setStack(stackToGive);
                mc.interactionManager.clickCreativeStack(stackToGive, slot);
            }else {
                mc.interactionManager.dropCreativeStack(stackToGive);
            }
        }
    }
    @ApiMethod
    public static void creativeDrop(ItemStack itemStack, int count){
        if(itemStack.isEmpty())return;
        mc.interactionManager.dropCreativeStack(itemStack.copyWithCount(count));
    }
    @ApiMethod
    public static void copyGiveCommand(ItemStack itemStack){

        mc.keyboard.setClipboard(createGiveCommand(itemStack.copyWithCount(itemStack.getMaxCount())));
    }
    @ApiMethod
    public static String createGiveCommand(ItemStack itemStack){
        if(itemStack.isEmpty())return "";
        StringBuilder builder = new StringBuilder("/minecraft:give @s ");
        builder.append(Registries.ITEM.getId(itemStack.getItem()));
        if(ItemStackUtils.hasInPatch(itemStack)){
            builder.append('[');
            Map<ComponentType, Optional> map = new HashMap<>( itemStack.components.changedComponents);
            int index = 0;
            for (var entry: map.entrySet()){
                if(index > 0){
                    builder.append(',');
                }
                Identifier identifier = Registries.DATA_COMPONENT_TYPE.getId(entry.getKey());
                if(identifier != null){
                    String cmp  = identifier.toString();
                    Optional val = entry.getValue();
                    if(val.isPresent()){
                        try{
                            String nbtSeri = entry.getKey().getCodecOrThrow().encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), val.get()).getOrThrow().toString();
                            builder.append(cmp).append('=').append(nbtSeri);
                            index ++;
                        }catch (Throwable e){
                            //exception, skip
                            Debug.chat(e.getMessage());
                        }
                    }else {
                        builder.append('!').append(cmp);
                        index ++;
                    }
                }
            }
            builder.append(']');
        }
        builder.append(" ").append(itemStack.getCount());
        return builder.toString();
    }
    @ApiMethod
    public static boolean isScreenHandlerValid(ScreenHandler handler){
        return  mc.player != null && mc.player.currentScreenHandler == handler;
    }
    @ApiMethod
    public static void quickMoveSlotOrDrop(HandledScreen handledScreen, int slot){
        ScreenHandler handler = handledScreen.getScreenHandler();
        if(!isScreenHandlerValid(handler))return;
        if(!handler.getCursorStack().isEmpty()){
            mc.interactionManager.clickSlot(handler.syncId,-999,0,SlotActionType.PICKUP,mc.player );
        }
        quickMoveSlot(handler, slot, true);
        if(!handler.getSlot(slot).getStack().isEmpty()){
            mc.interactionManager.clickSlot(handler.syncId, slot, 1,SlotActionType.THROW, mc.player);
        }
    }
    @Getter
    public static class SlotMatchingResult{
        public ItemStack sample;
        public int count ;
        public IntList slots;
        public SlotMatchingResult(){
            count = 0;
            slots = new IntArrayList();
            this.sample = null;
        }
        public void setItemSample(ItemStack stack){
            this.sample = stack.copy();
        }
        public void addMatchingSlot(int idx, Slot slot){
            slots.add(idx);
            count += slot.getStack().getCount();
        }



        public int[] toIntArray(){
            return slots.toIntArray();
        }
    }
    @ApiMethod
    public static SlotMatchingResult allSlotMatch(HandledScreen handledScreen){
        var result = new SlotMatchingResult();
        int[] array = IntStream.range(0, handledScreen.getScreenHandler().slots.size()).toArray();
        result.slots = new IntArrayList(array);
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getContainerSlots(HandledScreen handledScreen){
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.getScreenHandler().slots;
        int size = allSlots.size();
        for (int i=0; i< size; ++i){
            Slot slot = allSlots.get(i);
            if(slot != null && !(slot.inventory instanceof PlayerInventory)){
                //all match
                result.slots.add(i);

            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getPlayerInventorySlots(HandledScreen handledScreen){
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.getScreenHandler().slots;
        int size = allSlots.size();
        for (int i=0; i< size; ++i){
            Slot slot = allSlots.get(i);
            if(slot != null && slot.inventory instanceof PlayerInventory){
                //all match
                result.slots.add(i);

            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getEmptySlots(HandledScreen handledScreen, int... slots){
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.getScreenHandler().slots;
        for (int i : slots){
            Slot slot = allSlots.get(i);
            if(slot != null && slot.getStack().isEmpty()){

                //all match
                result.slots.add(i);

            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getItemStackMatchingSlot(HandledScreen screen, ItemStack stack, int... list){
        if(stack.isEmpty())return getEmptySlots(screen, list);
        var result = new SlotMatchingResult();
        result.setItemSample(stack);
        var allSlots = screen.getScreenHandler().slots;
        for (int i : list){
            Slot slot = allSlots.get(i);
            if(slot != null && !slot.getStack().isEmpty()){

                if( ItemStack.areItemsAndComponentsEqual(slot.getStack(), stack)){
                    //all match
                    result.addMatchingSlot(i, slot);
                }
            }
        }
        return result;
    }
    /**
     * move items that match itemStack <from the fromRange to the toRange> to the toSlot, try adding toAmountAdd count of itemStack
     * @param handledScreen
     * @param itemStack
     * @param toSlot
     * @param toAmountAdd
     * @param removeExist
     */
    @ApiMethod
    public static void moveStackToSlotRanged(HandledScreen handledScreen, ItemStack itemStack, int toSlot, int toAmountAdd, boolean removeExist, int fromRange, int toRange){
        moveStackToSlot(handledScreen, itemStack, toSlot, toAmountAdd, removeExist, IntStream.range(fromRange, toRange).toArray());
    }

    /**
     * move items that match itemStack from the trustedSlotIndexList to the toSlot, try adding toAmountAdd count of itemStack
     * @param handledScreen
     * @param itemStack
     * @param toSlot
     * @param toAmountAdd
     * @param removeExist
     * @param trustedSlotIndexList
     */
    @ApiMethod
    public static void moveStackToSlot(HandledScreen handledScreen, ItemStack itemStack, int toSlot, int toAmountAdd, boolean removeExist, int... trustedSlotIndexList){
        if(itemStack.isEmpty())return;
        ScreenHandler handler = handledScreen.getScreenHandler();
        if(!isScreenHandlerValid(handler))return;
        //操作前先清空指针
        if(!handler.getCursorStack().isEmpty()){
            mc.interactionManager.clickSlot(handler.syncId,-999,0,SlotActionType.PICKUP,mc.player );
        }
        ItemStack stackAt = handler.getSlot(toSlot).getStack();
        int toAmount = toAmountAdd;
        if(!stackAt.isEmpty()){
            if(ItemStack.areItemsAndComponentsEqual(stackAt, itemStack)){
                toAmount += stackAt.getCount();
            }else {
                // 不要动
                if(!removeExist)return;
                //remove stackAt
                mc.interactionManager.clickSlot(handler.syncId,toSlot,1,SlotActionType.QUICK_MOVE,mc.player);
                //不是哥们怎么没取完啊
                if(!handler.getSlot(toSlot).getStack().isEmpty()){
                    //看我给你丢出去
                    mc.interactionManager.clickSlot(handler.syncId,toSlot,1,SlotActionType.THROW,mc.player );
                }
            }

        }
        //填满一组不需要控制数量!
        if(toAmount >= itemStack.getMaxCount()){
            toAmount = itemStack.getMaxCount();
            if(handler.getSlot(toSlot).getStack().getCount() >=  toAmount){
                return;
            }
            //直接填满就行
            for (var i: trustedSlotIndexList){
                if(!handler.getSlot(i).getStack().isEmpty() && ItemStack.areItemsAndComponentsEqual(handler.getSlot(i).getStack(), itemStack)){
                    moveStackFromTo(handler, i, toSlot);
                    if(handler.getSlot(toSlot).getStack().getCount() >=  toAmount){
                        break;
                    }
                }
            }
        }else {
            //考虑数量
            for (var i: trustedSlotIndexList){
                if(!handler.getSlot(i).getStack().isEmpty() && ItemStack.areItemsAndComponentsEqual(handler.getSlot(i).getStack(), itemStack)){
                    int currentAmount = handler.getSlot(toSlot).getStack().getCount();
                    //
                    if(currentAmount + handler.getSlot(i).getStack().getCount() > toAmount){
                        //satisfy , use tasks to
                        moveStackFromToAmount(handler, i, toSlot, toAmount - currentAmount);
                        break;
                    }else {
                        moveStackFromTo(handler, i, toSlot);
                        if(handler.getSlot(toSlot).getStack().getCount() >=  toAmount){
                            break;
                        }
                    }
                }

            }
        }
    }
    @ApiMethod
    public static void moveRecipePatternToContainer(HandledScreen screen, ItemStack[] ingredients, int[] slot, int patternAmount, boolean removeOrigin){
        int[] playerInv = getPlayerInventorySlots(screen).toIntArray();
        moveRecipePatternToContainer(screen, ingredients, slot, patternAmount, removeOrigin, ((screen1, itemStack) -> getItemStackMatchingSlot(screen1, itemStack, playerInv)));
    }

    public static void moveRecipePatternToContainer(HandledScreen screen, ItemStack[] ingredients, int[] slot, int patternAmount, boolean removeOrigin, BiFunction<HandledScreen, ItemStack, SlotMatchingResult> slotMatchProvider){
        int size = ingredients.length;
        Preconditions.checkArgument(slot.length == size);
        Map<ItemStackSample, IntList> stackRecipe = new HashMap<>();
        IntList emptySlots = new IntArrayList();
        for ( int i=0; i< size; ++i){
            ItemStack item = ingredients[i];
            if(item != null && !item.isEmpty()){
                ItemStackSample sample = new ItemStackSample(item);
                int index = i;
                stackRecipe.compute(sample, (key, list)->{
                    if(list == null){
                        list = new IntArrayList();
                    }
                    list.add(index);
                    return list;
                });
            }else{
                emptySlots.add(i);
            }
        }

        for (var mapEntry: stackRecipe.entrySet()){
            ItemStackSample sample = mapEntry.getKey();
//            String sampleId = getSfIdOrNull(sample.sample());
            var matchResult = slotMatchProvider.apply(screen, sample.sample());
            //getItemStackMatchingSlot(screen, sample.sample(), true, playerInventory);
            int counter = matchResult.count;
            int[] cachedSlots = matchResult.toIntArray();
            ItemStack realStack = matchResult.sample;

//            int size = allSlots.size();
//            for (int i=0; i< size; ++i){
//                Slot slot = allSlots.get(i);
//                if(slot != null && slot.inventory instanceof PlayerInventory && !slot.getStack().isEmpty() ){
//                    if(realStack != null){
//                        if( ItemStack.areItemsAndComponentsEqual(slot.getStack(), realStack)){
//                            //all match
//                            cachedSlots.add(i);
//                            counter += slot.getStack().getCount();
//                        }
//                    }else {
//                        //the first match itemStack will be the realStack template
//                        if(Objects.equals(sampleId,getSfIdOrNull(slot.getStack()) )){
//                            realStack = slot.getStack();
//                            cachedSlots.add(i);
//                            counter += slot.getStack().getCount();
//                        }
//                    }
//
//                }
//            }
            //nothing match this sample, , , counter must be 0, there is no meaning doing left
            if(realStack == null || counter == 0){
                continue;
            }
            //copy stack to avoid modification
            //do not copy because it must be copied
//            realStack = realStack.copy();
            int needed = 0;
            for (var i: mapEntry.getValue()){
                needed += ingredients[i].getCount();
            }
            int maxSupply = Math.min( counter/ needed, patternAmount);
            for (var i: mapEntry.getValue()){
                int slotNeed = ingredients[i].getCount() * maxSupply;
                InvTasks.moveStackToSlot(screen, realStack, slot[i], slotNeed, removeOrigin, cachedSlots);
            }
        }
        for (var i: emptySlots){
            InvTasks.quickMoveSlotOrDrop(screen, i);
        }
    }

    @ApiMethod
    public static void moveStackFromTo(ScreenHandler handler, int fromIndex, int toSlot){
        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP,mc.player);
        mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
        if(!handler.getCursorStack().isEmpty()){
            mc.interactionManager.clickSlot(handler.syncId, fromIndex,0, SlotActionType.PICKUP, mc.player);
        }
    }
    @ApiMethod
    public static void moveStackFromToAmount(ScreenHandler handler, int fromIndex, int toSlot, int amount){
        Slot currentFrom = handler.getSlot(fromIndex);
        Slot currentTo = handler.getSlot(toSlot);
        int currentFromAmount = currentFrom.getStack().getCount();

        //from 的数量完全不够
        if(currentFromAmount <= amount){
            moveStackFromTo(handler, fromIndex, toSlot);
            return;
        }else {
            //from的数量超出了,我们只需要amount个
            int currentToAmount = currentTo.getStack().getCount();
            int max = currentFrom.getStack().getMaxCount();
            if(currentToAmount + amount >= max){
                //如果amount赛过去就满了《那和直接把from赛过去一样
                moveStackFromTo(handler, fromIndex, toSlot);
                return;
            }else {
                // amount < max - currentTo
                // currentFrom > amount
                for (int __=0; __< 10; ++__){
                    if(amount <=0){
                        return;
                    }
                    int halfTrans = (currentFromAmount + 1)/2;
                    int distanceToHalf = Math.abs(halfTrans - amount);
                    int minDelta = Math.min( Math.min(amount, currentFromAmount - amount), distanceToHalf);
                    if(minDelta == amount){
                        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
                        for (var i=0; i<amount;++i){
                            mc.interactionManager.clickSlot(handler.syncId, toSlot, 1, SlotActionType.PICKUP, mc.player);
                        }
                        if(!handler.getCursorStack().isEmpty()){
                            mc.interactionManager.clickSlot(handler.syncId, fromIndex,0, SlotActionType.PICKUP, mc.player);
                        }
                        return;
                    }else if(minDelta == currentFromAmount - amount){
                        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
                        for (int i= 0 ;i< minDelta; ++i){
                            mc.interactionManager.clickSlot(handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                        }
                        mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                        return;
                    }else {
                        //
                        if(halfTrans <= amount){
                            mc.interactionManager.clickSlot(handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                            //通过计算currentTo增长了多少来更新amount
                            amount = amount - currentTo.getStack().getCount() + currentToAmount;
                            currentToAmount = currentTo.getStack().getCount();
                            currentFromAmount = currentFrom.getStack().getCount();
                            continue;
                        }else {
                            mc.interactionManager.clickSlot(handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            int trans = (currentFromAmount+1)/2 - amount;
                            for (int i=0 ; i< trans; ++i){
                                mc.interactionManager.clickSlot(handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            }
                            mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                            return;
                        }
                    }

                }
                Debug.chat(Text.literal("Error while transfering itemStacks, which takes 10 more loop "));

            }
        }
    }


    public static int resizeCreativeYv(int y){
        return y-30;
    }
    private static final int MAX_INV_CACHE_SIZE = 256;
    private static int startCursor = 0;
    private static int endCursor = 0;
    private static MutableEntry<Pair<ClientWorld, BlockPos>, HandledScreen<?>>[] caches = new MutableEntry[MAX_INV_CACHE_SIZE];

    @ApiMethod
    public static List<HandledScreen<?>> getCachedInventories(){
        return IntStream.range(startCursor, (endCursor < startCursor)? (endCursor + MAX_INV_CACHE_SIZE) : endCursor)
            .map(i->i%MAX_INV_CACHE_SIZE)
            .mapToObj(i->caches[i])
            .map(i->i.value)
            .collect(Collectors.toCollection(ArrayList::new));
    }
    private static int nextCacheInt(int i){
        ++i;
        if(i >= MAX_INV_CACHE_SIZE){
            i = 0;
        }
        return i;
    }




    public static void registerTracedHandledScreen(HandledScreen<?> screen){
        if(screen instanceof CreativeInventoryScreen creativeInventoryScreen)return;
        Pair<ClientWorld, BlockPos> data;
        if(screen instanceof TileInventoryScreen tile && !tile.isVirtual()){
            BlockPos pos = tile.getPos();
            ClientWorld world = tile.getWorld();
            data = new Pair<>(world, pos);
            for (int i = startCursor ; i != endCursor; i = nextCacheInt(i)){
                MutableEntry<Pair<ClientWorld, BlockPos>, HandledScreen<?>> value = caches[i];
                if(value.key != null && Objects.equals(value.key.getSecond(), pos) && WorldUtils.areWorldEquals( value.key.getFirst() , world)){
                    value.value = screen;
                    return;
                }
            }
        }else {
            data = null;
        }
        //追加到队列末尾
        int index = endCursor;
        endCursor = nextCacheInt(endCursor);
        //如果队列已满，则从队列头驱逐一个元素
        if(endCursor == startCursor){
            startCursor = nextCacheInt(startCursor);
        }
        caches[index] = new MutableEntry<>(data, screen);
    }
    private static String lastServerName = null;
    //TODO: add cached if server same
    private static void refreshInventoryCache(Void v){
        String serverName = Utils.getServerName();
        if (!Objects.equals(serverName, lastServerName)){
            //refresh
            startCursor = endCursor = 0;
            Arrays.fill(caches, null);
        }
        lastServerName = serverName;
    }
    @ApiMethod
    public static boolean openInventoryCacheScreen(){
        if(mc.player == null)return false;
        ScreenAccess.of(new InventorySelectScreen(InvTasks::getCachedInventories)).openFromCurrent();
        return true;
    }
    public static final ItemStack INV_ICON_UNKNOWN = new ItemStack(Items.BARRIER);
    private static final ItemStack INV_ICON_NO_ITEM = new ItemStack(Items.BEDROCK);
    public static ItemStack generateInvIcon(HandledScreen<?> screen){
        if(screen instanceof TileInventoryScreen tile&& !tile.isVirtual()){
            Block blockType = tile.getBlockType();
            if(blockType != null){
                Item itemType = blockType.asItem();
                if(itemType != Items.AIR){
                    return new ItemStack(itemType);
                }
            }
            return INV_ICON_NO_ITEM;
        }
        return INV_ICON_UNKNOWN;
    }
    //suppress random source use when dropItem
    public static final ThreadLocal<Boolean> SUPPRESS_DROPITEM_SPAWN = ThreadLocal.withInitial(()-> false);

    public static void clickSlotAsync(int slotId, int button, SlotActionType actionType){
        if(mc.player == null)return;
        HandledScreen<?> screen = ClientPlayerAccess.of(mc.player).getServerHandledScreen();
        //handler or player inv
        ScreenHandler screenHandler = screen == null ? mc.player.currentScreenHandler : screen.getScreenHandler();
        int syncId = screenHandler.syncId;
        DefaultedList<Slot> defaultedList = screenHandler.slots;
        int i = defaultedList.size();
        List<ItemStack> list = Lists.newArrayListWithCapacity(i);

        for (Slot slot : defaultedList) {
            list.add(slot.getStack().copy());
        }

        InvTasks.SUPPRESS_DROPITEM_SPAWN.set(true);
        //catch all exceptions that might occur
        try{
            screenHandler.onSlotClick(slotId, button, actionType, mc.player);
        }finally{
            InvTasks.SUPPRESS_DROPITEM_SPAWN.set(false);
        }

        Int2ObjectMap<ItemStack> int2ObjectMap = new Int2ObjectOpenHashMap();

        for(int j = 0; j < i; ++j) {
            ItemStack itemStack = (ItemStack)list.get(j);
            ItemStack itemStack2 = ((Slot)defaultedList.get(j)).getStack();
            if (!ItemStack.areEqual(itemStack, itemStack2)) {
                int2ObjectMap.put(j, itemStack2.copy());
            }
        }

        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(syncId, screenHandler.getRevision(), slotId, button, actionType, screenHandler.getCursorStack().copy(), int2ObjectMap));
    }

    private static final Config.IntRef SPEED= Configs.INV_CONFIG.getInt(Configs.INV_CLICK_LIMIT);
    @Getter
    private static final LimitedSpeedExecutor clickExecutor=new LimitedSpeedExecutor(SPEED);
    static{
        Tasks.registerGameTask(r->{
            clickExecutor.reset();
        });
        Tasks.registerGameTask(InvTasks::specialInventoryTick);
        Listener.getGameJoinPoint().registerHandler(InvTasks::refreshInventoryCache);
    }
}
