package me.matl114.mixins.events;

import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerEvents {

    @Inject(method = "clickRecipe",at = @At("HEAD"))
    public void onClickRecipe(int syncId, NetworkRecipeId recipeId, boolean craftAll, CallbackInfo ci){
        Listener.getClickCraftingRecipe().broadcast(recipeId);
    }
    @Inject(method = "interactBlock", at = @At(value = "HEAD"))
    public void onPreInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir){
        //todo: can it be modifiable
        if( !Listener.doItemUseAtBlockPre(hand, hitResult)){
            cir.setReturnValue(ActionResult.PASS);
        }
    }
    @Inject(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",shift = At.Shift.AFTER))
    public void onPostInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir){
        Listener.doItemUseAtBlockPost(hand, hitResult);
    }


}
