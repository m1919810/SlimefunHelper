---
name: minecraft-agriculture-analysis
description: Analyze Minecraft agriculture data in this merged Yarn source tree. Use when统计农作物方块/物品、方块状态、成熟判定、可种植位置、种子映射，或统计可繁育动物及其繁育物品/tag。
disable-model-invocation: true
---

# Minecraft Agriculture Analysis

## Scope
- Primary source scope
  - `net/minecraft/block/**`
  - `net/minecraft/item/**`
  - `net/minecraft/entity/passive/**`
  - `net/minecraft/entity/mob/**`
  - `net/minecraft/registry/tag/ItemTags.java`
- Data source scope
  - `data/minecraft/tags/item/*food*.json`
  - `data/minecraft/tags/item/*tempt*.json`
  - `data/minecraft/loot_table/**` when harvest drops matter

## Core task
统计 Minecraft 农业相关两类数据：
1. 全部农作物方块 / 对应物品 / 方块状态 / 成熟状态判定 / 可种植位置判定 / 种子或种植物品对应关系
2. 全部可繁育动物 / 进入繁育状态的物品类型 / 对应 `ItemTags` 或硬编码物品 / 特殊繁育条件

输出人话版结构，不做逐行代码解释。

## Agriculture definition
默认把“农业”拆成两层，避免混淆：

### A. 标准可种植作物
优先统计这些：
- 耕地作物：`WHEAT`、`CARROTS`、`POTATOES`、`BEETROOTS`、`TORCHFLOWER_CROP`、`PITCHER_CROP`
- 藤/茎瓜类：`MELON_STEM`、`PUMPKIN_STEM`、`ATTACHED_MELON_STEM`、`ATTACHED_PUMPKIN_STEM`
- 特殊作物：`NETHER_WART`、`COCOA`、`SWEET_BERRY_BUSH`

### B. 农场可量产植物
用户说“全部农作物”且没有限定耕地时，也纳入：
- `CACTUS`
- `SUGAR_CANE`
- `BAMBOO`
- `KELP`
- `CAVE_VINES` / `CAVE_VINES_PLANT` if glow berries are relevant
- 需要时再扩展到蘑菇、树苗、藤蔓、海草、花等可种植植物；但要单独标成“非标准作物”。

## Crop anchors
从这些类开始，不要只搜方块名：
- `CropBlock`
  - 默认 `AGE = Properties.AGE_7`
  - `MAX_AGE = 7`
  - `isMature(state) => getAge(state) >= getMaxAge()`
  - `canPlantOnTop => Blocks.FARMLAND`
  - `canPlaceAt => light >= 8 && super.canPlaceAt(...)`
  - 默认种子：`Items.WHEAT_SEEDS`
- `BeetrootsBlock`
  - `AGE = Properties.AGE_3`
  - `getMaxAge() => 3`
  - 种子：`Items.BEETROOT_SEEDS`
- `CarrotsBlock`
  - 通常继承 `CropBlock`
  - 成熟：`AGE_7 == 7`
  - 种植物品就是 `Items.CARROT`
- `PotatoesBlock`
  - 通常继承 `CropBlock`
  - 成熟：`AGE_7 == 7`
  - 种植物品就是 `Items.POTATO`
- `TorchflowerBlock`
  - `AGE = Properties.AGE_1`
  - `getMaxAge() => 2`
  - `withAge(2) => Blocks.TORCHFLOWER`
  - 种子：`Items.TORCHFLOWER_SEEDS`
- `PitcherCropBlock`
  - `AGE = Properties.AGE_4`
  - `HALF = TallPlantBlock.HALF`
  - 成熟：lower half `AGE >= 4`
  - `AGE >= 3` 后变双格作物
  - 种植物品：`Items.PITCHER_POD`
  - 可种植：下方 `FARMLAND`，光照走 `CropBlock.hasEnoughLightAt`
- `StemBlock`
  - `AGE = Properties.AGE_7`
  - `AGE == 7` 后尝试在水平相邻空位生成果实，并把 stem 替换为 attached stem
  - 可种植：`Blocks.FARMLAND`
  - 种子来自构造参数 `pickBlockItem`
- `AttachedStemBlock`
  - 已结果状态，没有年龄成熟递增；要和 `StemBlock` 一起统计
- `NetherWartBlock`
  - `AGE = Properties.AGE_3`
  - 成熟：`AGE == 3`
  - 可种植：`Blocks.SOUL_SAND`
  - 种植物品：`Items.NETHER_WART`
