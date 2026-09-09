package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.Spawner;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinActivity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.item.*;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipePropertySet;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class InteractUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Predicate<ItemStack> ALWAYS_TRUE = stack -> true;

    @Nullable
    public static BlockState getBlockPlacement(
            Block block, PlayerEntity player, World world, BlockHitResult blockHitResult) {
        Item blockItem = block.asItem();
        return blockItem instanceof BlockItem blockItem1
                ? getBlockPlacement(blockItem1, player, world, blockHitResult)
                : null;
    }

    @Nullable
    public static BlockState getBlockPlacement(
            BlockItem blockItem, PlayerEntity player, World world, BlockHitResult blockHitResult) {
        ItemPlacementContext placement =
                new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(blockItem), blockHitResult);
        placement = blockItem.getPlacementContext(placement);
        return blockItem.getPlacementState(placement);
    }

    @Nullable
    public static BlockState getBlockPlacement(
            PlayerEntity player, Hand hand, ItemStack stack, BlockHitResult hitResult) {
        return stack.getItem() instanceof BlockItem blockItem
                ? blockItem.getPlacementState(new ItemPlacementContext(player, hand, stack, hitResult))
                : null;
    }
    // should equals getBlockPlacement(STONE) != null
    public static boolean canCubePlace(PlayerEntity player, BlockPos pos) {
        // cube
        World world = player.getEntityWorld();
        BlockState state = Blocks.STONE.getDefaultState();
        return state.canPlaceAt(world, pos)
                && world.canPlace(state, pos, player == null ? ShapeContext.absent() : ShapeContext.of(player));
    }
    // should equals getBlockPlacement(state.getBlock) != null
    public static boolean canBlockPlace(PlayerEntity player, BlockPos pos, BlockState state) {
        World world = player.getEntityWorld();
        return state.canPlaceAt(world, pos) && world.canPlace(state, pos, ShapeContext.of(player));
    }

    public static BlockPos getCurrentPlacePos(PlayerEntity player, BlockHitResult blockHitResult) {
        ItemPlacementContext placement =
                new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(Blocks.STONE), blockHitResult);
        return placement.getBlockPos();
    }

    public static boolean canCubePlace(PlayerEntity player, BlockHitResult state) {
        BlockPos pos = getCurrentPlacePos(player, state);
        return canCubePlace(player, pos);
    }

    public static ActionResult simulateInteract(EntityHitResult entityHitResult) {
        ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(
                mc.player, entityHitResult.getEntity(), entityHitResult, Hand.MAIN_HAND);
        if (!actionResult.isAccepted()) {
            actionResult = mc.interactionManager.interactEntity(mc.player, entityHitResult.getEntity(), Hand.MAIN_HAND);
        }

        if (actionResult instanceof ActionResult.Success) {
            ActionResult.Success success = (ActionResult.Success) actionResult;
            if (success.swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        return actionResult;
    }

    public static void swingHandIfSuccess(ActionResult actionResult3, Hand hand) {
        if (actionResult3 instanceof ActionResult.Success) {
            ActionResult.Success success3 = (ActionResult.Success) actionResult3;
            if (success3.swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(hand);
            }
        }
    }

    public static boolean canHoldUse(ItemStack stack) {
        return stack.contains(DataComponentTypes.CONSUMABLE)
                || stack.getItem() instanceof ShieldItem
                || VItem.getInstance().isSpear(stack)
                || stack.getMaxUseTime(mc.player) > 0;
    }

    public static Set<Block> STATE_MAY_INTERACT = null;

    public static boolean canShulkerOpen(World world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity) {
            if (shulkerBoxBlockEntity.getAnimationStage() != ShulkerBoxBlockEntity.AnimationStage.CLOSED) {
                return true;
            }
        }
        Box box = ShulkerEntity.calculateBoundingBox(
                        1.0F, state.get(ShulkerBoxBlock.FACING), 0.0F, 0.5F, pos.toBottomCenterPos())
                .contract(1.0E-6);
        return world.isSpaceEmpty(box);
    }

    public static boolean canEnderChestOpen(World world, BlockPos pos) {
        return !world.getBlockState(pos.up()).isSolidBlock(world, pos.up());
    }

    public static boolean canChestOpen(World world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        if (ChestBlock.isChestBlocked(world, pos)) {
            return false;
        }
        if (state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
            if (ChestBlock.isChestBlocked(world, otherPos)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canRespawnAnchorExplode(World world) {
        String worldName = world.getRegistryKey().getValue().toString();
        if (Objects.equals(worldName, "minecraft:overworld") || Objects.equals(worldName, "minecraft:the_end")) {
            return true;
        }
        if (!world.getDimension().hasCeiling()) {
            return true;
        }
        if (world.getDimension().respawnAnchorWorks()) {
            // may not explode
            return false;
        }
        return true;
    }

    private static boolean isInteractableRespawnAnchor(BlockState state, ItemStack stack) {
        int charges = state.get(RespawnAnchorBlock.CHARGES);
        if (charges == 0 && !stack.isOf(Items.GLOWSTONE)) {
            return false;
        }
        return true;
    }

    private static boolean canFenceConsume(World world, BlockPos pos, @Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        boolean hasLead = player.getMainHandStack().getItem() instanceof LeadItem
                || player.getOffHandStack().getItem() instanceof LeadItem;
        if (!hasLead) {
            return false;
        }
        List<Leashable> leashables =
                LeadItem.collectLeashablesAround(world, pos, entity -> entity.getLeashHolder() == player);
        return !leashables.isEmpty();
    }

    public static boolean canBlockOpenScreen(World world, BlockState state, BlockPos pos) {
        return state.createScreenHandlerFactory(world, pos) != null;
    }

    public static boolean canOpenScreen(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ChestBlock) {
            return canChestOpen(world, pos, state);
        }
        if (block instanceof ShulkerBoxBlock) {
            return canShulkerOpen(world, pos, state);
        }
        if (block instanceof EnderChestBlock) {
            return canEnderChestOpen(world, pos);
        }
        if (block instanceof LecternBlock) {
            return state.contains(LecternBlock.HAS_BOOK) && state.get(LecternBlock.HAS_BOOK);
        }
        NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
        return factory != null;
    }

    public static boolean isInteractAcceptable(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        return isInteractAcceptable(world, player, pos, state, ItemStack.EMPTY);
    }

    public static final Set<Block> shovelBlocks = new HashSet<>();

    static {
        shovelBlocks.add(Blocks.GRASS_BLOCK);
        shovelBlocks.add(Blocks.DIRT);
        shovelBlocks.add(Blocks.PODZOL);
        shovelBlocks.add(Blocks.COARSE_DIRT);
        shovelBlocks.add(Blocks.MYCELIUM);
        shovelBlocks.add(Blocks.ROOTED_DIRT);
    }

    public static boolean isInteractAcceptable(
            World world, PlayerEntity player, BlockPos pos, BlockState state, ItemStack interactStack) {
        Block block = state.getBlock();
        if (block instanceof RespawnAnchorBlock) {
            return isInteractableRespawnAnchor(state, interactStack);
        }
        if (block instanceof LecternBlock) {
            return state.contains(LecternBlock.HAS_BOOK) && state.get(LecternBlock.HAS_BOOK);
        }
        if (block instanceof FenceBlock) {
            return canFenceConsume(world, pos, player);
        }
        if (block instanceof JukeboxBlock) {
            return state.contains(JukeboxBlock.HAS_RECORD) && state.get(JukeboxBlock.HAS_RECORD);
        }
        if ((block instanceof CakeBlock || block instanceof CandleCakeBlock) && !player.canConsume(false)) {
            return false;
        }
        if (block instanceof PumpkinBlock pumpkinBlock) {
            return interactStack.isOf(Items.SHEARS);
        }
        if (block instanceof ComposterBlock composterBlock) {
            return (state.contains(ComposterBlock.LEVEL) && state.get(ComposterBlock.LEVEL) == 8)
                    || ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.containsKey(interactStack.getItem());
        }
        if (block instanceof BeehiveBlock beehive) {
            return state.contains(BeehiveBlock.HONEY_LEVEL)
                    && state.get(BeehiveBlock.HONEY_LEVEL) >= 5
                    && (interactStack.isOf(Items.SHEARS) || interactStack.isOf(Items.GLASS_BOTTLE));
        }
        if (block instanceof CampfireBlock campfireBlock) {
            return world.getRecipeManager()
                    .getPropertySet(RecipePropertySet.CAMPFIRE_INPUT)
                    .canUse(interactStack);
        }
        if (block instanceof AbstractCauldronBlock cauldronBlock) {
            return cauldronBlock.behaviorMap.map().containsKey(interactStack.getItem());
        }
        Item item = interactStack.getItem();
        // 矿车放铁轨
        if (item instanceof MinecartItem && state.isIn(BlockTags.RAILS)) {
            return true;
        }
        // 盔甲架
        if (item instanceof ArmorStandItem) {
            return true;
        }
        // 末地水晶
        if (item instanceof EndCrystalItem && (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))) {
            return true;
        }
        if (item instanceof SpawnEggItem) {
            return state.hasBlockEntity() && world.getBlockEntity(pos) instanceof Spawner
                    || state.getCollisionShape(world, pos).isEmpty();
        }
        // 打火石 / 火焰弹
        if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) {
            if (CampfireBlock.canBeLit(state) || CandleBlock.canBeLit(state) || CandleCakeBlock.canBeLit(state)) {
                return true;
            }
            BlockPos firePos = pos.offset(Direction.UP);
            if (AbstractFireBlock.canPlaceAt(world, firePos, player.getHorizontalFacing())) {
                return true;
            }
        }
        // 骨粉
        if (item instanceof BoneMealItem) {
            Block varBoneMealBlock = state.getBlock();
            if (varBoneMealBlock instanceof Fertilizable fertilizable
                    && fertilizable.isFertilizable(world, pos, state)) {
                return true;
            }
            BlockPos sidePos = pos.up();
            if (state.isSideSolidFullSquare(world, pos, Direction.UP)
                    && world.getBlockState(sidePos).isOf(Blocks.WATER)
                    && world.getFluidState(sidePos).getLevel() == 8) {
                return true;
            }
        }
        // 铲子拍平 / 熄灭营火
        if (item instanceof ShovelItem) {
            if (shovelBlocks.contains(block) && world.getBlockState(pos.up()).isAir()) {
                return true;
            }
            //            if (block instanceof CampfireBlock && state.get(CampfireBlock.LIT)) {
            //                return true;
            //            }
        }
        // 蜂蜜脾上蜡
        if (item instanceof HoneycombItem && HoneycombItem.getWaxedState(state).isPresent()) {
            return true;
        }
        // 水瓶变泥
        if (item instanceof PotionItem) {
            PotionContentsComponent potionContents =
                    interactStack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            if (potionContents.matches(Potions.WATER) && state.isIn(BlockTags.CONVERTABLE_TO_MUD)) {
                return true;
            }
        }

        if (STATE_MAY_INTERACT == null) {
            HashSet<Block> result = new HashSet<>();
            for (Block entry : Registries.BLOCK) {
                if (entry instanceof OperatorBlock
                        || entry instanceof AbstractSignBlock
                        || entry instanceof DoorBlock
                        || entry instanceof TrapdoorBlock
                        || entry instanceof FenceGateBlock
                        || entry instanceof BedBlock
                        || entry instanceof CakeBlock
                        || entry instanceof CandleCakeBlock
                        || entry instanceof FlowerPotBlock
                        || entry instanceof DecoratedPotBlock
                        || entry instanceof JukeboxBlock
                        || entry instanceof BellBlock
                        || entry instanceof LeverBlock
                        || entry instanceof ButtonBlock
                        || entry instanceof RedstoneOreBlock
                        || entry instanceof NoteBlock
                        || entry instanceof LightBlock
                        || entry instanceof DragonEggBlock
                        || entry instanceof ChestBlock
                        || entry instanceof ShulkerBoxBlock
                        || entry instanceof EnderChestBlock
                        || entry instanceof CraftingTableBlock
                        || entry instanceof StonecutterBlock
                        || entry instanceof LoomBlock
                        || entry instanceof SmithingTableBlock
                        || entry instanceof CartographyTableBlock
                        || entry instanceof GrindstoneBlock
                        || entry instanceof AnvilBlock
                        || entry instanceof BeaconBlock
                        || entry instanceof BarrelBlock
                        || entry instanceof BrewingStandBlock
                        || entry instanceof DispenserBlock
                        || entry instanceof HopperBlock
                        || entry instanceof CrafterBlock
                        || entry instanceof AbstractFurnaceBlock) {
                    result.add(entry);
                }
            }
            STATE_MAY_INTERACT = result;
        }
        return STATE_MAY_INTERACT.contains(block);
    }

    public static final Set<Class<? extends Entity>> ENTITY_MAY_INTERACT;

    static {
        HashSet<Class<? extends Entity>> result = new HashSet<>();
        ENTITY_MAY_INTERACT = result;
    }

    public static boolean isInteractAcceptable(
            World world, PlayerEntity player, Entity entity, ItemStack interactStack) {
        if (world == null || player == null || entity == null) {
            return false;
        }
        if (player.isSpectator() || !entity.isAlive()) {
            return false;
        }
        ItemStack stack = interactStack == null ? ItemStack.EMPTY : interactStack;
        if (isGenericAcceptedInteractItem(entity, stack)) {
            return true;
        }
        if (isVehicleEntityInteractAcceptable(player, entity, stack)) {
            return true;
        }
        if (isSpecialEntityInteractAcceptable(player, entity, stack)) {
            return true;
        }
        return false;
    }

    private static boolean isGenericAcceptedInteractItem(Entity entity, ItemStack stack) {
        if (stack.isOf(Items.NAME_TAG) && entity instanceof LivingEntity) {
            return true;
        }
        if (stack.getItem() instanceof SpawnEggItem && entity instanceof MobEntity) {
            return true;
        }
        if (stack.isOf(Items.LEAD) && entity instanceof Leashable && !(entity instanceof LeashKnotEntity)) {
            return true;
        }
        if (stack.isOf(Items.SADDLE) && entity instanceof Saddleable livingEntity && livingEntity.canBeSaddled()) {
            return true;
        }
        if (stack.isOf(Items.WATER_BUCKET) && entity instanceof Bucketable) {
            return true;
        }
        if (stack.isOf(Items.SHEARS) && entity instanceof Shearable shearable && shearable.isShearable()) {
            return true;
        }
        if (entity instanceof AnimalEntity animal && animal.isBreedingItem(stack)) {
            return true;
        }
        if (entity instanceof MooshroomEntity mooshroom) {
            if (!mooshroom.isBaby() && stack.isOf(Items.BOWL)) {
                return true;
            }
            return mooshroom.getVariant() == MooshroomEntity.Type.BROWN
                    && SuspiciousStewIngredient.of(stack.getItem()) != null;
        }
        if (entity instanceof CowEntity cow) {
            return stack.isOf(Items.BUCKET) && !cow.isBaby();
        }
        if (entity instanceof GoatEntity goat) {
            return stack.isOf(Items.BUCKET) && !goat.isBaby();
        }

        if (entity instanceof IronGolemEntity ironGolem) {
            return stack.isOf(Items.IRON_INGOT) && ironGolem.getHealth() < ironGolem.getMaxHealth();
        }
        if (entity instanceof ArmadilloEntity armadillo) {
            return stack.isOf(Items.BRUSH) && !armadillo.isBaby();
        }
        if (entity instanceof DolphinEntity) {
            return stack.isIn(ItemTags.FISHES);
        }
        if (entity instanceof TadpoleEntity) {
            return stack.isIn(ItemTags.FROG_FOOD) || stack.isOf(Items.WATER_BUCKET);
        }
        if (entity instanceof ParrotEntity) {
            return stack.isIn(ItemTags.PARROT_FOOD) || stack.isIn(ItemTags.PARROT_POISONOUS_FOOD);
        }
        return false;
    }

    private static boolean isVehicleEntityInteractAcceptable(PlayerEntity player, Entity entity, ItemStack stack) {
        if (entity instanceof VehicleInventory) {
            return true;
        }
        if (entity instanceof FurnaceMinecartEntity) {
            return true;
        }
        if (entity instanceof CommandBlockMinecartEntity) {
            return player.isCreativeLevelTwoOp();
        }
        if (entity instanceof MinecartEntity minecart) {
            return !player.shouldCancelInteraction() && !minecart.hasPassengers();
        }
        if (entity instanceof AbstractBoatEntity) {
            return !player.shouldCancelInteraction();
        }
        return false;
    }

    private static boolean isSpecialEntityInteractAcceptable(PlayerEntity player, Entity entity, ItemStack stack) {
        if (entity instanceof ArmorStandEntity armorStand) {
            return !armorStand.isMarker();
        }
        if (entity instanceof ItemFrameEntity itemFrame) {
            if (itemFrame.isRemoved()) {
                return false;
            }
            return !itemFrame.getHeldItemStack().isEmpty() || !stack.isEmpty();
        }
        if (entity instanceof LeashKnotEntity) {
            return true;
        }
        if (entity instanceof AllayEntity allay) {
            if (allay.isDancing() && stack.isIn(ItemTags.DUPLICATES_ALLAYS) && allay.canDuplicate()) {
                return true;
            }
            if (!allay.isHoldingItem() && !stack.isEmpty()) {
                return true;
            }
            return allay.isHoldingItem() && stack.isEmpty();
        }
        if (entity instanceof CamelEntity camel) {
            if (camel.isBaby()) {
                return camel.isBreedingItem(stack);
            }
            return true;
        }
        if (entity instanceof AbstractHorseEntity horse) {
            if (horse.isBaby()) {
                return horse.isBreedingItem(stack);
            }
            return true;
        }
        if (entity instanceof PigEntity pig) {
            if (pig.isSaddled() && !pig.hasPassengers() && !player.shouldCancelInteraction()) {
                return true;
            }
        }
        if (entity instanceof StriderEntity strider) {
            if (strider.isSaddled() && !strider.hasPassengers() && !player.shouldCancelInteraction()) {
                return true;
            }
        }
        if (entity instanceof VillagerEntity villager) {
            return !villager.hasCustomer() && !villager.isSleeping();
        }
        if (entity instanceof WanderingTraderEntity trader) {
            return !trader.hasCustomer() && !trader.isBaby();
        }
        if (entity instanceof WolfEntity wolf) {
            if (wolf.isTamed()) {
                if (wolf.isBreedingItem(stack) && wolf.getHealth() < wolf.getMaxHealth()) {
                    return true;
                }
                if (wolf.isOwner(player)) {
                    if (stack.getItem() instanceof DyeItem dyeItem && dyeItem.getColor() != wolf.getCollarColor()) {
                        return true;
                    }
                    if (stack.isOf(Items.WOLF_ARMOR) && !wolf.isBaby() && !wolf.isWearingBodyArmor()) {
                        return true;
                    }
                    if (wolf.isInSittingPose()
                            && wolf.isWearingBodyArmor()
                            && wolf.getBodyArmor().isDamaged()
                            && wolf.getBodyArmor().canRepairWith(stack)) {
                        return true;
                    }
                    return true;
                }
                return false;
            }
            return stack.isOf(Items.BONE) && !wolf.hasAngerTime();
        }
        if (entity instanceof CatEntity cat) {
            if (!cat.isTamed()) {
                return cat.isBreedingItem(stack);
            }
            if (cat.isOwner(player)) {
                if (stack.getItem() instanceof DyeItem dyeItem && dyeItem.getColor() != cat.getCollarColor()) {
                    return true;
                }
                if (cat.isBreedingItem(stack) && cat.getHealth() < cat.getMaxHealth()) {
                    return true;
                }
                return true;
            }
            return false;
        }
        if (entity instanceof OcelotEntity ocelot) {
            return !ocelot.isTrusting() && ocelot.isBreedingItem(stack);
        }
        if (entity instanceof ParrotEntity parrot) {
            if (!parrot.isTamed()) {
                return stack.isIn(ItemTags.PARROT_FOOD) || stack.isIn(ItemTags.PARROT_POISONOUS_FOOD);
            }
            return parrot.isOwner(player);
        }
        if (entity instanceof PiglinEntity piglin) {
            return piglin.getActivity() != PiglinActivity.ADMIRING_ITEM;
        }
        return false;
    }

    public static boolean isInteractAtAcceptable(
            World world, PlayerEntity player, Entity entity, Vec3d hitPos, ItemStack interactStack) {
        if (world == null || player == null || entity == null || hitPos == null) {
            return false;
        }
        if (player.isSpectator() || !entity.isAlive()) {
            return false;
        }
        ItemStack stack = interactStack == null ? ItemStack.EMPTY : interactStack;
        if (entity instanceof ArmorStandEntity armorStand) {
            if (armorStand.isMarker()) {
                return false;
            }
            if (stack.isOf(Items.NAME_TAG)) {
                return false;
            }
            if (stack.isEmpty()) {
                EquipmentSlot hitSlot = getArmorStandHitSlot(armorStand, hitPos);
                if (canArmorStandUseSlot(armorStand, hitSlot)
                        && !armorStand.getEquippedStack(hitSlot).isEmpty()) {
                    return true;
                }
                EquipmentSlot preferredSlot = EquipmentSlot.MAINHAND;
                if (canArmorStandUseSlot(armorStand, preferredSlot)
                        && !armorStand.getEquippedStack(preferredSlot).isEmpty()) {
                    return true;
                }
                EquipmentSlot offhandSlot = EquipmentSlot.OFFHAND;
                return canArmorStandUseSlot(armorStand, offhandSlot)
                        && !armorStand.getEquippedStack(offhandSlot).isEmpty();
            }
            EquipmentSlot slot = armorStand.getPreferredEquipmentSlot(stack);
            if (!canArmorStandUseSlot(armorStand, slot)) {
                return false;
            }
            if (slot.getType() == EquipmentSlot.Type.HAND && !armorStand.shouldShowArms()) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isInteractAcceptable(World world, PlayerEntity player, ItemStack interactStack) {
        if (world == null || player == null || interactStack == null || interactStack.isEmpty()) {
            return false;
        }
        if (!interactStack.isItemEnabled(world.getEnabledFeatures())) {
            return false;
        }
        if (player.getItemCooldownManager().isCoolingDown(interactStack)) {
            return false;
        }

        ConsumableComponent consumableComponent = interactStack.get(DataComponentTypes.CONSUMABLE);
        if (consumableComponent != null) {
            return consumableComponent.canConsume(player, interactStack);
        }

        EquippableComponent equippableComponent = interactStack.get(DataComponentTypes.EQUIPPABLE);
        if (equippableComponent != null && equippableComponent.swappable()) {
            if (!player.canUseSlot(equippableComponent.slot()) || !equippableComponent.allows(player.getType())) {
                return false;
            }
            ItemStack equippedStack = player.getEquippedStack(equippableComponent.slot());
            return !ItemStack.areItemsAndComponentsEqual(interactStack, equippedStack);
        }

        if (interactStack.getItem() instanceof ShieldItem || VItem.getInstance().isSpear(interactStack)) {
            return true;
        }

        Item item = interactStack.getItem();
        if (item instanceof BowItem) {
            return player.isInCreativeMode()
                    || !player.getProjectileType(interactStack).isEmpty();
        }
        if (item instanceof CrossbowItem) {
            ChargedProjectilesComponent chargedProjectilesComponent =
                    interactStack.get(DataComponentTypes.CHARGED_PROJECTILES);
            return chargedProjectilesComponent != null && !chargedProjectilesComponent.isEmpty()
                    || !player.getProjectileType(interactStack).isEmpty();
        }
        if (item instanceof TridentItem) {
            return true;
        }
        if (item instanceof GoatHornItem) {
            return interactStack.contains(DataComponentTypes.INSTRUMENT);
        }
        if (item instanceof FireworkRocketItem) {
            return player.isFallFlying();
        }
        if (item instanceof OnAStickItem) {
            return player.hasVehicle();
        }

        return item instanceof SpyglassItem
                || item instanceof BundleItem
                || item instanceof FishingRodItem
                || item instanceof BucketItem
                || item instanceof BoatItem
                || item instanceof PlaceableOnWaterItem
                || item instanceof SpawnEggItem
                || item instanceof EmptyMapItem
                || item instanceof GlassBottleItem
                || item instanceof WrittenBookItem
                || item instanceof WritableBookItem
                || item instanceof KnowledgeBookItem
                || item instanceof EnderEyeItem
                || item instanceof ProjectileItem;
    }

    private static EquipmentSlot getArmorStandHitSlot(ArmorStandEntity armorStand, Vec3d hitPos) {
        EquipmentSlot slot = EquipmentSlot.MAINHAND;
        boolean small = armorStand.isSmall();
        double y = hitPos.y / (armorStand.getScale() * armorStand.getScaleFactor());
        if (y >= 0.1
                && y < 0.1 + (small ? 0.8 : 0.45)
                && !armorStand.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
            slot = EquipmentSlot.FEET;
        } else if (y >= 0.9 + (small ? 0.3 : 0.0)
                && y < 0.9 + (small ? 1.0 : 0.7)
                && !armorStand.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            slot = EquipmentSlot.CHEST;
        } else if (y >= 0.4
                && y < 0.4 + (small ? 1.0 : 0.8)
                && !armorStand.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
            slot = EquipmentSlot.LEGS;
        } else if (y >= 1.6 && !armorStand.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            slot = EquipmentSlot.HEAD;
        } else if (armorStand.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()
                && !armorStand.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
            slot = EquipmentSlot.OFFHAND;
        }
        return slot;
    }

    private static boolean canArmorStandUseSlot(ArmorStandEntity armorStand, EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY && armorStand.canUseSlot(slot);
    }

    public static boolean canInteractAndPlace(PlayerEntity player, FlagEntry<BlockHitResult> sneak) {
        return sneak != null && (player.shouldCancelInteraction() || !sneak.flag());
    }

    public static boolean canInteractAndPlace(PlayerEntity player, boolean flag) {
        return player.shouldCancelInteraction() || !flag;
    }

    public static boolean canBeReplaceTo(BlockState fromState, BlockState toState) {
        if (fromState == null || toState == null) {
            return false;
        }
        if (fromState.equals(toState)) {
            return true;
        }

        return !getNextInteractionStep(fromState, toState).isEmpty();
    }

    public static Set<Pair<BlockState, Predicate<ItemStack>>> getNextInteractionStep(
            BlockState fromState, BlockState toState) {
        Set<Pair<BlockState, Predicate<ItemStack>>> result = new HashSet<>();
        if (fromState == null || toState == null) {
            return result;
        }
        Block fromBlock = fromState.getBlock();
        Block targetBlock = toState.getBlock();

        if (fromBlock == targetBlock) {
            if (fromBlock instanceof SlabBlock
                    && fromState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                    && toState.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
                result.add(Pair.of(fromState.with(SlabBlock.TYPE, SlabType.DOUBLE), isItem(fromBlock.asItem())));
            }
            int layers;
            if (fromBlock instanceof SnowBlock
                    && (layers = fromState.get(SnowBlock.LAYERS)) < 8
                    && toState.get(SnowBlock.LAYERS) > layers) {
                result.add(Pair.of(fromState.with(SnowBlock.LAYERS, layers + 1), isItem(fromBlock.asItem())));
            }
            if (fromBlock instanceof CandleBlock
                    && (layers = fromState.get(CandleBlock.CANDLES)) < 4
                    && toState.get(CandleBlock.CANDLES) > layers) {
                result.add(Pair.of(fromState.with(CandleBlock.CANDLES, layers + 1), isItem(fromBlock.asItem())));
            }
            if (fromBlock instanceof SeaPickleBlock
                    && (layers = fromState.get(SeaPickleBlock.PICKLES)) < 4
                    && toState.get(SeaPickleBlock.PICKLES) > layers) {
                result.add(Pair.of(fromState.with(SeaPickleBlock.PICKLES, layers + 1), isItem(fromBlock.asItem())));
            }
            if (fromBlock instanceof FlowerbedBlock
                    && (layers = fromState.get(FlowerbedBlock.FLOWER_AMOUNT)) < 4
                    && toState.get(FlowerbedBlock.FLOWER_AMOUNT) > layers) {
                result.add(
                        Pair.of(fromState.with(FlowerbedBlock.FLOWER_AMOUNT, layers + 1), isItem(fromBlock.asItem())));
            }
            //            if (fromBlock instanceof LeafLitterBlock
            //                    && fromState.get(LeafLitterBlock.SEGMENT_AMOUNT) < 4
            //                    && toState.equals(fromState.with(
            //                            LeafLitterBlock.SEGMENT_AMOUNT, fromState.get(LeafLitterBlock.SEGMENT_AMOUNT)
            // + 1))) {
            //                result.add(Pair.of(toState, isItem(fromBlock.asItem())));
            //            }
            if (fromBlock instanceof RepeaterBlock
                    && !toState.get(RepeaterBlock.DELAY).equals(fromState.get(RepeaterBlock.DELAY))) {
                result.add(Pair.of(fromState.cycle(RepeaterBlock.DELAY), ALWAYS_TRUE));
            }
            if (fromBlock instanceof ComparatorBlock
                    && toState.get(ComparatorBlock.MODE) != fromState.get(ComparatorBlock.MODE)) {
                result.add(Pair.of(fromState.cycle(ComparatorBlock.MODE), ALWAYS_TRUE));
            }
            if (fromBlock instanceof DoorBlock && toState.get(DoorBlock.OPEN) != fromState.get(DoorBlock.OPEN)) {
                result.add(Pair.of(fromState.cycle(DoorBlock.OPEN), ALWAYS_TRUE));
            }
            if (fromBlock instanceof TrapdoorBlock
                    && toState.get(TrapdoorBlock.OPEN) != fromState.get(TrapdoorBlock.OPEN)) {
                result.add(Pair.of(fromState.cycle(TrapdoorBlock.OPEN), ALWAYS_TRUE));
            }
            if (fromBlock instanceof FenceGateBlock
                    && toState.get(FenceGateBlock.OPEN) != fromState.get(FenceGateBlock.OPEN)) {
                result.add(Pair.of(fromState.cycle(FenceGateBlock.OPEN), ALWAYS_TRUE));
            }
            if (fromBlock instanceof LeverBlock
                    && toState.get(LeverBlock.POWERED) != fromState.get(LeverBlock.POWERED)) {
                result.add(Pair.of(fromState.cycle(LeverBlock.POWERED), ALWAYS_TRUE));
            }
            if (fromBlock instanceof ButtonBlock
                    && !fromState.get(ButtonBlock.POWERED)
                    && toState.equals(fromState.with(ButtonBlock.POWERED, true))) {
                result.add(Pair.of(toState, ALWAYS_TRUE));
            }
            if (fromBlock instanceof NoteBlock && toState.get(NoteBlock.NOTE) != fromState.get(NoteBlock.NOTE)) {
                result.add(Pair.of(fromState.cycle(NoteBlock.NOTE), ALWAYS_TRUE));
            }
            if (fromBlock instanceof CandleBlock
                    && fromState.get(CandleBlock.LIT)
                    && toState.equals(fromState.with(CandleBlock.LIT, false))) {
                result.add(Pair.of(toState, ItemStack::isEmpty));
            }
            if (fromBlock instanceof CandleBlock
                    && !fromState.get(CandleBlock.LIT)
                    && !fromState.get(CandleBlock.WATERLOGGED)
                    && toState.equals(fromState.with(CandleBlock.LIT, true))) {
                result.add(Pair.of(toState, isAnyOf(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)));
            }
            if (fromBlock instanceof RespawnAnchorBlock
                    && fromState.get(RespawnAnchorBlock.CHARGES) < 4
                    && toState.get(RespawnAnchorBlock.CHARGES) > fromState.get(RespawnAnchorBlock.CHARGES)) {
                result.add(Pair.of(
                        fromState.with(RespawnAnchorBlock.CHARGES, fromState.get(RespawnAnchorBlock.CHARGES) + 1),
                        isItem(Items.GLOWSTONE)));
            }
            if (fromBlock instanceof CakeBlock
                    && fromState.get(CakeBlock.BITES) < 6
                    && toState.equals(fromState.with(CakeBlock.BITES, fromState.get(CakeBlock.BITES) + 1))) {
                result.add(Pair.of(toState, ALWAYS_TRUE));
            }
            if (fromBlock instanceof FlowerPotBlock fromPot
                    && targetBlock instanceof FlowerPotBlock targetPot
                    && fromPot.getContent() != Blocks.AIR
                    && targetPot.getContent() == Blocks.AIR) {
                result.add(Pair.of(toState, ItemStack::isEmpty));
            }
        }

        if (fromBlock instanceof CakeBlock
                && targetBlock instanceof CandleCakeBlock
                && fromState.get(CakeBlock.BITES) == 0) {
            Item candleItem = getRequiredCandleItem(targetBlock);
            if (candleItem != null) {
                result.add(Pair.of(toState, isItem(candleItem)));
            }
        }
        if (fromBlock instanceof CandleCakeBlock
                && targetBlock instanceof CakeBlock
                && toState.get(CakeBlock.BITES) == 1) {
            result.add(Pair.of(toState, ALWAYS_TRUE));
        }
        if (fromBlock instanceof FlowerPotBlock fromPot
                && targetBlock instanceof FlowerPotBlock targetPot
                && fromPot.getContent() == Blocks.AIR
                && targetPot.getContent() != Blocks.AIR) {
            Block content = targetPot.getContent();
            result.add(Pair.of(
                    toState,
                    stack -> stack != null && stack.getItem() instanceof BlockItem item && item.getBlock() == content));
        }
        if (fromBlock instanceof PumpkinBlock && targetBlock == Blocks.CARVED_PUMPKIN) {
            result.add(Pair.of(toState, isItem(Items.SHEARS)));
        }
        return result;
    }

    private static Predicate<ItemStack> isItem(Item item) {
        return stack -> stack != null && stack.isOf(item);
    }

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

    private static Item getRequiredCandleItem(Block candleCakeBlock) {
        var blockId = Registries.BLOCK.getId(candleCakeBlock);
        String path = blockId.getPath();
        if (!path.endsWith("_cake")) {
            return null;
        }
        Item item = Registries.ITEM.get(blockId.withPath(path.substring(0, path.length() - 5)));
        return item == Items.AIR ? null : item;
    }
}
