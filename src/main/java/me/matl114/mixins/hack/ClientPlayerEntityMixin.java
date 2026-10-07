package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import java.util.Objects;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.hacks.*;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.extra.BadPacketsFix;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.*;
import me.matl114.hacks.modules.render.NoRender;
import me.matl114.utils.entity.PlayerInputUtils;
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
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity implements ClientPlayerAccess {
    @Unique
    private boolean forceNoFall;

    public boolean isForceNoFall() {
        return forceNoFall;
    }

    public void setForceNoFall(boolean fall) {
        this.forceNoFall = fall;
    }

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
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
    public abstract boolean isSneaking();

    @Shadow
    public abstract void swingHand(Hand hand);

    @Shadow
    public abstract boolean isUsingItem();

    @Shadow
    private boolean usingItem;

    @Shadow
    public abstract void init();

    @Shadow
    private boolean inSneakingPose;

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

    @Override
    public float getEffectFadeFactor(RegistryEntry<StatusEffect> effect, float tickProgress) {
        if (NoRender.INSTANCE.noNausea() && Objects.equals(effect, StatusEffects.NAUSEA)) {
            return 0.0F;
        }
        if ((NoRender.INSTANCE.noDarkNess() && Objects.equals(effect, StatusEffects.DARKNESS))
                || (NoRender.INSTANCE.noBlindness() && Objects.equals(effect, StatusEffects.BLINDNESS))) {
            return 0.0F;
        }
        return super.getEffectFadeFactor(effect, tickProgress);
    }

    @Inject(method = "closeHandledScreen", at = @At(value = "HEAD"), cancellable = true)
    public void closeHandledScreen(CallbackInfo ci) {
        if (!this.forceCloseInv && InvExtra.INSTANCE.enableKeepInv.get()) {
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
    public double getAttributeValue(RegistryEntry<EntityAttribute> attribute) {
        if (attribute == EntityAttributes.MOVEMENT_SPEED
                && MovTasks.getFlight().overrideWalkSpeed.get()) {
            return MovTasks.getFlight().getOverridingWalkSpeed();
        }
        return super.getAttributeValue(attribute);
    }

    @ModifyExpressionValue(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean noSlowUsingItem(boolean original) {
        if (NoSlowDown.INSTANCE.workNoSlowItemThisTick) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "isBlockedFromSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean noSlowUsingItemDoNotBlockSprint(boolean original) {
        if (NoSlowDown.INSTANCE.workNoSlowItemThisTick) {
            return false;
        }
        return original;
    }

    @WrapOperation(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;canStartSprinting()Z"))
    private boolean canStartSprintAddConditionWrapperBecauseOfViaFabricShit(
            ClientPlayerEntity instance, Operation<Boolean> original) {
        if (DisablerManager.INSTANCE.duplicatedSprintFix.get()
                && DisablerManager.INSTANCE.hasAnySprintPacketAlready
                && !PlayerStateManager.INSTANCE.lastSprint) {
            return false;
        }
        // fix viafabric
        boolean v = usingItem;
        if (NoSlowDown.INSTANCE.workNoSlowItemThisTick) {
            usingItem = false;
        }
        PlayerInputUtils.Input playerInput = PlayerInputUtils.of(this.input);
        Vec2f movementVector = this.input.movementVector;
        if (shouldDirectionalSprint()) {
            playerInput
                    .clone()
                    .forward(true)
                    .backward(false)
                    .applyInput(this.input)
                    .syncMovementVector(this.input);
        }
        try {
            return original.call(instance);
        } finally {
            usingItem = v;
            playerInput.applyInput(this.input);
            this.input.movementVector = movementVector;
        }
    }

    @WrapOperation(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldStopSprinting()Z"))
    private boolean canStopSprintAddConditionWrapperBecauseOfViaFabricShit(
            ClientPlayerEntity instance, Operation<Boolean> original) {
        // fix viafabric
        boolean v = usingItem;
        if (NoSlowDown.INSTANCE.workNoSlowItemThisTick) {
            usingItem = false;
        }
        PlayerInputUtils.Input playerInput = PlayerInputUtils.of(this.input);
        Vec2f movementVector = this.input.movementVector;
        if (shouldDirectionalSprint()) {
            playerInput
                    .clone()
                    .forward(true)
                    .backward(false)
                    .applyInput(this.input)
                    .syncMovementVector(this.input);
        }
        try {
            return original.call(instance);
        } finally {
            usingItem = v;
            playerInput.applyInput(this.input);
            this.input.movementVector = movementVector;
        }
    }

    @WrapOperation(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldStopSwimSprinting()Z"))
    private boolean nnoSlowUsingItemDoNotBlockSprint3(ClientPlayerEntity instance, Operation<Boolean> original) {
        // fix viafabric
        if (NoSlowDown.INSTANCE.workNoSlowItemThisTick) {
            boolean v = usingItem;
            usingItem = false;
            try {
                return original.call(instance);
            } finally {
                usingItem = v;
            }
        }
        return original.call(instance);
    }

    @ModifyExpressionValue(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldSlowDown()Z"))
    private boolean noSlowSneak(boolean original) {
        if (NoSlowDown.INSTANCE.shouldNoSlowSneak()) {
            return false;
        }
        return original;
    }

    @Override
    protected float getVelocityMultiplier() {
        if (NoSlowDown.INSTANCE.blockSlow.get()) {
            return 1.0f;
        }
        return super.getVelocityMultiplier();
    }

    @Inject(method = "getPermissions", at = @At("HEAD"), cancellable = true)
    protected void grantAllClientPermissions(CallbackInfoReturnable<PermissionPredicate> cir) {
        cir.setReturnValue(PermissionPredicate.ALL);
    }

    @WrapOperation(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;jump()V",
                            ordinal = 0))
    public void onCancelJumpAfterToggle(ClientPlayerEntity instance, Operation<Void> original) {}

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
        Sprint sprintModule = Sprint.INSTANCE;
        return sprintModule.directionalSprint.get()
                && (input.playerInput.backward() && !input.playerInput.forward())
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

    @ModifyExpressionValue(
            method = "shouldStopSwimSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean allDirectionSprint4(boolean original) {
        if (shouldDirectionalSprint()) {
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "tickNausea",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;keepOpenThroughPortal()Z"))
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
                && this.getEntityWorld().isClient()
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
        if (Velocity.INSTANCE.noBlock.get()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
                            ordinal = 0))
    private void captureInputPacketSendToAvoidIdiotViaFabricPlus(
            ClientPlayNetworkHandler instance, Packet packet, Operation<Void> original) {
        original.call(instance, packet);
        if (packet instanceof PlayerInputC2SPacket inputShit) {
            Event<PlayerInputC2SPacket> fakeEvent = new Event<>(inputShit, true, true);
            PlayerStateManager.INSTANCE.onPlayerInput(fakeEvent);
            BadPacketsFix.INSTANCE.onSendInput(fakeEvent);
        }
    }
}
