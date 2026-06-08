package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Objects;
import javax.annotation.Nullable;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.managers.Tasks;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.WorldUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class PlayerInteractionMixin implements PlayerInteractionAccess {
    @Shadow
    private float currentBreakingProgress;

    @Shadow
    private boolean breakingBlock;

    @Shadow
    private ItemStack selectedStack;

    @Shadow
    protected abstract void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Shadow
    public abstract boolean breakBlock(BlockPos pos);

    @Shadow
    private int blockBreakingCooldown;

    @Shadow
    private float blockBreakingSoundCooldown;

    @Shadow
    private BlockPos currentBreakingPos;

    @Nullable
    @Unique
    private BlockPos currentFailBreakPos = null;

    @Unique
    private int failBreakStartTick;

    @Override
    public BlockPos getCurrentMiningPos() {
        return currentBreakingPos;
    }

    @Override
    public void resetCurrentMiningPos() {
        currentBreakingPos = new BlockPos(-1, -1, -1);
        currentBreakingProgress = 0.0F;
    }

    @Override
    @Nullable
    public BlockPos getCurrentFailBreakPos() {
        return MineExtra.INSTANCE.doubleBreak.get() ? currentFailBreakPos : null;
    }

    @Override
    public boolean isFailBreakEmpty() {
        return currentFailBreakPos == null;
    }

    @Override
    public float predictCurrentMiningProgressWithTool(ItemStack tool) {
        BlockState block = this.client.world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float miningSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(this.client.player, block, tool);
        float speed = WorldUtils.calcBlockBreakingDelta(block, this.client.world, currentBreakingPos, miningSpeed);
        int ticksSinceLastStart = Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
        // loading progress...
        return speed * ticksSinceLastStart;
    }

    @Override
    public float getCurrentMiningProgress(boolean shouldPredict) {
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        if ((breakingBlock && isCurrentlyBreaking(currentBreakingPos))) {
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }
        // when oneBlock mode, the value is the predicted value
        // when not in oneBlock mode, we should consider the predict flag
        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && !shouldPredict) {
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }

        // ack predict
        float speed = block.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getEntityWorld(),
                currentBreakingPos);
        //                else if(HotKeys.getHotkeyToggleManager().getState(HotKeys.QUICK_MINE) && speed >
        // breakThreshold.get()){
        //                    //no need to restore currentBreakingProgress?
        //                    return;
        //                }
        int ticksSinceLastStart = Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
        // loading progress...
        return speed * ticksSinceLastStart;
    }

    @Override
    public float getFailBreakMiningProgress() {
        if (currentFailBreakPos == null) {
            return -1.0F;
        }
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
        if (block.isAir()) {
            return -1.0F;
        }
        float speed = block.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getEntityWorld(),
                currentBreakingPos);
        return (Tasks.getTick() - failBreakStartTick) * speed;
    }

    @Unique
    @Override
    public boolean beginFailBreak(BlockPos pos) {
        if (currentFailBreakPos == null) {
            currentFailBreakPos = pos;
            currentBreakingPos = pos;
            failBreakStartTick = MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
            return true;
        }
        return false;
    }

    @Unique
    @Override
    public boolean moveCurrentMiningToFailBreak() {
        return beginFailBreak(currentBreakingPos);
    }

    @Unique
    @Override
    public void clearFailBreak() {
        currentFailBreakPos = null;
    }

    @Shadow
    private GameMode gameMode;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    @Override
    @Unique
    public void sendBreakPacket(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    @Override
    @Unique
    public void startMiningBlock(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            // every start break change the server side start break time
            currentBreakingProgress = 0.0F;
            BlockState state = client.world.getBlockState(pos);
            if (!state.isAir()
                    && state.calcBlockBreakingDelta(this.client.player, this.client.player.getEntityWorld(), pos)
                            >= 1.0F) {
                // insta break do not change current breaking pos
            } else {
                currentBreakingPos = pos;
            }
            return new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    @Override
    @Unique
    public void syncSelectedHotbar(int x) {
        client.player.getInventory().setSelectedSlot(x);
        this.syncSelectedSlot();
    }
    //    public void autoSendStopPacket(){
    //        if(currentBreakingPos != null){
    //            sendStopBreakPacket(currentBreakingPos, Direction.UP);
    //        }
    //    }
    public float calculateBreakingSpeed(BlockPos blockPos) {
        if (this.gameMode.isCreative()) {
            return 100000.0f;
        } else {
            BlockState state = this.client.world.getBlockState(blockPos);
            return state.calcBlockBreakingDelta(this.client.player, this.client.player.getEntityWorld(), blockPos);
        }
    }

    public boolean preCalculateInstantBreak(BlockPos blockPos) {
        if (this.gameMode.isCreative()) {
            return true;
        } else {
            BlockState state = this.client.world.getBlockState(blockPos);
            float speed =
                    state.calcBlockBreakingDelta(this.client.player, this.client.player.getEntityWorld(), blockPos);
            MineExtra mineExtra = MineExtra.INSTANCE;
            if (speed >= 1.0f
                    || (speed > mineExtra.breakThreshold.get() && mineExtra.quickMine.get())
                    || (mineExtra.fakeInstaBreak.get() && speed > ((mineExtra.breakThreshold.get() / 2.0) + 0.04d))) {
                return true;
            } else return false;
        }
    }

    // speed up with early packet when progress>0.7
    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/tutorial/TutorialManager;onBlockBreaking(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;F)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void fastbreak(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        MineExtra mineExtra = MineExtra.INSTANCE;
        if (mineExtra.quickMine.get()) {
            // Debug.info(breakThreshold.get(),this.currentBreakingProgress);
            // do insta break
            if (this.currentBreakingProgress >= mineExtra.breakThreshold.get()) {
                // Debug.info("here");
                if (mineExtra.ignoreNextFastBreakStatus > 0) {
                    return;
                }
                this.breakingBlock = false;
                this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                    this.breakBlock(pos);
                    return new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                float speed = blockState.calcBlockBreakingDelta(
                        MinecraftClient.getInstance().player, MinecraftClient.getInstance().world, pos);
                mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
                if (!mineExtra.optimizeOneBlock.get()) {
                    this.currentBreakingProgress = 0.0F;
                }
                this.blockBreakingSoundCooldown = 0.0F;
                this.blockBreakingCooldown = mineExtra.cooldownManaging();
                cir.cancel();
            }
        }
    }

    //
    // fixme: fix
    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true)
    public void samePositionOptimize(
            BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir, BlockState blockState) {
        // remove the flag, can work even if fastbreak off
        MineExtra mineExtra = MineExtra.INSTANCE;
        if (mineExtra.optimizeOneBlock.get()) {

            if (Objects.equals(pos, currentBreakingPos)) {

                if (!mineExtra.shouldExecuteOptimizeOneBlock()) {
                    return;
                }
                // loading progress...
                this.currentBreakingPos = pos;
                this.currentBreakingProgress = getCurrentMiningProgress(true);
                this.breakingBlock = true;
                this.selectedStack = this.client.player.getMainHandStack();
                this.client.world.setBlockBreakingInfo(
                        this.client.player.getId(), this.currentBreakingPos, this.getBlockBreakingProgress());
                // avoid targeting another block too quickly
                // update blockbreaking progress, do anything you want, sendpackets or sth
                this.updateBlockBreakingProgress(pos, direction);
                cir.setReturnValue(true);
            } else {
                // 不同的时候
                // 尝试把当前的挖掘进度转为doubleMine或者直接终止 double Mine需要考虑
                // 是否需要考虑
                if (mineExtra.doubleBreak.get()) {

                    // FIXed: double break collapse with optimizeOneBlockBreak
                    // : if predicted progress should be finished, do not make doublebreak
                    float predictedProgress = getCurrentMiningProgress(true);
                    if (predictedProgress <= 1.0F) {
                        if (onDoubleBreakAbort()) {
                            // fixme： can not pass MultiBreak check..., may add a TickPacket or something
                            this.sendSequencedPacket(
                                    MinecraftClient.getInstance().world,
                                    (seq) -> new PlayerActionC2SPacket(
                                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                                            currentBreakingPos,
                                            direction,
                                            seq));
                            // fake packet to cheat MultiBreak module
                            // magic, doesn't always work
                            // shit, it crash with AirLiquidBreak.
                            // shit, player has to choose in hand
                            if (mineExtra.fastBreakBypassMode.get() == MineExtra.Mode.BYPASS_GRIM_BAD_PACKETS
                                    && mineExtra.grimBadPacketFix1.get()) {
                                this.sendSequencedPacket(
                                        MinecraftClient.getInstance().world,
                                        (seq) -> new PlayerActionC2SPacket(
                                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, seq));
                            }
                        }
                    }
                }
            }
        }
    }

    @Redirect(
            method = "cancelBlockBreaking",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F"))
    private void sameBlockOptimizeDoNotResetProgress(ClientPlayerInteractionManager instance, float value) {
        // do not set the fucking value
        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            ((PlayerInteractionMixin) (Object) instance).currentBreakingProgress = value;
        }
    }

    @Redirect(
            method = "cancelBlockBreaking",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak(ClientPlayNetworkHandler instance, Packet packet) {

        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && onDoubleBreakAbort()) {
            // we make optimizeOneBlockMine delay its destroy packet to changing the currentPosition in method
            // sameBlockOptimize
            Vec3d shouldFacing = currentBreakingPos
                    .toCenterPos()
                    .subtract(MinecraftClient.getInstance().player.getEyePos());
            Direction dir = Direction.getFacing(shouldFacing).getOpposite();
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq) -> {
                return new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, dir, seq);
            });
            return;
        }

        instance.sendPacket(packet);
    }

    @Redirect(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak2(
            ClientPlayNetworkHandler instance, Packet packet, @Local(argsOnly = true) Direction direction) {
        // conflict with optimizeOneBlock

        if (!MineExtra.INSTANCE.optimizeOneBlock.get() && onDoubleBreakAbort()) {
            // we make optimizeOneBlockMine delay its destroy packet to check onDoubleBreakAbort() and  changing the
            // currentPosition in method sameBlockOptimize
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (seq) -> {
                return new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBreakingPos, direction, seq);
            });
            return;
        }

        instance.sendPacket(packet);
    }

    private boolean onDoubleBreakAbort() {
        if (MineExtra.INSTANCE.doubleBreak.get() && currentFailBreakPos == null) {
            ClientPlayerEntity playerEntity = MinecraftClient.getInstance().player;
            // FIX: DO NOT USE BlockPos.ZERO
            if (playerEntity.canInteractWithBlockAt(this.currentBreakingPos, 1.0D)) {
                BlockState state = MinecraftClient.getInstance().world.getBlockState(this.currentBreakingPos);
                if (!state.isAir() && !state.isLiquid()) {
                    float speed = state.calcBlockBreakingDelta(
                            MinecraftClient.getInstance().player,
                            MinecraftClient.getInstance().player.getEntityWorld(),
                            currentBreakingPos);
                    if (speed > 0) {
                        moveCurrentMiningToFailBreak();
                        MineExtra.INSTANCE.onPostStopMiningFastBreak(
                                currentBreakingPos, speed, currentBreakingProgress);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Shadow
    protected abstract int getBlockBreakingProgress();

    @Shadow
    public abstract boolean updateBlockBreakingProgress(BlockPos pos, Direction direction);

    @Shadow
    protected abstract boolean isCurrentlyBreaking(BlockPos pos);

    @Shadow
    protected abstract void syncSelectedSlot();

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 0,
                            shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacket(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        MineExtra.INSTANCE.onStartingMine(pos, Float.MAX_VALUE, true);
    }

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I",
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            cancellable = true)
    public void fastBreakCreative(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        this.blockBreakingCooldown = MineExtra.INSTANCE.cooldownManaging();
        if (MineExtra.INSTANCE.quickMine.get()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakPacket(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir,
            net.minecraft.block.BlockState blockState) {
        float speed = blockState.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getEntityWorld(),
                pos);
        MineExtra.INSTANCE.onStartingMine(pos, speed, false);
        if (MineExtra.INSTANCE.quickMine.get()) {
            // make cooldown issues

            if (!MineExtra.INSTANCE.shouldUseQuickMine()) {
                return;
            }

            // speed>1.0f可以秒破 此处不调用
            // Debug.info("speed",speed);
            if (!blockState.isAir()) {
                if (speed < 1.0f) {
                    MineExtra mineExtra = MineExtra.INSTANCE;
                    if (speed > mineExtra.breakThreshold.get()) {
                        this.breakingBlock = false;
                        // this start-break + end-break can not pass grimac check, need a normal break to reset buffers
                        // calculate advantages
                        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                            this.breakBlock(pos);
                            return new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                        });
                        mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
                        //                CompletableFuture.runAsync(()->{
                        //
                        //                });
                        if (!mineExtra.optimizeOneBlock.get()) {
                            this.currentBreakingProgress = 0.0F;
                        }
                        this.blockBreakingSoundCooldown = 0.0F;
                        this.blockBreakingCooldown = mineExtra.cooldownManaging();
                    } else if (mineExtra.fakeInstaBreak.get()
                            && speed > ((mineExtra.breakThreshold.get() / 2.0) + 0.04d)) {
                        mineExtra.nextTickEarlyBreak = true;
                    }
                }
            }
        }
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 0,
                            shift = At.Shift.AFTER),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void instaBreakPacketWhenUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MineExtra.INSTANCE.onStartingMine(pos, Float.MAX_VALUE, true);
        this.blockBreakingCooldown = MineExtra.INSTANCE.cooldownManaging();
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
                            ordinal = 0),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void earlyBreakNextTickPacketSend(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (MineExtra.INSTANCE.fakeInstaBreak.get() && MineExtra.INSTANCE.nextTickEarlyBreak) {
            BlockState blockState = MinecraftClient.getInstance().world.getBlockState(pos);
            float speed = blockState.calcBlockBreakingDelta(
                    MinecraftClient.getInstance().player,
                    MinecraftClient.getInstance().player.getEntityWorld(),
                    pos);
            this.breakingBlock = false;
            this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
                this.breakBlock(pos);
                return new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
            });
            MineExtra.INSTANCE.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress + speed);
            if (!MineExtra.INSTANCE.optimizeOneBlock.get()) this.currentBreakingProgress = 0.0F;
            this.blockBreakingSoundCooldown = 0.0F;
            this.blockBreakingCooldown = MineExtra.INSTANCE.cooldownManaging();
            //
            MinecraftClient.getInstance()
                    .world
                    .setBlockBreakingInfo(MinecraftClient.getInstance().player.getId(), this.currentBreakingPos, -1);
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER))
    private void onCommonBlockBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MineExtra.INSTANCE.onPostStopMiningLegally(pos);
    }

    @Redirect(
            method = "updateBlockBreakingProgress",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;currentBreakingProgress:F",
                            ordinal = 4))
    private void onSameBlockDoNotResetProgress(ClientPlayerInteractionManager instance, float value) {
        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            ((PlayerInteractionMixin) (Object) instance).currentBreakingProgress = value;
        }
    }

    //    @Inject(method = "getReachDistance",at = @At(value = "HEAD"),cancellable = true)
    //    public void widerReachDistance(CallbackInfoReturnable<Float> cir){
    //
    //    }
    @Inject(method = "hasLimitedAttackSpeed", at = @At(value = "HEAD"), cancellable = true)
    public void cancelAttackSpeedLimit(CallbackInfoReturnable<Boolean> cir) {
        if (CombatTasks.getCombatExtra().noCooldown.get()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo ci) {
        if (currentFailBreakPos != null) {
            resetFailBreak:
            {
                if (MinecraftClient.getInstance().world != null) {
                    BlockState state = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
                    if (client.player == null || gameMode != GameMode.SURVIVAL) {
                        currentFailBreakPos = null;
                        break resetFailBreak;
                    }
                    // we do not mine air or liquid
                    if (state == null || state.isAir() || state.isLiquid()) {
                        currentFailBreakPos = null;
                        break resetFailBreak;
                    }
                    float speed = state.calcBlockBreakingDelta(
                            MinecraftClient.getInstance().player,
                            MinecraftClient.getInstance().world,
                            currentFailBreakPos);
                    if (speed > 0.0F && ((Tasks.getTick() - failBreakStartTick) * speed > 1.0F)) {
                        // speed < 0 hard
                        currentFailBreakPos = null;
                        break resetFailBreak;
                    }
                    if (client.player != null) {
                        // leave too far
                        if (client.player.getPos().squaredDistanceTo(currentBreakingPos.toCenterPos()) > 225) {
                            currentFailBreakPos = null;
                            break resetFailBreak;
                        }
                    }
                }
            }
        }
    }

    @ModifyExpressionValue(
            method = "clickSlot",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/entity/player/PlayerEntity;currentScreenHandler:Lnet/minecraft/screen/ScreenHandler;"))
    public ScreenHandler onClickSlot(ScreenHandler original, @Local(argsOnly = true) PlayerEntity player) {
        return player instanceof ClientPlayerAccess clientPlayer ? clientPlayer.getServerScreenHandler() : original;
    }

    @Inject(method = "isCurrentlyBreaking", at = @At("HEAD"), cancellable = true)
    public void onCurrentlyBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // completely ignore the damage change
        cir.setReturnValue(Objects.equals(pos, currentBreakingPos)
                && ItemStackUtils.matchItemMiningAbility(this.client.player.getMainHandStack(), this.selectedStack));
    }
}
