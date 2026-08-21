package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ACTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AutoShulker extends BaseModule {
    public final ModulePath autoInv = makePath(Configs.INV_CONFIG, "auto-inv");
    public final ModulePath autoShulkerPath = autoInv.add("auto-shulker");

    public AutoShulker() {
        super("AutoShulker");
        bindFlag(autoShulker);
    }

    // 自动潜影盒子功能开关
    public final FlagRef autoShulker =
            flagBuilder(autoShulkerPath.add("enable")).build();

    // 自动潜影盒切换快捷键
    public final KeyBindRef autoShulkerToggleKey = moduleEntry(
                    autoShulkerPath.add("toggle-key"),
                    new MultiKeyBind(), // 默认按键 H
                    autoShulkerPath.add("enable") // 关联自动潜影盒开关
                    )
            .build();
    // todo: add steal

    // 0 Tick 偷取开关（潜影盒专用）
    public final FlagRef autoShulker0TickSteal =
            flagBuilder(autoShulkerPath.add("0tick-steal")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onClickShulkerBoxOrPlaceShulkerBox);
    }

    public void onClickShulkerBoxOrPlaceShulkerBox(Event<PlayerInteractBlockC2SPacket> event) {
        if (event.isCancelled()) return;
        if (autoShulker.get()) {
            PlayerInteractBlockC2SPacket packet = event.context;
            BlockHitResult hitResult = packet.getBlockHitResult();
            boolean hasShift = mc.player.isSneaking();
            BlockState state = mc.world.getBlockState(hitResult.getBlockPos());
            if (state.getBlock() instanceof ShulkerBoxBlock
                    && !hasShift
                    && mc.world.getBlockEntity(hitResult.getBlockPos()) instanceof ShulkerBoxBlockEntity bl) {
                int size = InvTasks.predictOpenVanillaContainerSize(hitResult.getBlockPos());
                // can open
                if (size > 0) {
                    // interact shulker
                    if (autoShulker0TickSteal.get()) {
                        InvTasks.executePredictInventoryAction(bl, handler -> {
                            for (var i = 0; i < size; ++i) {
                                mc.interactionManager.clickSlot(
                                        handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            }
                        });
                        int tick = Tasks.getTick();
                        // add timeout
                        ScreenUtils.getOpenScreenFuture()
                                .thenRunAsync(
                                        () -> {
                                            if (tick + 4 > Tasks.getTick()) {
                                                mc.player.closeHandledScreen();
                                            }
                                        },
                                        mc);
                    } else {
                        int tick = Tasks.getTick();
                        ScreenUtils.getOpenScreenFuture().thenRun(() -> {
                            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                                if (tick + 4 <= Tasks.getTick()) {
                                    return;
                                }
                                for (var i = 0; i < size; ++i) {
                                    mc.interactionManager.clickSlot(
                                            mc.player.currentScreenHandler.syncId,
                                            i,
                                            0,
                                            SlotActionType.QUICK_MOVE,
                                            mc.player);
                                }
                            }
                            mc.player.closeHandledScreen();
                        });
                    }
                }
            } else {
                if (hasShift) {
                    mc.player.setSneaking(false);
                    PlayerInputUtils.of(mc.player).sneak(false).sendPlayerSneakUpdatePacket();
                    ClientPlayerAccess.of(mc.player).resyncSneak();
                }
                BlockPos placedBlock = hitResult.getBlockPos().offset(hitResult.getSide());
                BlockState placedState = mc.world.getBlockState(placedBlock);

                if (placedState.getBlock() instanceof ShulkerBoxBlock) {
                    BlockHitResult hitResult1 = RaycastUtils.createRealHitResult(placedBlock);
                    // mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult1);
                    ACTasks.addPostTransactionAction((ch) -> {
                        InteractionTasks.interactBlock(Hand.MAIN_HAND, hitResult1, true);
                    });
                }
            }
        }
    }
}