- `CocoaBlock`
  - `AGE = Properties.AGE_2`
  - 成熟：`AGE == 2`
  - 另有 `FACING`
  - 可种植：面向方向相邻方块必须在 `BlockTags.JUNGLE_LOGS`
  - 种植物品：`Items.COCOA_BEANS`
- `SweetBerryBushBlock`
  - `AGE = Properties.AGE_3`
  - 可收获：`AGE > 1`
  - 完全成熟：`AGE == 3`
  - 种植/采摘物品：`Items.SWEET_BERRIES`
- `CactusBlock`
  - `AGE = Properties.AGE_15`
  - 生长阈值：`AGE == 15` 长高；`AGE == 8` 可能长 `CACTUS_FLOWER`
  - 可种植：下方 `CACTUS` 或 `BlockTags.SAND`；水平邻居不能是实体方块或 lava；上方不能是液体
- `SugarCaneBlock`
  - `AGE = Properties.AGE_15`
  - 生长阈值：`AGE == 15` 长高
  - 可种植：下方自身，或下方 `BlockTags.DIRT` / `BlockTags.SAND` 且邻水或 frosted ice

## Block / item mapping workflow
1. 从 `Blocks.java` 定位注册：确认 block id、block class、settings 是否 `ticksRandomly`。
2. 从 block class 读取：
   - age property
   - max age / mature condition
   - extra state such as `HALF`, `FACING`
   - `canPlantOnTop` / `canPlaceAt`
   - harvest interaction if different from full maturity
3. 从 `Items.java` 定位种植物品：
   - `AliasedBlockItem` usually means one item places a hidden crop block
   - `BlockItem` means block item itself places the block
   - custom item classes may encode placement constraints
4. 从 loot table 校验掉落物：种子对应关系不要只用掉落物推断，优先用 `getSeedsItem`、item registration 和 placement item。
5. 把结果分成“成熟状态”和“可收获状态”：例如 sweet berry `AGE > 1` 可收获，但 `AGE == 3` 才完全成熟。

## Crop output template
按这个结构输出：

```markdown
## 农作物统计
- 方块：`Blocks.WHEAT`
- 作物类：`CropBlock`
- 种植物品：`Items.WHEAT_SEEDS`
- 收获/产物物品：`Items.WHEAT` + seeds from loot table
- 方块状态：`AGE_7`
- 成熟判定：`age >= 7`
- 可种植位置：下方 `Blocks.FARMLAND`，且放置处光照 `>= 8`
- 备注：是否骨粉、是否双格、是否有 attached 状态
```

## Breeding anchors
先理解总入口，再统计实体：
- `AnimalEntity.interactMob`
  - 手持物品先走 `isBreedingItem(stack)`
  - 成年且 `canEat()` 时进入 love 状态
  - 幼体吃繁育物品会加速成长
- `AnimalMateGoal` / custom `MateGoal`
  - 负责找同类、靠近、生小实体
- `ItemTags.java`
  - 当前版本大量繁育物品通过 `ItemTags.*_FOOD` 表达
- `data/minecraft/tags/item/*food*.json`
  - tag 的真实物品集合在这里

