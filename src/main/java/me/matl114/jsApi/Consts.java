package me.matl114.jsApi;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import me.matl114.utils.ApiMethod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import xyz.wagyourtail.jsmacros.core.language.BaseLanguage;
import xyz.wagyourtail.jsmacros.core.language.BaseScriptContext;
import xyz.wagyourtail.jsmacros.core.language.EventContainer;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * this provides the common consts which may be used in js Scripts
 * classes will be obfuscated when at runtime,
 * but invocation or newInstance is ok
 */
@ApiMethod
public interface Consts {
    Class<?> Vec3d = net.minecraft.util.math.Vec3d.class;
    Class<?> BlockPos = net.minecraft.util.math.BlockPos.class;
    Class<?> IPlayer = PlayerEntity.class;
    Class<?> Entity = net.minecraft.entity.Entity.class;
    Class<?> IWorld = net.minecraft.world.World.class;
    Class<?> ItemStack = net.minecraft.item.ItemStack.class;
    Class<?> Direction = net.minecraft.util.math.Direction.class;
    Class<?> HitResult = net.minecraft.util.hit.HitResult.class;
    Class<?> BlockHitResult = net.minecraft.util.hit.BlockHitResult.class;
    Class<?> EntityHitResult = net.minecraft.util.hit.EntityHitResult.class;

    Class<?> PacketByteBuf = net.minecraft.network.PacketByteBuf.class;
    Class<?> NbtElement = net.minecraft.nbt.NbtElement.class;
    Class<?> NbtCompound = net.minecraft.nbt.NbtElement.class;
    Class<?> Block = net.minecraft.block.Block.class;
    Class<?> BlockState = net.minecraft.block.BlockState.class;
    Class<?> BlockEntity = net.minecraft.block.entity.BlockEntity.class;
    Class<?> FluidState = net.minecraft.fluid.FluidState.class;


    net.minecraft.util.math.BlockPos BlockPos_ZERO = net.minecraft.util.math.BlockPos.ORIGIN;
    net.minecraft.util.math.Vec3d Vec3d_ZERO = net.minecraft.util.math.Vec3d.ZERO;
    MinecraftClient MC = MinecraftClient.getInstance();

    Class<?> ClientPlayerEntity = net.minecraft.client.network.ClientPlayerEntity.class;
    Class<?> ClientWorld = net.minecraft.client.world.ClientWorld.class;

    // 实体相关
    Class<?> PlayerEntity = net.minecraft.entity.player.PlayerEntity.class;
    Class<?> LivingEntity = net.minecraft.entity.LivingEntity.class;

    Class<?> AnimalEntity = net.minecraft.entity.passive.AnimalEntity.class;
    Class<?> MobEntity = net.minecraft.entity.mob.MobEntity.class;
    Class<?> HostileEntity = net.minecraft.entity.mob.HostileEntity.class;

    // 世界和方块
    Class<?> Chunk = net.minecraft.world.chunk.Chunk.class;
    Class<?> WorldChunk = net.minecraft.world.chunk.WorldChunk.class;


    // 物品和方块实体
    Class<?> Item = net.minecraft.item.Item.class;
    Class<?> Inventory = net.minecraft.inventory.Inventory.class;
    Class<?> Slot = net.minecraft.screen.slot.Slot.class;
    Class<?> PlayerInventory = net.minecraft.entity.player.PlayerInventory.class;
    Class<?> Container = net.minecraft.inventory.Inventory.class;


    // 交互和命中


    // 网络和NBT
    Class<?> NbtList = net.minecraft.nbt.NbtList.class;
    Class<?> NbtInt = net.minecraft.nbt.NbtInt.class;
    Class<?> NbtString = net.minecraft.nbt.NbtString.class;

    // 文本和聊天
    Class<?> Text = net.minecraft.text.Text.class;

    // GUI和屏幕
    Class<?> Screen = net.minecraft.client.gui.screen.Screen.class;
    Class<?> HandledScreen = net.minecraft.client.gui.screen.ingame.HandledScreen.class;

    // 事件和状态
    Class<?> Enchantment = net.minecraft.enchantment.Enchantment.class;


    // =========================== Minecraft 常量实例 ===========================


    // 方向常量
    net.minecraft.util.math.Direction Direction_UP = net.minecraft.util.math.Direction.UP;
    net.minecraft.util.math.Direction Direction_DOWN = net.minecraft.util.math.Direction.DOWN;
    net.minecraft.util.math.Direction Direction_NORTH = net.minecraft.util.math.Direction.NORTH;
    net.minecraft.util.math.Direction Direction_SOUTH = net.minecraft.util.math.Direction.SOUTH;
    net.minecraft.util.math.Direction Direction_EAST = net.minecraft.util.math.Direction.EAST;
    net.minecraft.util.math.Direction Direction_WEST = net.minecraft.util.math.Direction.WEST;

    // 命中类型
    net.minecraft.util.hit.HitResult.Type HitResult_MISS = net.minecraft.util.hit.HitResult.Type.MISS;
    net.minecraft.util.hit.HitResult.Type HitResult_BLOCK = net.minecraft.util.hit.HitResult.Type.BLOCK;
    net.minecraft.util.hit.HitResult.Type HitResult_ENTITY = net.minecraft.util.hit.HitResult.Type.ENTITY;



    // =========================== Java 基础类 ===========================

