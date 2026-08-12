package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class ClientMixin implements Cloneable, ClientAccess {

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Shadow
    private int itemUseCooldown;

    @Shadow
    static MinecraftClient instance;

    @Final
    @Shadow
    public GameOptions options;

    @Unique
    public void setItemUseCooldown(int cooldown) {
        this.itemUseCooldown = cooldown;
    }

    @Unique
    public int getItemUseCooldown() {
        return this.itemUseCooldown;
    }

    @ModifyArg(
            method = "handleInputEvents",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",
                            ordinal = 1))
    public Screen onRedirectInventoryKeyPress(Screen screen) {
        if (InvExtra.INSTANCE.enableKeepInv.get()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null
                    && ClientPlayerAccess.of(player).getKeepedInvHandler() != null
                    && ClientPlayerAccess.of(player).getKeepedInv() != null) {
                HandledScreen screen1 = ClientPlayerAccess.of(player).getKeepedInv();
                player.currentScreenHandler = ClientPlayerAccess.of(player).getKeepedInvHandler();
                ClientPlayerAccess.of(player).clearKeepedInventory(false);
                return screen1;
            }
        }
        return screen;
    }

    @ModifyExpressionValue(
            method = "doAttack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onEnableRidingAttack(boolean original) {

        if (CombatExtra.INSTANCE.rideAttack.get()) {
            // always not riding
            return false;
        }
        return original;
    }

    boolean lastUse = false;

    @WrapOperation(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z", ordinal = 2))
    public boolean onHoldUse(KeyBinding instance, Operation<Boolean> original) {
        boolean pressed = original.call(instance);
        if (InteractionTasks.getInteractExtra().holdUse.get()) {
            // hold use logic
            boolean lastUseFlag = lastUse;
            lastUse = pressed;
            if (player.isUsingItem()) {
                if (lastUseFlag == pressed) {
                    return true;
                }
                // if toggle off in the first few ticks , it is seen as original
                if (player.getItemUseTime()
                        < InteractionTasks.getInteractExtra().holdUseStartTick.get()) {
                    return pressed;
                }

                return lastUseFlag;
            }
        }
        return pressed;
    }

    // for attack when using shield
    @WrapOperation(
            method = "handleInputEvents",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                            ordinal = 0))
    public boolean onAllowingPlayerAttackWhenUseItem(ClientPlayerEntity instance, Operation<Boolean> original) {
        boolean flag = original.call(instance);
        if (flag && CombatExtra.INSTANCE.useAttack.get()) {
            // do attack logic
            boolean bl3 = false;
            // still do attack first
            while (options.attackKey.wasPressed()) {
                bl3 |= this.doAttack();
            }
            // escape pickItemKey
            while (options.pickItemKey.wasPressed()) {
                this.doItemPick();
            }
        }
        return flag;
    }

    @WrapOperation(
            method = "handleBlockBreaking",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                            ordinal = 0))
    public boolean onAllowingPlayerBreakingWhenUseItem(ClientPlayerEntity instance, Operation<Boolean> original) {
        if (CombatExtra.INSTANCE.useAttack.get()) {
            return false;
        } else {
            return original.call(instance);
        }
    }

    //    @Redirect(method = "doItemUse", at = @At(value = "FIELD", target =
    // "Lnet/minecraft/client/MinecraftClient;itemUseCooldown:I"))
    //    public void onRewriteItemCooldown1(MinecraftClient instance, int value){
    //
    //    }

    @WrapOperation(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onAllowRidingUse(ClientPlayerEntity instance, Operation<Boolean> original) {
        if (InteractExtra.INSTANCE.rideUse.get()) {
            return false;
        }
        return original.call(instance);
    }

    @Shadow
    protected abstract void handleBlockBreaking(boolean b);

    @Shadow
    protected abstract void doItemPick();

    @Shadow
    protected abstract boolean doAttack();

    @Shadow
    @Nullable
    public Screen currentScreen;

    @Shadow
    public int attackCooldown;

    @Unique
    public void setAttackCooldown(int cooldown) {
        attackCooldown = cooldown;
    }

    @Unique
    public int getAttackCooldown() {
        return attackCooldown;
    }

    @Shadow
    public abstract Window getWindow();

    @Shadow
    protected abstract void doItemUse();

    @Override
    public ClientAccess clone() {
        try {
            ClientAccess clone = (ClientMixin) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Inject(method = "hasReducedDebugInfo", at = @At("HEAD"), cancellable = true)
    private void onEnhanceDebug(CallbackInfoReturnable<Boolean> cir) {
        if (RenderExtra.INSTANCE.enhancedDebugHud.get()) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    public void simulateRightClick() {
        doItemUse();
    }

    @Unique
    public void simulateLeftClick() {
        doAttack();
    }

    @Unique
    public ActionResult simulateUseItem(Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (!itemStack.isEmpty()) {
            ActionResult actionResult3 = this.interactionManager.interactItem(this.player, hand);
            if (actionResult3 instanceof ActionResult.Success) {
                ActionResult.Success success3 = (ActionResult.Success) actionResult3;
                if (success3.swingSource() == ActionResult.SwingSource.CLIENT) {
                    this.player.swingHand(hand);
                }
            }
            return actionResult3;
        }
        return ActionResult.FAIL;
    }
}
