package me.matl114.mixins.HackMixin;


import me.matl114.access.ClientAccess;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.CombatTasks;
import me.matl114.hackUtils.RenderTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Unique
    public void setCooldown(int cooldown){
        this.itemUseCooldown = cooldown;
    }
    @ModifyArg(method = "handleInputEvents",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",ordinal = 1))
    public Screen onRedirectInventoryKeyPress(Screen screen){
        if(HotKeys.getButtonToggleManager().getState(HotKeys.KEEP_INV)){
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player!=null&&ClientPlayerAccess.of(player).getKeepedInvHandler()!=null&& ClientPlayerAccess.of(player).getKeepedInv()!=null){
                HandledScreen screen1= ClientPlayerAccess.of(player).getKeepedInv();
                player.currentScreenHandler=ClientPlayerAccess.of(player).getKeepedInvHandler();
                ClientPlayerAccess.of(player).clearKeepedInventory(false);
                return screen1;
            }
        }
        return screen;
    }


    @Unique
    private static final AtomicBoolean RIDING_ATTACK= Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_RIDING);
    @Redirect(method = "doAttack",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onEnableRidingAttack(ClientPlayerEntity instance) {

        if(RIDING_ATTACK.get()){
            //always not riding
            return false;
        }
        return instance.isRiding();
    }
    @Inject(method = "doAttack",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",shift = At.Shift.BEFORE))
    public void onAttackWhenMissedEntity(CallbackInfoReturnable<Boolean> cir) {
        if(crosshairTarget!=null&& crosshairTarget.getType()!=HitResult.Type.ENTITY){
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.ALWAYS_ATTACK)){
                CombatTasks.autoAttackBest(false);
            }
        }
    }


    @Inject(method = "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V",shift = At.Shift.BEFORE,ordinal = 1),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onInjectTickTasks(CallbackInfo ci){
        this.profiler.swap("slimefun-helper-tasks");
        if(this.player!=null){
            Tasks.doGameTick(this.player);
        }
        Tasks.doTick();
    }
    @Unique
    private static final AtomicBoolean USEINGiTEM_ATTACK = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_SHIELDING);
    //for attack when using shield
    @Redirect(method = "handleInputEvents",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",ordinal = 0))
    public boolean onAllowingPlayerAttackWhenUseItem(ClientPlayerEntity player) {
        boolean flag = player.isUsingItem();
        if(flag&&USEINGiTEM_ATTACK.get()){
            //do attack logic
            boolean bl3 = false;
            while(instance.options.attackKey.wasPressed()) {
                bl3 |= this.doAttack();
            }
            while(instance.options.pickItemKey.wasPressed()) {
                this.doItemPick();
            }

            this.handleBlockBreaking(instance.currentScreen == null && !bl3 && instance.options.attackKey.isPressed() && instance.mouse.isCursorLocked());
        }
        return flag;
    }
    @Unique
    private static final AtomicInteger USE_ITEM_NO_COOLDOWN = Configs.INTERACT_CONFIG.getInt(Configs.INTERACT_NO_COOLDOWN);
    @Redirect(method = "handleBlockBreaking",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",ordinal = 0))
    public boolean onAllowingPlayerBreakingWhenUseItem(ClientPlayerEntity player) {
        if(USEINGiTEM_ATTACK.get()){
            return false;
        }else{
            return player.isUsingItem();
        }
    }
//    @Redirect(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;itemUseCooldown:I"))
//    public void onRewriteItemCooldown1(MinecraftClient instance, int value){
//
//    }
    @Unique
    private static final AtomicBoolean RIDE_USE = Configs.INTERACT_CONFIG.getBoolean(Configs.INTERACT_WHEN_RIDING);
    @Redirect(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    public boolean onAllowRidingUse(ClientPlayerEntity instance){
        //inject the cooldown, before the riding call
        int val = USE_ITEM_NO_COOLDOWN.get();
        if(val >= 0){
            this.itemUseCooldown = val;
        }
        if(RIDE_USE.get()){
            return false;
        }
        return instance.isRiding();
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


//    @Inject(method = "startIntegratedServer",at = @At("HEAD"))
//    public void onStartIntegratedServer(LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, boolean newWorld, CallbackInfo ci) {
//        Debug.info("Debug: start integrated server");
//    }

}