    // 集合框架
    Class<?> JavaList = java.util.List.class;
    Class<?> ArrayList = java.util.ArrayList.class;
    Class<?> LinkedList = java.util.LinkedList.class;
    Class<?> JavaMap = java.util.Map.class;
    Class<?> HashMap = java.util.HashMap.class;
    Class<?> LinkedHashMap = java.util.LinkedHashMap.class;
    Class<?> TreeMap = java.util.TreeMap.class;
    Class<?> JavaSet = java.util.Set.class;
    Class<?> HashSet = java.util.HashSet.class;
    Class<?> TreeSet = java.util.TreeSet.class;
    Class<?> Collection = java.util.Collection.class;
    Class<?> Iterator = java.util.Iterator.class;
    Class<?> JavaArrays = java.util.Arrays.class;
    Class<?> Collections = java.util.Collections.class;
    Class<?> Comparator = java.util.Comparator.class;

    // 数学和数字
    Class<?> JavaMath = java.lang.Math.class;
    Class<?> Random = java.util.Random.class;
    Class<?> ThreadLocalRandom = java.util.concurrent.ThreadLocalRandom.class;
    Class<?> BigInteger = java.math.BigInteger.class;
    Class<?> BigDecimal = java.math.BigDecimal.class;
    Class<?> JavaNumber = java.lang.Number.class;
    Class<?> Integer = java.lang.Integer.class;
    Class<?> Long = java.lang.Long.class;
    Class<?> Double = java.lang.Double.class;
    Class<?> Float = java.lang.Float.class;
    Class<?> Byte = java.lang.Byte.class;
    Class<?> Short = java.lang.Short.class;
    Class<?> JavaBoolean = java.lang.Boolean.class;
    Class<?> Character = java.lang.Character.class;
    Class<?> JavaString = java.lang.String.class;

    // 函数式接口
    Class<?> Function = java.util.function.Function.class;
    Class<?> Consumer = java.util.function.Consumer.class;
    Class<?> Supplier = java.util.function.Supplier.class;
    Class<?> Predicate = java.util.function.Predicate.class;
    Class<?> BiFunction = java.util.function.BiFunction.class;
    Class<?> BiConsumer = java.util.function.BiConsumer.class;
    Class<?> Runnable = java.lang.Runnable.class;
    Class<?> Callable = java.util.concurrent.Callable.class;


    // 文件和IO
    Class<?> File = java.io.File.class;
    Class<?> Path = java.nio.file.Path.class;
    Class<?> Paths = java.nio.file.Paths.class;
    Class<?> Files = java.nio.file.Files.class;

    // 并发和多线程
    Class<?> CompletableFuture = java.util.concurrent.CompletableFuture.class;
    Class<?> AtomicInteger = java.util.concurrent.atomic.AtomicInteger.class;
    Class<?> AtomicLong = java.util.concurrent.atomic.AtomicLong.class;
    Class<?> AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean.class;
    Class<?> AtomicReference = java.util.concurrent.atomic.AtomicReference.class;

    // 反射
    Class<?> Clazz = java.lang.Class.class;

    // 正则表达式
    Class<?> Pattern = java.util.regex.Pattern.class;
    Class<?> Matcher = java.util.regex.Matcher.class;

    // 系统相关
    Class<?> JavaSystem = java.lang.System.class;

    // GUI (AWT/Swing - 可选)
    Class<?> Color = java.awt.Color.class;


    // 其他实用类
    Class<?> JavaObject  = java.lang.Object.class;
    Class<?> Objects = java.util.Objects.class;
    Class<?> Optional = java.util.Optional.class;
    Class<?> Stream = java.util.stream.Stream.class;
    Class<?> IntStream = java.util.stream.IntStream.class;
    Class<?> LongStream = java.util.stream.LongStream.class;
    Class<?> DoubleStream = java.util.stream.DoubleStream.class;
    Class<?> UUID = java.util.UUID.class;




    public static Supplier<Map<String, Object>> ConstantMap = Suppliers.memoize(()->{
        return java.util.Arrays.stream(Consts.class.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(f -> !Supplier.class.isAssignableFrom(f.getType()))
            .map(f -> {
                try {
                    return Pair.of(f.getName(), f.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    });
    public static Supplier<Map<String, String>> AliasMap = Suppliers.memoize(()->{
        return ImmutableMap.<String, String>builder()
            .put("NBT", "NbtElement")
            .put("NBTMap", "NbtCompound")
            .put("NBTList", "NbtList")

            .build();
    });
    public static void importConstantToContext(Object context0) throws Throwable{
        EventContainer<?> context = (EventContainer<?>)context0;
        var ctx0 = context.getCtx().getContext();
        var bindingMap = ReflectHelper.invoke(ctx0, "getBindings", "js");
        importConstantNames( bindingMap);
    }
    public static void importConstantNames(Object varMap) throws Throwable{
        var map = ConstantMap.get();
        Map<String, Object> obj = new LinkedHashMap<>(map);
        var alias = AliasMap.get();
        alias.forEach((k,v)-> {
            if (map.containsKey(v)){
                obj.put(k, map.get(v));
            }
        });
        //polygolt Value
        Method m = ReflectHelper.findMethodByType(varMap, "putMember", String.class, Object.class).get(0);
        obj.forEach((k, v)-> {
            try {
                m.invoke(varMap, k, v);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });



    }

}
