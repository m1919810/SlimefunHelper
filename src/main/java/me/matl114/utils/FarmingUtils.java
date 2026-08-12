package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import me.matl114.utils.annotations.NeedTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CamelHuskEntity;
import net.minecraft.entity.mob.ZombieHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.HappyGhastEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@NeedTest
public class FarmingUtils {
    private static final Set<Item> BREED_ITEM_CANDIDATES = setOf(
            Items.WHEAT,
            Items.CARROT,
            Items.POTATO,
            Items.BEETROOT,
            Items.WHEAT_SEEDS,
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.TORCHFLOWER_SEEDS,
            Items.PITCHER_POD,
            Items.GOLDEN_CARROT,
            Items.DANDELION,
            Items.BAMBOO,
            Items.SWEET_BERRIES,
            Items.GLOW_BERRIES,
            Items.SEAGRASS,
            Items.SLIME_BALL,
            Items.TROPICAL_FISH_BUCKET,
            Items.SPIDER_EYE,
            Items.CACTUS,
            Items.WARPED_FUNGUS,
            Items.CRIMSON_FUNGUS,
            Items.SNOWBALL,
            Items.RED_MUSHROOM,
            Items.RABBIT_FOOT,
            Items.HAY_BLOCK,
            Items.SUGAR,
            Items.APPLE,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.COD,
            Items.COOKED_COD,
            Items.SALMON,
            Items.COOKED_SALMON,
            Items.TROPICAL_FISH,
            Items.PUFFERFISH,
            Items.RABBIT_STEW,
            Items.BEEF,
            Items.COOKED_BEEF,
            Items.PORKCHOP,
            Items.COOKED_PORKCHOP,
            Items.MUTTON,
            Items.COOKED_MUTTON,
            Items.CHICKEN,
            Items.COOKED_CHICKEN,
            Items.RABBIT,
            Items.COOKED_RABBIT,
            Items.ROTTEN_FLESH,
            Items.PUFFERFISH_BUCKET,
            Items.COD_BUCKET,
            Items.SALMON_BUCKET,
            Items.OPEN_EYEBLOSSOM,
            Items.POPPY,
            Items.BLUE_ORCHID,
            Items.ALLIUM,
            Items.AZURE_BLUET,
            Items.RED_TULIP,
            Items.ORANGE_TULIP,
            Items.WHITE_TULIP,
            Items.PINK_TULIP,
            Items.OXEYE_DAISY,
            Items.CORNFLOWER,
            Items.LILY_OF_THE_VALLEY,
            Items.WITHER_ROSE,
            Items.TORCHFLOWER,
            Items.SUNFLOWER,
            Items.LILAC,
            Items.PEONY,
            Items.ROSE_BUSH,
            Items.PITCHER_PLANT,
            Items.FLOWERING_AZALEA_LEAVES,
            Items.FLOWERING_AZALEA,
            Items.MANGROVE_PROPAGULE,
            Items.CHERRY_LEAVES,
            Items.PINK_PETALS,
            Items.WILDFLOWERS,
            Items.CHORUS_FLOWER,
            Items.SPORE_BLOSSOM,
            Items.CACTUS_FLOWER);

    private static final Set<Class<? extends Entity>> FOOD_ONLY_ENTITY_TYPES =
            Set.of(CamelHuskEntity.class, HappyGhastEntity.class, ZombieHorseEntity.class);

    private static final Predicate<ItemStack> ALWAYS_TRUE = stack -> true;

    @NeedTest
    public static PlantType getPlantType(Item item) {
        for (PlantType plantType : PlantType.values()) {
            if (plantType.getSeedItem() == item) {
                return plantType;
            }
        }
        return null;
    }

    @NeedTest
    public static PlantType getPlantType(Block block) {
        for (PlantType plantType : PlantType.values()) {
            if (plantType.getRelatedBlocks().contains(block)) {
                return plantType;
            }
        }
        return null;
    }

