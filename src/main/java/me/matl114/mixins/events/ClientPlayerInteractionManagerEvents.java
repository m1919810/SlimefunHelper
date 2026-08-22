package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayDeque;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.SlotClickAction;
import me.matl114.events.impl.UseItem;
import me.matl114.events.impl.UseItemOnBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerEvents {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "clickRecipe", at = @At("HEAD"))
    public void onClickRecipe(int syncId, RecipeEntry<?> recipe, boolean craftAll, CallbackInfo ci) {
        Listener.getClickCraftingRecipe().broadcast(recipe);
    }

    @Inject(
            method = "interactItem",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onCancelSend(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        Event<UseItem> handEvent = new Event<>(new UseItem(ActionResult.PASS, hand), true, true);
        Listener.getPrePlayerUseItem().handleValue(handEvent);
        if (handEvent.isCancelled()) {
            cir.setReturnValue(handEvent.context.actionResult());
        }
    }

    @Inject(method = "method_41929", at = @At("RETURN"))
    public void onInteractItem(
            Hand hand,
            PlayerEntity playerEntity,
            MutableObject<ActionResult> mutableObject,
            int sequence,
            CallbackInfoReturnable<Packet> cir) {
        ActionResult acc = mutableObject.getValue();
        Event<UseItem> eventResult = new Event<>(new UseItem(acc, hand), false, true);
        Listener.getPostPlayerUseItem().handleValue(eventResult);
        mutableObject.setValue(eventResult.context.actionResult());
    }

    @Inject(method = "interactBlock", at = @At(value = "HEAD"), cancellable = true)
    public void onPreInteractBlock(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir,
            @Local(argsOnly = true) LocalRef<BlockHitResult> hand2) {
        Event<UseItemOnBlock> blockHitResultEvent =
                new Event<>(new UseItemOnBlock(hitResult, ActionResult.SUCCESS, hand), true, true);
        Listener.getPrePlayerUseItemAtBlock().handleValue(blockHitResultEvent);
        if (blockHitResultEvent.isCancelled()) {
            cir.setReturnValue(blockHitResultEvent.context.actionResult());
        } else {
            BlockHitResult hitResult2 = blockHitResultEvent.context.hitResult();
            if (hitResult2 != hitResult) {
                hand2.set(hitResult2);
            }
        }
    }

    @Inject(
            method = "interactBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
                            shift = At.Shift.AFTER))
    public void onPostInteractBlock(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir,
            @Local MutableObject<ActionResult> mutableObject) {
        ActionResult acc = mutableObject.getValue();
        Event<UseItemOnBlock> eventResult = new Event<>(new UseItemOnBlock(hitResult, acc, hand), false, true);
        Listener.getPostPlayerUseItemAtBlock().handleValue(eventResult);
        mutableObject.setValue(eventResult.context.actionResult());
    }

    @Unique
    private ArrayDeque<MutableBoolean> lastInteractCaptureBlockPlace = new ArrayDeque<>(4);

    @ModifyArg(
            method = "interactBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V"),
            index = 1)
    public SequencedPacketCreator onModifyArgument(
            SequencedPacketCreator packetCreator,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true) BlockHitResult hitResult,
            @Local MutableObject<ActionResult> actionResult) {
        ItemStack stackCopy = client.player.getStackInHand(hand).copy();
        BlockState state = client.world.getBlockState(hitResult.getBlockPos());

        return (seq) -> {
            MutableBoolean captureBlockPlace = new MutableBoolean(false);
            lastInteractCaptureBlockPlace.addLast(captureBlockPlace);
            try {
                var packet = packetCreator.predict(seq);
                if (packet instanceof PlayerInteractBlockC2SPacketAccess access) {
                    access.setUseContext(new PlayerInteractBlockC2SPacketAccess.UseContext(
                            stackCopy, state, actionResult.getValue(), captureBlockPlace.booleanValue()));
                }
                return packet;
            } finally {
                lastInteractCaptureBlockPlace.removeLast();
            }
        };
    }

    @Inject(
            method = "interactBlockInternal",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;"))
    private void onInteractBlockInternalCaptureBlockPlace(
            ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var re = lastInteractCaptureBlockPlace.peekLast();
        if (re != null) {
            re.setValue(true);
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void onClickSlot(
            int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        Event<SlotClickAction> eventClickSlot =
                new Event<>(new SlotClickAction(actionType, syncId, slotId, button), true, false);
        Listener.getPreClickSlot().handleValue(eventClickSlot);
        if (eventClickSlot.isCancelled()) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "clickSlot", at = @At("RETURN"))
    public void onClickSlotPost(
            int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        Listener.getPostClickSlot().broadcast(new SlotClickAction(actionType, syncId, slotId, button));
    }

    @Inject(
            method =
                    "createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;ZZ)Lnet/minecraft/client/network/ClientPlayerEntity;",
            at = @At("RETURN"))
    public void onCreatePlayer(
            ClientWorld world,
            StatHandler statHandler,
            ClientRecipeBook recipeBook,
            boolean lastSneaking,
            boolean lastSprinting,
            CallbackInfoReturnable<ClientPlayerEntity> cir) {
        ClientPlayerEntity player = cir.getReturnValue();
        Listener.getPlayerInitConfiguration().broadcast(player);
    }
}
