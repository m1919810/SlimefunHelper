package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import javax.annotation.Nullable;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.managers.Tasks;
import me.matl114.utils.AttributeUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    /**
     * doubleBreak / failMine 使用的备用挖掘槽位。
     *
     * <p>主挖掘位置切走后，旧位置如果仍值得继续复用，就暂存在这里，等待后续 stop 或自动完成逻辑消费。
     */
    @Nullable
    @Unique
    private BlockPos currentFailBreakPos = null;

    /**
     * failBreak 槽位建立时对应的 start tick。
     *
     * <p>它和 {@link #currentFailBreakPos} 一起构成“备用挖掘会话”的最小状态，用于按服务端 start/stop
     * 状态机推导理论进度，而不是依赖客户端原版破坏动画。
     */
    @Unique
    private int failBreakStartTick;

    /**
     * 返回当前主挖掘槽位绑定的位置。
     *
     * <p>这是服务端后续 stop 包默认要对应的位置，也是 optimizeOneBlock 复用的主状态位。
     */
    @Override
    public BlockPos getCurrentMiningPos() {
        return currentBreakingPos;
    }

    /**
     * 本地清空当前主挖掘位。
     *
     * <p>这里只处理客户端会话态，不主动补发 stop。调用方通常在确定该上下文已经无效、或者需要显式重建
     * start 上下文时使用它。
     */
    @Override
    public void resetCurrentMiningPos() {
        currentBreakingPos = new BlockPos(-1, -1, -1);
        resetLocalMiningProgress();
    }

    /**
     * 读取当前 failBreak 槽位。
     *
     * <p>这里直接暴露当前备用槽位本身，不再由 doubleBreak 开关决定可见性；是否允许写入或消费该槽位，
     * 由具体调用路径自行判断。
     */
    @Override
    @Nullable
    public BlockPos getCurrentFailBreakPos() {
        return currentFailBreakPos;
    }

    /**
     * 判断 failBreak 槽位当前是否空闲。
     *
     * <p>这是对外暴露的稳定语义，调用方不需要再依赖 null 细节自行拼装状态判断。
     */
    @Override
    public boolean isFailBreakEmpty() {
        return currentFailBreakPos == null;
    }

    public int getCurrentMiningTicks() {
        return Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
    }

    public int getFailBreakMiningTicks() {
        return Tasks.getTick() - failBreakStartTick;
    }

    /**
     * 读取当前主挖掘位进度。
     *
     * <p>传入 null 时，优先返回原版仍然有效的本地缓存进度；只有在 stop 判定显式传入工具时，才按指定工具速度回退到
     * start tick 推导值。
     */
    @Override
    public float getCurrentMiningProgress(@Nullable ItemStack tool) {
        BlockState block = MinecraftClient.getInstance().world.getBlockState(currentBreakingPos);
        if (block.isAir()) {
            return -1.0F;
        }
        if (tool == null && (breakingBlock && isCurrentlyBreaking(currentBreakingPos))) {
            return this.currentBreakingProgress == 0.0F ? -1.0F : this.currentBreakingProgress;
        }

        ItemStack usedTool = tool == null ? this.client.player.getMainHandStack() : tool;
        float miningSpeed =
                WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(this.client.player, block, usedTool);
        float speed = WorldUtils.calcBlockBreakingDelta(block, this.client.world, currentBreakingPos, miningSpeed);
        int ticksSinceLastStart = Tasks.getTick() - MineExtra.INSTANCE.lastStartMineBreakingProgressResetTick;
        return speed * ticksSinceLastStart;
    }

    /**
     * 显式建立一个 failBreak 槽位。
     *
     * <p>成功时会同时：
     * <ul>
     *     <li>登记备用位置</li>
     *     <li>把当前主挖掘位切到该位置，便于后续 stop 复用同一套位置语义</li>
     *     <li>记录该会话对应的 start tick</li>
     * </ul>
     *
     * <p>如果槽位已被占用，则拒绝覆盖，避免多个未完成的备用会话互相踩状态。
     */
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

    /**
     * 尝试把当前主挖掘位整体迁入 failBreak 槽位。
     *
     * <p>这是 doubleBreak / 切块续挖最常用的入口，用于在开始处理新方块前，先保留旧方块的服务端挖掘上下文。
     */
    @Unique
    @Override
    public boolean moveCurrentMiningToFailBreak() {
        return beginFailBreak(currentBreakingPos);
    }

    /**
     * 清空 failBreak 槽位和它的时间基线。
     *
     * <p>一旦调用，表示这段备用挖掘上下文已经失效、完成或不再值得继续复用。
     */
    @Unique
    @Override
    public void clearFailBreak() {
        currentFailBreakPos = null;
        failBreakStartTick = 0;
    }

    /**
     * 只复位本地缓存的破坏进度。
     *
     * <p>这是一个纯本地 helper，不做位置切换，也不修改发包状态，用于把多个 stop/start 分支里的进度清理收口。
     */
    @Unique
    private void resetLocalMiningProgress() {
        currentBreakingProgress = 0.0F;
    }

    /**
     * 清理“原版仍在持续挖掘”的本地标记。
     *
     * <p>很多 bypass 分支在提前 stop 时，都需要先把原版 breaking 标志降下来，避免后续 tick 继续按普通挖掘流推进。
     */
    @Unique
    private void clearBreakingState() {
        this.breakingBlock = false;
    }

    /**
     * 执行一次 stop 之后的本地统一收尾。
     *
     * <p>它集中维护三类状态：
     * <ul>
     *     <li>是否清空本地进度缓存</li>
     *     <li>声音冷却归零</li>
     *     <li>交互冷却按 MineExtra 策略重置</li>
     * </ul>
     *
     * <p>这样不同 stop 路径就不需要再各自散写相同字段。
     */
    @Unique
    private void applyPostStopState(boolean resetProgress) {
        if (resetProgress) {
            resetLocalMiningProgress();
        }
        this.blockBreakingSoundCooldown = 0.0F;
        this.blockBreakingCooldown = MineExtra.INSTANCE.cooldownManaging();
    }

    /**
     * 只发送一个 STOP_DESTROY_BLOCK。
     *
     * <p>用于那些“服务端已有有效 start 上下文，当前只需要补一个 stop 包”的路径，例如同位置复用、
     * doubleBreak 补包等。
     */
    @Unique
    private void sendStopBreakPacketInternal(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    /**
     * 先执行一次本地 breakBlock，再发送 STOP_DESTROY_BLOCK 收尾。
     *
     * <p>这是更强语义的“完成一次 break”，用于 quickMine / fakeInstaBreak 这类希望立即把本地表现和 stop
     * 动作一起落地的路径。
     */
    @Unique
    private void sendBreakAndStopPacket(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence) -> {
            this.breakBlock(pos);
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        });
    }

    @Unique
    private void continueSameBlockMining(BlockPos pos, Direction direction) {
        this.currentBreakingPos = pos;
        this.currentBreakingProgress = getCurrentMiningProgress(null);
        this.blockBreakingCooldown = 0;
        this.breakingBlock = true;
        this.selectedStack = this.client.player.getMainHandStack();
        this.client.world.setBlockBreakingInfo(
                this.client.player.getId(), this.currentBreakingPos, this.getBlockBreakingProgress());
        this.updateBlockBreakingProgress(pos, direction);
    }

    @Unique
    private boolean tryAbortCurrentMiningIntoFailBreak() {
        if (!MineExtra.INSTANCE.doubleBreak.get() || !isFailBreakEmpty()) {
            return false;
        }
        ClientPlayerEntity playerEntity = MinecraftClient.getInstance().player;
        if (!playerEntity.canInteractWithBlockAt(this.currentBreakingPos, 1.0D)) {
            return false;
        }
        BlockState state = MinecraftClient.getInstance().world.getBlockState(this.currentBreakingPos);
        if (state.isAir() || state.isLiquid()) {
            return false;
        }
        float speed = state.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player,
                MinecraftClient.getInstance().player.getEntityWorld(),
                currentBreakingPos);
        if (speed <= 0) {
            return false;
        }
        moveCurrentMiningToFailBreak();
        MineExtra.INSTANCE.onPostStopMiningFastBreak(currentBreakingPos, speed, currentBreakingProgress);
        return true;
    }

    /**
     * 判断当前 failBreak 槽位是否已经不值得继续保留。
     *
     * <p>清理条件包括：
     * <ul>
     *     <li>玩家或模式已经不再允许继续按生存挖掘处理</li>
     *     <li>槽位方块已空气化或液体化</li>
     *     <li>按 start tick 推导已经理论完成，不再需要继续挂起</li>
     *     <li>玩家与该位置距离过远，继续复用失去意义</li>
     * </ul>
     */
    @Unique
    private boolean shouldClearFailBreakBecauseInvalidState() {
        if (MinecraftClient.getInstance().world == null) {
            return false;
        }
        BlockState state = MinecraftClient.getInstance().world.getBlockState(currentFailBreakPos);
        if (client.player == null || gameMode != GameMode.SURVIVAL) {
            return true;
        }
        if (state == null || state.isAir() || state.isLiquid()) {
            return true;
        }
        float speed = state.calcBlockBreakingDelta(
                MinecraftClient.getInstance().player, MinecraftClient.getInstance().world, currentFailBreakPos);
        // in the case of server lag
        if (speed > 0.0F && ((Tasks.getTick() - failBreakStartTick - 1) * speed > 1.0F)) {
            return true;
        }
        return client.player != null
                && client.player.getPos().squaredDistanceTo(currentBreakingPos.toCenterPos()) > 225;
    }

    @Shadow
    private GameMode gameMode;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    /**
     * 对外暴露的 stop 语义入口。
     *
     * <p>这里只发送 stop 包，不附带本地 breakBlock；更强的“break + stop”组合由内部 helper 单独负责。
     */
    @Override
    @Unique
    public void sendBreakPacket(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
        }));
    }

    /**
     * 对外暴露的 start 语义入口。
     *
     * <p>调用时会复位本地进度，并在非 instant break 情况下切换主挖掘位置。这样外部模块就不需要再知道
     * “什么时候改 currentBreakingPos、什么时候只发 start 包” 这类内部细节。
     */
    @Override
    @Unique
    public void startMiningBlock(BlockPos pos, Direction direction) {
        this.sendSequencedPacket(MinecraftClient.getInstance().world, (sequence -> {
            resetLocalMiningProgress();
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
            return MineExtra.INSTANCE.shouldTreatAsInstantBreak(speed);
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
        IndexEntry<ItemStack> tool = MineExtra.INSTANCE.getGhostHandMiningTool(blockState);
        float progress = getCurrentMiningProgress(tool.val());
        if (mineExtra.shouldExecuteFastBreak(progress)) {
            this.currentBreakingProgress = progress;
            clearBreakingState();
            MineExtra.INSTANCE.fastBreakGhostHand =
                    Pair.of(InvExtra.INSTANCE.swapInventoryIndexToHand(tool.index()), pos);
            AttributeUtils.updateAttribute(this.client.player);
            float speed =
                    blockState.calcBlockBreakingDelta(MinecraftClient.getInstance().player, this.client.world, pos);
            sendBreakAndStopPacket(pos, direction);
            mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
            applyPostStopState(!mineExtra.optimizeOneBlock.get());
            cir.cancel();
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
                continueSameBlockMining(pos, direction);
                cir.setReturnValue(true);
            } else {
                float predictedProgress = getCurrentMiningProgress(null);
                if (mineExtra.shouldTryDoubleBreak(predictedProgress)) {
                    sendFailBreakCurrentPos(direction);
                }
            }
        }
    }

    @WrapOperation(
            method = "cancelBlockBreaking",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak(ClientPlayNetworkHandler instance, Packet packet, Operation<Void> original) {

        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            // we make optimizeOneBlockMine delay its destroy packet to changing the currentPosition in method
            // sameBlockOptimize
            if (sendFailBreakCurrentPos(null)) {
                return;
            }
        }
        original.call(instance, packet);
    }

    @Unique
    @Override
    public boolean sendFailBreakCurrentPos(@Nullable Direction direction) {
        if (tryAbortCurrentMiningIntoFailBreak()) {
            if (direction == null) {
                Vec3d shouldFacing = currentBreakingPos
                        .toCenterPos()
                        .subtract(MinecraftClient.getInstance().player.getEyePos());
                direction = Direction.getFacing(shouldFacing).getOpposite();
            }
            sendStopBreakPacketInternal(currentBreakingPos, direction);
            return true;
        }
        return false;
    }

    @WrapOperation(
            method = "attackBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void onDoubleBreak2(
            ClientPlayNetworkHandler instance,
            Packet packet,
            Operation<Void> original,
            @Local(argsOnly = true) Direction direction) {
        // conflict with optimizeOneBlock

        if (!MineExtra.INSTANCE.optimizeOneBlock.get()) {
            // we make optimizeOneBlockMine delay its destroy packet to check onDoubleBreakAbort() and  changing the
            // currentPosition in method sameBlockOptimize
            if (sendFailBreakCurrentPos(direction)) {
                return;
            }
        }
        original.call(instance, packet);
    }

    @Shadow
    protected abstract int getBlockBreakingProgress();

    @Shadow
    public abstract boolean updateBlockBreakingProgress(BlockPos pos, Direction direction);

    @Shadow
    protected abstract boolean isCurrentlyBreaking(BlockPos pos);

    @Shadow
    protected abstract void syncSelectedSlot();

    @Shadow
    protected abstract ActionResult interactBlockInternal(
            ClientPlayerEntity player, Hand hand, BlockHitResult hitResult);

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
        applyPostStopState(false);
        if (MineExtra.INSTANCE.quickMine.get()) {
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "method_41930",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F"))
    public float fastBreakGhostHand(
            BlockState instance,
            PlayerEntity player,
            BlockView blockView,
            BlockPos blockPos,
            Operation<Float> original) {
        if (MineExtra.INSTANCE.ghostHandMine.get()) {
            var bestTool = MineExtra.INSTANCE.getGhostHandMiningTool(instance);
            float miningSpeed =
                    WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(player, instance, bestTool.val());
            float progress = WorldUtils.calcBlockBreakingDelta(instance, blockView, blockPos, miningSpeed);
            if (progress > 1.01) {
                MineExtra.INSTANCE.instaBreakGhostHand =
                        Pair.of(InvExtra.INSTANCE.swapInventoryIndexToHand(bestTool.index()), blockPos);
                AttributeUtils.updateAttribute(player);
            }
        }
        return original.call(instance, player, blockView, blockPos);
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
        float speed;
        var usingTool = MineExtra.INSTANCE.getGhostHandMiningTool(blockState);
        float playerSpeed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(
                this.client.player, blockState, usingTool.val());
        speed = WorldUtils.calcBlockBreakingDelta(blockState, this.client.world, pos, playerSpeed);

        MineExtra mineExtra = MineExtra.INSTANCE;
        mineExtra.onStartingMine(pos, speed, false);
        if (!mineExtra.shouldUseQuickMine() || blockState.isAir()) {
            return;
        }
        if (mineExtra.shouldTriggerEarlyStop(speed)) {
            MineExtra.INSTANCE.fastBreakGhostHand =
                    Pair.of(InvExtra.INSTANCE.swapInventoryIndexToHand(usingTool.index()), pos);
            AttributeUtils.updateAttribute(this.client.player);
            clearBreakingState();
            sendBreakAndStopPacket(pos, direction);
            mineExtra.onPostStopMiningFastBreak(pos, speed, this.currentBreakingProgress);
            applyPostStopState(!mineExtra.optimizeOneBlock.get());
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
        applyPostStopState(false);
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

    @Unique
    private int lastBreakCooldown = 0;

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo ci) {
        // tick cooldown when not pressing
        if (lastBreakCooldown != blockBreakingCooldown) {
            lastBreakCooldown = blockBreakingCooldown;
        } else if (blockBreakingCooldown > 0) {
            blockBreakingCooldown--;
            lastBreakCooldown = blockBreakingCooldown;
        }
        if (!isFailBreakEmpty() && shouldClearFailBreakBecauseInvalidState()) {
            clearFailBreak();
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

    @Inject(
            method = "interactItem",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V",
                            shift = At.Shift.AFTER),
            order = -114514)
    private void onInteractPreSend(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LegacySnapRotManager.INSTANCE.betweenViaPacket = true;
    }

    @Inject(
            method = "interactItem",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/apache/commons/lang3/mutable/MutableObject;<init>()V",
                            remap = false),
            order = 114514)
    private void onInteractPostSend(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LegacySnapRotManager.INSTANCE.betweenViaPacket = false;
    }

    @Override
    @Unique
    public ActionResult simulateInteractBlock(Hand hand, BlockHitResult hitResult) {
        return interactBlockInternal(this.client.player, hand, hitResult);
    }

    @Override
    @Unique
    public ActionResult simulateInteractItem(Hand hand) {
        var player = this.client.player;
        ItemStack itemStack = player.getStackInHand(hand);
        if (player.getItemCooldownManager().isCoolingDown(itemStack)) {
            return ActionResult.PASS;
        } else {
            ActionResult actionResult = itemStack.use(this.client.world, player, hand);
            // restore
            player.setStackInHand(hand, itemStack);
            return actionResult;
        }
    }
}
