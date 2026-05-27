package me.matl114.hacks.modules.mine;

import java.util.Objects;
import java.util.OptionalInt;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import me.matl114.utils.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PacketMine extends BaseModule {
    public PacketMine() {
        bindFlag(autoEnable);
    }

    public ModulePath packetMine = makePath(Configs.MINE_CONFIG, "mine-oneblock");

    public final FlagRef autoEnable = flagBuilder(packetMine.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final IntRef multiplePackets = intBuilder(packetMine.add("multiple-packets"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef considerAirState =
            flagBuilder(packetMine.add("consider-air-state")).build();

    public final DoubleRef mineThreshold = builder(packetMine.add("mine-threshold"), DoubleRef.TYPE)
            .defaultValue(0.7)
            .validator(Configs.doubleRange(-0.0001F, 1.0001F))
            .build();

    public final FlagRef autoTool = flagBuilder(packetMine.add("auto-pickaxe")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    public void cancelPacketMine(BlockPos pos) {
        BlockPos po = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
        if (Objects.equals(po, pos)) {
            PlayerInteractionAccess.of(mc.interactionManager).resetCurrentMiningPos();
        }
    }

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (isActive()) {
            if (mc.interactionManager != null && mc.player != null) {
                BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                // todo: add predicted speed
                if (pos != null) {
                    double lenSq = new Box(pos).squaredMagnitude(mc.player.getEyePos());
                    if (lenSq <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {

                        BlockState blockState = mc.world.getBlockState(pos);
                        OptionalInt currentItemSlot = getCurrentUsableTool(blockState);
                        Slot currentToolSlot = currentItemSlot.isPresent()
                                ? mc.player.currentScreenHandler.getSlot(currentItemSlot.getAsInt())
                                : null;
                        ItemStack currentTool = currentToolSlot == null
                                ? mc.player.getStackInHand(Hand.MAIN_HAND)
                                : currentToolSlot.getStack();
                        if (canMine(blockState, currentTool)) {
                            int selectedSlot = mc.player.getInventory().getSelectedSlot();
                            Runnable callback = null;
                            if (currentItemSlot.isPresent() && currentToolSlot != null) {
                                callback = InvExtra.INSTANCE.swapInventoryIndexToHand(currentToolSlot.getIndex());
                            }
                            Vec3d shouldFacing = pos.toCenterPos().subtract(mc.player.getEyePos());
                            Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                            if (considerAirState.get()
                                    && PlayerInteractionAccess.of(mc.interactionManager)
                                                    .getCurrentMiningProgress(true)
                                            > 0.98F) {
                                mc.interactionManager.breakBlock(pos);
                                // mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                            }
                            for (int i = 0; i < multiplePackets.get(); ++i) {
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                                PlayerInteractionAccess.of(mc.interactionManager)
                                        .sendStopBreakPacket(pos, dir);
                            }
                            if (callback != null) {
                                callback.run();
                            }
                        }
                    }
                }
            }
        }
    }

    public OptionalInt getCurrentUsableTool(BlockState currentState) {
        PlayerInventory inv = mc.player.getInventory();
        if (autoTool.get()) {
            ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
            double bestMiningSpeed =
                    WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, currentState, stack);
            int bestMiningIndex = inv.getSelectedSlot();
            for (var i = 0; i < inv.size(); ++i) {
                ItemStack stackInventory = inv.getStack(i);
                if (!stackInventory.isEmpty()) {
                    double mul = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                            mc.player, currentState, stackInventory);
                    if (mul > bestMiningSpeed) {
                        bestMiningIndex = i;
                        bestMiningSpeed = mul;
                    }
                }
            }
            if (bestMiningIndex != -1) {
                return mc.player.currentScreenHandler.getSlotIndex(inv, bestMiningIndex);
            }
        }
        return mc.player.currentScreenHandler.getSlotIndex(inv, inv.getSelectedSlot());
    }

    public boolean canMine(BlockState state, ItemStack tool) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (state.getBlock().getHardness() >= 0.0F && !state.isLiquid() && !state.isAir()) {
            if (mineThreshold.get() > 0) {
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                return access.predictCurrentMiningProgressWithTool(tool) > Math.min(0.98, mineThreshold.get());
            }
            return true;
        } else {
            return false;
        }
    }
}
