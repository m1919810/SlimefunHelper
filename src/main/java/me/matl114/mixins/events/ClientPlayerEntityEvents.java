package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import java.util.Objects;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Tasks;
import me.matl114.utils.entity.PlayerInputUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityEvents extends AbstractClientPlayerEntity implements ClientPlayerEntityAccess {
    @Shadow
    private PlayerInput lastPlayerInput;

    @Unique
    private boolean resyncLastInput = false;

    @Shadow
    private double lastXClient;

    @Shadow
    private double lastZClient;

    @Shadow
    private double lastYClient;

    @Shadow
    private float lastPitchClient;

    @Shadow
    private float lastYawClient;

    @Shadow
    public Input input;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    private boolean lastSprinting;

    @Shadow
    private boolean lastOnGround;

    @Shadow
    public abstract void init();

    @Shadow
    private int ticksSinceLastPositionPacketSent;

    public ClientPlayerEntityEvents(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Unique
    public LegalMovementManager movementManager;

    @Unique
    public LegalMovementManager getLegalMovementManager() {
        return this.movementManager;
    }

    @Override
    @Unique
    public void setLastSprintFlag(boolean lastSprint) {
        this.lastSprinting = lastSprint;
    }

    public void setLastSneakFlag(boolean lastSprint) {
        this.lastPlayerInput = new PlayerInput(
                this.lastPlayerInput.forward(),
                this.lastPlayerInput.backward(),
                this.lastPlayerInput.left(),
                this.lastPlayerInput.right(),
                this.lastPlayerInput.jump(),
                lastSprint,
                this.lastPlayerInput.sprint());
    }

    @Unique
    @Override
    public void setLastOnGroundFlag(boolean lastOnGround) {
        this.lastOnGround = lastOnGround;
    }

    public void resyncPos() {
        this.lastXClient = 0;
        this.lastZClient = 0;
        this.lastYClient = 0;
    }

    public void resyncRot() {
        this.lastPitchClient = 0;
        this.lastYawClient = 0;
    }

    public void resyncInput() {
        this.resyncLastInput = true;
    }

    @Unique
    public void resyncMovementPacket() {
        this.ticksSinceLastPositionPacketSent = 100;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onClientPlayerInitConfiguration(
            MinecraftClient client,
            ClientWorld world,
            ClientPlayNetworkHandler networkHandler,
            StatHandler stats,
            ClientRecipeBook recipeBook,
            PlayerInput lastPlayerInput,
            boolean lastSprinting,
            CallbackInfo ci) {
        this.movementManager = new LegalMovementManager();
    }

    @Unique
    private static Vec2f compatMovementVectorWithViaFabric(Vec2f vec2f) {
        // shit,
        return ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 4) ? vec2f : vec2f.normalize();
    }

    @Inject(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    public void onPostInputTick(CallbackInfo ci) {
        if (!checkClientPlayer()) return;
        PlayerInput currentInput = this.input.playerInput;
        if (!Listener.getPlayerKeyboardInputTick().isEmpty()) {
            Listener.getPlayerKeyboardInputTick().handleValue(new Event<>(this.input, false, false));
        }
        getLegalMovementManager().postInputTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
        // changed, update movementVector
        if (!Objects.equals(currentInput, this.input.playerInput)) {
            PlayerInputUtils.Input i0 = new PlayerInputUtils.Input(this.input.playerInput);
            this.input.movementVector =
                    compatMovementVectorWithViaFabric(new Vec2f(i0.sidewaysSpeed(), i0.forwardSpeed()));
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.BEFORE))
    public void prePlayerTick(CallbackInfo ci) {
        if (!checkClientPlayer()) return;
        this.movementManager.preProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
    }

    @Unique
    int lastCancelTick = 0;

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.AFTER),
            order = 100)
    public void onAfterTick(CallbackInfo ci) {
        if (!checkClientPlayer()) return;
        Event<ClientPlayerEntity> event = new Event<>((ClientPlayerEntity) (AbstractClientPlayerEntity) this, true);
        Listener.getClientPlayerSendMovementPoint().handleValue(event);
        if (hasVehicle()) {
            if (!this.movementManager.preInputProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this)
                    || event.isCancelled()) {
                lastCancelTick = Tasks.getTick();
            }
        } else {
            if (!this.movementManager.preMovementProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this)
                    || event.isCancelled()) {
                lastCancelTick = Tasks.getTick();
            }
        }
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasVehicle()Z"))
    private boolean onTick(boolean original) {
        if (Tasks.getTick() == lastCancelTick) {
            // redirect to sendMovementPackets to eat shit
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "sendMovementPackets",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z"))
    private boolean onCancelSendMovementBehaviour(boolean original) {
        if (Tasks.getTick() == lastCancelTick) {
            lastCancelTick = 0;
            return false;
        }
        return original;
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/PlayerInput;equals(Ljava/lang/Object;)Z"))
    private boolean onPlayerInputPackets(PlayerInput instance, Object object, Operation<Boolean> original) {
        if (resyncLastInput) {
            resyncLastInput = false;
            return false;
        }
        return original.call(instance, object);
    }

    @Unique
    public void onPlayerInputPackets() {
        if (!this.lastPlayerInput.equals(this.input.playerInput) || resyncLastInput) {
            resyncLastInput = false;
            this.networkHandler.sendPacket(new PlayerInputC2SPacket(this.input.playerInput));
            this.lastPlayerInput = this.input.playerInput;
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    public void postwrapperPlayerMovementSentTick(CallbackInfo ci) {
        if (!checkClientPlayer()) return;
        onPostPlayerMovementTick((ClientPlayerEntity) (Object) this);
    }

    @Unique
    private void onPostPlayerMovementTick(ClientPlayerEntity player) {
        Listener.getClientPlayerPostSendMovementPoint().broadcast(player);
        movementManager.postProgress(player);
    }

    @Override
    public boolean checkGliding() {
        if (!checkClientPlayer()) return super.checkGliding();
        boolean fallflying = this.isFallFlying();
        boolean shouldSwitch = false;
        if (!fallflying) {
            shouldSwitch = this.canGlide() && !this.isTouchingWater();
        }
        Event<Boolean> switchGliding = new Event<>(shouldSwitch, true, true, fallflying);
        Listener.getPlayerSwitchFallFlying().handleValue(switchGliding);
        boolean switchFlag;
        if (switchGliding.isCancelled()) {
            switchFlag = false;
        } else {
            switchFlag = switchGliding.context();
        }
        if (!fallflying) {
            if (switchFlag) {
                startGliding();
                return true;
            } else {
                return false;
            }
        } else {
            if (switchFlag) {
                stopGliding();
                return true;
            } else {
                return false;
            }
        }
    }

    @ModifyArg(
            method = "sendMovementPackets",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private Packet onSendMovementPackets(Packet par1) {
        if (par1 instanceof PlayerMoveC2SPacketAccess acc) {
            acc.setCause(PlayerMoveC2SPacketAccess.Cause.PLAYER_MOVEMENT);
        }
        return par1;
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (checkClientPlayer()) {
            if (!Listener.getPlayerDropSelectedItem().fireEvent(entireStack)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "closeHandledScreen", at = @At("HEAD"), cancellable = true)
    private void onCloseHandledScreen(CallbackInfo ci) {
        if (checkClientPlayer()) {
            if (!Listener.getPlayerCloseHandledScreen().fireEvent(null)) {
                ci.cancel();
            }
        }
    }
}