## Breedable entity candidate list
默认统计这些有 `AnimalEntity` 繁育入口或繁育等价行为的实体：
- `CowEntity` / `MooshroomEntity` via `AbstractCowEntity`: `ItemTags.COW_FOOD` => wheat
- `SheepEntity`: `ItemTags.SHEEP_FOOD` => wheat
- `GoatEntity`: `ItemTags.GOAT_FOOD` => wheat
- `PigEntity`: `ItemTags.PIG_FOOD` => carrot, potato, beetroot
- `ChickenEntity`: `ItemTags.CHICKEN_FOOD` => wheat/melon/pumpkin/beetroot seeds, torchflower seeds, pitcher pod
- `RabbitEntity`: `ItemTags.RABBIT_FOOD` => carrot, golden carrot, dandelion
- `BeeEntity`: `ItemTags.BEE_FOOD` => flowers and flower-like blocks
- `TurtleEntity`: `ItemTags.TURTLE_FOOD` => seagrass; also egg laying flow
- `PandaEntity`: `ItemTags.PANDA_FOOD` => bamboo; also nearby bamboo condition in mating goal
- `FoxEntity`: `ItemTags.FOX_FOOD` => sweet berries, glow berries
- `WolfEntity`: `ItemTags.WOLF_FOOD` => meat/fish variants; must be tamed for normal breeding
- `CatEntity`: `ItemTags.CAT_FOOD` => cod, salmon; must be tamed for normal breeding
- `OcelotEntity`: `ItemTags.OCELOT_FOOD` => cod, salmon; trust/taming-like condition is separate from breeding result
- `HorseEntity` / `DonkeyEntity` / `MuleEntity` / related horse family: verify `AbstractHorseEntity`, concrete subclasses, `ItemTags.HORSE_FOOD`, `ItemTags.HORSE_TEMPT_ITEMS`
- `LlamaEntity` / `TraderLlamaEntity`: `ItemTags.LLAMA_FOOD`; usually wheat/hay block with strength/temper side effects
- `CamelEntity`: `ItemTags.CAMEL_FOOD` => cactus
- `ArmadilloEntity`: `ItemTags.ARMADILLO_FOOD` => spider eye
- `FrogEntity`: `ItemTags.FROG_FOOD` => slime ball
- `AxolotlEntity`: `ItemTags.AXOLOTL_FOOD` => tropical fish bucket
- `StriderEntity`: `ItemTags.STRIDER_FOOD` / `STRIDER_TEMPT_ITEMS` => warped fungus
- `HoglinEntity`: `ItemTags.HOGLIN_FOOD` => crimson fungus; verify pacification/repellent constraints
- `SnifferEntity`: `ItemTags.SNIFFER_FOOD` => torchflower seeds
- `HappyGhastEntity`: `ItemTags.HAPPY_GHAST_FOOD` => snowball; distinguish tempt item behavior
- `NautilusEntity` / `AbstractNautilusEntity`: `ItemTags.NAUTILUS_FOOD` and bucket-food tags; verify exact breeding/taming boundary
- `CamelHuskEntity`: `ItemTags.CAMEL_HUSK_FOOD` if present in this version
- `ZombieHorseEntity`: `ItemTags.ZOMBIE_HORSE_FOOD` if present in this version

Treat `AllayEntity` duplication and `ShulkerEntity` splitting as “繁育等价/复制机制”，not normal animal breeding, unless the user asks to include non-animal reproduction.

## Breeding item workflow
1. Search candidate classes for:
   - `isBreedingItem(ItemStack stack)`
   - `new AnimalMateGoal`
   - custom `MateGoal`
   - `SpawnReason.BREEDING`
   - `lovePlayer`, `canBreedWith`, `createChild`
2. For every entity, record the source of its food rule:
   - direct `stack.isOf(Items.X)`
   - `stack.isIn(ItemTags.X_FOOD)`
   - `Ingredient.ofItems(...)`
   - custom method or status condition
3. Resolve tag values from `data/minecraft/tags/item/<tag>.json`.
4. Distinguish:
   - breeding food
   - tempting item
   - taming item
   - healing/feeding item
   - duplication item
5. Record non-item constraints:
   - tamed/trusting status
   - adult/baby status
   - biome/block/environment constraint
   - owner requirement
   - special cooldown or pregnancy/egg-laying state

## Breeding output template
按这个结构输出：

```markdown
## 可繁育动物统计
- 实体：`CowEntity` / `MooshroomEntity`
- 家族入口：`AbstractCowEntity`
- 繁育入口：`AnimalEntity.interactMob` + `AnimalMateGoal`
- 繁育物品类型：`ItemTags.COW_FOOD`
- tag 实际值：`minecraft:wheat`
- 额外条件：成年、未冷却、可进入 love 状态
- 子实体来源：`createChild` / entity type
```

## Completeness checks
完成统计前必须做这些检查：
- 方块侧：不要漏掉 `CropBlock` 子类、`StemBlock` / `AttachedStemBlock`、非 `CropBlock` 但有 `AGE` 的农业植物。
- 物品侧：不要把“收获物”误当“种子”；胡萝卜、马铃薯、下界疣、甜浆果、甘蔗、仙人掌这类本体就是种植物品。
- 状态侧：`AGE` 不是唯一状态；`FACING`、`HALF`、attached stem 状态会影响解释。
- 位置侧：必须读取 `canPlantOnTop` 或 `canPlaceAt`，不要凭常识写。
- 动物侧：只看 `ItemTags` 不够；还要看 `isBreedingItem` 和具体实体的交互/繁育条件。
- 数据侧：tag JSON 是物品集合来源；源码里的 `ItemTags` 只是名字入口。

## Output shape
优先使用这些章节：
- 作物口径
- 农作物方块总表
- 方块状态与成熟判定
- 可种植位置判定
- 种子 / 种植物品 / 收获物对应
- 可繁育动物总表
- 繁育物品 tag 展开
- 特殊条件和边界
- 需要进一步确认的非标准农业对象

## FarmingUtils implementation template
用户需要直接生成农业工具类时，可以使用下面这个实现作为基线。

