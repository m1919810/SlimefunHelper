package me.matl114.hacks.modules.mine;

import com.google.common.util.concurrent.Runnables;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.channels.EventChannel;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PacketMine extends BaseModule {
    public static PacketMine INSTANCE;

    public PacketMine() {
        bindFlag(autoEnable);
        INSTANCE = this;
    }

    public ModulePath packetMine = makePath(Configs.MINE_CONFIG, "mine-oneblock");

    public final FlagRef autoEnable = flagBuilder(packetMine.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final IntRef multiplePackets = intBuilder(packetMine.add("multiple-packets"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef realBreak =
            flagBuilder(packetMine.add("simulate-real-break")).build();

    public final FlagRef airBreak =
            flagBuilder(packetMine.add("consider-air-break")).build();

    public final FlagRef swingHand = flagBuilder(packetMine.add("swing-hand")).build();

    public final DoubleRef mineThreshold = builder(packetMine.add("mine-threshold"), DoubleRef.TYPE)
            .defaultValue(0.7)
            .validator(Configs.doubleRange(-0.0001F, 1.0001F))
            .build();

    public final FlagRef autoTool = flagBuilder(packetMine.add("auto-pickaxe")).build();

    public final FlagRef autoToolDoubleBreak =
            flagBuilder(packetMine.add("auto-pickaxe-double-break")).build();

    public final FlagRef groundDeceive =
            flagBuilder(packetMine.add("ground-deceive")).build();

    public final FlagRef groundOnlyWhenNoControl =
            flagBuilder(packetMine.add("ground-only-when-no-control")).build();

    public final FlagRef mineOnce =
            flagBuilder(packetMine.add("only-mine-once-per-click")).build();

    public final FlagRef whiteList =
            flagBuilder(packetMine.add("enable-mine-white-list")).build();

    public final NBTRef<RegistryRegex<Block>> whiteListRegex = builder(
                    packetMine.add("mine-white-list"), RegistryRegex.<Block>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^()$"), Registries.BLOCK))
            .build();

    BlockPos lastMinePos;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getMineBlockAction(), this::onMineBlockAction);
    }

    public boolean hasMiningTarget() {
        return getCurrentMiningPos() != null;
    }

    public boolean isMining() {
        return hasMiningTarget();
    }

    public boolean isMining(BlockPos pos) {
        return Objects.equals(getCurrentMiningPos(), pos);
    }

    public BlockPos getCurrentMiningPos() {
        if (mc.interactionManager == null) {
            return null;
        }
        return PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
    }

    public void cancelPacketMine(BlockPos pos) {
        BlockPos po = getCurrentMiningPos();
        if (Objects.equals(po, pos)) {
            PlayerInteractionAccess.of(mc.interactionManager).resetCurrentMiningPos();
        }
    }

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (switchCallback != null) {
            switchCallback.run();
            switchCallback = null;
        }
        if (isActive()) {
            if (checkNull()) return;
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (mineOnce.get() && Objects.equals(pos, lastMinePos)) {
                return;
            }
            tickMine();
        }
    }

    public Runnable switchCallback = null;

    public void tickMine() {
        if (mc.interactionManager != null && mc.player != null) {
            if (switchCallback != null) {
                switchCallback.run();
                switchCallback = null;
            }
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (pos == null) return;

            Runnable currentTickCallback = null;
            boolean postMineCallback = false;
            float progress = 0;
            // todo: add predicted speed
            double lenSq = new Box(pos).squaredMagnitude(mc.player.getEyePos());
            if (lenSq <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {
                BlockState blockState = mc.world.getBlockState(pos);
                IndexEntry<ItemStack> currentItemSlot = getCurrentUsableTool(blockState);

                ItemStack currentTool = currentItemSlot.val();
                if (canMine(blockState, currentTool)) {
                    Event<Pre> eventPre = new Event<>(Pre.INSTANCE, true, false, pos);
                    prePacketMine.handleValue(eventPre);
                    if (!eventPre.isCancelled()) {
                        if (groundDeceive.get() && !mc.player.isOnGround()) {
                            boolean shouldExecute = true;
                            if (groundOnlyWhenNoControl.get()
                                    && !PlayerInputUtils.of(mc.options).hasMovementControl()) {
                                shouldExecute = false;
                            }
                            if (shouldExecute) {

                                mc.getNetworkHandler()
                                        .sendPacket(LegacySnapRotManager.INSTANCE.createSnapAt(
                                                PlayerStateManager.INSTANCE.lastPitch,
                                                PlayerStateManager.INSTANCE.lastYaw,
                                                true));
                                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                                FloatingUtils.INSTANCE.setForceOnGroundVia(true);
                            }
                        }
                        Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(currentItemSlot.index());

                        Vec3d shouldFacing = pos.toCenterPos().subtract(mc.player.getEyePos());
                        Direction dir = Direction.getFacing(shouldFacing).getOpposite();
                        progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .getCurrentMiningProgress(currentTool);

                        if (realBreak.get() && progress > 0.98F) {
                            mc.interactionManager.breakBlock(pos);
                        }
                        for (int i = 0; i < multiplePackets.get(); ++i) {
                            if (swingHand.get())
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                            PlayerInteractionAccess.of(mc.interactionManager).sendBreakPacket(pos, dir);
                        }
                        currentTickCallback = callback;
                        postMineCallback = true;
                    }
                }
            }
            if (autoToolDoubleBreak.get()
                    && PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos() != null) {
                tickGhostHandDoubleBreak(currentTickCallback, groundDeceive.get());
            } else {
                if (currentTickCallback != null) {
                    currentTickCallback.run();
                }
            }
            if (postMineCallback) {
                postPacketMine.broadcast(Post.INSTANCE, pos, progress);
            } else if (WorldUtils.isChunkLoaded(pos)) {
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || state.isLiquid()) {
                    lastMinePos = pos;
                }
            }
        }
    }

    public void tickGhostHandDoubleBreak(Runnable currentTickCallback, boolean groundDeceive) {
        if (switchCallback != null) {
            return;
        }
        BlockPos failPos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
        if (failPos == null) {
            if (currentTickCallback != null) {
                currentTickCallback.run();
            }
            return;
        }
        BlockState blockState = mc.world.getBlockState(failPos);
        IndexEntry<ItemStack> currentItemSlot = getCurrentUsableTool(blockState);
        ItemStack currentTool = currentItemSlot.val();
        if (canMineFailBreak(blockState, currentTool, groundDeceive)) {
            Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(currentItemSlot.index());
            Runnable currentCallback = currentTickCallback == null ? Runnables.doNothing() : currentTickCallback;
            switchCallback = () -> {
                callback.run();
                currentCallback.run();
            };
        } else {
            if (currentTickCallback != null) {
                currentTickCallback.run();
            }
        }
    }

    public void onMineBlockAction(Event<HitResult> event) {
        if (event.context != null && event.context.getType() == HitResult.Type.BLOCK) {
            lastMinePos = null;
        }
    }

    @Nonnull
    public IndexEntry<ItemStack> getCurrentUsableTool(BlockState currentState) {
        if (autoTool.get()) {
            BlockState calS;
            if (currentState.isAir() || currentState.isLiquid()) {
                calS = Blocks.OBSIDIAN.getDefaultState();
            } else {
                calS = currentState;
            }
            var re = InventoryUtils.findBestPlayerItem(
                    item -> {
                        return (double)
                                WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, calS, item);
                    },
                    true,
                    true);
            if (re != null) {
                return re;
            }
        }
        return InventoryUtils.getSelectedItem();
    }

    public boolean isMineable(BlockState state) {
        return state.getBlock().getHardness() >= 0.0F
                && !state.isLiquid()
                && (airBreak.get() || !state.isAir())
                && (!whiteList.get() || !whiteListRegex.get().test(state.getBlock()));
    }

    public boolean canMine(BlockState state, ItemStack tool) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (isMineable(state)) {
            if (mineThreshold.get() > 0) {
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                float speed = access.predictCurrentMiningProgressWithTool(tool);
                if (groundDeceive.get() && !mc.player.isOnGround()) {
                    speed *= 5;
                }
                return speed > Math.min(0.98, mineThreshold.get());
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean canMineFailBreak(BlockState state, ItemStack tool, boolean groundDeceive) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (isMineable(state)) {
            var access = PlayerInteractionAccess.of(mc.interactionManager);
            var speed = access.predictFailMiningProgressWithTool(tool, 0);
            if (groundDeceive && !mc.player.isOnGround()) {
                speed *= 5;
            }
            return speed > 0.99;
        } else {
            return false;
        }
    }

    @Getter
    @Cancelable
    @ExtraArgs({BlockPos.class})
    public static final EventChannel<Pre> prePacketMine = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs({BlockPos.class, float.class})
    public static final EventChannel<Post> postPacketMine = new EventChannel<>();

    public static class Pre {
        public static final Pre INSTANCE = new Pre();

        private Pre() {}
    }

    public static class Post {
        public static final Post INSTANCE = new Post();

        private Post() {}
    }
}
