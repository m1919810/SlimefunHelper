package me.matl114.hackUtils;

import me.matl114.SlimefunHelper;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;
import java.util.function.Consumer;

public class AntiGrimTasks {
    public static void init(){

    }
    //represent that is there any anti-cheats transactions
    private static int peekPingRequest;
    private static int lastPingTick;
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    //anti grim's post check
    //sent after pong packet
    private static final Deque<Consumer<ClientPlayNetworkHandler>> queuePackets = new ArrayDeque<>( 33);

    public static void addPostTransactionAction(Consumer<ClientPlayNetworkHandler> packet){
        if(!SlimefunHelper.HACK_VERSION || peekPingRequest == 0){
            if(mc.getNetworkHandler() != null){
                packet.accept(mc.getNetworkHandler());
            }
        }else {
            queuePackets.addLast(packet);
        }
    }

    public static void sendRuntimePostPacket(Packet<?> packet){
        //IF NOT hackversion, do not enable antigrim
        if(!SlimefunHelper.HACK_VERSION || peekPingRequest == 0){
            //there is no anticheat
            if(mc.getNetworkHandler() != null){
                mc.getNetworkHandler().sendPacket(packet);
            }
            //network disconnected
        }else {
            queuePackets.addLast((ch)->ch.sendPacket(packet));
        }
    }
    public static void peekPingPacketIn(CommonPingS2CPacket packetPing){
        peekPingRequest += 1;
        lastPingTick = Tasks.getTick();
//        Debug.info("in", packetPing.getPacketId(), peekPingRequest);
    }
    public static void postPongPacketOut(Packet<?> packet, Object[] args){
        //we sent the Common Pong in Ping's handle
        if(packet instanceof CommonPingS2CPacket pong){
            //end transaction,
            //fresh queue
            peekPingRequest -= 1;
//            Debug.info("out",pong.getPacketId(), peekPingRequest);

            //fix anything wrong wtf
            if(peekPingRequest < 0 )peekPingRequest = 0;
            //anyway ,flush
            runAllPackets(mc.getNetworkHandler());
        }
    }
    private static void runAllPackets(ClientPlayNetworkHandler handler){
        if(!queuePackets.isEmpty()){
            if(handler != null){
                var iter = queuePackets.iterator();
                while (iter.hasNext()){
                    iter.next().accept(handler);
                    iter.remove();
                }
            }else {
                queuePackets.clear();
            }
        }
    }
    private static void onDisconnectReset(Void v){
        peekPingRequest = 0;
    }
    private static void onWatchPingLongTimeNoSent(){
        if(peekPingRequest > 0 && lastPingTick + 20 < Tasks.getTick()){
            peekPingRequest = 0 ;
            lastPingTick = Tasks.getTick();
            runAllPackets(mc.getNetworkHandler());
        }
    }

    static{
        Listener.registerSinglePacketListener(CommonPingS2CPacket.class, AntiGrimTasks::peekPingPacketIn);
        Listener.getMainThreadPacketPostApplyPoint().registerHandler(AntiGrimTasks::postPongPacketOut);
        Listener.getServerDisconnectPoint().registerHandler(AntiGrimTasks::onDisconnectReset);
        Tasks.registerTickTask(AntiGrimTasks::onWatchPingLongTimeNoSent);
    }
}
