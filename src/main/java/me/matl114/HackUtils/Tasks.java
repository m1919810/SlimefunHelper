package me.matl114.HackUtils;

import me.matl114.Access.ClientPlayerAccess;
import me.matl114.Access.PlayerInteractionAccess;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.ConfigureScreen;
import me.matl114.ManageUtils.SelectScreen;
import me.matl114.ModConfig;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.apache.logging.log4j.core.appender.rolling.action.IfAll;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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

    public static void openSelectScreen(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            HashMap<String,Runnable> map = new HashMap<>();
            for(Config config:Config.getConfigs()){
                map.put(config.getConfigName(),()->openConfigScreen(config));
            }
            MinecraftClient.getInstance().setScreen(new SelectScreen(map,4,Text.of("")));
        }
    }
    public static void openConfigScreen(Config config){
        MinecraftClient.getInstance().setScreen(new ConfigureScreen(config,Text.of("")));
    }


    private static double RANGE=5;
    private static AtomicInteger MAX_PER_TICK= Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_MAX_INSTANT_MINE);
    private static AtomicBoolean ONLY_MINE_ABOVE=Configs.MINE_CONFIG.getBoolean(Configs.MINE_BOT_ONLY_MINE_ABOVE);
    private static Config.StringRef WHITELIST_REGEX=Configs.MINE_CONFIG.getString(Configs.MINE_BOT_WHITELIST);
    private static AtomicInteger PACKET_MULTIPLIER=Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_PACKET_MULTIPLE);
    private static BlockPos CACHED_POSITION=null;
    private static boolean distanceOut(BlockPos pos1, Vec3d playerPos){
        if(pos1==null||playerPos.squaredDistanceTo(Vec3d.ofCenter(pos1))>RANGE*RANGE){
            return true;
        }return false;
    }
    private static String RECORDED_WHITELIST_REGEX=null;
    private static HashSet<Block> block=new HashSet<>();
    private static HashSet<Block> refreshBlockWhitelist(){
        if(!Objects.equals(WHITELIST_REGEX.get(),RECORDED_WHITELIST_REGEX)){
            RECORDED_WHITELIST_REGEX=WHITELIST_REGEX.get();
            block.clear();
            for(Block block1:Registries.BLOCK){
                if(Pattern.matches(WHITELIST_REGEX.get(),Registries.BLOCK.getId(block1).getPath())){
                    block.add(block1);
                }
            }
        }
        return block;
    }
    private static boolean isWhitelisted(Block block){
        return refreshBlockWhitelist().contains(block);
    }
    private static boolean isMineable(World world,BlockPos pos){
        BlockState state=world.getBlockState(pos);
        if(!state.isAir()){
            Block block=state.getBlock();
            if(isWhitelisted(block)){
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
        }else if(!isWhitelisted(state.getBlock())){
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
    public static void onMineBotStart(ClientPlayerEntity player,ClientPlayerInteractionManager manager){
        //int runTime=tickCounter.getAndIncrement();
        //int matched=
        isWorking.set(true);
        for(int i=0;i<PACKET_MULTIPLIER.get();++i){
            if( minebot(player,manager)>=5){
                break;
            }
        }
        isWorking.set(false);
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
            boolean preCalculation=PlayerInteractionAccess.of(manager).preCalculateInstantBreak(CACHED_POSITION);
            tryMine+=1;

            manager.updateBlockBreakingProgress(CACHED_POSITION, Direction.UP);
            if(!preCalculation){
                break;
            }

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



    private static BlockPos CACHED_ONE_BLOCK=null;
    private static BlockPos rayTraceBlock(ClientPlayerEntity player){
        var blockState=player.getWorld().raycast(new RaycastContext(player.getEyePos(),player.getEyePos().add(player.getRotationVec(1.0f).multiply(6.0f)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if(blockState.getType()== HitResult.Type.BLOCK){
            return blockState.getBlockPos();
        }else return null;
    }
    private static int no_one_block_mention=0;
    private static int NO_ONE_BLOCK_MENTION_LIMIT=100;
    public static void onMineOneBlockStart(ClientPlayerEntity player,ClientPlayerInteractionManager manager){
        if(CACHED_ONE_BLOCK==null){
            BlockPos rayTraceBlock=rayTraceBlock(player);
            if(rayTraceBlock!=null){
                CACHED_ONE_BLOCK=rayTraceBlock;
                player.sendMessage(Text.literal("Find traced block!"));
                PlayerInteractionAccess.of(manager).sendStartPacket(CACHED_ONE_BLOCK,Direction.UP);
            }else {
                if(no_one_block_mention<NO_ONE_BLOCK_MENTION_LIMIT){
                    no_one_block_mention++;
                }else {
                    no_one_block_mention=0;
                    player.sendMessage(Text.literal("No trace block found!"));
                }
            }
        }else{
            no_one_block_mention=0;
            BlockState state=player.getWorld().getBlockState(CACHED_ONE_BLOCK);
            if(!state.isAir()){
                PlayerInteractionAccess.of(manager).sendStopPacket(CACHED_ONE_BLOCK,Direction.UP);
            }
        }
    }
    public static void onMineOneBlockStop(){
        CACHED_ONE_BLOCK=null;
    }
}
