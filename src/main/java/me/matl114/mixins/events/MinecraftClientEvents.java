package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.File;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.GlobalEventVars;
import me.matl114.events.Listener;
import me.matl114.utils.collections.Point;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.util.Hand;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(MinecraftClient.class)
@Environment(EnvType.CLIENT)
public abstract class MinecraftClientEvents {
    @Shadow
    @Nullable
    public Screen currentScreen;

    @Shadow
    private int itemUseCooldown;

    @Shadow
    public HitResult crosshairTarget;

    @Shadow
    public abstract Window getWindow();

    @Shadow
    public abstract void tick();

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    public void onPreSetScreen(Screen screen, CallbackInfo ci, @Local(argsOnly = true) LocalRef<Screen> screenRef) {
        if (!Listener.getPreSetScreen().isEmpty()) {
            Event<Screen> screenEvent = new Event<>(screen, true, true);
            Listener.getPreSetScreen().handleValue(screenEvent);
            if (screenEvent.isCancelled()) {
                ci.cancel();
            } else {
                if (screenEvent.context != screen) {
                    screenRef.set(screenEvent.context);
                }
            }
        }
    }

    @Inject(
            method = "setScreen",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
                            ordinal = 3,
                            shift = At.Shift.BEFORE),
            cancellable = true)
    public void onPostSetScreen(Screen screen, CallbackInfo ci) {
        if (!Listener.getPostSetScreen().isEmpty()) {
            Event<Screen> screenEvent = new Event<>(this.currentScreen, true, false);
            Listener.getPostSetScreen().handleValue(screenEvent);
            if (screenEvent.isCancelled()) {
                ci.cancel();
                // FIX: even if post set is cancelled , the screen must be initialized or exception will be thrown
                if (this.currentScreen != null) {
                    (this.currentScreen)
                            .init(getWindow().getScaledWidth(), getWindow().getScaledHeight());
                }
                return;
            }
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    public void onServerDisconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        // origin exit
        if (!transferring) Listener.getServerDisconnectPoint().broadcast(null);
    }

    @WrapOperation(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"))
    private void onGameRenderer(
            GameRenderer instance, RenderTickCounter tickCounter, boolean tick, Operation<Void> original) {
        Event<GameRenderer> rendererEvent = new Event<>(instance, true, false, tickCounter, tick);
        Listener.getGameRender().handleValue(rendererEvent);
        if (!rendererEvent.isCancelled()) {
            original.call(instance, tickCounter, tick);
        }
    }

    @Inject(
            method =
                    "printCrashReport(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;Lnet/minecraft/util/crash/CrashReport;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/System;exit(I)V", shift = At.Shift.BEFORE),
            cancellable = true)
    private static void onSystemExit(
            MinecraftClient client, File runDirectory, CrashReport crashReport, CallbackInfo ci) {
        GlobalEventVars.crashReport = crashReport;
        if (!Listener.getClientMainExit().isEmpty()) {
            Event<MinecraftClient> exitEvent = new Event<>(client, client.isRunning(), false, crashReport);
            Listener.getClientMainExit().handleValue(exitEvent);
            if (exitEvent.isCancelled()) {
                ci.cancel();
            }
        }
    }

    //     //deprecated ItemUseEvent
    //        @Inject(method = "doItemUse", at = @At(value = "INVOKE", target =
    //
    // "Lnet/minecraft/client/network/ClientPlayerEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;", shift = At.Shift.BEFORE), cancellable = true)
    //        private void doItemUseEvent(CallbackInfo ci, @Local Hand hand){
    //            if(!Listener.getTriggerRightClick().isEmpty()){
    //                Event<Hand> useWithHandEvent = new Event<>(hand, true, false);
    //                Listener.getTriggerRightClick().handleValue(useWithHandEvent);
    //                if(useWithHandEvent.isCancelled()){
    //                    ci.cancel();
    //                }
    //            }
    //        }
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void onItemUseTargetStore(CallbackInfo ci) {

        cacheHitResult = crosshairTarget;
    }

    @Inject(
            method = "doItemUse",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"),
            cancellable = true)
    private void onItemUseEvent(CallbackInfo ci, @Local LocalRef<Hand> hand) {
        crosshairTarget = cacheHitResult;
        Hand currentHand = hand.get();
        while (true) {
            Event<HitResult> hitResultEvent = new Event<>(crosshairTarget, true, true, currentHand);
            Listener.getItemUseAction().handleValue(hitResultEvent);
            if (hitResultEvent.isCancelled() || hitResultEvent.context == null) {
                int nextHand = currentHand.ordinal() + 1;
                Hand[] hands = Hand.values();
                if (nextHand < hands.length) {
                    currentHand = hands[nextHand];
                } else {
                    ci.cancel();
                    return;
                }
            } else {
                crosshairTarget = hitResultEvent.context;
                break;
            }
        }
        hand.set(currentHand);
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void onItemUseTargetRestore(CallbackInfo ci) {
        crosshairTarget = cacheHitResult;
    }

    @Inject(
            method = "doItemUse",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z",
                            shift = At.Shift.BEFORE))
    private void onItemCooldown(CallbackInfo ci) {
        // inject the cooldown, before the riding call
        Event<Integer> event = new Event<>(null, true, true);
        Listener.getUseItemCooldownReset().handleValue(event);
        if (event.isCancelled()) {
            this.itemUseCooldown = 0;
        } else if (event.context() != null) {
            this.itemUseCooldown = event.context();
        }
    }

    @Unique
    private HitResult cacheHitResult = null;

    @Inject(
            method = "handleBlockBreaking",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;",
                            ordinal = 0,
                            shift = At.Shift.BEFORE))
    private void onMineBlock(boolean breaking, CallbackInfo ci) {
        Event<HitResult> hitResultEvent = new Event<>(this.crosshairTarget, true, true);
        Listener.getMineBlockAction().handleValue(hitResultEvent);
        if (hitResultEvent.isCancelled() || hitResultEvent.context != crosshairTarget) {
            cacheHitResult = crosshairTarget;
            crosshairTarget = hitResultEvent.isCancelled() ? null : hitResultEvent.context;
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("RETURN"))
    private void onRestoreHitResultAfterBreak(CallbackInfo ci) {
        if (cacheHitResult != null) {
            this.crosshairTarget = cacheHitResult;
        }
        cacheHitResult = null;
    }

    @Unique
    boolean skipCurrentInputEvent = false;

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V",
                            shift = At.Shift.BEFORE))
    public void onPreInputEvent(CallbackInfo ci) {
        Event<Void> re = new Event<>(null, true, false);
        Listener.getPreHandleInputEvents().handleValue(re);
        if (re.isCancelled()) {
            skipCurrentInputEvent = true;
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    public void onPreInputEventCancel(CallbackInfo ci) {
        if (skipCurrentInputEvent) {
            skipCurrentInputEvent = false;
            ci.cancel();
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V",
                            shift = At.Shift.AFTER))
    public void onPostInputEvent(CallbackInfo ci) {
        Listener.getPostHandleInputEvents().handleValue(new Event<>(null, false, false));
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V",
                            shift = At.Shift.BEFORE,
                            ordinal = 1),
            cancellable = true)
    public void onPreTick(CallbackInfo ci) {
        if (!Listener.getPreTick().fireEvent(null)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                            shift = At.Shift.BEFORE,
                            ordinal = 1),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onPostTick(CallbackInfo ci, @Local Profiler profiler) {
        profiler.swap("post-tick");
        Listener.getPostTick().broadcast(null);
    }

    // move before the block interaction, so that it will not reset cooldown when interact block or swing hand
    @Inject(
            method = "doAttack",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;",
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true)
    public void onAttackAction(CallbackInfoReturnable<Boolean> cir) {
        if (crosshairTarget != null) {
            Event<HitResult> resultEvent = new Event<>(crosshairTarget, true, true);
            Listener.getAttackAction().handleValue(resultEvent);
            if (resultEvent.isCancelled()) {
                cir.setReturnValue(false);
            } else {
                if (!Objects.equals(resultEvent.context(), crosshairTarget)) {
                    cacheHitResult = crosshairTarget;
                    crosshairTarget = resultEvent.context();
                } else {
                    cacheHitResult = null;
                }
            }
        }
    }

    @Inject(
            method = "doAttack",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",
                            shift = At.Shift.BEFORE))
    private void restoreAttackTarget(CallbackInfoReturnable<Boolean> cir) {
        if (cacheHitResult != null) {
            crosshairTarget = cacheHitResult;
            cacheHitResult = null;
        }
    }

    @Inject(
            method = "onResolutionChanged",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;",
                            shift = At.Shift.BEFORE))
    public void onResolutionChanged(CallbackInfo ci) {
        Listener.getResolutionChange()
                .handleValue(new Event<>(
                        new Point(
                                MinecraftClient.getInstance().getWindow().getScaledWidth(),
                                MinecraftClient.getInstance().getWindow().getScaledHeight()),
                        false,
                        false));
    }
}