```java
package me.matl114.utils;

import net.minecraft.block.BambooBlock;
import net.minecraft.block.BeetrootsBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CaveVines;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.PitcherCropBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TorchflowerBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CamelHuskEntity;
import net.minecraft.entity.mob.ZombieHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.HappyGhastEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FarmingUtils {
    private static final Set<Item> BREED_ITEM_CANDIDATES = items(
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
        Items.CACTUS_FLOWER
    );

    private static final Set<Class<? extends Entity>> FOOD_ONLY_ENTITY_TYPES = Set.of(
        CamelHuskEntity.class,
        HappyGhastEntity.class,
        ZombieHorseEntity.class
    );

    public static PlantType getPlantType(Item item) {
        for (PlantType plantType : PlantType.values()) {
            if (plantType.getSeedItem() == item) {
                return plantType;
            }
        }
        return null;
    }

    public static PlantType getPlantType(Block block) {
        for (PlantType plantType : PlantType.values()) {
            if (plantType.blocks.contains(block)) {
                return plantType;
            }
        }
        return null;
    }

    public static BlockHitResult tryPlantAt(World world, BlockPos pos, PlantType plantType) {
        if (world == null || pos == null || plantType == null) {
            return null;
        }

        for (BlockState state : plantType.placementStates) {
            if (canUsePlacementState(world, pos, state)) {
                Direction supportDirection = getSupportDirection(state);
                BlockPos supportPos = pos.offset(supportDirection);
                return new BlockHitResult(Vec3d.ofCenter(supportPos), supportDirection.getOpposite(), supportPos, false);
            }
        }
        return null;
    }

    public static boolean isBreedable(Entity entity) {
        return entity instanceof AnimalEntity && !isFoodOnlyEntity(entity) && !getBreedItems(entity).isEmpty();
    }

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

    private static boolean isFoodOnlyEntity(Entity entity) {
        for (Class<? extends Entity> type : FOOD_ONLY_ENTITY_TYPES) {
            if (type.isInstance(entity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canUsePlacementState(World world, BlockPos pos, BlockState state) {
        BlockState current = world.getBlockState(pos);
        if (!current.isAir() && !current.isOf(Blocks.WATER)) {
            return false;
        }
        return state.canPlaceAt(world, pos);
    }

    private static Direction getSupportDirection(BlockState state) {
        if (state.contains(CocoaBlock.FACING)) {
            return state.get(CocoaBlock.FACING);
        }
        if (state.isOf(Blocks.CAVE_VINES) || state.isOf(Blocks.CAVE_VINES_PLANT)) {
            return Direction.UP;
        }
        return Direction.DOWN;
    }

    private static Set<Item> items(Item... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    private static Set<Block> blocks(Collection<BlockState> states) {
        Set<Block> result = new LinkedHashSet<>();
        for (BlockState state : states) {
            result.add(state.getBlock());
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> states(BlockState... states) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(states)));
    }

    private static Set<BlockState> combine(Collection<BlockState>... groups) {
        Set<BlockState> result = new LinkedHashSet<>();
        for (Collection<BlockState> group : groups) {
            result.addAll(group);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> ageStates(Block block, IntProperty ageProperty, int maxAge) {
        Set<BlockState> result = new LinkedHashSet<>();
        for (int age = 0; age <= maxAge; age++) {
            result.add(block.getDefaultState().with(ageProperty, age));
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> facingAgeStates(Block block, EnumProperty<Direction> facingProperty, IntProperty ageProperty, int maxAge) {
        Set<BlockState> result = new LinkedHashSet<>();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            for (int age = 0; age <= maxAge; age++) {
                result.add(block.getDefaultState().with(facingProperty, direction).with(ageProperty, age));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> attachedStemStates(Block block) {
        Set<BlockState> result = new LinkedHashSet<>();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            result.add(block.getDefaultState().with(CocoaBlock.FACING, direction));
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> pitcherStates() {
        Set<BlockState> result = new LinkedHashSet<>();
        for (int age = 0; age <= 4; age++) {
            result.add(Blocks.PITCHER_CROP.getDefaultState().with(PitcherCropBlock.AGE, age).with(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER));
            if (age >= 3) {
                result.add(Blocks.PITCHER_CROP.getDefaultState().with(PitcherCropBlock.AGE, age).with(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<BlockState> booleanStates(Block block, BooleanProperty property) {
        return states(block.getDefaultState().with(property, false), block.getDefaultState().with(property, true));
    }

    public enum PlantType {
        WHEAT(Items.WHEAT_SEEDS, true, ageStates(Blocks.WHEAT, CropBlock.AGE, 7)),
        CARROT(Items.CARROT, true, ageStates(Blocks.CARROTS, CropBlock.AGE, 7)),
        POTATO(Items.POTATO, true, ageStates(Blocks.POTATOES, CropBlock.AGE, 7)),
        BEETROOT(Items.BEETROOT_SEEDS, true, ageStates(Blocks.BEETROOTS, BeetrootsBlock.AGE, 3)),
        TORCHFLOWER(Items.TORCHFLOWER_SEEDS, true, combine(ageStates(Blocks.TORCHFLOWER_CROP, TorchflowerBlock.AGE, 1), states(Blocks.TORCHFLOWER.getDefaultState())), ageStates(Blocks.TORCHFLOWER_CROP, TorchflowerBlock.AGE, 1)),
        PITCHER(Items.PITCHER_POD, true, pitcherStates(), states(Blocks.PITCHER_CROP.getDefaultState().with(PitcherCropBlock.AGE, 0).with(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER))),
        MELON(Items.MELON_SEEDS, false, combine(ageStates(Blocks.MELON_STEM, CropBlock.AGE, 7), attachedStemStates(Blocks.ATTACHED_MELON_STEM), states(Blocks.MELON.getDefaultState())), ageStates(Blocks.MELON_STEM, CropBlock.AGE, 7)),
        PUMPKIN(Items.PUMPKIN_SEEDS, false, combine(ageStates(Blocks.PUMPKIN_STEM, CropBlock.AGE, 7), attachedStemStates(Blocks.ATTACHED_PUMPKIN_STEM), states(Blocks.PUMPKIN.getDefaultState())), ageStates(Blocks.PUMPKIN_STEM, CropBlock.AGE, 7)),
        NETHER_WART(Items.NETHER_WART, true, ageStates(Blocks.NETHER_WART, NetherWartBlock.AGE, 3)),
        COCOA(Items.COCOA_BEANS, true, facingAgeStates(Blocks.COCOA, CocoaBlock.FACING, CocoaBlock.AGE, 2)),
        SWEET_BERRY(Items.SWEET_BERRIES, false, ageStates(Blocks.SWEET_BERRY_BUSH, SweetBerryBushBlock.AGE, 3)),
        CACTUS(Items.CACTUS, false, combine(ageStates(Blocks.CACTUS, CactusBlock.AGE, 15), states(Blocks.CACTUS_FLOWER.getDefaultState())), states(Blocks.CACTUS.getDefaultState())),
        SUGAR_CANE(Items.SUGAR_CANE, false, ageStates(Blocks.SUGAR_CANE, SugarCaneBlock.AGE, 15), states(Blocks.SUGAR_CANE.getDefaultState())),
        BAMBOO(Items.BAMBOO, false, states(Blocks.BAMBOO_SAPLING.getDefaultState(), Blocks.BAMBOO.getDefaultState().with(BambooBlock.AGE, 0).with(BambooBlock.STAGE, 0)), states(Blocks.BAMBOO_SAPLING.getDefaultState(), Blocks.BAMBOO.getDefaultState())),
        KELP(Items.KELP, false, states(Blocks.KELP.getDefaultState(), Blocks.KELP_PLANT.getDefaultState()), states(Blocks.KELP.getDefaultState())),
        GLOW_BERRY(Items.GLOW_BERRIES, false, combine(booleanStates(Blocks.CAVE_VINES, CaveVines.BERRIES), booleanStates(Blocks.CAVE_VINES_PLANT, CaveVines.BERRIES)), states(Blocks.CAVE_VINES.getDefaultState().with(CaveVines.BERRIES, true)));

        private final Item seedItem;
        private final boolean needReplant;
        private final Set<BlockState> optionalStates;
        private final Set<BlockState> placementStates;
        private final Set<Block> blocks;

        PlantType(Item seedItem, boolean needReplant, Set<BlockState> optionalStates) {
            this(seedItem, needReplant, optionalStates, optionalStates);
        }

        PlantType(Item seedItem, boolean needReplant, Set<BlockState> optionalStates, Set<BlockState> placementStates) {
            this.seedItem = seedItem;
            this.needReplant = needReplant;
            this.optionalStates = optionalStates;
            this.placementStates = placementStates;
            this.blocks = blocks(optionalStates);
        }

        public Item getSeedItem() {
            return seedItem;
        }

        public Set<BlockState> getOptionalStates() {
            return optionalStates;
        }

        public boolean needReplant() {
            return needReplant;
        }
    }
}
```

Keep it concise, structural, and source-grounded.