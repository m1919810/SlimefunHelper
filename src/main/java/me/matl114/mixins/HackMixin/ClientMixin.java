package me.matl114.mixins.HackMixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.access.ClientAccess;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.CombatTasks;
import me.matl114.hackUtils.RenderTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.Point;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.util.Hand;
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
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;


@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class ClientMixin implements Cloneable, ClientAccess {


    @Shadow private Profiler profiler;

    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow @Nullable public ClientPlayerInteractionManager interactionManager;

    @Shadow @Nullable public HitResult crosshairTarget;

    @Shadow private int itemUseCooldown;

    @Shadow
    static MinecraftClient instance;
    @Final
    @Shadow
    public GameOptions options;

    @Unique
    public void setCooldown(int cooldown){
        this.itemUseCooldown = cooldown;
    }

    @Unique
    public int getCooldown(){
        return this.itemUseCooldown;
    }

    @ModifyArg(method = "handleInputEvents",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",ordinal = 1))
    public Screen onRedirectInventoryKeyPress(Screen screen){
        if(HotKeys.getButtonToggleManager().getState(HotKeys.KEEP_INV)){
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player!=null&&ClientPlayerAccess.of(player).getKeepedInvHandler()!=null&& ClientPlayerAccess.of(player).getKeepedInv() != null){
                HandledScreen screen1= ClientPlayerAccess.of(player).getKeepedInv();
                player.currentScreenHandler=ClientPlayerAccess.of(player).getKeepedInvHandler();
                ClientPlayerAccess.of(player).clearKeepedInventory(false);
                return screen1;
            }
        }
        return screen;
    }

    @Inject(method = "setScreen", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;", ordinal = 3, shift = At.Shift.BEFORE), cancellable = true)
    public void onSetScreenPost(Screen screen, CallbackInfo ci){
        if(!Listener.getPostSetScreen().isEmpty()){
            Event<Screen> screenEvent = new Event<>(this.currentScreen, true, false);
            Listener.getPostSetScreen().handleValue(screenEvent);
            if(screenEvent.isCancelled()){
                ci.cancel();
                //FIX: even if post set is cancelled , the screen must be initialized or exception will be thrown
                if(this.currentScreen != null){
                    (this.currentScreen).init(MinecraftClient.getInstance(), getWindow().getScaledWidth(), getWindow().getScaledHeight());
                }
                return;
            }
        }
    }

    @Inject(method = "onResolutionChanged", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;", ordinal = 0, shift = At.Shift.BEFORE))
    public void onResolutionChanged(CallbackInfo ci){
        Listener.getCurrentScreenResize().handleValue(new Event<>(new Point(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight()), false, false));
    }


    @Unique
    private static final Config.FlagRef RIDING_ATTACK= Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_RIDING);
    @ModifyExpressionValue(method = "doAttack",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onEnableRidingAttack(boolean original) {

        if(RIDING_ATTACK.get()){
            //always not riding
            return false;
        }
        return original;
    }
    //move before the block interaction, so that it will not reset cooldown when interact block or swing hand
    @Inject(method = "doAttack",at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;",shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    public void onAttackWhenMissedEntity(CallbackInfoReturnable<Boolean> cir) {
        if(crosshairTarget!=null){
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.ALWAYS_ATTACK)){
                //todo change to Event
                if(CombatTasks.autoAttackBest(false)){
                    //return true to cancel block break, because this is going to delay attack
                    //stop another attack-like action before delay attack finish, because another task may reset attack-interval
                    this.attackCooldown = 1;
                    cir.setReturnValue(false);
                }else if(crosshairTarget.getType() == HitResult.Type.ENTITY){
                    this.attackCooldown = 0;
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "doAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",shift = At.Shift.AFTER))
    private void onShieldPredict(CallbackInfoReturnable<Boolean> cir){
        if(crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.ENTITY){
            //predict after attack
            CombatTasks.handleShieldPredict(player.getPitch(), player.getYaw());
        }
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V", shift = At.Shift.BEFORE, ordinal = 1))
    public void onPreTick(CallbackInfo ci){
        Listener.getPreTick().handleValue(new Event<>(null, false, false));
    }

    @Inject(method = "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V",shift = At.Shift.BEFORE,ordinal = 1),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onPostTick(CallbackInfo ci){
        this.profiler.swap("slimefun-helper-tasks");
        Listener.getPostTick().handleValue(new Event<>(null, false, false));

    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V", shift = At.Shift.BEFORE))
    public void onPreInputEvent(CallbackInfo ci){
        Listener.getPreHandleInput().handleValue(new Event<>(null, false, false));
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V", shift = At.Shift.AFTER))
    public void onPostInputEvent(CallbackInfo ci){
        Listener.getPostHandleEvent().handleValue(new Event<>(null, false, false));
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;overlay:Lnet/minecraft/client/gui/screen/Overlay;", shift = At.Shift.BEFORE))
    public void onInputEventIfScreenOpen(CallbackInfo ci){
        if(MinecraftClient.getInstance().currentScreen != null || MinecraftClient.getInstance().getOverlay() != null){
            this.profiler.swap("Keybindings");
            handleInputEventWhenScreenOpen();

        }
    }
    @Unique
    private void handleInputEventWhenScreenOpen(){
        //check in game and do the tick
        if(MinecraftClient.getInstance().player != null){
            this.handleBlockBreaking(false );
            if (this.attackCooldown > 0) {
                --this.attackCooldown;
            }
        }
    }


    @Unique
    private static final Config.FlagRef USEINGiTEM_ATTACK = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_SHIELDING);
    //for attack when using shield
    @Redirect(method = "handleInputEvents",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",ordinal = 0))
    public boolean onAllowingPlayerAttackWhenUseItem(ClientPlayerEntity player) {
        boolean flag = player.isUsingItem();
        if(flag&&USEINGiTEM_ATTACK.get()){
            //do attack logic
            boolean bl3 = false;
            //still do attack first
            while(instance.options.attackKey.wasPressed()) {
                bl3 |= this.doAttack();
            }
            //escape pickItemKey
            while(instance.options.pickItemKey.wasPressed()) {
                this.doItemPick();
            }

//            this.handleBlockBreaking(instance.currentScreen == null && !bl3 && instance.options.attackKey.isPressed() && instance.mouse.isCursorLocked());
        }
        return flag;
    }

    @Redirect(method = "handleBlockBreaking",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",ordinal = 0))
    public boolean onAllowingPlayerBreakingWhenUseItem(ClientPlayerEntity player) {
        if(USEINGiTEM_ATTACK.get()){
            return false;
        }else{
            return player.isUsingItem();
        }
    }
    private HitResult cacheHitResult = null;
    @Inject(method = "handleBlockBreaking", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;", ordinal = 0, shift = At.Shift.BEFORE))
    private void onBlockBreak(boolean breaking, CallbackInfo ci){
        Event<HitResult> hitResultEvent = new Event<>(this.crosshairTarget, true, true);
        Listener.getMineBlockAction().handleValue(hitResultEvent);
        if(hitResultEvent.isCancelled() || hitResultEvent.context != crosshairTarget){
            cacheHitResult = crosshairTarget;
            crosshairTarget = hitResultEvent.isCancelled()? null:  hitResultEvent.context;
        }
    }
    @Inject(method = "handleBlockBreaking", at = @At("RETURN"))
    private void onRestoreHitResult(CallbackInfo ci){
        if(cacheHitResult != null){
            this.crosshairTarget = cacheHitResult;
        }
        cacheHitResult = null;
    }

//    @Redirect(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;itemUseCooldown:I"))
//    public void onRewriteItemCooldown1(MinecraftClient instance, int value){
//
//    }
    @Unique
    private static final Config.FlagRef RIDE_USE = Configs.INTERACT_CONFIG.getBoolean(Configs.INTERACT_WHEN_RIDING);
    @Redirect(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onAllowRidingUse(ClientPlayerEntity instance){
        //inject the cooldown, before the riding call
        Event<Integer> event = new Event<>(null, true, true);
        Listener.getUseItemCooldownReset().handleValue(event);
        if(event.isCancelled()){
            this.itemUseCooldown =0;
        }else if(event.context() != null){
            this.itemUseCooldown = event.context();
        }

        if(RIDE_USE.get()){
            return false;
        }
        return instance.isRiding();
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;", shift = At.Shift.BEFORE), cancellable = true)
    private void doItemUseEvent(CallbackInfo ci, @Local Hand hand){
        if(!Listener.getTriggerRightClick().isEmpty()){
            Event<Hand> useWithHandEvent = new Event<>(hand, true, false);
            Listener.getTriggerRightClick().handleValue(useWithHandEvent);
            if(useWithHandEvent.isCancelled()){
                ci.cancel();
            }
        }
    }


    @Shadow
    protected abstract void handleBlockBreaking(boolean b) ;

    @Shadow
    protected abstract void doItemPick();

    @Shadow
    protected abstract boolean doAttack();

    @Shadow @Nullable public Screen currentScreen;

    @Shadow @Final public GameRenderer gameRenderer;

    @Shadow protected abstract void render(boolean tick);

    @Shadow public int attackCooldown;

    @Shadow public abstract Window getWindow();

    @Override
    public ClientAccess clone() {
        try {
            ClientAccess clone = (ClientMixin) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    public void onDisconnectListen(Screen disconnectionScreen, boolean transferring, CallbackInfo ci){
        //origin exit
        if(!transferring)
            Listener.getServerDisconnectPoint().handleValue(null);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"))
    private void onTrySleepGameRender(GameRenderer renderer, RenderTickCounter counter, boolean z){
        if(RenderTasks.isScreenSleeping()){
            if(RenderTasks.sleepingRenderTick()){
                return;
            }
        }
        renderer.render(counter, z);

    }
    @Unique
    private static final Config.FlagRef debugHudEnhance = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_ENHANCED_DEBUG_HUD);

    @Inject(method = "hasReducedDebugInfo", at = @At("HEAD"), cancellable = true)
    private void onEnhanceDebug(CallbackInfoReturnable<Boolean> cir){
        if(debugHudEnhance.get()){
            cir.setReturnValue(false);
        }
    }

//    @Inject(method = "startIntegratedServer",at = @At("HEAD"))
//    public void onStartIntegratedServer(LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, boolean newWorld, CallbackInfo ci) {
//        Debug.info("Debug: start integrated server");
//    }

}