    @NeedTest
    public static BlockHitResult tryPlantAt(World world, BlockPos pos, PlantType plantType) {
        if (world == null || pos == null || plantType == null) {
            return null;
        }

        BlockState current = world.getBlockState(pos);
        if (!current.isAir() && !current.isOf(Blocks.WATER)) {
            return null;
        }

        for (BlockState state : plantType.getPlacementStates()) {
            if (state.canPlaceAt(world, pos)) {
                Direction supportDirection = getSupportDirection(state);
                BlockPos supportPos = pos.offset(supportDirection);
                return new BlockHitResult(
                        Vec3d.ofCenter(supportPos), supportDirection.getOpposite(), supportPos, false);
            }
        }
        return null;
    }

    @NeedTest
    public static Pair<Predicate<ItemStack>, BlockHitResult> getInteractTransition(
            World world, BlockPos pos, BlockState state1, BlockState state2) {
        if (world == null || pos == null || state1 == null || state2 == null) {
            return null;
        }

        Block block1 = state1.getBlock();
        Block block2 = state2.getBlock();

        if (block1 == block2) {
            if (block1 instanceof SlabBlock
                    && state1.get(SlabBlock.TYPE) != SlabType.DOUBLE
                    && state2.equals(
                            state1.with(SlabBlock.TYPE, SlabType.DOUBLE).with(SlabBlock.WATERLOGGED, false))) {
                BlockHitResult hit = state1.get(SlabBlock.TYPE) == SlabType.BOTTOM
                        ? hit(pos, Direction.UP, 0.5, 1.0, 0.5)
                        : hit(pos, Direction.DOWN, 0.5, 0.0, 0.5);
                return Pair.of(isItem(block1.asItem()), hit);
            }

            if (block1 instanceof SnowBlock
                    && state2.equals(state1.with(SnowBlock.LAYERS, Math.min(8, state1.get(SnowBlock.LAYERS) + 1)))) {
                return Pair.of(isItem(block1.asItem()), hit(pos, Direction.UP, 0.5, 1.0, 0.5));
            }

            if (block1 instanceof CandleBlock) {
                if (state1.get(CandleBlock.CANDLES) < 4
                        && state2.equals(state1.with(CandleBlock.CANDLES, state1.get(CandleBlock.CANDLES) + 1))) {
                    return Pair.of(isItem(block1.asItem()), null);
                }
                if (state1.get(CandleBlock.LIT) && state2.equals(state1.with(CandleBlock.LIT, false))) {
                    return Pair.of(ItemStack::isEmpty, null);
                }
                if (!state1.get(CandleBlock.LIT)
                        && !state1.get(CandleBlock.WATERLOGGED)
                        && state2.equals(state1.with(CandleBlock.LIT, true))) {
                    return Pair.of(isAnyOf(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE), null);
                }
            }

            if (block1 instanceof SeaPickleBlock
                    && state1.get(SeaPickleBlock.PICKLES) < 4
                    && state2.equals(state1.with(SeaPickleBlock.PICKLES, state1.get(SeaPickleBlock.PICKLES) + 1))) {
                return Pair.of(isItem(block1.asItem()), null);
            }

            if (block1 instanceof FlowerbedBlock
                    && state1.get(FlowerbedBlock.FLOWER_AMOUNT) < 4
                    && state2.equals(
                            state1.with(FlowerbedBlock.FLOWER_AMOUNT, state1.get(FlowerbedBlock.FLOWER_AMOUNT) + 1))) {
                return Pair.of(isItem(block1.asItem()), null);
            }

            if (block1 instanceof LeafLitterBlock
                    && state1.get(LeafLitterBlock.SEGMENT_AMOUNT) < 4
                    && state2.equals(state1.with(
                            LeafLitterBlock.SEGMENT_AMOUNT, state1.get(LeafLitterBlock.SEGMENT_AMOUNT) + 1))) {
                return Pair.of(isItem(block1.asItem()), null);
            }

            if (block1 instanceof RepeaterBlock && state2.equals(state1.cycle(RepeaterBlock.DELAY))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof ComparatorBlock
                    && state1.get(ComparatorBlock.MODE) != state2.get(ComparatorBlock.MODE)
                    && state2.get(ComparatorBlock.MODE)
                            == state1.cycle(ComparatorBlock.MODE).get(ComparatorBlock.MODE)
                    && state1.get(ComparatorBlock.FACING) == state2.get(ComparatorBlock.FACING)) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof NoteBlock
                    && state2.equals(state1.with(NoteBlock.NOTE, (state1.get(NoteBlock.NOTE) + 1) % 25))) {
                return Pair.of(ALWAYS_TRUE, hit(pos, Direction.NORTH, 0.5, 0.5, 0.0));
            }

            if (block1 instanceof DoorBlock && state2.equals(state1.cycle(DoorBlock.OPEN))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof TrapdoorBlock && state2.equals(state1.cycle(TrapdoorBlock.OPEN))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof FenceGateBlock
                    && state1.get(FenceGateBlock.FACING) == state2.get(FenceGateBlock.FACING)
                    && state2.equals(state1.cycle(FenceGateBlock.OPEN))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof LeverBlock && state2.equals(state1.cycle(LeverBlock.POWERED))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof ButtonBlock
                    && !state1.get(ButtonBlock.POWERED)
                    && state2.equals(state1.with(ButtonBlock.POWERED, true))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof CakeBlock
                    && state1.get(CakeBlock.BITES) < 6
                    && state2.equals(state1.with(CakeBlock.BITES, state1.get(CakeBlock.BITES) + 1))) {
                return Pair.of(ALWAYS_TRUE, null);
            }

            if (block1 instanceof RespawnAnchorBlock
                    && state1.get(RespawnAnchorBlock.CHARGES) < 4
                    && state2.equals(
                            state1.with(RespawnAnchorBlock.CHARGES, state1.get(RespawnAnchorBlock.CHARGES) + 1))) {
                return Pair.of(isItem(Items.GLOWSTONE), null);
            }

            if (block1 instanceof FlowerPotBlock pot1 && block2 instanceof FlowerPotBlock pot2) {
                if (pot1.getContent() != Blocks.AIR && pot2.getContent() == Blocks.AIR) {
                    return Pair.of(ItemStack::isEmpty, null);
                }
            }

            if (block1 instanceof BeehiveBlock
                    && state1.get(BeehiveBlock.HONEY_LEVEL) >= 5
                    && state2.equals(state1.with(BeehiveBlock.HONEY_LEVEL, 0))) {
                return Pair.of(isAnyOf(Items.SHEARS, Items.GLASS_BOTTLE), null);
            }

            if (block1 instanceof ComposterBlock) {
                int level1 = state1.get(ComposterBlock.LEVEL);
                int level2 = state2.get(ComposterBlock.LEVEL);
                if (level1 == 0 && level2 == 1) {
                    return Pair.of(FarmingUtils::canIncreaseComposterLevel, null);
                }
                if (level1 == 8 && level2 == 0) {
                    return Pair.of(ALWAYS_TRUE, null);
                }
            }

            if (block1 instanceof CopperGolemStatueBlock
                    && state1.get(CopperGolemStatueBlock.POSE).getNext() == state2.get(CopperGolemStatueBlock.POSE)
                    && state1.get(CopperGolemStatueBlock.FACING) == state2.get(CopperGolemStatueBlock.FACING)
                    && state1.get(CopperGolemStatueBlock.WATERLOGGED)
                            == state2.get(CopperGolemStatueBlock.WATERLOGGED)) {
                return Pair.of(FarmingUtils::isStatuePoseSwitchItem, null);
            }
        }

