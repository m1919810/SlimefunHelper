package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class PrinterRewrite extends BaseModule {
    public static final String[] PRINTER = makePath("block-rotate.litematica-printer-rewrite.enable");

    public static final String[] HOTKEY = makePath("block-rotate.litematica-printer-rewrite.hotkey");

    public static final String[] PRINTER_MODE = makePath("block-rotate.litematica-printer-rewrite.mode");

    public static final String[] SPEED = makePath("block-rotate.litematica-printer-rewrite.delay");

    public static final String[] LEGAL = makePath("block-rotate.litematica-printer-rewrite.legal-mode");

    public static final String[] MULTIPLY = makePath("block-rotate.litematica-printer-rewrite.multiply");

    public static final String[] RANGE = makePath("block-rotate.litematica-printer-rewrite.range");

    public static final String[] RENDER = makePath("block-rotate.litematica-printer-rewrite.render");

    public PrinterRewrite() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.INTERACT_CONFIG, PRINTER).build();

    public final KeyBindRef hotkey = toggleHotkey(Configs.INTERACT_CONFIG, HOTKEY, new MultiKeyBind(), PRINTER)
            .build();

    public final FlagRef legal = flagBuilder(Configs.INTERACT_CONFIG, LEGAL).build();
    public final EnumRef<Configs.LegalInteractMode> mode = builder(
                    Configs.INTERACT_CONFIG, PRINTER_MODE, Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.DELAY_MOVEMENT)
            .build();

    public final IntRef delay = builder(Configs.INTERACT_CONFIG, SPEED, IntRef.TYPE)
            .defaultValue(5)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = builder(Configs.INTERACT_CONFIG, MULTIPLY, IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public List<Vec3i> blocksSeq = new ArrayList<>();

    public void updateBlocks(int i) {
        blocksSeq = new ArrayList<>();
        List<Vec3i> list = new ArrayList<>();
        for (var y = -i; y <= i; ++y) {
            for (var z = -i; z <= i; ++z) {
                list.add(new Vec3i(y, 0, z));
            }
        }
        list.sort(Comparator.comparingDouble(v -> v.getX() * v.getX() + v.getZ() * v.getZ()));
        for (var x = -i; x <= i; ++x) {
            for (var p : list) {
                blocksSeq.add(new Vec3i(p.getX(), x, p.getZ()));
            }
        }
    }

    public final IntRef interactRangeOverride = builder(Configs.INTERACT_CONFIG, RANGE, IntRef.TYPE)
            .defaultValue(5)
            .validator(Configs.INT_POSITIVE)
            .updateListener(this::updateBlocks)
            .build();

    public final FlagRef render = flagBuilder(Configs.INTERACT_CONFIG, RENDER).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    int countDown;
    final Set<BlockPos> placeFailureBlocks = new HashSet<>();
    final Set<BlockPos> placeSuccessBlocks = new HashSet<>();

    public void onPreInputEvent(Event<Void> event) {
        if (++countDown > delay.get()) {
            countDown = 0;
        } else {
            return;
        }
        placeFailureBlocks.clear();
        placeSuccessBlocks.clear();
        if (enable.get() && LitematicaHooks.getInstance().isEnabled()) {
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();
            BlockPos posStanding = mc.player.getSteppingPos();
            BlockPos posCenter = posStanding.add(0, 1, 0);
            int multiply = legal.get() ? 1 : mul.get();
            int placeCount = 0;
            for (var offset : blocksSeq) {
                BlockPos checkPos = posCenter.add(offset);
                BlockState state = litematicaWorld.getBlockState(checkPos);
                if (!state.isAir() && !state.isLiquid()) {
                    BlockState clientState = mc.world.getBlockState(checkPos);
                    if ((clientState.isAir() || clientState.isLiquid() || clientState.isReplaceable())
                            && clientState != state) {
                        // do place
                        if (doPlace(checkPos, state, !legal.get())) {
                            placeCount += 1;
                            if (placeCount >= multiply) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public int supplyBlocks(Block needBlock) {
        Item needItem = needBlock.asItem();
        if (needItem == Items.AIR) return -1;
        var entry = InventoryUtils.findPlayerItem((item) -> item.getItem() == needItem, true);
        return entry == null ? -1 : entry.index();
    }

    public void putCanNotPlace(BlockPos pos) {
        placeFailureBlocks.add(pos);
    }

    public void putSuccessPlace(BlockPos pos) {
        placeSuccessBlocks.add(pos);
    }

    public boolean doPlace(BlockPos pos, BlockState targetState, boolean useAirPlace) {
        Block needBlock = targetState.getBlock();
        int idx = supplyBlocks(needBlock);
        if (idx == -1) {
            putCanNotPlace(pos);
            return false;
        }
        BlockHitResult result =
                BlockRotate.createHitResultRelatived(mc.player.getFacing(), pos, targetState, useAirPlace, useAirPlace);
        if (result != null) {
            FlagRef enableRotateFix = InteractionTasks.getBlockRotate().enable2;
            FlagRef enableLegalLook = InteractionTasks.getBlockRotate().legal;
            boolean state = enableRotateFix.get();
            boolean state2 = enableLegalLook.get();
            if (!state) {
                enableRotateFix.set(true);
            }
            if (state2) {
                // cancel legal look fix because we here handle the look, do not duplicate
                enableLegalLook.set(false);
            }
            try {
                Runnable callback = InvTasks.getInvExtra().swapInventoryIndexToHand(idx);
                if (callback == null) {
                    putCanNotPlace(pos);
                    return false;
                }
                handlePlace(result);
                // sb grimac
                // callback.run()
                putSuccessPlace(pos);
                return true;
            } finally {
                if (!state) {
                    enableRotateFix.set(false);
                }
                if (state2) {
                    enableLegalLook.set(true);
                }
            }
        } else {
            putCanNotPlace(pos);
            return false;
        }
    }

    public void handlePlace(BlockHitResult result) {
        if (!legal.get()) {
            InteractionTasks.placeBlock(Hand.MAIN_HAND, result);
        } else {
            InteractionTasks.handlePlaceMode(mode.get(), result, Hand.MAIN_HAND);
        }
    }

    public void onRender(Event<MatrixStack> event) {
        MatrixStack stack = event.context();
        if (enable.get() && render.get()) {
            RenderUtils.stopDrawVirtual(stack);
            try {
                VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                    Vec3d camerPos = RenderUtils.getCameraPos();
                    for (var bx : placeFailureBlocks) {
                        operation.drawOutlinedBox(
                                stack,
                                vertexConsumer,
                                Vec3d.of(bx).subtract(camerPos),
                                Vec3d.of(bx).add(1, 1, 1).subtract(camerPos),
                                ColorUtils.withAlphaInt(Color.RED.getRGB(), 1.0F));
                    }

                    for (var bx : placeSuccessBlocks) {
                        operation.drawOutlinedBox(
                                stack,
                                vertexConsumer,
                                Vec3d.of(bx).subtract(camerPos),
                                Vec3d.of(bx).add(1, 1, 1).subtract(camerPos),
                                ColorUtils.withAlphaInt(Color.GREEN.getRGB(), 1.0F));
                    }
                }));
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public void onPresetReload(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case HACKING, VANILLA -> legal.set(false);
            default -> legal.set(true);
        }
    }
}
