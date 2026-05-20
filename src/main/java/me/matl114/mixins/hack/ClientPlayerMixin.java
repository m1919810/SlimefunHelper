package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.*;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.hacks.modules.move.MoveTimer;
import me.matl114.hacks.modules.move.Sprint;
import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMixin extends AbstractClientPlayerEntity implements ClientPlayerAccess {

    @Final
    @Shadow
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    private double lastX;

    @Shadow
    private double lastBaseY;

    @Shadow
    private double lastZ;

    @Accessor("lastX")
    public abstract double getLastX();

    @Accessor("lastBaseY")
    public abstract double getLastBaseY();

    @Accessor("lastZ")
    public abstract double getLastZ();

    @Accessor("lastOnGround")
    public abstract boolean getLastOnGround();

    @Accessor("lastPitch")
    public abstract float getLastPitch();

    @Accessor("lastYaw")
    public abstract float getLastYaw();

    @Unique
    private boolean forceNoFall;

    public boolean isForceNoFall() {
        return forceNoFall;
    }

    public void setForceNoFall(boolean fall) {
        this.forceNoFall = fall;
    }

    public ClientPlayerMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    //    @Unique
    //    private ScreenHandler keepedInventoryHandler=null;
    @Shadow
    public abstract void closeScreen();

    @Shadow
    @Final
    protected MinecraftClient client;

    @Shadow
    private float lastYaw;

    @Shadow
    private float lastPitch;

    @Shadow
    public abstract void tick();

    @Shadow
    public abstract void move(MovementType movementType, Vec3d movement);

    @Shadow
    protected abstract void sendMovementPackets();

    @Shadow
    public Input input;

    @Shadow
    private boolean lastSprinting;

    @Shadow
    private boolean lastSneaking;

    @Shadow
    public abstract boolean isSneaking();

    @Shadow
    public abstract void swingHand(Hand hand);

    @Shadow public abstract boolean isUsingItem();

    @Shadow private boolean usingItem;
    @Getter
    @Unique
    public HandledScreen keepedInv = null;

    @Getter
    @Unique
    public ScreenHandler keepedInvHandler = null;

    @Unique
    boolean forceCloseInv = false;

    @Unique
    public void clearKeepedInventory(boolean closeInv) {
        // todo closeInv log
        keepedInv = null;
        ScreenHandler handler = keepedInvHandler;
        keepedInvHandler = null;
        if (closeInv) {
            forceCloseInv = true;
            try {
                ((ClientPlayerEntity) (Object) this).closeHandledScreen();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                forceCloseInv = false;
            }
        }
    }

    @ModifyExpressionValue(
            method = "tickNausea",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 0))
    public boolean noNausea(boolean val) {
        if (RenderTasks.getRenderExtra().noNausea.get()) {
            return false;
        }
        return val;
    }

    @Inject(method = "closeHandledScreen", at = @At(value = "HEAD"), cancellable = true)
    public void closeHandledScreen(CallbackInfo ci) {
        if (!this.forceCloseInv && InvTasks.getKeepInv().enable.get()) {
            // do not keep the inventory handler because we can get accessed to it any time
            if (this.client.currentScreen instanceof HandledScreen handled
                    && !(handled.getScreenHandler() instanceof PlayerScreenHandler)
                    && !(handled.getScreenHandler() instanceof CreativeInventoryScreen.CreativeScreenHandler)) {
                keepedInv = handled;
                this.keepedInvHandler = ((ClientPlayerEntity) (Object) this).currentScreenHandler;
                this.closeScreen();
                ci.cancel();
            }
        }
    }

    @Unique
    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        RenderExtra extra = RenderTasks.getRenderExtra();
        if (extra.noEffect.get()
                && extra.noEffectForce.get()
                && (extra.noEffectTypes.get().test(effect.getEffectType()))) {
            return false;
        }

        return super.canHaveStatusEffect(effect);
    }

    @Unique
    public double getAttributeValue(RegistryEntry<EntityAttribute> attribute) {
        if (attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED
                && MovTasks.getCreativeFlight().overrideWalkSpeed.get()) {
            return MovTasks.getCreativeFlight().getOverridingWalkSpeed();
        }
        return super.getAttributeValue(attribute);
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    public boolean noSlowUsingItem(boolean original) {
        if (MovTasks.getNoSlowDown().useItem.get()) {
    private boolean noSlowUsingItem(boolean original) {
        if (MovTasks.getNoSlowDown().shouldNoSlowUseItem()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "isBlockedFromSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean noSlowUsingItemDoNotBlockSprint(boolean original) {
        if (MovTasks.getNoSlowDown().shouldNoSlowUseItem()) {
            return false;
        }
        return original;
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;canStartSprinting()Z"))
    private boolean noSlowUsingItemDoNotBlockSprint1(ClientPlayerEntity instance, Operation<Boolean> original){
        //fix viafabric
        if(MovTasks.getNoSlowDown().shouldNoSlowUseItem()){
            boolean v = usingItem;
            usingItem = false;
            try{
                return original.call(instance);
            }finally {
                usingItem = v;
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldStopSprinting()Z"))
    private boolean noSlowUsingItemDoNotBlockSprint2(ClientPlayerEntity instance, Operation<Boolean> original){
        //fix viafabric
        if(MovTasks.getNoSlowDown().shouldNoSlowUseItem()){
            boolean v = usingItem;
            usingItem = false;
            try{
                return original.call(instance);
            }finally {
                usingItem = v;
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldStopSwimSprinting()Z"))
    private boolean nnoSlowUsingItemDoNotBlockSprint3(ClientPlayerEntity instance, Operation<Boolean> original){
        //fix viafabric
        if(MovTasks.getNoSlowDown().shouldNoSlowUseItem()){
            boolean v = usingItem;
            usingItem = false;
            try{
                return original.call(instance);
            }finally {
                usingItem = v;
            }
        }
        return original.call(instance);
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldSlowDown()Z"))
    private boolean noSlowSneak(boolean original) {
        if (MovTasks.getNoSlowDown().shouldNoSlowSneak()) {
            return false;
        }
        return original;
    }

    @Override
    protected float getVelocityMultiplier() {
        if (MovTasks.getNoSlowDown().blockSlow.get()) {
            return 1.0f;
        }
        return super.getVelocityMultiplier();
    }

    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    protected void grantAllClientPermissions(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(4);
    }

    @Override
    public double getBlockInteractionRange() {
        MineExtra mineExtra = MineTasks.getMineExtra();
        if (true) {
            return mineExtra.getReachDistance();
        }
        return super.getBlockInteractionRange();
    }
    //
    //    @Override
    //    public double getEntityInteractionRange() {
    //
    //        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
    //            return super.getEntityInteractionRange() + 1.0;
    //        }
    //        return super.getEntityInteractionRange();
    //    }

    @Override
    @Unique
    public void setLastSprintFlag(boolean lastSprint) {
        this.lastSprinting = lastSprint;
    }

    public void setLastSneakFlag(boolean lastSprint) {
        this.lastSneaking = lastSprint;
    }

    @Unique
    @Override
    public void setLastOnGroundFlag(boolean lastOnGround) {
        this.lastOnGround = lastOnGround;
    }

    public void resyncPos() {
        this.lastX = 0;
        this.lastZ = 0;
        this.lastBaseY = 0;
    }

    public void resyncRot() {
        this.lastPitch = 0;
        this.lastYaw = 0;
    }

    //    @Unique
    //    public void syncLocationPackets(){
    //        double d = this.getX() - this.lastX;
    //        double e = this.getY() - this.lastBaseY;
    //        double f = this.getZ() - this.lastZ;
    //        double g = (double)(this.getYaw() - this.lastYaw);
    //        double h = (double)(this.getPitch() - this.lastPitch);
    //
    //        boolean bl2 = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) ||
    // this.ticksSinceLastPositionPacketSent > 20;
    //        boolean bl3 = g != 0.0 || h != 0.0;
    //        if (bl2 && bl3) {
    //            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(this.getX(), this.getY(), this.getZ(),
    // this.getYaw(), this.getPitch(), this.isOnGround()));
    //        } else if (bl2) {
    //            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.getX(), this.getY(),
    // this.getZ(), this.isOnGround()));
    //        } else if (bl3) {
    //            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(this.getYaw(), this.getPitch(),
    // this.isOnGround()));
    //        } else if (this.lastOnGround != this.isOnGround()) {
    //            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(this.isOnGround()));
    //        }
    //
    //        if (bl2) {
    //            this.lastX = this.getX();
    //            this.lastBaseY = this.getY();
    //            this.lastZ = this.getZ();
    //            this.ticksSinceLastPositionPacketSent = 0;
    //        }
    //
    //        if (bl3) {
    //            this.lastYaw = this.getYaw();
    //            this.lastPitch = this.getPitch();
    //        }
    //        this.lastOnGround = this.isOnGround();
    //    }

    @Redirect(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;jump()V",
                            ordinal = 0))
    public void onCancelJumpAfterToggle(ClientPlayerEntity instance) {}

    // multiply movements timer
    // todo: speeding up with more packets, not big speed (timer speedup

    @Override
    public void travel(Vec3d movementInput) {
        super.travel(movementInput);
        MoveTimer timer = MovTasks.getMoveTimer();
        if (timer.isActive()) {
            for (int i = 0; i < timer.timer.get(); ++i) {
                this.sendMovementPackets();
                super.travel(movementInput);
            }
        }
    }

    @Unique
    private boolean shouldDirectionalSprint() {
        Sprint sprintModule = MovTasks.getSprint();
        return sprintModule.directionalSprint.get()
                && this.input.movementForward <= -0.8
                && sprintModule.enableSprintDirectionalThisTick;
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean allDirectionSprint3(boolean original) {
        if (shouldDirectionalSprint()) {
            return true;
        }
        return original;
    }

    // for directional sprint
    @Inject(method = "isWalking", at = @At("HEAD"), cancellable = true)
    protected void allDirectionSprint4(CallbackInfoReturnable<Boolean> cir) {
        if (shouldDirectionalSprint()) {
            if (!this.isSubmergedInWater()) {
                // check ticket
                cir.setReturnValue(true);
            }
        }
    }

    @ModifyExpressionValue(
            method = "tickNausea",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"))
    private boolean onPortalGui(boolean original) {
        if (ExtraTasks.getClientExtra().portalGui.get()) return true;
        return original;
    }

    // redirection conflict with viafabricplus
    //    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target =
    // "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    //    private boolean allDirectionSprint(Input instance){
    //        if(legalDirectional.get()){
    //            return true;
    //        }
    //        return instance.hasForwardMovement();
    //    }

    @Unique
    @Override
    public ItemEntity dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        if (!stack.isEmpty()
                && this.getWorld().isClient
                && InvTasks.SUPPRESS_DROPITEM_SPAWN.get()
                && !MinecraftClient.getInstance().isOnThread()) {
            this.swingHand(Hand.MAIN_HAND);
            return null;
        } else {
            return super.dropItem(stack, throwRandomly, retainOwnership);
        }
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    public void onBlockVelocity(double x, double z, CallbackInfo ci) {
        if (MovTasks.getVelocity().noBlock.get()) {
            ci.cancel();
        }
    }
}
