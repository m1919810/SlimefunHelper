package me.matl114.hackUtils;

import me.matl114.listenerUtils.Listener;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

public class InteractionTasks {
    public static void init(){

    }
    private static BlockHitResult lastInteract = null;
    private static int lastInteractTimestamp = -1;
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static void listenInteractBlockPacket(PlayerInteractBlockC2SPacket packet){
        if(packet.getBlockHitResult().getType() == HitResult.Type.BLOCK){
            lastInteract = packet.getBlockHitResult();
            lastInteractTimestamp = Tasks.getTick();
        }
    }
    public static BlockPos predictScreenFrom(Predicate<Block> targetBlock){
        int timeStamp = Tasks.getTick();
        //在一秒内反应的 可以考虑
        if(timeStamp < lastInteractTimestamp + 20 && lastInteract != null){
            BlockPos hitPose = lastInteract.getBlockPos();
            if(hitPose != null && targetBlock.test( mc.world.getBlockState(hitPose).getBlock())){
                return hitPose;
            }
            //block Type not match,
        }
        return MovTasks.rayTraceSpecificBlock((b)->b == Blocks.DISPENSER || b == Blocks.DROPPER).orElse(null);
    }


    static{
        Listener.registerSinglePacketListener(PlayerInteractBlockC2SPacket.class, InteractionTasks::listenInteractBlockPacket);
    }
}
