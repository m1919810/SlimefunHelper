package me.matl114.HackUtils;

import me.matl114.Access.ClientPlayerAccess;
import me.matl114.Access.PlayerInteractionAccess;
import me.matl114.ManageUtils.Configs;
import me.matl114.ModConfig;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.logging.log4j.core.appender.rolling.action.IfAll;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Tasks {
   // public static void init(){}

    public static void clearKeepedInv(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            player.sendMessage(Text.literal("已清除界面历史记录"));
        }
    }

    private static HashSet<Block> CAN_MINE=new HashSet<>(){{
        add(Blocks.COBBLESTONE);
        add(Blocks.STONE);
        for(Block block: Registries.BLOCK.stream().toArray(Block[]::new)){
            if(Registries.BLOCK.getId(block).getPath().endsWith("ore")){
                //Debug.info("add minable block ",Registries.BLOCK.getId(block));
                add(block);
            }
        }
    }};
    private static double RANGE=5;
    private static AtomicInteger MAX_PER_TICK= ModConfig.getConfigValue(Configs.MINE_BOT_MAX_PER_TICK);
    private static AtomicBoolean ONLY_MINE_ABOVE=ModConfig.getConfigOption(Configs.MINE_BOT_MINE_ABOVE);
    private static BlockPos CACHED_POSITION=null;
    private static boolean distanceOut(BlockPos pos1, Vec3d playerPos){
        if(pos1==null||playerPos.squaredDistanceTo(Vec3d.ofCenter(pos1))>RANGE*RANGE){
            return true;
        }return false;
    }
    private static boolean isMineable(World world,BlockPos pos){
        BlockState state=world.getBlockState(pos);
        if(!state.isAir()){
            Block block=state.getBlock();
            if(CAN_MINE.contains(block)){
                return true;
            }
        }
        return false;
    }
    private static boolean isMined(World world,BlockPos pos){
        if (pos == null||world==null) {
            return true;
        }
        BlockState state=world.getBlockState(pos);
        if(state.isAir()){
            return true;
        }else if(!CAN_MINE.contains(state.getBlock())){
            return true;
        }
        return false;
    }
    private static int[] dx=new int[256];
    private static int[] dy=new int[256];
    private static int length;
    public static AtomicBoolean isWorking=new AtomicBoolean(false);
    static {
        List<int[]> points = new ArrayList<>();
        int range=6;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                points.add(new int[]{x, y});
            }
        }
        AtomicInteger counter=new AtomicInteger(0);
        Collections.sort(points, Comparator.comparingDouble(p -> Math.sqrt(p[0] * p[0] + p[1] * p[1])));
        points.forEach(p ->{
            int index=counter.getAndIncrement();
            dx[index]=p[0];
            dy[index]=p[1];
        });
        length=counter.get();
    }
    private static BlockPos findNextMinePos(World world,Vec3d playerPos){
        BlockPos playerBlock=BlockPos.ofFloored(playerPos);
        int lowest=ONLY_MINE_ABOVE.get()?-1:-6;
        for(int y=lowest; y<=6; y++){
            for(int i=0;i<length;i++){
                int x=dx[i];
                int z=dy[i];
                BlockPos newPose=playerBlock .add(x,y,z);
                if(distanceOut(newPose,playerPos)){
                    continue;
                }else {
                    if(isMineable(world,newPose)){
                        return newPose;
                    }
                }
            }
        }
        return null;
    }
    private static int no_block_mention=0;
    private static int NO_BLOCK_MENTION_LIMIT=400;
    private static CompletableFuture asyncMineBotThread=null;
    private static AtomicBoolean stopSignal=new AtomicBoolean(false);
    private static AtomicInteger tickCounter=new AtomicInteger(0);
    public static void onMineBotStart(ClientPlayerEntity player,ClientPlayerInteractionManager manager){
        //int runTime=tickCounter.getAndIncrement();
        //int matched=
        isWorking.set(true);
        for(int i=0;i<5;++i){
            if( minebot(player,manager)>=8){
                break;
            }
        }
        isWorking.set(false);
//        if(matched>0&&matched<5){
//            //not fastbreak, meet hard
//            //auto send packet
//            CompletableFuture.runAsync(()->{
//                do{
//                    PlayerInteractionAccess.of(manager).autoSendStopPacket();
//                    try{
//                        Thread.sleep(4);
//                    }catch (Throwable e){e.printStackTrace();                    }
//                }while (runTime==tickCounter.get());
//            });
//        }
    }

    public static void onMineBotStop(){
        CACHED_POSITION=null;
    }
    private static int minebot(ClientPlayerEntity player,ClientPlayerInteractionManager manager){
        int tryMine=0;
        do{
            Vec3d playerPos=player.getEyePos(); ;
            if(distanceOut(CACHED_POSITION,playerPos)||isMined(player.getWorld(),CACHED_POSITION)){
                CACHED_POSITION=findNextMinePos(player.getWorld(),playerPos);
            }
            if(CACHED_POSITION==null){

                break;
            }
            manager.updateBlockBreakingProgress(CACHED_POSITION, Direction.UP);

            tryMine+=1;
        }while(!manager.isBreakingBlock()&&tryMine<MAX_PER_TICK.get());
        if(tryMine==0){
            no_block_mention++;
            if(no_block_mention>NO_BLOCK_MENTION_LIMIT){
                no_block_mention=0;
                player.sendMessage(Text.literal("No more minable blocks nearby!"));
            }
        }else {
            no_block_mention=0;
        }
        return tryMine;
    }
}
