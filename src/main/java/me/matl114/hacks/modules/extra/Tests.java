package me.matl114.hacks.modules.extra;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class Tests extends BaseModule {
    public static final String[] TEST_ARGS1={"test","arg1"};
    public static final String[] TEST_ARGS2={"test","arg2"};
    public static final String[] TEST_MOVEMENT_TEST = {"test", "movement-test-1"};
    public static final String[] TEST_HOTKEY = {"hotkeys", "test-func"};

    public static final String[] TEST_TOGGLE_1 = {"hotkeys-toggle", "hktest1"};
    public static final String[] TEST_TOGGLE_2 = {"hotkeys-toggle", "hktest2"};
    public static final String[] TEST_TOGGLE_3 = {"hotkeys-toggle", "hktest3"};
    public static final String[] TEST_TOGGLE_4 = {"hotkeys-toggle", "hktest4"};



    private final IntRef delay= builder(Configs.TEST_CONFIG, TEST_ARGS1, IntRef.TYPE)
        .defaultValue(480000)
        .build()
        ;
    private final IntRef bigDelay= builder(Configs.TEST_CONFIG, TEST_ARGS2, IntRef.TYPE)
        .defaultValue(461)
        .build()
        ;

    public final KeyBindRef key1 = toggleHotkey(TEST_TOGGLE_1, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_1))
        .build();

    public final KeyBindRef key2 = toggleHotkey(TEST_TOGGLE_2, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_2))
        .build();

    public final KeyBindRef key3 = toggleHotkey(TEST_TOGGLE_3, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_3))
        .build();

    public final KeyBindRef key4 = toggleHotkey(TEST_TOGGLE_4, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_4))
        .build();


    private final KeyBindRef testKeyBind = hotkey(TEST_HOTKEY)
        .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T))
        .registerHotkey(HotKeyUtils.wrapAsHandler(this::doTest))
        .build();
    private boolean swapState=false;
    private CompletableFuture<Void> future=null;
    private boolean running=false;
    private final Random random=new Random();

    private int counter=0;

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
    public void stopItemSwapPacketInternal(){
        running=false;
        if(future!=null){
            future.cancel(true);
            future=null;
        }
    }

    public void doTest(){
        if(mc.player == null)return;

        //todo: try fix tp-into-lava issue
        Vec3d target = mc.player.getPos().add(mc.player.getRotationVector().multiply(20));
        RenderTasks.drawBox(mc.player.dimensions.getBoxAt(target), 200, Color.RED);
        MovTasks.generateTpSequence(mc.player.getPos(), target, true, 1000, true);

        //todo: try to simulate a explosion to escape anti cheat
        //todo: try to send clientbound packets to server (wtf to see if grimac got mistaken)
        //todo: try to gain advantage from OnGround packets

    }
}