        if (block1 instanceof CakeBlock && block2 instanceof CandleCakeBlock) {
            if (state1.get(CakeBlock.BITES) == 0) {
                Item candleItem = getRequiredCandleItem(block2);
                if (candleItem != null) {
                    return Pair.of(isItem(candleItem), null);
                }
            }
        }

        if (block1 instanceof CandleCakeBlock && block2 instanceof CakeBlock) {
            if (state2.equals(Blocks.CAKE.getDefaultState().with(CakeBlock.BITES, 1))) {
                return Pair.of(ALWAYS_TRUE, null);
            }
        }

        if (block1 instanceof CandleCakeBlock && block2 instanceof CandleCakeBlock) {
            if (state1.get(CandleCakeBlock.LIT) && state2.equals(state1.with(CandleCakeBlock.LIT, false))) {
                return Pair.of(ItemStack::isEmpty, hit(pos, Direction.UP, 0.5, 0.75, 0.5));
            }
            if (!state1.get(CandleCakeBlock.LIT) && state2.equals(state1.with(CandleCakeBlock.LIT, true))) {
                return Pair.of(isAnyOf(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE), null);
            }
        }

        if (block1 instanceof FlowerPotBlock pot1 && block2 instanceof FlowerPotBlock pot2) {
            if (pot1.getContent() == Blocks.AIR && pot2.getContent() != Blocks.AIR) {
                Block content = pot2.getContent();
                return Pair.of(
                        stack -> stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == content,
                        null);
            }
        }

