package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
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
    public void onClickRecipe(int syncId, RecipeEntry<?> recipe, boolean craftAll, CallbackInfo ci) {
        Listener.getClickCraftingRecipe().broadcast(recipe);
    }

    @Inject(method = "interactBlock", at = @At(value = "HEAD"))
    public void onPreInteractBlock(
            ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        // todo: can it be modifiable
        if (!Listener.doItemUseAtBlockPre(hand, hitResult)) {
            cir.setReturnValue(ActionResult.PASS);
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
            ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        Listener.doItemUseAtBlockPost(hand, hitResult);
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
        Event<SlotActionType> eventClickSlot = new Event<>(actionType, true, false, slotId, slotId, button);
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
}
