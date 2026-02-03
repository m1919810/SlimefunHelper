package me.matl114.jsApi;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.JavaOps;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xyz.wagyourtail.jsmacros.client.api.helpers.world.entity.EntityHelper;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@ApiMethod
public class WorldHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static BlockState getBlockState(World world, BlockPos pos) {
        return world.getBlockState(pos);
    }

    public static Object getBlockData(World world, BlockPos pos) throws Throwable{
        Class<?> clazz = Class.forName("xyz.wagyourtail.jsmacros.client.api.helpers.world.BlockDataHelper");
        return clazz.getConstructors()[0].newInstance(world.getBlockState(pos), world.getBlockEntity(pos), pos);
    }

    public static boolean isWorldClient(World world){
        return world.isClient;
    }

    public static void setBlockState(World world, BlockPos pos, BlockState state){
        mc.execute(()-> world.setBlockState(pos, state));
    }

    public static FluidState getFluidState(World world, BlockPos pos){
        return world.getFluidState(pos);
    }

    public static BlockEntity getBlockEntity(World world, BlockPos pos){
        return world.getBlockEntity(pos);
    }

    public static Block getBlockOfState(BlockState state){
        return state.getBlock();
    }
    public static BlockState getDefaultState(Block block){
        return block.getDefaultState();
    }

    public static String getBlockIdOfState(BlockState state){
        return RegistryHelper.getIdInRegistry(Registries.BLOCK, state.getBlock());
    }

    public static Map<String, Object> getStateMap(BlockState state){
        return (Map<String, Object>) BlockState.CODEC.encodeStart(ItemStackUtils.registry().getOps(JavaOps.INSTANCE), state).getOrThrow();
    }

    public static BlockState createStateByMap(Map<String, Object> obj){
        return BlockState.CODEC.decode(ItemStackUtils.registry().getOps(JavaOps.INSTANCE), obj).getOrThrow().getFirst();
    }

    public static boolean isInWorldBorder(int x, int y, int z){
        return isInWorldBorder(new BlockPos(x, y, z));
    }

    public static boolean isInWorldBorder(Object pos0){
        BlockPos pos = DataHelper.createBlockPos(pos0);
        return mc.world.getWorldBorder().contains(pos);
    }

    public static Entity getEntityById(int i) throws ExecutionException, InterruptedException {
        FutureTask<Entity> futureTask = new FutureTask<>(()->mc.world.getEntityById(i));
        mc.execute(futureTask);
        return futureTask.get();
    }

    public static Entity getEntityByUid(Object obj) throws ExecutionException, InterruptedException {
        UUID uuid = (obj instanceof UUID uid)? uid : UUID.fromString(obj.toString());

        FutureTask<Entity> futureTask = new FutureTask<>(()-> mc.world.getEntityLookup().get(uuid));
        mc.execute(futureTask);
        return futureTask.get();
    }

    public static Entity getEntityByIdUnsafe(int i)  {
        return mc.world.getEntityById(i);
    }

    public static Entity getEntityByUidUnsafe(Object obj) {
        UUID uuid = (obj instanceof UUID uid)? uid : UUID.fromString(obj.toString());
        return mc.world.getEntityLookup().get(uuid);
    }

    public static List<Entity> getEntitiesByDistance() throws Throwable{
        return sortEntitiesByDistance(getEntities());
    }

    public static <T> List<T> sortEntitiesByDistance(List<T> en){
        return (List<T>)(en.stream().sorted(Comparator.comparingDouble((d) -> JsHelper.unwrap(d, Entity.class).getEntityPos().squaredDistanceTo(mc.player.getEntityPos()))).collect(Collectors.toCollection(ArrayList::new)));
    }

    public static List<Entity> getEntities() throws ExecutionException, InterruptedException {
        FutureTask<List<Entity>> futureTask = new FutureTask<>(WorldHelper::getEntitiesUnsafe);
        mc.execute(futureTask);
        return futureTask.get();
    }
    public static List<Entity> getEntitiesUnsafe(){
        return ImmutableList.copyOf(mc.world.getEntities());
    }

    public static List<Entity> getEntities(double distance) throws ExecutionException, InterruptedException {
        List<Entity> list = getEntities();
        double dsSquared = distance * distance;
        return list.stream().filter(s -> s.squaredDistanceTo(mc.player) <= dsSquared).collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<Entity> getEntitiesInBox(Object center, int xhalf, int yhalf, int zhalf) throws ExecutionException, InterruptedException {
        Vec3d centerPos = JsHelper.unwrap(center, Vec3d.class);
        Box box = new Box(centerPos.subtract(xhalf, yhalf, zhalf), centerPos.add(xhalf, yhalf, zhalf));
        List<Entity> list = new ArrayList<>();
        Future<Void> futureTask = new FutureTask<>(()->{
            mc.world.getEntityLookup().forEachIntersects(box, list::add);
            return null;
        });
        futureTask.get();
        return list;
    }

}
