package me.matl114.hackUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.access.ScreenAccess;
import me.matl114.gui.TestingScreen2;
import me.matl114.gui.itemEdit.ItemEditScreen;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.SlimefunHelper;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

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
    public static boolean isPeriod(int period){
        return tickCounter % period == 0;
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
           // Debug.info(test);
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
    public static void doTryBeaconPacket(){
        int t;
        try{
            t=Integer.parseInt(HotKeys.SHARED_ARGUMENT.get());
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        int a;
        try{
            a=Integer.parseInt(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            a = 0;
            Debug.chat("Invalid argument passed");
        }
        StatusEffect effect1 = Registries.STATUS_EFFECT.get(t);
        StatusEffect effect2 = Registries.STATUS_EFFECT.get(a);
        Debug.info("updated", effect1 == null ? null : Registries.STATUS_EFFECT.getId(effect1) , effect2 == null ? null : Registries.STATUS_EFFECT.getId(effect2));
        mc.getNetworkHandler().sendPacket(new UpdateBeaconC2SPacket(Optional.ofNullable( Registries.STATUS_EFFECT.get(t)),Optional.ofNullable(Registries.STATUS_EFFECT.get(a))));
    }
    public static void doTest(){
        //doTryBeaconPacket();
     //   ItemEditTasks.openEditScreenLater(mc.player.getMainHandStack().copy(), null);
        ScreenAccess.of(new TestingScreen2(Text.empty())).openFromCurrent();
//        Debug.info(Text.empty().append(Text.literal("byd")).append(Text.literal("byd").withColor(114514)).append(Text.literal("byd").withColor(1919810)).withColor(13333).getString());
//        Debug.info(Text.literal("byder").withColor(14444).getString());
//        Screen screen = mc.currentScreen;
//        if(screen != null){Debug.info(screen.getTitle().getString());
//        }
        String val = "&a666&7yyy";
        MutableText text = ChatUtils.stringToText(val);
        Debug.chat(text);
        Debug.info(ChatUtils.textToString(text));
    }

    public static void runTask(String taskId, String[] args){

    }

    public static void doTryInteractWithNPCInDifferentDimension()  {
       // Debug.info(Registries.ITEM.getRawId( ItemBridge.TESTITEM));
        int t;
        try{
            t=Integer.parseInt(HotKeys.SHARED_ARGUMENT.get());
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        int attack;
        try{
            attack=Integer.parseInt(HotKeys.SHARED_ARGUMENT_2.get());
        }catch (Throwable e){
            attack = 0;
            Debug.chat("Invalid argument passed");
        }
        var screen = ClientPlayerAccess.of(mc.player).getServerHandledScreen();
        var handler = screen.getScreenHandler();
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), t, 0, SlotActionType.PICKUP,handler.getCursorStack(),new Int2ObjectOpenHashMap<>()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), attack, 0, SlotActionType.PICKUP,handler.getCursorStack(),new Int2ObjectOpenHashMap<>()));
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), t, 0, SlotActionType.PICKUP,handler.getCursorStack(),new Int2ObjectOpenHashMap<>()));
////        Constructor<PlayerInteractEntityC2SPacket> constructor =(Constructor<PlayerInteractEntityC2SPacket>) Arrays.stream(PlayerInteractEntityC2SPacket.class.getConstructors()).filter(c->c.getParameters().length==3).findAny().orElse(null);
//        PlayerInteractEntityC2SPacket packet= switch (attack){
//            case 0 -> new PlayerInteractEntityC2SPacket(t,false, PlayerInteractEntityC2SPacket.ATTACK);
//            case 1 -> new PlayerInteractEntityC2SPacket(t,false, new PlayerInteractEntityC2SPacket.InteractHandler(Hand.MAIN_HAND));
//            case 2 -> {
//                var re = mc.world.getEntityById(t);
//                if(re != null){
//                    Debug.info("using interactAt packet");
//                    yield  PlayerInteractEntityC2SPacket.interactAt(re, false,Hand.MAIN_HAND, re.getEyePos());
//                }else {
//                    Debug.info("Entity not found ,using interact packet");
//                    yield new PlayerInteractEntityC2SPacket(t,false, new PlayerInteractEntityC2SPacket.InteractHandler(Hand.MAIN_HAND));
//                }
//            }default -> new PlayerInteractEntityC2SPacket(t,false, new PlayerInteractEntityC2SPacket.InteractHandler(Hand.MAIN_HAND));
//        };
////        try{
////            packet=constructor.newInstance();
////        }catch (Throwable e){
////            Debug.chat("Error");
////            Debug.info(constructor);
////            return;
////        }
//        MinecraftClient.getInstance().getNetworkHandler().sendPacket(
//               packet
//        );
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
    public static void doMineDetectAllPacketSent(){
        //1145,66,5145
        String value= HotKeys.SHARED_ARGUMENT.get();
        final double x,y,z;
        try{
            String[] vals = value.split(",");
            x = Double.parseDouble(vals[0]);
            y = Double.parseDouble(vals[1]);
            z = Double.parseDouble(vals[2]);
        }catch (Throwable e){
            Debug.info("Invalid argument passed",value);
            return;
        }

        Debug.info("using argument",x,y,z);
        mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> {
            addSequencePacketUpdateCallback(sequence, ()->{
                Debug.info("callback of blockpos mining",x,y,z,"called");
                return TimedPacketCatcher.REMOVAL & TimedPacketCatcher.CANCEL;
            });
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos((int) x, (int) y, (int) z), Direction.UP, sequence);
        }));
    }
    public static void doButtonTaskTest1(){
        //instance.doContainerPacketClickAndDragInternal();
        instance.doShulkerTryDupePacket();
    }
    public static void doHokeyTaskTest1(){
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

    public static void doContainerPacketClickAndDragInternal(){
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
//        if(packet instanceof InventoryS2CPacket packet1){
//            Debug.info("Inventory Packet here");
//        }
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
        if(true)
            return true;
        if(packet instanceof EntitySpawnS2CPacket || packet instanceof EntitiesDestroyS2CPacket ){
            return true;
        }
        if(mc.player == null)return true;
        if(packet instanceof PlayerPositionLookS2CPacket tp){
            Debug.info("Accept "+packet.getClass().getSimpleName());
            Debug.info("check teleport", tp.getTeleportId(), tp.getX(), tp.getY(), tp.getZ() );
            Debug.info("current player", mc.player.getPos());
        }
      //  listenSomePacket(packet);
//        if(packet instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdate){
//            Debug.info("slot", slotUpdate.getSlot(), slotUpdate.getStack());
//        }
//        if(packet instanceof InventoryS2CPacket inventory){
//            Debug.info("inventory", inventory.getContents(), inventory.getCursorStack());
//        }
        return true;
    }
    public static boolean doPacketListenApply(Packet<?> packet){
//        if(packet instanceof InventoryS2CPacket packet1){
//            Debug.info("Inventory packet", packet1.getSyncId(), "with client, ", mc.player.currentScreenHandler.syncId);
//        }
        if(packet instanceof TeleportConfirmC2SPacket packet1){
            Debug.info("apply confirm", packet1.getTeleportId());
        }
        return true;
    }

    public static boolean doPacketListenOut(Packet<?> packet){
     //   tryTridentDupe(packet);
        return true;
    }
    public static void listenSomePacket(Packet<?> packetIn){
        if(packetIn instanceof ParticleS2CPacket || packetIn instanceof EntityPositionS2CPacket || packetIn instanceof EntityTrackerUpdateS2CPacket || packetIn instanceof EntityS2CPacket || packetIn instanceof EntitySetHeadYawS2CPacket || packetIn instanceof EntityVelocityUpdateS2CPacket || packetIn instanceof OverlayMessageS2CPacket || packetIn instanceof WorldTimeUpdateS2CPacket || packetIn instanceof EntityAttributesS2CPacket || packetIn instanceof EntityEquipmentUpdateS2CPacket || packetIn instanceof EntitySpawnS2CPacket || packetIn instanceof EntityStatusS2CPacket || packetIn instanceof EntitiesDestroyS2CPacket || packetIn instanceof BossBarS2CPacket || packetIn instanceof PlayerListS2CPacket || packetIn instanceof PlaySoundS2CPacket || packetIn instanceof ChunkDataS2CPacket || packetIn instanceof CommonPingS2CPacket || packetIn instanceof HealthUpdateS2CPacket || packetIn instanceof UnloadChunkS2CPacket || packetIn instanceof TeamS2CPacket || packetIn instanceof ScoreboardScoreUpdateS2CPacket || packetIn instanceof ScoreboardScoreResetS2CPacket || packetIn instanceof EntityAnimationS2CPacket){
            //ignore useless informations
            return;
        }
        if(packetIn instanceof BlockEntityUpdateS2CPacket packet){
            Debug.info("accept blockEntity update at",packet.getPos(),packet.getNbt());
        }else if(packetIn instanceof BlockUpdateS2CPacket packet){
            Debug.info("accept block Update at",packet.getPos());
        }else if(packetIn instanceof WorldEventS2CPacket packet){
            Debug.info("accept worldEvent at",packet.getPos(),packet.getEventId());
        }
//        else if(packetIn instanceof ChunkDataS2CPacket packet){
//            Debug.info("accept chunk data at",packet.getChunkX(),packet.getChunkZ());
//        }
        else{
            Debug.info("accept packet ",packetIn.getClass().getSimpleName());
        }
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
    public static void trySendPickItemPacket(){
        String value= HotKeys.SHARED_ARGUMENT.get();
        int t;
        try{
            t=Integer.parseInt(value);
        }catch (Throwable e){
            Debug.chat("Invalid argument passed");
            return;
        }
        mc.interactionManager.pickFromInventory(t);
    }
    private static final Map<Class<?>,ArrayDeque<TimedPacketCatcher<?>>> maped = new ConcurrentHashMap<>();
    public static void addSequencePacketUpdateCallback(int sequence, IntSupplier callback){
        addPacketCatcher(new TimedPacketCatcher<PlayerActionResponseS2CPacket>(PlayerActionResponseS2CPacket.class,20) {
            @Override
            public int catchPacket(PlayerActionResponseS2CPacket packet) {
                Debug.info(packet.sequence(),sequence);
                if(packet.sequence() == sequence){
                    return callback.getAsInt();
                }
                return (~REMOVAL & ~ CANCEL);
            }
        });
    }
    public static <T extends Packet<?>> void addPacketCatcher(TimedPacketCatcher<T> packet){
        var re =  maped.computeIfAbsent(packet.clazz, k -> new ArrayDeque<>());
        synchronized (re){
            re.addLast(packet);
        }
    }
    @AllArgsConstructor
    public static abstract class TimedPacketCatcher<T extends Packet<?>>{
        Class<T> clazz;
        int waitTick;
        public boolean count(){
            return --waitTick < 0;
        }
        public int catchPkt(Packet<?> packet){
           // Debug.info("catch pkt?");
            if(clazz.isInstance(packet)){
                return catchPacket((T)packet);
            }else {
                return (~REMOVAL & ~ CANCEL);
            }
        }
        public static final int REMOVAL = 1;
        public static final int CANCEL = 2;
        public void timeoutCallback(){
            Debug.info("timeout waiting for packet ",clazz.getSimpleName());
        }
        //return if removal at mask 1, cancel at mask 2
        public abstract int catchPacket(T packet);
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
    private static Vec3d lastPosDragBack ;
    private static int lastPosUpdateStamp;
    static{
        if(SlimefunHelper.HACK_VERSION){
            RenderTasks.init();
            MineTasks.init();
            InvTasks.init();
            ChatTasks.init();
            CombatTasks.init();
            MovTasks.init();
            SlimefunTasks.init();
        }else {
            ChatTasks.init();
            SlimefunTasks.init();
        }

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
//            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.HOTKEY_TEST1)){
//                Tasks.sendDropAllPacket();
//            }
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
        Predicate<Packet<?>> catcher = (packet -> {
            var identifier = Listener.getMappedPacketClass(packet.getClass());
            var handlers = maped.get(identifier);
            if(handlers != null && !handlers.isEmpty()){
                synchronized (handlers){
                    var iter = handlers.iterator();
                    while (iter.hasNext()){
                        var handler = iter.next();
                        int code = handler.catchPkt(packet);
                        if((code & TimedPacketCatcher.REMOVAL) != 0){
                            iter.remove();
                        }
                        if((code & TimedPacketCatcher.CANCEL) != 0){
                            return false;
                        }
                    }
                }
            }

            return true;
        });
        // Listener.registerPacketListener(catcher, true);
        Listener.getMainThreadPacketPreApplyPoint().registerHandler((p, o)-> catcher.test(p));
        Listener.registerPacketListener(catcher, false);
        registerTickTask(()->{
            for (Map.Entry<Class<?>, ArrayDeque<TimedPacketCatcher<?>>> entry : maped.entrySet()) {
                //remove when timeout ,(TimeUnit:tick)
                var handlers = entry.getValue();
                synchronized (handlers) {
                    var iter2 = handlers.iterator();
                    while (iter2.hasNext()) {
                        var handler = iter2.next();
                        if (handler.count()) {
                            handler.timeoutCallback();
                            iter2.remove();
                        }
                    }
                }
            }
        });
        Listener.getMainThreadPacketPreApplyPoint().registerHandler((packet, objects) -> doPacketListenApply(packet));

//        Listener.registerPacketListener(packet->{
//            if(mc.player!=null && packet instanceof PlayerPositionLookS2CPacket loopUp && HotKeys.getHotkeyToggleManager().getState(HotKeys.HOTKEY_TEST1)){
//                Debug.info("check loopup packet",loopUp.getX(),loopUp.getY(),loopUp.getZ());
//                if(MathUtils.squareSum(loopUp.getX() - mc.player.getX(), loopUp.getY() - mc.player.getY(), loopUp.getZ() - mc.player.getZ())< 16){
//                    Vec3d dragBackPos = new Vec3d(loopUp.getX(), loopUp.getY(), loopUp.getZ());
//                    if(lastPosDragBack == null || lastPosDragBack.squaredDistanceTo(dragBackPos) > 0.1 ){
//                        lastPosDragBack = dragBackPos;
//                        lastPosUpdateStamp = Tasks.getTick();
//                        Debug.info("update Position Record");
//                    }else{
//                        Debug.info("reject PositionLoopUp");
//                        return false;
//                    }
//
//                }
//            }
//            return true;
//        },true);
//        registerGameTask((playerEntity) -> {
//            if(lastPosDragBack != null && lastPosUpdateStamp +100 < Tasks.getTick()){
//                //out of data record, reset to null
//                lastPosDragBack = null;
//            }
//        });
//        Listener.registerPacketListener((packet->{
//            if( packet instanceof PlayerPositionLookS2CPacket packet1){
//
//            }
//        }),true);
    }
}
