package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
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
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    public void onClickRecipe(int syncId, NetworkRecipeId recipeId, boolean craftAll, CallbackInfo ci) {
        Listener.getClickCraftingRecipe().broadcast(recipeId);
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
        Event<ActionResult> handEvent = new Event<>(ActionResult.PASS, true, true, hand);
        Listener.getPrePlayerUseItem().handleValue(handEvent);
        if (handEvent.isCancelled()) {
            cir.setReturnValue(handEvent.context);
        }
    }

    @Inject(method = "method_41929", at = @At("RETURN"))
    public void onInteractItem(
            Hand hand,
            PlayerEntity playerEntity,
            MutableObject<ActionResult> mutableObject,
            int sequence,
            CallbackInfoReturnable<Packet> cir) {
        ActionResult acc = mutableObject.get();
        Event<ActionResult> eventResult = new Event<>(mutableObject.get(), false, true, hand);
        Listener.getPostPlayerUseItem().handleValue(eventResult);
        if (eventResult.context != acc) {
            mutableObject.setValue(eventResult.context);
        }
    }

    @Inject(method = "interactBlock", at = @At(value = "HEAD"))
    public void onPreInteractBlock(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir,
            @Local(argsOnly = true) LocalRef<BlockHitResult> hand2) {
        Event<BlockHitResult> blockHitResultEvent = new Event<>(hitResult, true, true, hand);
        Listener.getPrePlayerUseItemAtBlock().handleValue(blockHitResultEvent);
        if (blockHitResultEvent.isCancelled()) {
            cir.setReturnValue(ActionResult.SUCCESS);
        } else {
            BlockHitResult hitResult2 = blockHitResultEvent.context;
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
        ActionResult acc = mutableObject.get();
        Event<ActionResult> eventResult = new Event<>(acc, false, true, hitResult, hand);
        Listener.getPostPlayerUseItemAtBlock().handleValue(eventResult);
        ActionResult acc2 = eventResult.context;
        if (acc2 != acc) {
            mutableObject.setValue(acc2);
        }
    }

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
            @Local(argsOnly = true) BlockHitResult hitResult) {
        ItemStack stackCopy = client.player.getStackInHand(hand).copy();
        BlockState state = client.world.getBlockState(hitResult.getBlockPos());

        return (seq) -> {
            var packet = packetCreator.predict(seq);
            if (packet instanceof PlayerInteractBlockC2SPacketAccess access) {
                access.setUseContext(new PlayerInteractBlockC2SPacketAccess.UseContext(stackCopy, state));
            }
            return packet;
        };
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void onClickSlot(
            int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        Event<SlotActionType> eventClickSlot = new Event<>(actionType, true, false, syncId, slotId, button);
        Listener.getPreClickSlot().handleValue(eventClickSlot);
        if (eventClickSlot.isCancelled()) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "clickSlot", at = @At("RETURN"))
    public void onClickSlotPost(
            int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        Listener.getPostClickSlot().broadcast(actionType, syncId, slotId, button);
    }

    @Inject(
            method =
                    "createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;Lnet/minecraft/util/PlayerInput;Z)Lnet/minecraft/client/network/ClientPlayerEntity;",
            at = @At("RETURN"))
    public void onCreatePlayer(
            ClientWorld world,
            StatHandler statHandler,
            ClientRecipeBook recipeBook,
            PlayerInput lastPlayerInput,
            boolean lastSprinting,
            CallbackInfoReturnable<ClientPlayerEntity> cir) {
        ClientPlayerEntity player = cir.getReturnValue();
        Listener.getPlayerInitConfiguration().broadcast(player);
    }
}
