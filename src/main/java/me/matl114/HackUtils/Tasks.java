package me.matl114.HackUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Tasks {
    public static void init(){

    }
    private Tasks(){

    }
    @Getter
    private static Tasks instance = new Tasks();

    //todo find where is the error when 17 name login
    //finded ,at packet
    //todo find how to dupe with ITEM
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final HashSet<Runnable> tasks = new LinkedHashSet<>();
    private final HashSet<Consumer<ClientPlayerEntity>> gameTasks = new LinkedHashSet<>();
    public static void registerTickTask(Runnable r){
         instance.tasks.add(r);
    }
    //run when player is not null
    public static void registerGameTask(Consumer<ClientPlayerEntity> r){
        instance.gameTasks.add(r);
    }
    public static void doTick(){
        instance.tasks.forEach(Runnable::run);
    }
    public static void doGameTick(ClientPlayerEntity player){
        instance.gameTasks.forEach(i->i.accept(player));
    }
    public static void sendDropAllPacket(){
        instance.sendDropAllPacketInternal();
    }
    private void sendDropAllPacketInternal(){
        for (int i=0;i<8;++i){
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM,BlockPos.ofFloored(0,0,0), Direction.DOWN));
        }
    }
    private boolean swapState=false;
    private CompletableFuture<Void> future=null;
    private boolean running=false;
    private final Random random=new Random();
    private final AtomicInteger delay= Configs.TEST_CONFIG.getInt(Configs.TEST_ARGS1);
    private final AtomicInteger bigDelay= Configs.TEST_CONFIG.getInt(Configs.TEST_ARGS2);
    private int counter=0;
    public static void sendItemSwapPacket(){
        instance.sendItemSwapPacketInternal();
    }
    private void sendItemSwapPacketInternal(){
//        slimefunTick++;
//        if(slimefunTick<5){
//
//            return;
//        }else if(slimefunTick>21){
//            slimefunTick=0;
//            return;
//        }
        running=true;

        if(future == null){
            String test="Start running 9 test";
            Debug.info(test);
            future = CompletableFuture.runAsync(()->{
                try{
                    do {
    //
                        if (counter>delay.get()) {
                            counter=0;
                            try {
                                Thread.sleep(bigDelay.get());
                            } catch (Throwable e) {
                            }
                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
                                    new ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId, MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 25, 40, SlotActionType.SWAP, MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
                            );
                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
                                    new ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId, MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 19, 40, SlotActionType.SWAP, MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
                            );

                            swapState=false;
                        }else{

                            counter+=10_000;
                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
                                    new ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId, MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 19, 40, SlotActionType.SWAP, MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
                            );

                            long a=System.nanoTime()+10_000;
                            do{
                            }while (System.nanoTime()<a);
                        }

    //                    try {
    ////                        Thread.sleep( switch (swapTick%3){
    ////                            case 0: yield 3;
    ////                            case 1: yield 7;
    ////                            case 2: yield 11;
    ////                            default:
    ////                                yield 13;
    ////                        });
    ////
    //                        //Thread.sleep(delay.get());
    //                        //Thread.sleep(3+random.nextInt(8));
    //                    } catch (Throwable e) {
    //                    }
                    }while (running);
                }catch(Throwable e){
                    Debug.info(e);
                }
            });

        }

    }
    public static void stopItemSwapPacket(){
        instance.stopItemSwapPacketInternal();
    }
    public void stopItemSwapPacketInternal(){
        running=false;
        if(future!=null){
            future.cancel(true);
            future=null;
        }
    }

    public void doContainerPacketExplosion(){
        String value= HotKeys.SHARED_ARGUMENT.get();
        int t;
        try{
            t=Integer.parseInt(value);
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        int iter;
        try{
            iter=Integer.parseInt(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }

//        ItemStack stack=new ItemStack(Items.BARRIER,114514);
//        NbtList list=new NbtList();
//        for (int i=0;i<114;++i){
//            list.add(new NbtCompound());
//        }
//        stack.getOrCreateNbt().put("114",list);
        for (int i=0;i<iter;++i){
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
                    new ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId, MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), t, 40, SlotActionType.SWAP, MinecraftClient.getInstance().player.currentScreenHandler.getSlot(13).getStack(), new Int2ObjectOpenHashMap<>())
            );
        }
    }
    public static void doButtonTaskTest1(){
        instance.doContainerPacketClickAndDragInternal();
    }
    public void doContainerPacketClickAndDragInternal(){
        String value= HotKeys.SHARED_ARGUMENT.get();
        int t;
        try{
            t=Integer.parseInt(value);
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        int iter;
        try{
            iter=Integer.parseInt(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(mc.player.currentScreenHandler.syncId,mc.player.currentScreenHandler.getRevision(),
                t,0,SlotActionType.PICKUP,mc.player.currentScreenHandler.getSlot(13).getStack(),new Int2ObjectOpenHashMap<>()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(mc.player.currentScreenHandler.syncId,mc.player.currentScreenHandler.getRevision(),
                -999, ScreenHandler.packQuickCraftData(0,0),SlotActionType.QUICK_CRAFT,mc.player.currentScreenHandler.getSlot(13).getStack(),new Int2ObjectOpenHashMap<>()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(mc.player.currentScreenHandler.syncId,mc.player.currentScreenHandler.getRevision(),
                iter, ScreenHandler.packQuickCraftData(1,0),SlotActionType.QUICK_CRAFT,mc.player.currentScreenHandler.getSlot(13).getStack(),new Int2ObjectOpenHashMap<>()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(mc.player.currentScreenHandler.syncId,mc.player.currentScreenHandler.getRevision(),
                -999, ScreenHandler.packQuickCraftData(2,0),SlotActionType.QUICK_CRAFT,mc.player.currentScreenHandler.getSlot(13).getStack(),new Int2ObjectOpenHashMap<>()));
    }
    static{
        RenderTasks.init();
        MineTasks.init();
        InvTasks.init();
        ChatTasks.init();
        CombatTasks.init();
        registerGameTask((playerEntity) -> {
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.HOTKEY_TEST1)){
                Tasks.sendDropAllPacket();
            }
            if(HotKeys.getButtonToggleManager().getState("test1")){
                Tasks.sendItemSwapPacket();
            }else{
                Tasks.stopItemSwapPacket();
            }
        });
    }
}
