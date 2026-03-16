package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class ClientMixin implements Cloneable, ClientAccess {

    @Shadow
    private Profiler profiler;

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
        if (InvTasks.getKeepInv().enable.get()) {
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

        if (CombatTasks.getCombatExtra().rideAttack.get()) {
            // always not riding
            return false;
        }
        return original;
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;overlay:Lnet/minecraft/client/gui/screen/Overlay;",
                            shift = At.Shift.BEFORE))
    public void onInputEventIfScreenOpen(CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null
                || MinecraftClient.getInstance().getOverlay() != null) {
            this.profiler.swap("Keybindings");
            handleInputEventWhenScreenOpen();
        }
    }

    @Unique
    private void handleInputEventWhenScreenOpen() {
        // check in game and do the tick
        if (MinecraftClient.getInstance().player != null) {
            this.handleBlockBreaking(false);
            if (this.attackCooldown > 0) {
                --this.attackCooldown;
            }
        }
    }

    // for attack when using shield
    @Redirect(
            method = "handleInputEvents",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                            ordinal = 0))
    public boolean onAllowingPlayerAttackWhenUseItem(ClientPlayerEntity player) {
        boolean flag = player.isUsingItem();
        if (flag && CombatTasks.getCombatExtra().shieldAttack.get()) {
            // do attack logic
            boolean bl3 = false;
            // still do attack first
            while (instance.options.attackKey.wasPressed()) {
                bl3 |= this.doAttack();
            }
            // escape pickItemKey
            while (instance.options.pickItemKey.wasPressed()) {
                this.doItemPick();
            }
        }
        return flag;
    }

    @Redirect(
            method = "handleBlockBreaking",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                            ordinal = 0))
    public boolean onAllowingPlayerBreakingWhenUseItem(ClientPlayerEntity player) {
        if (CombatTasks.getCombatExtra().shieldAttack.get()) {
            return false;
        } else {
            return player.isUsingItem();
        }
    }

    //    @Redirect(method = "doItemUse", at = @At(value = "FIELD", target =
    // "Lnet/minecraft/client/MinecraftClient;itemUseCooldown:I"))
    //    public void onRewriteItemCooldown1(MinecraftClient instance, int value){
    //
    //    }

    @Redirect(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onAllowRidingUse(ClientPlayerEntity instance) {
        if (InteractionTasks.getInteractExtra().rideUse.get()) {
            return false;
        }
        return instance.isRiding();
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
    @Final
    public GameRenderer gameRenderer;

    @Shadow
    protected abstract void render(boolean tick);

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
        if (RenderTasks.getRenderExtra().enhancedDebugHud.get()) {
            cir.setReturnValue(false);
        }
    }
}
