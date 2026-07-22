package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.SpawnHelper;

public class AutoSlab extends BaseModule {
    public AutoSlab() {
        bindFlag(enable);
    }

    public final ModulePath autoPlate = makePath(Configs.INTERACT_CONFIG, "place-utils.auto-slab");

    public final FlagRef enable = flagBuilder(autoPlate.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(autoPlate.addHotkey(), new MultiKeyBind(), autoPlate.addEnable())
            .build();

    public List<Vec3i> blocksSeq = new ArrayList<>();
    public final EnumRef<Configs.LegalInteractMode> mode = builder(
                    autoPlate.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final FlagRef airplace = flagBuilder(autoPlate.add("air-place")).build();

    public final IntRef delay =
            intBuilder(autoPlate.add("delay")).defaultValue(5).build();

    public final IntRef mul =
            intBuilder(autoPlate.add("multiply")).defaultValue(1).build();

    public final DoubleRef range = doubleBuilder(autoPlate.add("interact-range"))
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .updateListener(s -> blocksSeq = MathUtils.create3DPointListInRange(s))
            .build();

    public final FlagRef slabOnly = flagBuilder(autoPlate.add("slab-only")).build();

    public final FlagRef useBlockRotate =
            flagBuilder(autoPlate.add("use-block-rotate")).build();

    public final FlagRef returnBlock =
            flagBuilder(autoPlate.add("ghost-hand-swap-back")).build();

    public final FlagRef render = flagBuilder(autoPlate.add("render")).build();

    public final NBTRef<WrapColor> color = builder(autoPlate.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Color.GREEN)))
            .build();

    public final Set<Item> availableSlabs = new HashSet<>();
    public final Set<Block> canSpawnOnBlocks = new HashSet<>();
    public final Set<Block> availableBlocks = new HashSet<>();
    public final RenderCollector<Box> boxCollector = RenderCollectors.createBoxCollector(true, false, false);

    public void onServerLeave(Event<Void> eventLeave) {
        availableBlocks.clear();
    }

    {
        for (var re : Registries.BLOCK) {
            try {
                if (re.getDefaultState().allowsSpawning(null, null, EntityType.CREEPER)) {
                    canSpawnOnBlocks.add(re);
                }
            } catch (Throwable e) {
            }
            if (re instanceof SlabBlock slab) {
                availableSlabs.add(re.asItem());
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public void initializeMap() {
        if (availableBlocks.isEmpty()) {
            for (var re : Registries.BLOCK) {
                try {
                    if (!canSpawnOnBlocks.contains(re)) {
                        BlockState state = re.getDefaultState();
                        try {
                            if (!MoonriseBlockStateBaseAccess.of(state).isConstantCollisionShapeEmpty()) {
                                availableBlocks.add(re);
                            }
                        } catch (Throwable e) {
                        }
                        if (!SpawnHelper.isClearForSpawn(
                                null, null, state, state.getFluidState(), EntityType.CREEPER)) {
                            availableBlocks.add(re);
                        }
                    }
                } catch (Throwable e) {
                }
            }
        }
    }

    public boolean isAvailable(Item item) {
        if (slabOnly.get()) {
            return availableSlabs.contains(item);
        } else {
            if (item instanceof BlockItem bl) {
                initializeMap();
                return availableBlocks.contains(bl.getBlock());
            } else {
                return false;
            }
        }
    }

    int timer = 0;
    List<BlockPos> fillBlockPoses = new ArrayList<>();

    public void refreshBlocks() {
        fillBlockPoses.clear();
        BlockPos pos = mc.player.getBlockPos();
        for (var re : blocksSeq) {
            BlockPos testPos = pos.add(re);
            BlockState testState = mc.world.getBlockState(testPos);
            if (testState.isAir() || testState.isLiquid() || testState.isReplaceable()) {
                if (WorldUtils.canEntitySpawnAt(mc.world, testPos, EntityType.CREEPER)) {
                    boxCollector.submit(new Box(testPos), color.get().withAlpha(255));
                    fillBlockPoses.add(testPos);
                }
            }
        }
    }

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) return;
        boxCollector.clear();
        if (enable.get()) {
            refreshBlocks();
            if (++timer >= delay.get()) {
                timer = 0;
                tickPlace();
            }
        }
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (checkNull()) return;
        if (enable.get() && render.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                boxCollector.render3D(event.context);
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public void tickPlace() {
        int cnt = 0;
        int multiply = (mode.get().canMultiRotPlace() || (DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled()))
                ? mul.get()
                : 1;
        List<Runnable> stack = new ArrayList<>(multiply);
        for (var testPos : fillBlockPoses) {
            var entry = supplyItem();
            if (entry != null && entry.val().getItem() instanceof BlockItem bl) {
                BlockState targetState = bl.getBlock().getDefaultState();
                FlagEntry<BlockHitResult> hitResult = InteractionTasks.createSpecificStateHitResult(
                        testPos, targetState, airplace.get(), !mode.get().isLegal());
                if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                        && InteractExtra.INSTANCE.isWithinInteractRange(
                                mc.player.getPos(), hitResult.val().getBlockPos(), range.get())
                        && InteractUtils.getBlockPlacement(bl, mc.player, mc.world, hitResult.val()) != null) {
                    Runnable runnable = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                    if (runnable == null) break;
                    stack.add(runnable);
                    if (useBlockRotate.get()) {
                        BlockRotate.INSTANCE.addTempStateSchematic(testPos, targetState);
                    }
                    InteractionTasks.handlePlaceMode(mode.get(), hitResult.val(), Hand.MAIN_HAND);
                    cnt += 1;
                    if (cnt >= multiply) {
                        break;
                    }
                }
            }
        }
        if (returnBlock.get()) {
            int size = stack.size();
            for (var i = size - 1; i >= 0; i--) {
                stack.get(i).run();
            }
        }
    }

    public IndexEntry<ItemStack> supplyItem() {
        return InventoryUtils.findPlayerItem(s -> isAvailable(s.getItem()), true, false);
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
        airplace.set(!event.context.getValue().hasAC());
    }
}
