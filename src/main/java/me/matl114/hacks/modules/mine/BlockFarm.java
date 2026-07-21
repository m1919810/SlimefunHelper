package me.matl114.hacks.modules.mine;

import me.matl114.accessors.hacks.PlayerInteractionAccess;
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
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class BlockFarm extends BaseModule {
    public BlockFarm() {
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.MINE_CONFIG, "mine-utils.block-farm");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef delay = intBuilder(root.add("delay")).defaultValue(6).build();

    public final IntRef mul = intBuilder(root.add("multiply")).defaultValue(9).build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(root.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final FlagRef enableWhiteList =
            flagBuilder(root.add("white-list-enable")).build();

    public final NBTRef<RegistryRegex<Item>> whiteList = builder(
                    root.add("white-list"), RegistryRegex.<Item>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^(ender_chest|bookshelf)$"), Registries.ITEM))
            .build();

    public final FlagRef render = flagBuilder(root.add("render")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMineBlockAction(), this::onPlayerMineAttackBlock);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInput);
        registerListener(RenderListener.getRender2DEvent(), this::onRender2D);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    BlockItem currentPlacingItem;
    RenderCollector<RenderElements.Text> textRenderer = RenderCollectors.createTextCollector();

    public void onPlayerMineAttackBlock(Event<HitResult> hitResultEvent) {
        if (enable.get()) {
            currentPlacingItem = null;
            if (hitResultEvent.context instanceof BlockHitResult hitResult) {
                BlockPos placePos = hitResult.getBlockPos();
                BlockState currentState;
                if (placePos != null
                        && !(currentState = mc.world.getBlockState(placePos)).isAir()
                        && !currentState.isLiquid()) {
                    Item it = currentState.getBlock().asItem();
                    if (it instanceof BlockItem bl
                            && bl != Items.AIR
                            && (!enableWhiteList.get() || whiteList.get().test(it))) {
                        currentPlacingItem = bl;
                    }
                }
            }

        } else {
            currentPlacingItem = null;
        }
    }

    int timer;

    public void onPreInput(Event<Void> event) {
        if (checkNull()) return;
        textRenderer.clear();
        if (enable.get() && currentPlacingItem != null) {
            var access = PlayerInteractionAccess.of(mc.interactionManager);
            BlockPos pos = access.getCurrentMiningPos();
            BlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() == currentPlacingItem.getBlock()) {

                if (timer++ > delay.get()
                        && new Box(pos).squaredMagnitude(mc.player.getEyePos())
                                < MathUtils.s2(AttributeUtils.getPlayerBlockInteractionRange(mc.player))) {
                    timer = 0;
                    tickMineAndPlace(currentPlacingItem);
                }

                textRenderer.submit(
                        new RenderElements.Text(
                                Text.literal("Farm: %s"
                                        .formatted(Registries.ITEM
                                                .getId(currentPlacingItem)
                                                .getPath())),
                                pos.toCenterPos().add(0, 0.6, 0),
                                0.66F),
                        -1);
            }
        }
    }

    public IndexEntry<ItemStack> supplyItems(BlockItem blockItem) {
        return InventoryUtils.findBestPlayerItem(
                s -> {
                    if (s.getItem() == blockItem) {
                        return -(double) s.getCount();
                    } else {
                        return null;
                    }
                },
                true,
                false);
    }

    public void tickMineAndPlace(BlockItem blockItem) {
        int multiply = (mode.get().canMultiRotPlace() || (DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled()))
                ? mul.get()
                : 1;
        Runnable callback = null;
        var access = PlayerInteractionAccess.of(mc.interactionManager);

        for (var i = 0; i < multiply; ++i) {
            if (access.breakIfComplete()) {
                if (mc.player.getStackInHand(Hand.MAIN_HAND).getItem() != blockItem) {
                    if (callback != null) {
                        callback.run();
                        callback = null;
                    }
                    var entry = supplyItems(blockItem);
                    if (entry != null) {
                        callback = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                    } else {
                        break;
                    }
                }
                BlockPos pos = access.getCurrentMiningPos();
                FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                        pos, !mode.get().isLegal(), !mode.get().isLegal());
                if (hitResult != null && InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                    InteractionTasks.handlePlaceMode(mode.get(), hitResult.val(), Hand.MAIN_HAND);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        if (callback != null) {
            callback.run();
            callback = null;
        }
    }

    public void onRender2D(Event<VDrawContext> vdraw) {
        if (enable.get() && render.get()) {
            vdraw.context.pushMatrix();
            try {
                textRenderer.render2D(vdraw.context);
            } finally {
                vdraw.context.popMatrix();
            }
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
    }
}