        if (block1 instanceof PumpkinBlock && block2 == Blocks.CARVED_PUMPKIN) {
            Direction facing = state2.get(CarvedPumpkinBlock.FACING);
            return Pair.of(isItem(Items.SHEARS), hit(pos, facing, 0.5, 0.5, 0.5));
        }

        return null;
    }

    @NeedTest
    public static boolean isBreedable(Entity entity) {
        return entity instanceof AnimalEntity
                && !isFoodOnlyEntity(entity)
                && !getBreedItems(entity).isEmpty();
    }

    @NeedTest
    public static Set<ItemStack> getBreedItems(Entity entity) {
        if (!(entity instanceof AnimalEntity animalEntity) || isFoodOnlyEntity(entity)) {
            return Set.of();
        }

        Set<ItemStack> result = new LinkedHashSet<>();
        for (Item item : BREED_ITEM_CANDIDATES) {
            ItemStack stack = new ItemStack(item);
            if (animalEntity.isBreedingItem(stack)) {
                result.add(stack);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @NeedTest
    private static Predicate<ItemStack> isItem(Item item) {
        return stack -> stack != null && stack.isOf(item);
    }

    @NeedTest
    private static Predicate<ItemStack> isAnyOf(Item... items) {
        return stack -> {
            if (stack == null) {
                return false;
            }
            for (Item item : items) {
                if (stack.isOf(item)) {
                    return true;
                }
            }
            return false;
        };
    }

    @NeedTest
    private static boolean canIncreaseComposterLevel(ItemStack stack) {
        return stack != null
                && ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.containsKey(stack.getItem())
                && ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(stack.getItem()) > 0.0F;
    }

    @NeedTest
    private static boolean isStatuePoseSwitchItem(ItemStack stack) {
        return stack != null && !stack.isIn(ItemTags.AXES) && !stack.isOf(Items.HONEYCOMB);
    }

    @NeedTest
    private static Item getRequiredCandleItem(Block candleCakeBlock) {
        var blockId = Registries.BLOCK.getId(candleCakeBlock);
        String path = blockId.getPath();
        if (!path.endsWith("_cake")) {
            return null;
        }
        Item item = Registries.ITEM.get(blockId.withPath(path.substring(0, path.length() - 5)));
        return item == Items.AIR ? null : item;
    }

    @NeedTest
    private static BlockHitResult hit(BlockPos pos, Direction side, double x, double y, double z) {
        return new BlockHitResult(new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z), side, pos, false);
    }

    @NeedTest
    private static boolean isFoodOnlyEntity(Entity entity) {
        for (Class<? extends Entity> type : FOOD_ONLY_ENTITY_TYPES) {
            if (type.isInstance(entity)) {
                return true;
            }
        }
        return false;
    }

    @NeedTest
    private static Direction getSupportDirection(BlockState state) {
        if (state.contains(CocoaBlock.FACING)) {
            return state.get(CocoaBlock.FACING);
        }
        if (state.isOf(Blocks.CAVE_VINES) || state.isOf(Blocks.CAVE_VINES_PLANT)) {
            return Direction.UP;
        }
        return Direction.DOWN;
    }

    @SafeVarargs
    @NeedTest
    private static <T> Set<T> setOf(T... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    @NeedTest
    private static Set<BlockState> allStates(Block... blocks) {
        Set<BlockState> result = new LinkedHashSet<>();
        for (Block block : blocks) {
            result.addAll(block.getStateManager().getStates());
        }
        return Collections.unmodifiableSet(result);
    }

    @NeedTest
    private static Set<Block> blocksOfStates(Set<BlockState> states) {
        Set<Block> result = new LinkedHashSet<>();
        for (BlockState state : states) {
            result.add(state.getBlock());
        }
        return Collections.unmodifiableSet(result);
    }

    @NeedTest
    private static boolean isVerticalHarvestable(World world, BlockPos pos, Block... blocks) {
        BlockState state = world.getBlockState(pos);
        BlockState below = world.getBlockState(pos.down());
        for (Block block : blocks) {
            if (state.isOf(block)) {
                for (Block support : blocks) {
                    if (below.isOf(support)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NeedTest
    private static boolean isBambooHarvestable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.BAMBOO)) {
            return false;
        }
        BlockState below = world.getBlockState(pos.down());
        return below.isOf(Blocks.BAMBOO) || below.isOf(Blocks.BAMBOO_SAPLING);
    }

    @NeedTest
    private static boolean isAttachedFruitHarvestable(World world, BlockPos pos, Block fruit, Block attachedStem) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(fruit)) {
            return false;
        }
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction)).isOf(attachedStem)) {
                return true;
            }
        }
        return false;
    }

    @NeedTest
    private static boolean isPitcherHarvestable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.PITCHER_CROP)) {
            return false;
        }
        if (state.get(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER) {
            return state.get(PitcherCropBlock.AGE) >= 4;
        }
        BlockState below = world.getBlockState(pos.down());
        return below.isOf(Blocks.PITCHER_CROP)
                && below.get(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
                && below.get(PitcherCropBlock.AGE) >= 4;
    }

    @NeedTest
    public enum PlantType {
        WHEAT(
                Items.WHEAT_SEEDS,
                true,
                allStates(Blocks.WHEAT),
                Set.of(Blocks.WHEAT),
                setOf(Blocks.WHEAT.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.WHEAT) && state.get(CropBlock.AGE) >= 7;
                }),
        CARROT(
                Items.CARROT,
                true,
                allStates(Blocks.CARROTS),
                Set.of(Blocks.CARROTS),
                setOf(Blocks.CARROTS.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.CARROTS) && state.get(CropBlock.AGE) >= 7;
                }),
        POTATO(
                Items.POTATO,
                true,
                allStates(Blocks.POTATOES),
                Set.of(Blocks.POTATOES),
                setOf(Blocks.POTATOES.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.POTATOES) && state.get(CropBlock.AGE) >= 7;
                }),
        BEETROOT(
                Items.BEETROOT_SEEDS,
                true,
                allStates(Blocks.BEETROOTS),
                Set.of(Blocks.BEETROOTS),
                setOf(Blocks.BEETROOTS.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.BEETROOTS) && state.get(BeetrootsBlock.AGE) >= 3;
                }),
        TORCHFLOWER(
                Items.TORCHFLOWER_SEEDS,
                true,
                allStates(Blocks.TORCHFLOWER_CROP, Blocks.TORCHFLOWER),
                Set.of(Blocks.TORCHFLOWER),
                setOf(Blocks.TORCHFLOWER_CROP.getDefaultState()),
                (world, pos) -> world.getBlockState(pos).isOf(Blocks.TORCHFLOWER)),
        PITCHER(
                Items.PITCHER_POD,
                true,
                allStates(Blocks.PITCHER_CROP),
                Set.of(Blocks.PITCHER_CROP),
                setOf(Blocks.PITCHER_CROP.getDefaultState().with(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER)),
                FarmingUtils::isPitcherHarvestable),
        MELON(
                Items.MELON_SEEDS,
                false,
                allStates(Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM, Blocks.MELON),
                Set.of(Blocks.MELON),
                setOf(Blocks.MELON_STEM.getDefaultState()),
                (world, pos) -> isAttachedFruitHarvestable(world, pos, Blocks.MELON, Blocks.ATTACHED_MELON_STEM)),
        PUMPKIN(
                Items.PUMPKIN_SEEDS,
                false,
                allStates(Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN),
                Set.of(Blocks.PUMPKIN),
                setOf(Blocks.PUMPKIN_STEM.getDefaultState()),
                (world, pos) -> isAttachedFruitHarvestable(world, pos, Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM)),
        NETHER_WART(
                Items.NETHER_WART,
                true,
                allStates(Blocks.NETHER_WART),
                Set.of(Blocks.NETHER_WART),
                setOf(Blocks.NETHER_WART.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.NETHER_WART) && state.get(NetherWartBlock.AGE) >= 3;
                }),
        COCOA(
                Items.COCOA_BEANS,
                true,
                allStates(Blocks.COCOA),
                Set.of(Blocks.COCOA),
                setOf(Blocks.COCOA.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.COCOA) && state.get(CocoaBlock.AGE) >= 2;
                }),
        SWEET_BERRY(
                Items.SWEET_BERRIES,
                false,
                allStates(Blocks.SWEET_BERRY_BUSH),
                Set.of(Blocks.SWEET_BERRY_BUSH),
                setOf(Blocks.SWEET_BERRY_BUSH.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isOf(Blocks.SWEET_BERRY_BUSH) && state.get(SweetBerryBushBlock.AGE) > 1;
                }),
        CACTUS(
                Items.CACTUS,
                false,
                allStates(Blocks.CACTUS, Blocks.CACTUS_FLOWER),
                Set.of(Blocks.CACTUS, Blocks.CACTUS_FLOWER),
                setOf(Blocks.CACTUS.getDefaultState()),
                (world, pos) -> world.getBlockState(pos).isOf(Blocks.CACTUS_FLOWER)
                        || isVerticalHarvestable(world, pos, Blocks.CACTUS)),
        SUGAR_CANE(
                Items.SUGAR_CANE,
                false,
                allStates(Blocks.SUGAR_CANE),
                Set.of(Blocks.SUGAR_CANE),
                setOf(Blocks.SUGAR_CANE.getDefaultState()),
                (world, pos) -> isVerticalHarvestable(world, pos, Blocks.SUGAR_CANE)),
        BAMBOO(
                Items.BAMBOO,
                false,
                allStates(Blocks.BAMBOO, Blocks.BAMBOO_SAPLING),
                Set.of(Blocks.BAMBOO),
                setOf(Blocks.BAMBOO_SAPLING.getDefaultState(), Blocks.BAMBOO.getDefaultState()),
                FarmingUtils::isBambooHarvestable),
        KELP(
                Items.KELP,
                false,
                allStates(Blocks.KELP, Blocks.KELP_PLANT),
                Set.of(Blocks.KELP, Blocks.KELP_PLANT),
                setOf(Blocks.KELP.getDefaultState()),
                (world, pos) -> isVerticalHarvestable(world, pos, Blocks.KELP, Blocks.KELP_PLANT)),
        GLOW_BERRY(
                Items.GLOW_BERRIES,
                false,
                allStates(Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT),
                Set.of(Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT),
                setOf(Blocks.CAVE_VINES.getDefaultState()),
                (world, pos) -> {
                    BlockState state = world.getBlockState(pos);
                    return (state.isOf(Blocks.CAVE_VINES) || state.isOf(Blocks.CAVE_VINES_PLANT))
                            && state.get(CaveVines.BERRIES);
                });

        private final Item seedItem;
        private final boolean needReplant;
        private final Set<BlockState> optionalStates;
        private final Set<Block> harvestableBlocks;
        private final Set<BlockState> placementStates;
        private final Set<Block> relatedBlocks;
        private final BiPredicate<World, BlockPos> harvestPredicate;

        PlantType(
                Item seedItem,
                boolean needReplant,
                Set<BlockState> optionalStates,
                Set<Block> harvestableBlocks,
                Set<BlockState> placementStates,
                BiPredicate<World, BlockPos> harvestPredicate) {
            this.seedItem = seedItem;
            this.needReplant = needReplant;
            this.optionalStates = optionalStates;
            this.harvestableBlocks = harvestableBlocks;
            this.placementStates = placementStates;
            this.relatedBlocks = blocksOfStates(optionalStates);
            this.harvestPredicate = harvestPredicate;
        }

        @NeedTest
        public Item getSeedItem() {
            return seedItem;
        }

        @NeedTest
        public Set<BlockState> getOptionalStates() {
            return optionalStates;
        }

        @NeedTest
        public boolean needReplant() {
            return needReplant;
        }

        @NeedTest
        public Set<Block> getHarvestableBlocks() {
            return harvestableBlocks;
        }

        @NeedTest
        public boolean canHarvest(World world, BlockPos pos) {
            return harvestPredicate.test(world, pos);
        }

        @NeedTest
        public Set<BlockState> getPlacementStates() {
            return placementStates;
        }

        @NeedTest
        public Set<Block> getRelatedBlocks() {
            return relatedBlocks;
        }
    }
}
