package me.matl114.hacks.modules.survival;

import java.util.function.Consumer;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
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
        super("BlockFarm");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-mine-utils.block-farm");

    {
        portConfigs(makePath(Configs.MINE_CONFIG, "mine-utils.block-farm"), root);
    }

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef delay = intBuilder(root.add("delay")).defaultValue(6).build();

    public final IntRef mul = intBuilder(root.add("multiply")).defaultValue(9).build();

    public final FlagRef enableWhiteList =
            flagBuilder(root.add("white-list-enable")).build();

    public final NBTRef<RegistryRegex<Item>> whiteList = builder(
                    root.add("white-list"), RegistryRegex.<Item>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^(ender_chest|bookshelf)$"), Registries.ITEM))
            .build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    public final FlagRef render = flagBuilder(root.add("render")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMineBlockAction(), this::onPlayerMineAttackBlock);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInput);
        registerListener(RenderListener.getRender2DEvent(), this::onRender2D);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.interact.interact-block.use-argument", 0, dblank, dx, dy));
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
        int multiply = mul.get();
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
                if (!Interact.INSTANCE.placeBlock(pos)) {
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
}
