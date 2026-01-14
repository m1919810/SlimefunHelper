package me.matl114.hackUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import me.matl114.access.PlayerInteractionAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.renders.RenderMain;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.AbstractMainCommand;
import me.matl114.utils.UtilClass.AttrKeyValue;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.SubCommand;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MineTasks {
   // public static void init(){}
   public static void init(){

   }
    private static MinecraftClient mc = MinecraftClient.getInstance();
    static {


        Tasks.registerGameTask((player)->{
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.MINEBOT)){
                MineTasks.onMineBotStart(player,mc.interactionManager);
            }else{
                MineTasks.onMineBotStop();
            }
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.MINE_ONEBLOCK)){
                MineTasks.onMineOneBlockStart(player,mc.interactionManager);
            }else {
                MineTasks.onMineOneBlockStop();
            }
        });
    }
    private static final Config.DoubleRef RANGE=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_REACH);
    private static final Config.IntRef MAX_PER_TICK= Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_MAX_INSTANT_MINE);
    private static final Config.FlagRef LOW_FIRST =Configs.MINE_CONFIG.getBoolean(Configs.MINE_BOT_DOWN_PRIORITY);
    private static final Config.IntRef MIN_Y=Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_MINE_MIN_DY);
    private static final Config.IntRef MAX_Y=Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_MINE_MAX_DY);
    private static final Config.StringRef WHITELIST_REGEX=Configs.MINE_CONFIG.getString(Configs.MINE_BOT_WHITELIST);
    private static final Config.IntRef PACKET_MULTIPLIER=Configs.MINE_CONFIG.getInt(Configs.MINE_BOT_PACKET_MULTIPLE);
    private static BlockPos CACHED_POSITION=null;
    private static boolean distanceOut(BlockPos pos1, Vec3d playerPos){
        if(pos1==null||playerPos.squaredDistanceTo(Vec3d.ofCenter(pos1))>MathUtils.s2( RANGE.get())){
            return true;
        }return false;
    }
    private static String RECORDED_WHITELIST_REGEX=null;
    private static final  HashSet<Block> block=new HashSet<>();
    private static HashSet<Block> refreshBlockWhitelist(){
        if(!Objects.equals(WHITELIST_REGEX.getValue(), RECORDED_WHITELIST_REGEX)){
            RECORDED_WHITELIST_REGEX=WHITELIST_REGEX.getValue();
            block.clear();
            for(Block block1:Registries.BLOCK){
                if(Pattern.matches(WHITELIST_REGEX.getValue(),Registries.BLOCK.getId(block1).getPath())){
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
        if(!state.isAir() && !state.isLiquid()){
            Block block=state.getBlock();
            if(block.getHardness() >= 0.0F &&  isWhitelisted(block)){
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
    private static final int[] dx=new int[4096];
    private static final int[] dy=new int[4096];
    private static final int[] dz=new int[4096];
    private static final int[] x_=new int[1024];
    private static final int[] y_=new int[1024];
    private static final int length;
    private static final int length2;
    public static AtomicBoolean isWorking=new AtomicBoolean(false);
    static {
        List<int[]> points = new ArrayList<>();
        int range=6;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    points.add(new int[]{x, y,z});
                }

            }
        }
        var counter=new AtomicInteger(0);
        points.sort(Comparator.comparingDouble(p -> Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])));
        points.forEach(p ->{
            int index=counter.getAndIncrement();
            dx[index]=p[0];
            dy[index]=p[1];
            dz[index]=p[2];
        });
        length=counter.get();
        points.clear();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                points.add(new int[]{x, y});
            }
        }
        counter.set(0);
        points.sort(Comparator.comparingDouble(p -> Math.sqrt(p[0] * p[0] + p[1] * p[1])));
        points.forEach(p ->{
            int index=counter.getAndIncrement();
            x_[index]=p[0];
            y_[index]=p[1];
        });
        length2=counter.get();
    }
    private static BlockPos findNextMinePos(World world,Vec3d playerPos){
        BlockPos playerBlock=BlockPos.ofFloored(playerPos);
        int lowest=MIN_Y.get();
        int highest=MAX_Y.get();
        if(LOW_FIRST.get()){
            for(int y=lowest;y<highest;y++){
                for(int i=0;i<length2;++i){
                    int x=x_[i];
                    int z=y_[i];
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
        }else {
            for(int i=0;i<length;i++){
                int x=dx[i];
                int y=dy[i];
                int z=dz[i];
                if(y>=lowest&&y<=highest){
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
            int mineResult=minebot(player,manager);
;            if( mineResult>=5){
                break;
            }
        }
        isWorking.set(false);
    }

    public static void onMineBotStop(){
        CACHED_POSITION=null;
    }
    private static final Config.FlagRef rightClick = Configs.MINE_CONFIG.getBoolean(Configs.MINE_BOT_RIGHT_CLICK);
    private static final Config.FlagRef useToolProtect = Configs.MINE_CONFIG.getBoolean(Configs.MINE_BOT_DURABILITY_PROTECT);
    private static final Config.FlagRef minebotLegalMode = Configs.MINE_CONFIG.getBoolean(Configs.MINE_BOT_LEGAL_MODE);
    private static boolean checkDurability(int dur){
        if(useToolProtect.get() && mc.player != null){
            ItemStack item = mc.player.getMainHandStack();

            int durabilityLimit ;// item.get(DataComponentTypes.UNBREAKABLE) != null ? Integer.MAX_VALUE: (
            if(item.get(DataComponentTypes.UNBREAKABLE) != null){
                durabilityLimit = Integer.MAX_VALUE;
            }else{
               var optionalUnbreaking = ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.UNBREAKING);
               int multiply = 1;
               if(optionalUnbreaking.isPresent()){
                   multiply = EnchantmentHelper.getLevel(optionalUnbreaking.get(), item) + 1;
               }
               durabilityLimit = dur / multiply;
            }
            if(!item.isEmpty() && item.getMaxDamage() > Math.max( 10, durabilityLimit)  && item.getDamage() > item.getMaxDamage() - Math.max(10, durabilityLimit)){
                //
                Debug.chat("Your tool runs out of durability! stop mining");
                return false;
            }
        }
        return true;
    }
    private static int minebot(ClientPlayerEntity player,ClientPlayerInteractionManager manager){

        int tryMine=0;

        if(rightClick.get()){
            //  Vec3d face = player.getEyePos().subtract(Vec3d.of(CACHED_POSITION));
//            HitResult result = mc.crosshairTarget;
//            if(result instanceof BlockHitResult blockHitResult){
//                Debug.info(blockHitResult.getSide().toString());
//                manager.sendSequencedPacket(mc.world, (sequence) -> {
//                    return new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHitResult  , sequence);
//                });
//                tryMine+=1;
//            }
            manager.sendSequencedPacket(mc.world, (sequence) -> {
                return new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch());
            });
        }else{
            if(!checkDurability(3 * MAX_PER_TICK.get())){
                onMineBotStop();
                HotKeys.getHotkeyToggleManager().getToggle(HotKeys.MINEBOT).run();
                return Integer.MAX_VALUE;
            }
            do{
                Vec3d playerPos=player.getEyePos(); ;
                if(distanceOut(CACHED_POSITION, playerPos)||isMined(player.getWorld(),CACHED_POSITION)){
                    CACHED_POSITION=findNextMinePos(player.getWorld(),playerPos);
                }
                if(CACHED_POSITION==null){
                    break;
                }
                boolean preCalculation = PlayerInteractionAccess.of(manager).preCalculateInstantBreak(CACHED_POSITION);
                tryMine+=1;
                // use real Direction
                Vec3d shouldFacing = CACHED_POSITION.toCenterPos().subtract(player.getEyePos());
                Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                manager.updateBlockBreakingProgress(CACHED_POSITION, dir);
                //fake a swing packet , so that we can bypass some packet check
                if(minebotLegalMode.get())
                    player.swingHand(Hand.MAIN_HAND);

                if(!preCalculation){
                    break;
                }
            }while(!manager.isBreakingBlock() && tryMine<MAX_PER_TICK.get());
        }
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


    private static final Config.IntRef ONE_BLOCK_MULTIPLY_PACKET=Configs.MINE_CONFIG.getInt(Configs.MINE_ONEBLOCK_PACKET_MULTIPLE);
    private static BlockPos CACHED_ONE_BLOCK=null;
    public static BlockPos rayTraceBlock(ClientPlayerEntity player){
        var blockState=player.getWorld().raycast(new RaycastContext(player.getEyePos(),player.getEyePos().add(player.getRotationVec(1.0f).multiply(6.0f)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if(blockState.getType()== HitResult.Type.BLOCK){
            return blockState.getBlockPos();
        }else return null;
    }
    private static int no_one_block_mention=0;
    private static int NO_ONE_BLOCK_MENTION_LIMIT=100;
    //controllable oneblock Miner: make mixin : use attackblock to start : already finished
    public static void onMineOneBlockStart(ClientPlayerEntity player,ClientPlayerInteractionManager manager){
        if(!checkDurability(10 * ONE_BLOCK_MULTIPLY_PACKET.get())){
            onMineOneBlockStop();
            HotKeys.getHotkeyToggleManager().getToggle(HotKeys.MINE_ONEBLOCK).run();
            return;
        }
        if(CACHED_ONE_BLOCK==null){
            BlockPos rayTraceBlock=rayTraceBlock(player);
            if(rayTraceBlock!=null){
                CACHED_ONE_BLOCK=rayTraceBlock;
                player.sendMessage(Text.literal("Find traced block!"));
                //USE REAL Direction
                Vec3d shouldFacing = CACHED_ONE_BLOCK.toCenterPos().subtract(player.getEyePos());
                PlayerInteractionAccess.of(manager).sendStartBreakPacket(CACHED_ONE_BLOCK, Direction.getFacing(shouldFacing).getOpposite());
            }else {
                if(no_one_block_mention<NO_ONE_BLOCK_MENTION_LIMIT){
                    no_one_block_mention++;
                }else {
                    no_one_block_mention=0;
                    Debug.chat("No trace block found!");
                }
            }
        }else{
//            BlockState state=player.getWorld().getBlockState(CACHED_ONE_BLOCK);
//            if(!state.isAir()){

            double lenSq = CACHED_ONE_BLOCK.toCenterPos().squaredDistanceTo(player.getEyePos());
            if(lenSq > MathUtils.s2(2 * player.getBlockInteractionRange() )){
                no_one_block_mention += 1;
                if (no_one_block_mention > NO_ONE_BLOCK_MENTION_LIMIT){
                    no_one_block_mention = 0;
                    Debug.info("OneBlock target too faraway from you: ", ChatUtils.getDisplayedLocation(Vec3d.of(CACHED_ONE_BLOCK)));
                }
            }else {
                no_one_block_mention=0;
                if(lenSq < MathUtils.s2(player.getBlockInteractionRange() + 1)){
                    Vec3d shouldFacing = CACHED_ONE_BLOCK.toCenterPos().subtract(player.getEyePos());
                    Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                    for(int i=0;i<ONE_BLOCK_MULTIPLY_PACKET.get();++i){
                        PlayerInteractionAccess.of(manager).sendStopBreakPacket(CACHED_ONE_BLOCK, dir);
                    }
                }
            }

            //}
        }
    }
    public static void onMineOneBlockStop(){
        CACHED_ONE_BLOCK=null;
    }

    private static final Config.FlagRef renderMine = Configs.MINE_CONFIG.getBoolean(Configs.MINE_RENDER_CURRENT_MINING_BLOCK);
    public static void renderMineBlockTask(Event<MatrixStack> renderEvent){
        RenderUtils.startDrawVirtual(renderEvent.context);
        try{
            if(renderMine.get() && mc.interactionManager != null && mc.player != null && mc.world != null){
                BlockPos blockPos =  PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                Vec3d pos =Vec3d.of(blockPos);
                //超过200格的不渲染
                if(mc.player.getPos().squaredDistanceTo(pos) < 40000){
                    RenderUtils.setAsShaderColor(Color.BLUE, 1.0F);
                    RenderUtils.drawOutlinedBox(renderEvent.context, pos, pos.add(1.0, 1.0, 1.0));
                    float progress =  PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningProgress(true);
                    if(progress > 0.0F){
                        BlockState state = mc.world.getBlockState(blockPos);
                        Box box;
                        if(state.isAir()){
                            box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                        }else{
                            VoxelShape shape = state.getOutlineShape(mc.world, blockPos);
                            box = shape.isEmpty()? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0): shape.getBoundingBox();
                        }
                        Vec3d vec3 = box.getMaxPos().subtract(box.getMinPos()).multiply(0.5);

                        RenderUtils.setAsShaderColor(Color.YELLOW, 0.25F);
                        Vec3d vec3d = pos.add(box.getCenter());
                        float clamped = MathHelper.clamp(progress, 0.0F, 1.0F) ;
                        RenderUtils.drawSolidBox(renderEvent.context.peek().getPositionMatrix(), vec3d.add(vec3.multiply(-clamped)), vec3d.add(vec3.multiply(clamped)));
                    }
                }


                BlockPos doubleMinePos =  PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
                if(doubleMinePos != null){
                    Vec3d doubleMineVec = Vec3d.of(doubleMinePos);
                    if(mc.player.getPos().squaredDistanceTo(doubleMineVec) < 40000 && !Objects.equals(doubleMineVec, pos)){
                        float progressFail = PlayerInteractionAccess.of(mc.interactionManager).getFailBreakMiningProgress();
                        RenderUtils.setAsShaderColor(Color.MAGENTA, 1.0F);
                        RenderUtils.drawOutlinedBox(renderEvent.context, doubleMineVec, doubleMineVec.add(1.0, 1.0, 1.0));
                        if(progressFail > 0.0F){
                            BlockState state = mc.world.getBlockState(doubleMinePos);
                            Box box;
                            if(state.isAir()){
                                box = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                            }else{
                                VoxelShape shape = state.getOutlineShape(mc.world, doubleMinePos);
                                box = shape.isEmpty()? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0): shape.getBoundingBox();
                            }
                            Vec3d vec3 = box.getMaxPos().subtract(box.getMinPos()).multiply(0.5);

                            RenderUtils.setAsShaderColor(Color.ORANGE, 0.25F);
                            Vec3d vec3d = doubleMineVec.add(box.getCenter());
                            float clamped = MathHelper.clamp(progressFail, 0.0F, 1.0F) ;
                            RenderUtils.drawSolidBox(renderEvent.context.peek().getPositionMatrix(), vec3d.add(vec3.multiply(-clamped)), vec3d.add(vec3.multiply(clamped)));
                        }
                    }

                }

            }
        }finally {
            RenderUtils.stopDrawVirtual(renderEvent.context);
        }

    }

    //where to place it
    //todo: minearua conflict with optimize
    private static BlockPos mineAruaPosCache;
    private static int lastRefreshMineAruaTick = 0;
    private static final Config.StringRef whitelist = Configs.MINE_CONFIG.getString(Configs.MINEARUA_WHILELIST);
    private static String cachedMinearuaWhitelist;
    private static HashSet<Block> minearuaBlock = new HashSet<>();
    private static Set<Block> minearuaWhitelist(){
        if(!Objects.equals(cachedMinearuaWhitelist, whitelist.get())){
            cachedMinearuaWhitelist = whitelist.get();
            for(Block block1:Registries.BLOCK){
                if(Pattern.matches(cachedMinearuaWhitelist, Registries.BLOCK.getId(block1).getPath())){
                    minearuaBlock.add(block1);
                }
            }
        }
        return minearuaBlock;
    }
    private static boolean isMineAruaTarget(World world,BlockPos pos){
        BlockState state=world.getBlockState(pos);
        if(state != null &&  !state.isAir() && !state.isLiquid()){
            Block block=state.getBlock();
            if(block.getHardness() >= 0.0F &&  minearuaWhitelist().contains(block)){
                return true;
            }
        }
        return false;
    }
    private static BlockPos refreshMineAruaTarget(){
        if(mc.player != null && mc.world != null){
            //every time check if current cache is here
            Vec3d eyepos = mc.player.getEyePos();
            if(mineAruaPosCache != null && isMineAruaTarget(mc.world, mineAruaPosCache) && !distanceOut(mineAruaPosCache, eyepos)){
                return mineAruaPosCache;
            }
            //refresh only 4 ticks once
            if(Tasks.getTick() >= lastRefreshMineAruaTick + 4){
                BlockPos currentBlockPos = mc.player.getBlockPos();
                for (var i = 0 ; i < length; ++ i){
                    BlockPos pos = currentBlockPos.add(dx[i], dy[i], dz[i]);
                    if(isMineAruaTarget(mc.world, pos) && !distanceOut(pos, eyepos)){
                        return pos;
                    }
                }
            }

        }
        return null;
    }
    private static void onMinearuaRedirect(Event<HitResult> hitResultEvent){
        if(mc.player != null && HotKeys.getHotkeyToggleManager().getState(HotKeys.MINEARUA)){
            BlockPos pos = refreshMineAruaTarget();
            if(pos != mineAruaPosCache){
                if(pos != null){
                    Debug.chat(Text.literal("[Mine Arua] Redirect mine target ").append(ChatUtils.getDisplayedLocation(Vec3d.of(pos))).formatted(Formatting.GREEN));
                }
                mineAruaPosCache = pos;
                lastRefreshMineAruaTick = Tasks.getTick();
            }

            if(mineAruaPosCache != null){
                Direction dir = Direction.getFacing(mineAruaPosCache.toCenterPos().subtract(mc.player.getEyePos())).getOpposite();
                HitResult hitResult = new BlockHitResult(Vec3d.of(mineAruaPosCache), dir, mineAruaPosCache, false);
                hitResultEvent.context(hitResult);
            }
        }

    }





    public static void onAntiXrayDemoTest(BlockPos testPos){
        Debug.chat("starting test on block ", testPos);
//        mc.getNetworkHandler().sendPacket(new );
    }
//    private static final Config.FlagRef fastBreakCooldownBypass =Configs.MINE_CONFIG.getBoolean(Configs.MINE_BYPASS_FAST_BREAK_CHECK);

//    public static void doBypassFastBreakNoCooldownTry(BlockPos pos, int currentCoolDown, boolean takeInstaBreak){
//        if(fastBreakCooldownBypass.get()){
//            int needed = 5 - currentCoolDown;
//            Direction dir = Direction.getFacing( pos.toCenterPos().subtract(mc.player.getEyePos())).getOpposite();
//            if(false)
//            for (int i=0; i< needed -1 ; ++i){
//                mc.interactionManager.sendSequencedPacket(mc.world, (t)->{
//                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir);
//                });
//            }
//        }
//    }

    private static final Config.FlagRef aaxrayEnable = Configs.MINE_CONFIG.getBoolean(Configs.XRAY_ENABLE);
    private static final Config.FlagRef seedEnable = Configs.MINE_CONFIG.getBoolean(Configs.XRAY_ENABLE_SEED);
    private static final Config.StringRef seedMapString = Configs.INTERNAL_CONFIG.getString(Configs.SEED_MAP);
    private static final Object2LongMap<String> seedMap = new Object2LongOpenHashMap<>();
    private static final Map<Long, Map<Ore, Set<Vec3d>>> chunkSeedCache = new ConcurrentHashMap<>();
    private static final Map<Long, Map<BlockPos, BlockState>> fakeOres = new ConcurrentHashMap<>();
    private static final Config.IntRef chunkSeedRange = Configs.MINE_CONFIG.getInt(Configs.XRAY_ENABLE_SEED_RADIUS);
    private static final Config.FlagRef chunkSeedMakeFakeOre = Configs.MINE_CONFIG.getBoolean(Configs.XRAY_MAKE_CLIENTSIDE_ORE);
    private static final Set<BlockPos> simpleDetection = new HashSet<>();
    private static final Config.FlagRef simpleDetectionEnable = Configs.MINE_CONFIG.getBoolean(Configs.XRAY_ENABLE_SIMPLE);
    private static final Config.FlagRef renderOreSimResult = Configs.MINE_CONFIG.getBoolean(Configs.XRAY_RENDER_FAKE_ORE);
    static{
        try{
            JsonObject jsonMap = (JsonObject) JsonParser.parseString(seedMapString.getValue());
            for (var jsonEntry: jsonMap.asMap().entrySet()){
                seedMap.put(jsonEntry.getKey(), jsonEntry.getValue().getAsLong());
            }
        }catch (Throwable e){
            throw  new RuntimeException(e);
        }
    }
    //-26225.23 67.00 -23482.37
    public static void onDimensionChange(Void v){
        onClearCachedResults();
        clearSimpleDetectionCache();
        if(seedEnable.get()){
            //reload config
            onEnableSeedOre();
        }
    }
    public static void antiXrayTick(){
        if(!aaxrayEnable.get())return;
        int tick = Tasks.getTick();
        if(simpleDetectionEnable.get())
            if(tick % 20 == 0){
                doSimpleDetection();
                if(tick % (20 * 60) == 0){
                    clearSimpleDetectionCache();
                }
            }

    }
    private static void seedMapChange(String key){
        String element = new Gson().toJson(new HashMap<>(seedMap));
        seedMapString.setValue(element);
        Configs.INTERNAL_CONFIG.save();
        Config.launchSaveTasks();
        onSeedChange(key);
    }
    @ApiMethod
    public static void setWorldSeed(long seed){
        seedMap.put(CommonUtils.getWorldName(), seed);
        seedMapChange(CommonUtils.getWorldName());
    }
    @ApiMethod
    public static void removeWorldSeed(String world){
        seedMap.removeLong(world);
        seedMapChange(world);
    }
    @ApiMethod
    public static boolean isCurrentWorldSeedInputExist(){
        return seedMap.containsKey(CommonUtils.getWorldName());
    }
    @ApiMethod
    public static long getCurrentWorldSeedInput(){
        return seedMap.getLong(CommonUtils.getWorldName());
    }
    @ApiMethod
    public static void validateCurrentSeed(){
        if(!validateCurrentSeedExist())return;
        long value = seedMap.getLong(CommonUtils.getWorldName());
        Debug.chat(Text.literal("[世界种子] 核验当前世界种子中:").formatted(Formatting.GREEN));
        Debug.chat(Text.literal("[世界种子] 输入的种子: " ).formatted(Formatting.GREEN).append(ChatUtils.getDisplayedLong(value)));
        long hashed = mc.world.getBiomeAccess().seed;
        Debug.chat(Text.literal("[世界种子] 服务器加密种子: " ).append(ChatUtils.getDisplayedLong(hashed)));
        if(BiomeAccess.hashSeed(value ) ==  hashed){
            Debug.chat(Text.literal("[世界种子] 验证通过").formatted(Formatting.GREEN));
        }else {
            Debug.chat(Text.literal("[世界种子] 验证失败").formatted(Formatting.RED));
        }
    }
    public static boolean validateCurrentSeedExist(){
        if(isCurrentWorldSeedInputExist()){
            return true;
        }else {
            Debug.chat(Text.literal("[世界种子] 暂时没有设置 %s 世界的种子".formatted(CommonUtils.getWorldName())).formatted(Formatting.RED));
            if(seedEnable.get()){
                onDisableSeedOre();
            }
            return false;
        }
    }
    @ApiMethod
    public static void doSimpleDetection(){
        int distance = (int) Math.ceil( RANGE.get());
        if(distance > 1){
            BlockPos currentPlayer = BlockPos.ofFloored( mc.player.getPos());
            for (var i = -distance; i <= distance; ++i){
                for (var j = -distance;  j <= distance; ++j){
                    for(var  k = -distance; k <= distance; ++k){
                        if(MathUtils.s2(i) + MathUtils.s2(j) + MathUtils.s2(k) < MathUtils.s2(mc.player.getBlockInteractionRange() + 1.0F)){
                            BlockPos testPos = currentPlayer.add(i, j, k);//TODO 增加暴露判定
                            if(!simpleDetection.contains(testPos)){
                                simpleDetection.add(testPos);
                                mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                                    Vec3d shouldFacing = testPos.toCenterPos().subtract(mc.player.getEyePos());
                                    Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                                    // use real direction
                                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, testPos, dir, sequence);
                                });
                            }
                        }

                    }
                }
            }
        }
    }

    private static void onSeedChange(String key){
        if(!validateCurrentSeedExist())return;
        if(Objects.equals(CommonUtils.getWorldName(), key) && seedEnable.get()){
            Debug.chat("[种子矿透] 重载Seed Ore Simulation功能");
            onEnableSeedOre();
        }
    }
    private static void onReloadSeedOre(){
        if(seedEnable.get()){
            onEnableSeedOre();
        }
    }
    @ApiMethod
    private static void onEnableSeedOre(){
        if(!validateCurrentSeedExist())return;
        try{
            seedEnable.set(true);
            Configs.MINE_CONFIG.save();
            Config.launchSaveTasks();
            onClearCachedResults();
            oreConfig = Ore.getRegistry();
            if(mc.player != null && mc.world != null){
                Debug.chat(Text.literal("[种子矿透] 启用该功能, 范围 %d".formatted(chunkSeedRange.get())).formatted(Formatting.GREEN));
                onLoadCurrentVisibleChunks();
            }

        }catch (Throwable e){
            Debug.info(e);
            if(mc.player != null)
                Debug.chat(Text.literal("[种子矿透] 启用时出现报错, 已关闭..."));
            seedEnable.set(false);
            Configs.MINE_CONFIG.save();
            Config.launchSaveTasks();
        }

    }
    @ApiMethod
    private static void onDisableSeedOre(){
        if(seedEnable.get()){
            seedEnable.set(false);
            Configs.MINE_CONFIG.save();
            Config.launchSaveTasks();
            oreConfig = null;
            if(mc.player != null && mc.world != null){
                Debug.chat(Text.literal("[种子矿透] 禁用该功能").formatted(Formatting.RED));
                onRemoveFakeOreVisibleChunks();
            }

        }
    }

    private static void onLoadCurrentVisibleChunks(){
        if(mc.world == null)return;
        for (Chunk chunk : CommonUtils.chunks(false)){
            updateChunk(chunk);
        }
    }
    private static void onReloadFakeOreVisibleChunks(){
        if(mc.world == null)return;
        for (Chunk chunk : CommonUtils.chunks(false)){
            long key = chunk.getPos().toLong();
            var map = chunkSeedCache.get(key);
            if(map != null && !map.isEmpty()){
                updateOreClientSide(key, map);
            }
        }
    }
    private static void onRemoveFakeOreVisibleChunks(){
        if(mc.world == null)return;
        for (Chunk chunk : CommonUtils.chunks(false)){
            long key = chunk.getPos().toLong();
            var map = fakeOres.remove(key);
            if(map != null && !map.isEmpty()){
                removeChunkFakeOres(key, map);
            }
        }
    }
    private static void removeChunkFakeOres(long key, Map<BlockPos, BlockState> originDatas){
        for (var re: originDatas.entrySet()){
            BlockPos pos0 = re.getKey();
            BlockState state0 = re.getValue();
            Tasks.scheduleDelayed(()->{
                if(mc.world != null){
                    mc.world.setBlockState(pos0, state0);
                }
            },1);
        }
    }

    private static void onClearCachedResults(){
        chunkSeedCache.clear();
        fakeOres.clear();;
    }

    private static void onAutoToggleOffWhenDisconnected(){
        onDisableSeedOre();
    }

    public static void clearSimpleDetectionCache(){
        simpleDetection.clear();
    }

    public static void onChunkUpdate(Event<Packet<?>> packet){
        if(seedEnable.get() && packet.context() instanceof ChunkDataS2CPacket dataS2CPacket){
            int x = dataS2CPacket.getChunkX();
            int z = dataS2CPacket.getChunkZ();
            Tasks.scheduleDelayed(()->{
                updateChunk(mc.world.getChunk(x, z));
            }, 2);
        }
    }

    public static void onBlockUpdate(BlockUpdateS2CPacket packet){

            //remove cache whenever
            //remove async
        if(!chunkSeedCache.isEmpty() || !fakeOres.isEmpty()){
            long chunkKey = ChunkPos.toLong((packet).getPos());
            var map = chunkSeedCache.get(chunkKey);
            Vec3d pos = Vec3d.of(packet.getPos());
            if(map != null && !map.isEmpty()){
                for (var ore: map.values()){
                    ore.remove(pos);
                }
            }
            var map2 = fakeOres.get(chunkKey);
            if(map2 != null && !map2.isEmpty()){
                map2.remove(packet.getPos());
            }
        }
    }

    public static void onRenderFakeOreTasks(Event<MatrixStack> event){
        var stack = event.context;
        if(mc.player == null || oreConfig == null)return;
        if(!seedEnable.get())return;
        if(!renderOreSimResult.get())return;;
        if(!MineTasks.validateCurrentSeedExist())return;
        RenderUtils.startDrawVirtual(stack);
        try{
            int chunkX = mc.player.getChunkPos().x;
            int chunkZ = mc.player.getChunkPos().z;

            int rangeVal = chunkSeedRange.get();
            for (int range = 0; range <= rangeVal; range++) {
                for (int x = -range + chunkX; x <= range + chunkX; x++) {
                    renderChunk(x, chunkZ + range - rangeVal, stack);
                }
                for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                    renderChunk(x, chunkZ - range + rangeVal + 1, stack);
                }
            }
        }finally {
            RenderUtils.stopDrawVirtual(stack);
        }


    }
    private static void renderChunk(int x, int z, MatrixStack event){
        long chunkKey = ChunkPos.toLong(x,z);

        if (chunkSeedCache.containsKey(chunkKey)) {
            Map<Ore, Set<Vec3d>> chunk = chunkSeedCache.get(chunkKey);

            for (Map.Entry<Ore, Set<Vec3d>> oreRenders : chunk.entrySet()) {
                if (oreRenders.getKey().active.getOriginValue() == Boolean.TRUE) {
                    RenderUtils.setAsShaderColor(
                        oreRenders.getKey().color
                        , 1.0F);
                    for (Vec3d pos : oreRenders.getValue()) {
                        Vec3d centerPos = BlockPos.ofFloored(pos).toCenterPos();

                       // event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, oreRenders.getKey().color, 0);
                        RenderUtils.drawOutlinedBox(event, centerPos.add(RenderTasks.FROM), centerPos.add(RenderTasks.TO));
                    }
                }
            }
        }
    }

    @ApiMethod
    public static Map<String, Set<Vec3d>> getSeedOres(int x, int z){
        Map<String, Set<Vec3d>> map = new HashMap<>();
        for (var entry: chunkSeedCache.getOrDefault(ChunkPos.toLong(x, z), Map.of()).entrySet()){
            map.put(entry.getKey().active.getKeyName(), entry.getValue());
        }
        return map;
    }

    private static void updateChunk(Chunk chunk) {
        if(!seedEnable.get())return;
        if(!validateCurrentSeedExist()){
            return;
        }
        var chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        ClientWorld world = mc.world;
        //clear cache when switching world
        Map<Ore, Set<Vec3d>> h;

        if (chunkSeedCache.containsKey(chunkKey) || world == null || oreConfig == null) {
           h = chunkSeedCache.get(chunkKey);
        }else{
            Set<RegistryKey<Biome>> biomes = new HashSet<>();
            ChunkPos.stream(chunkPos, 1).forEach(chunkPosx -> {
                Chunk chunkxx = world.getChunk(chunkPosx.x, chunkPosx.z, ChunkStatus.BIOMES, false);
                if (chunkxx == null) return;

                for(ChunkSection chunkSection : chunkxx.getSectionArray()) {
                    chunkSection.getBiomeContainer().forEachValue(entry -> biomes.add(entry.getKey().get()));
                }
            });
            Set<Ore> oreSet = biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());

            int chunkX = chunkPos.x << 4;
            int chunkZ = chunkPos.z << 4;
            ChunkRandom random = new ChunkRandom(ChunkRandom.RandomProvider.XOROSHIRO.create(0));

            long populationSeed = random.setPopulationSeed(getCurrentWorldSeedInput(), chunkX, chunkZ);
            h = new ConcurrentHashMap<>();
            for (Ore ore : oreSet) {

                Set<Vec3d> ores = ConcurrentHashMap.newKeySet();

                random.setDecoratorSeed(populationSeed, ore.index, ore.step);

                int repeat = ore.count.get(random);

                for (int i = 0; i < repeat; i++) {

                    if (ore.rarity != 1F && random.nextFloat() >= 1/ore.rarity) {
                        continue;
                    }

                    int x = random.nextInt(16) + chunkX;
                    int z = random.nextInt(16) + chunkZ;
                    int y = ore.heightProvider.get(random, ore.heightContext);
                    BlockPos origin = new BlockPos(x,y,z);

                    RegistryKey<Biome> biome = chunk.getBiomeForNoiseGen(x,y,z).getKey().get();

                    if (!getDefaultOres(biome).contains(ore)) {
                        continue;
                    }

                    if (ore.scattered) {
                        ores.addAll(generateHidden(world, random, origin, ore.size));
                    } else {
                        ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance));
                    }
                }
                if (!ores.isEmpty()) {
                    h.put(ore, ores);
                }
            }
        }
        if(h != null && !h.isEmpty()){
            //fake ore do not load automatically
            chunkSeedCache.put(chunkKey, h);
            if(chunkSeedMakeFakeOre.get()){
                updateOreClientSide(chunkKey, h);
            }
        }


    }
    private static void updateOreClientSide(long chunkey, Map<Ore, Set<Vec3d>> ores){
        if(mc.world == null)return;

        //return blockstates already cached
        var map0 = fakeOres.remove(chunkey);
        if(map0 != null && !map0.isEmpty()){
            removeChunkFakeOres(chunkey, map0);
        }
        //todo: remove obfuscated ores from server , add config
        Map<BlockPos, BlockState> newFakeOres = new ConcurrentHashMap<>();
        int minY = mc.world.getBottomY();
        for(var ore0 : ores.entrySet()){
            Ore oreType = ore0.getKey();
            var sample = oreType.sampleBlock;
            var sampleDeepslate = oreType.sampleDeepslateBlock;
            loop:
            for (var pos: ore0.getValue()){
                if(pos.y < minY + 4){
                    //mostly bedrock , escape
                    continue ;
                }
                var block = pos.y > 0? sample: sampleDeepslate;
                var blockState = block.getDefaultState();
                var blockPos = BlockPos.ofFloored(pos);
                BlockState state = mc.world.getBlockState(blockPos);
                //todo: air can be faked!!! that's allshit
                if(!state.isAir() && state.getBlock() != sample && state.getBlock() != sampleDeepslate){
                    //not naked and not the same
                    for (Direction direction : Direction.values()){
                        BlockPos testPos = blockPos.offset(direction);
                        if(mc.world.getBlockState(testPos).isAir()){
                            continue loop;
                        }
                    }
                    newFakeOres.put(blockPos, state);
                    //run sync
                    Tasks.scheduleDelayed(()->{
                        if(mc.world != null)
                            mc.world.setBlockState(blockPos, blockState);
                    }, 2);
                }

            }
        }
        fakeOres.put(chunkey, newFakeOres);
    }
    //todo add minearua

    private static  Map<RegistryKey<Biome>, List<Ore>> oreConfig;
    private static List<Ore> getDefaultOres(RegistryKey<Biome> biomeRegistryKey) {
        if (oreConfig.containsKey(biomeRegistryKey)) {
            return oreConfig.get(biomeRegistryKey);
        } else {
            return oreConfig.values().stream().findAny().get();
        }
    }


    public static class MineCommand extends AbstractMainCommand{
        SubCommand mainCommand = genMainCommand("mine");
        SubCommand seedToggle = new SubCommand("seedore", genArgument("toggle"), "!!mine seedore <toggle> 切换是否启动seed ore simulation"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                boolean var  =parseInput(var4).getFirst().nextBoolean();
                if(var){
                    if(!seedEnable.get()){
                        onEnableSeedOre();
                    }
                }else{
                    if(seedEnable.get()){
                        onDisableSeedOre();
                    }
                }
                return true;
            }
        }
            .setTabCompletor("toggle", ()->List.of("true", "false"))
            .register(this);
        SubCommand seedInput = new SubCommand("seed", genArgument("operation", "seed"), "!!mine seed <operation> <seed> 进行seed操作"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String op = re.nextNonnull();
                switch (op){
                    case "set" -> {
                        String na = re.nextNonnull();
                        long val;
                        if(seedMap.containsKey(na)){
                            val = seedMap.getLong(na);
                        }else{
                            val = Long.parseLong(na);
                        }
                        setWorldSeed(val);
                        Debug.chat("[世界种子] 设置", CommonUtils.getWorldName(), "的种子为", val);
                    }
                    case "remove" ->{
                        String key = re.nextNonnull();
                        removeWorldSeed(key);
                    }
                    case "list" ->{
                        Debug.chat("[世界种子] 列表");
                        for (var entry : seedMap.object2LongEntrySet()){
                            Debug.chat(entry.getKey(), ":",ChatUtils.getDisplayedLong(entry.getLongValue()));
                        }
                    }
                    case "validate"->{

                        validateCurrentSeed();

                    }
                }
                return true;
            }
        }
            .setEnum("operation", List.of("set", "remove", "validate", "list"))
            .setDefault("seed", "0")
            .setTabCompletor("seed", ()-> Streams.concat(Stream.of("0", "114514"), seedMap.keySet().stream()).toList())
            .register(this);
        SubCommand toggleRender = new SubCommand("orerender", genArgument("toggle"), "!!mine orerender <toggle> 切换是否渲染sim ore"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                boolean val = parseInput(var4).getFirst().nextBoolean();
                renderOreSimResult.set(val);
                Configs.MINE_CONFIG.save();
                Config.launchSaveTasks();
                Debug.chat("[种子矿透] 切换渲染:", val);
                return true;
            }
        }
            .setTabCompletor("toggle", ()->List.of("true", "false"))
            .register(this)
            ;
        SubCommand toggleFakeOre = new SubCommand("fakeore", genArgument("operation"), "!!mine fakeore <operation> 进行假矿渲染切换"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                String val = parseInput(var4).getFirst().nextNonnull();
                switch (val){
                    case "on"->{
                        Debug.chat("[种子矿透] 切换假矿: true");
                        chunkSeedMakeFakeOre.set(true);
                        Configs.MINE_CONFIG.save();
                        onReloadFakeOreVisibleChunks();
                    }
                    case "off"->{
                        Debug.chat("[种子矿透] 切换假矿: false");
                        chunkSeedMakeFakeOre.set(false);
                        Configs.MINE_CONFIG.save();
                        onRemoveFakeOreVisibleChunks();
                    }
                    case "reload"->{
                        Debug.chat("[种子矿透] 重载可视距离内的假矿");
                        onReloadFakeOreVisibleChunks();
                    }
                }
                Config.launchSaveTasks();
                return true;
            }
        }
            .setEnum("operation", List.of("on", "off", "reload"))
            .register(this);
    }
    static{
        Ore.init();
        ChatTasks.registerSubCommands("mine", MineCommand::new);
        Listener.getMainThreadPacketPostApplyPoint().registerHandler(MineTasks::onChunkUpdate);
        Listener.registerSinglePacketListener(BlockUpdateS2CPacket.class, MineTasks::onBlockUpdate);
        RenderMain.getRenderLayerTasks().registerHandler(MineTasks::onRenderFakeOreTasks);
//        Listener.getGameJoinPoint().registerHandler(MineTasks::onDimensionChange);
        Listener.getWorldSwitchPoint().registerHandler(MineTasks::onDimensionChange);
        RenderMain.getRenderLayerTasks().registerHandler(MineTasks::renderMineBlockTask);
        Listener.getMineBlockAction().registerHandler(MineTasks::onMinearuaRedirect);
    }
    // ====================================
    // Mojang code
    // ====================================

    private static ArrayList<Vec3d> generateNormal(ClientWorld world, ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }

        return new ArrayList<>();
    }

    private static ArrayList<Vec3d> generateVeinPart(ClientWorld world, ChunkRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir) {

        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];

        ArrayList<Vec3d> poses = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = MathHelper.lerp(f, startX, endX);
            q = MathHelper.lerp(f, startY, endY);
            r = MathHelper.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4];
                double w = ds[n * 4 + 1];
                double aa = ds[n * 4 + 2];
                int ab = Math.max(MathHelper.floor(v - u), x);
                int ac = Math.max(MathHelper.floor(w - u), y);
                int ad = Math.max(MathHelper.floor(aa - u), z);
                int ae = Math.max(MathHelper.floor(v + u), ab);
                int af = Math.max(MathHelper.floor(w + u), ac);
                int ag = Math.max(MathHelper.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an);
                                            mutable.set(ah, aj, al);
                                            if (aj >= -64 && aj < 320 && ( world.getBlockState(mutable).isOpaque())) {
                                                if (shouldPlace(world, mutable, discardOnAir, random)) {
                                                    poses.add(new Vec3d(ah, aj, al));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return poses;
    }

    private static boolean shouldPlace(ClientWorld world, BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.add(direction.getVector())).isOpaque() && discardOnAir != 1F) {
                return false;
            }
        }
        return true;
    }

    private static ArrayList<Vec3d> generateHidden(ClientWorld world, ChunkRandom random, BlockPos blockPos, int size) {

        ArrayList<Vec3d> poses = new ArrayList<>();

        int i = random.nextInt(size + 1);

        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = randomCoord(random, size) + blockPos.getX();
            int y = randomCoord(random, size) + blockPos.getY();
            int z = randomCoord(random, size) + blockPos.getZ();
            if (world.getBlockState(new BlockPos(x, y, z)).isOpaque()) {
                if (shouldPlace(world, new BlockPos(x, y, z), 1F, random)) {
                    poses.add(new Vec3d(x, y, z));
                }
            }
        }

        return poses;
    }

    private static int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }



    public static class Ore {
        private static final AttrKeyValue<Boolean> coal        = AttrKeyValue.bool("Coal");
        private static final AttrKeyValue<Boolean> iron        = AttrKeyValue.bool("Iron");
        private static final AttrKeyValue<Boolean> gold        = AttrKeyValue.bool("Gold");
        private static final AttrKeyValue<Boolean> redstone    = AttrKeyValue.bool("Redstone");
        private static final AttrKeyValue<Boolean> diamond     = AttrKeyValue.bool("Diamond");
        private static final AttrKeyValue<Boolean> lapis       = AttrKeyValue.bool("Lapis");
        private static final AttrKeyValue<Boolean> copper      = AttrKeyValue.bool("Copper");
        private static final AttrKeyValue<Boolean> emerald     = AttrKeyValue.bool("Emerald");
        private static final AttrKeyValue<Boolean> quartz      = AttrKeyValue.bool("Quartz");
        private static final AttrKeyValue<Boolean> debris      = AttrKeyValue.bool("Ancient Debris");
        private static final Map<String, Pair<Block, Block>> oreMapping = ImmutableMap.<String, Pair<Block, Block>>builder()
            .put("Coal", Pair.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE))
            .put("Iron", Pair.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE))
            .put("Gold", Pair.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE))
            .put("Redstone", Pair.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE))
            .put("Diamond", Pair.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE))
            .put("Lapis", Pair.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE))
            .put("Copper", Pair.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE))
            .put("Emerald", Pair.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE))
            .put("Quartz", Pair.of(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE))
            .put("Ancient Debris", Pair.of(Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS))
            .build();
        public static final  List<AttrKeyValue<Boolean>>   oreSettings = new ArrayList<>(Arrays.asList(coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris));
        private static Config.StringRef oreWhiteList = Configs.MINE_CONFIG.getString(Configs.XRAY_ORE_TYPE);
        static{
            oreWhiteList.addUpdateListenerWithUpdate(Ore::reloadOreSettings);
        }
        public static void reloadOreSettings(String value){
            try{
                var regex = Pattern.compile(value).asMatchPredicate();
                for (var ore: oreSettings){
                    if(regex.test(ore.getKeyName().toLowerCase(Locale.ROOT))){
                        ore.valueChange(ore, "true");
                    }else{
                        ore.valueChange(ore, "false");
                    }
                }
            }catch (Throwable e){
                //Debug.chat("");
            }
        }
        public static void init(){

        }
        public static Map<RegistryKey<Biome>, List<Ore>> getRegistry() {

            RegistryWrapper.WrapperLookup registry = BuiltinRegistries.createWrapperLookup();
            RegistryWrapper.Impl<PlacedFeature> features = registry.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
            var reg = registry.getWrapperOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder().dimensions();
            RegistryKey<DimensionOptions> options = CommonUtils.getCurrentDimensionOption();
            var dim = reg.get(options);

            var biomes = dim.chunkGenerator().getBiomeSource().getBiomes();
            var biomes1 = biomes.stream().toList();

            List<PlacedFeatureIndexer.IndexedFeatures> indexer = PlacedFeatureIndexer.collectIndexedFeatures(
                biomes1, biomeEntry -> biomeEntry.value().getGenerationSettings().getFeatures(), true
            );


            Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_COAL_LOWER, 6, coal, new Color(47, 44, 54));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_COAL_UPPER, 6, coal, new Color(47, 44, 54));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_IRON_MIDDLE, 6, iron, new Color(236, 173, 119));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_IRON_SMALL, 6, iron, new Color(236, 173, 119));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_IRON_UPPER, 6, iron, new Color(236, 173, 119));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD, 6, gold, new Color(247, 229, 30));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD_LOWER, 6, gold, new Color(247, 229, 30));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD_EXTRA, 6, gold, new Color(247, 229, 30));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD_NETHER, 7, gold, new Color(247, 229, 30));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD_DELTAS, 7, gold, new Color(247, 229, 30));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_REDSTONE, 6, redstone, new Color(245, 7, 23));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_REDSTONE_LOWER, 6, redstone, new Color(245, 7, 23));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_DIAMOND, 6, diamond, new Color(33, 244, 255));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_DIAMOND_BURIED, 6, diamond, new Color(33, 244, 255));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_DIAMOND_LARGE, 6, diamond, new Color(33, 244, 255));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_DIAMOND_MEDIUM, 6, diamond, new Color(33, 244, 255));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_LAPIS, 6, lapis, new Color(8, 26, 189));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_LAPIS_BURIED, 6, lapis, new Color(8, 26, 189));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_COPPER, 6, copper, new Color(239, 151, 0));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_COPPER_LARGE, 6, copper, new Color(239, 151, 0));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_EMERALD, 6, emerald, new Color(27, 209, 45));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_QUARTZ_NETHER, 7, quartz, new Color(205, 205, 205));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_QUARTZ_DELTAS, 7, quartz, new Color(205, 205, 205));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_DEBRIS_SMALL, 7, debris, new Color(209, 27, 245));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE, 7, debris, new Color(209, 27, 245));


            Map<RegistryKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();

            biomes1.forEach(biome -> {
                biomeOreMap.put(biome.getKey().get(), new ArrayList<>());
                biome.value().getGenerationSettings().getFeatures().stream()
                    .flatMap(RegistryEntryList::stream)
                    .map(RegistryEntry::value)
                    .filter(featureToOre::containsKey)
                    .forEach(feature -> {
                        biomeOreMap.get(biome.getKey().get()).add(featureToOre.get(feature));
                    });
            });
            return biomeOreMap;
        }

        private static void registerOre(
            Map<PlacedFeature, Ore> map,
            List<PlacedFeatureIndexer.IndexedFeatures> indexer,
            RegistryWrapper.Impl<PlacedFeature> oreRegistry,
            RegistryKey<PlacedFeature> oreKey,
            int genStep,
            AttrKeyValue<Boolean> active,
            Color color
        ) {
            var orePlacement = oreRegistry.getOrThrow(oreKey).value();

            int index = indexer.get(genStep).indexMapping().applyAsInt(orePlacement);
            var pair = oreMapping.getOrDefault(active.getKeyName(), Pair.of(Blocks.IRON_ORE, Blocks.IRON_ORE));
            Ore ore = new Ore(orePlacement, pair.getFirst(), pair.getSecond(), genStep, index, active, color);

            map.put(orePlacement, ore);
        }

        public int step;
        public int index;
        public Block sampleBlock;
        public Block sampleDeepslateBlock;
        public AttrKeyValue<Boolean> active;
        public IntProvider count = ConstantIntProvider.create(1);
        public HeightProvider heightProvider;
        public HeightContext heightContext;
        public float rarity = 1;
        public float discardOnAirChance;
        public int size;
        public Color color;
        public boolean scattered;

        private Ore(PlacedFeature feature, Block block, Block deepslate, int step, int index, AttrKeyValue<Boolean> active, Color color) {
            this.step = step;
            this.index = index;
            this.active = active;
            this.color = color;
            this.sampleBlock = block;
            this.sampleDeepslateBlock = deepslate;
            int bottom = MinecraftClient.getInstance().world.getBottomY();
            int height = MinecraftClient.getInstance().world.getDimension().logicalHeight();
            this.heightContext = new HeightContext(null, HeightLimitView.create(bottom, height));

            for (PlacementModifier modifier : feature.placementModifiers()) {
                if (modifier instanceof CountPlacementModifier count) {
                    this.count = count.count;

                } else if (modifier instanceof HeightRangePlacementModifier height0) {
                    this.heightProvider =  height0.height;

                } else if (modifier instanceof RarityFilterPlacementModifier rare) {
                    this.rarity = rare.chance;
                }
            }

            FeatureConfig featureConfig = feature.feature().value().config();

            if (featureConfig instanceof OreFeatureConfig oreFeatureConfig) {
                this.discardOnAirChance = oreFeatureConfig.discardOnAirChance;
                this.size = oreFeatureConfig.size;
            } else {
                throw new IllegalStateException("config for " + feature + "is not OreFeatureConfig.class");
            }

            if (feature.feature().value().feature() instanceof ScatteredOreFeature) {
                this.scattered = true;
            }
        }
    }
}
