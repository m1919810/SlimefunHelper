package me.matl114.HackUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.ListenerUtils.Listener;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class Tasks {
    public static void init(){

    }
    private Tasks(){

    }
    @Getter
    private static Tasks instance = new Tasks();
    private static Random randomContext = new Random();
    private static volatile int tickCounter;
    @Getter
    private static volatile int tickRandom;
    private static volatile int secondCounter;
    @Getter
    private static volatile int sencondRandom;
    public static int getTick(){
        return tickCounter;
    }
    public static int getSecond(){
        return secondCounter;
    }
   // private static final AtomicInteger tickRandomSource = new AtomicInteger(0);
    //todo find where is the error when 17 name login
    //finded ,at packet
    //todo find how to dupe with ITEM
    private static final MinecraftClient mc = MinecraftClient.getInstance();
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
            String test="Start running 12 test";
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

                    }while (running);
                }catch(Throwable e){
                    Debug.info(e);
                    running=false;
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
    public static void doTryInteractWithNPCInDifferentDimension()  {
        String value= HotKeys.SHARED_ARGUMENT.get();
        int t;
        try{
            t=Integer.parseInt(value);
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        int attack;
        try{
            attack=Integer.parseInt(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            attack = 0;
        }
//        Constructor<PlayerInteractEntityC2SPacket> constructor =(Constructor<PlayerInteractEntityC2SPacket>) Arrays.stream(PlayerInteractEntityC2SPacket.class.getConstructors()).filter(c->c.getParameters().length==3).findAny().orElse(null);
        PlayerInteractEntityC2SPacket packet = new PlayerInteractEntityC2SPacket(t,false, attack==0? PlayerInteractEntityC2SPacket.ATTACK: new PlayerInteractEntityC2SPacket.InteractHandler(Hand.MAIN_HAND));
//        try{
//            packet=constructor.newInstance();
//        }catch (Throwable e){
//            Debug.chat("Error");
//            Debug.info(constructor);
//            return;
//        }
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(
               packet
        );
    }
    public static void doTeleport()  {
        String value= HotKeys.SHARED_ARGUMENT.get();
        double x;
        try{
            x = Double.parseDouble(value);
        }catch (Throwable e){
            x = 0;
        }
        double y;
        try{
            y = Double.parseDouble(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            y = 0;
        }
//        Constructor<PlayerInteractEntityC2SPacket> constructor =(Constructor<PlayerInteractEntityC2SPacket>) Arrays.stream(PlayerInteractEntityC2SPacket.class.getConstructors()).filter(c->c.getParameters().length==3).findAny().orElse(null);
        Debug.info("using argument",x,y);
        mc.player.setPos(mc.player.getX()+x, mc.player.getY(), mc.player.getZ()+y);
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(),mc.player.getY(),mc.player.getZ(),true);
//        PlayerInteractEntityC2SPacket packet = new PlayerInteractEntityC2SPacket(t,false, attack==0? PlayerInteractEntityC2SPacket.ATTACK: new PlayerInteractEntityC2SPacket.InteractHandler(Hand.MAIN_HAND));
//        try{
//            packet=constructor.newInstance();
//        }catch (Throwable e){
//            Debug.chat("Error");
//            Debug.info(constructor);
//            return;
//        }
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(
            packet
        );
    }
    public static void doButtonTaskTest1(){
        //instance.doContainerPacketClickAndDragInternal();
        instance.doShulkerTryDupePacket();
    }
    public static void doHokeyTaskTest1(){
        doTeleport();
    }
    public void doShulkerTryDupePacket(){
        if(mc.currentScreen instanceof ShulkerBoxScreen shulkerBoxScreen){
            ScreenHandler handler=shulkerBoxScreen.getScreenHandler();
            BlockPos shulkerBlock=MineTasks.rayTraceBlock(mc.player);
            if(shulkerBlock!=null){

                Debug.info(shulkerBlock);
//                var re= PlayerInteractionAccess.of( mc.interactionManager);
//                float speed=re.calculateBreakingSpeed(shulkerBlock);
//                re.sendStartBreakPacket(shulkerBlock,Direction.UP);
//                Debug.info("should send start!");
//                float sum=speed;
//                while(sum<0.72){
//                    sum+=speed;
//                    try{
//                        Thread.sleep(50);
//                    }catch (Throwable e){e.printStackTrace();
//                    }
//                }
                //can not bypass canUse check
                //re.sendStopBreakPacket(shulkerBlock,Direction.UP);

                for (int i=0;i<9;++i){
                    mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), i, 1, SlotActionType.QUICK_MOVE, handler.getCursorStack(), new Int2ObjectOpenHashMap<>()));
                }
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, shulkerBlock, Direction.UP, mc.world.getPendingUpdateManager().getSequence()));
//                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, shulkerBlock, Direction.UP, mc.world.getPendingUpdateManager().getSequence()));
                Debug.info("send all slotpacket!");
                Debug.info("should send stop!");

            }
        }
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
    public boolean ignoreSlotPacketWhileRun(Packet<?> packet){
        if(running &&(packet instanceof ScreenHandlerSlotUpdateS2CPacket||packet instanceof InventoryS2CPacket) ){
           //Debug.info("114");
            return false;
        }
        return true;
    }
    public static boolean doPacketListenIn(Packet<?> packet){
        if(!instance.ignoreSlotPacketWhileRun(packet)){
            return false;
        }

        return true;
    }
    public static boolean doPacketListenOut(Packet<?> packet){
     //   tryTridentDupe(packet);
        return true;
    }
    public static void tryTridentDupe(Packet<?> packet){
        if(packet instanceof PlayerActionC2SPacket packet1 && packet1.getAction()== PlayerActionC2SPacket.Action.RELEASE_USE_ITEM ){
            Debug.info("try!");
            Debug.info("slot ",mc.player.getInventory().selectedSlot);
            //swap selected slot to recipeSlot 3
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
//            if(dropTridents.get())mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 44, 0, SlotActionType.THROW, mc.player);
        }
    }
    @AllArgsConstructor
    static abstract class TimedTask  {
        abstract boolean runTask();
        int delay;
        boolean execute(){
            if(--delay <= 0 ){
                return runTask();
            }
            return false;
        }
    }

    static class DelayedTimedTask extends TimedTask {
        Runnable task;

        public DelayedTimedTask(Runnable runnable, int delay) {
            super(delay);
            this.task = runnable;
        }

        @Override
        boolean runTask() {
            task.run();
            return true;
        }
    }
    static class RepeatTimedTask extends TimedTask{

        boolean isCancelled = false;
        BooleanSupplier task;
        int period ;

        public RepeatTimedTask(BooleanSupplier shouldStop, int delay, int period) {
            super(delay);
            this.task = shouldStop;
            this.period = period;
        }

        @Override
        boolean runTask() {
            if(isCancelled ){
                return true;
            }
            if(task.getAsBoolean()){
                isCancelled = true;
                return true;
            }else{
                delay = period;
                return false;
            }
        }
    }
    private static final Deque<TimedTask> taskQueue = new ConcurrentLinkedDeque<>();
    public static void scheduleDelayed(Runnable task, int delay){
        taskQueue.addLast(new DelayedTimedTask(task, delay));
    }
    public static void scheduleRepeated(BooleanSupplier task, int delay, int period){
        taskQueue.addLast(new RepeatTimedTask(task, delay, period));
    }
    static{
        RenderTasks.init();
        MineTasks.init();
        InvTasks.init();
        ChatTasks.init();
        CombatTasks.init();
        MovTasks.init();
        registerTickTask(()->{
            ++ tickCounter;
            tickRandom = randomContext.nextInt();
            if(tickCounter < 0){
                tickCounter = 0;
            }else if(tickCounter % 20 == 0){
                ++secondCounter ;
                sencondRandom = randomContext.nextInt();
            }
        });
        registerGameTask((playerEntity) -> {
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.HOTKEY_TEST1)){
                Tasks.sendDropAllPacket();
            }
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.AUTO_ATTACK)){
                CombatTasks.handleAutoAttack(playerEntity);
            }
        });
        registerTickTask(()->{
            var iter = taskQueue.iterator();
            while (iter.hasNext()){
                try{
                    var task = iter.next();
                    if(task.execute()){
                        iter.remove();
                    }
                }catch (Throwable e){
                    Debug.info("unexpected error while executing TimedTask:");
                    Debug.info(e);
                    iter.remove();
                }
            }
        });
        Listener.registerPacketListener(Tasks::doPacketListenIn,true);
        Listener.registerPacketListener(Tasks::doPacketListenOut,false);
//        Listener.registerPacketListener((packet->{
//            if( packet instanceof PlayerPositionLookS2CPacket packet1){
//
//            }
//        }),true);
    }
}
