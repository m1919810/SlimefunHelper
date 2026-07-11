package me.matl114.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.recipe.RecipePropertySet;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.attribute.EnvironmentAttributes;

public class InteractUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

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

    public static boolean canCubePlace(PlayerEntity player, BlockPos pos) {
        // cube
        World world = player.getEntityWorld();
        BlockState state = Blocks.STONE.getDefaultState();
        return state.canPlaceAt(world, pos) && world.canPlace(state, pos, ShapeContext.ofPlacement(player));
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
                || stack.contains(DataComponentTypes.BLOCKS_ATTACKS)
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
        if (world.getDimension().attributes().containsKey(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS_GAMEPLAY)
                && world.getDimension()
                                .attributes()
                                .getEntry(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS_GAMEPLAY)
                                .argument()
                        instanceof Boolean bl
                && bl) {
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
        List<Leashable> leashables = Leashable.collectLeashablesAround(
                world, Vec3d.ofCenter(pos), entity -> entity.getLeashHolder() == player);
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
            return !world.getBlockState(pos.up()).isSolidBlock(world, pos.up());
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

    public static boolean canInteract(PlayerEntity player, FlagEntry<BlockHitResult> sneak) {
        return player.shouldCancelInteraction() || !sneak.flag();
    }
}
