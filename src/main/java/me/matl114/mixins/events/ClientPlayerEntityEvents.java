package me.matl114.mixins.events;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import java.util.Objects;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.utils.collections.LinkNode;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.utils.entity.ProgressWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityEvents extends AbstractClientPlayerEntity implements ClientPlayerEntityAccess {
    @Shadow
    public Input input;

    @Shadow
    private PlayerInput lastPlayerInput;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    public ClientPlayerEntityEvents(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Unique
    public LegalMovementManager movementManager;

    @Unique
    private final LinkNode<ProgressWrapper<ClientPlayerEntity>> headNode = LinkNode.createHead();

    @Unique
    private BidirectionalIterator<ProgressWrapper<ClientPlayerEntity>> usedIterator;

    @Unique
    public LegalMovementManager getLegalMovementManager() {
        return this.movementManager;
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
        this.addTickWrapper(this.movementManager);
        Listener.getPlayerInitConfiguration().broadcast((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
    }

    @Inject(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    public void onPostInputTick(CallbackInfo ci) {
        PlayerInput currentInput = this.input.playerInput;
        if (!Listener.getPlayerKeyboardInputTick().isEmpty()) {
            Listener.getPlayerKeyboardInputTick().handleValue(new Event<>(this.input, false, false));
        }
        getLegalMovementManager().postInputTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
        // changed, update movementVector
        if (!Objects.equals(currentInput, this.input.playerInput)) {
            PlayerInputUtils.Input i0 = PlayerInputUtils.of(this.input);
            this.input.movementVector = new Vec2f(i0.sidewaysSpeed(), i0.forwardSpeed()).normalize();
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.AFTER))
    public void prewrappedPlayerMovementSentTick(CallbackInfo ci) {
        Event<ClientPlayerEntity> event = new Event<>((ClientPlayerEntity) (AbstractClientPlayerEntity) this, true);
        Listener.getClientPlayerSendMovementPoint().handleValue(event);
        if (!event.isCancelled()) {
            var iter = LinkNode.iterator(headNode);
            while (iter.hasNext()) {
                var next = iter.next();
                next.preProgress((ClientPlayerEntity) (Object) this);
            }
            usedIterator = iter;
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    public void onAfterTick(CallbackInfo ci) {
        if (hasVehicle()) {
            if (!this.movementManager.preInputProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this)) {
                ci.cancel();
                onPlayerInputPackets();
                onPostPlayerMovementTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
            }
        } else {
            if (!this.movementManager.preMovementProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this)) {
                ci.cancel();
                onPlayerInputPackets();
                onPostPlayerMovementTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
            }
        }
    }

    @Unique
    public void onPlayerInputPackets() {
        if (!this.lastPlayerInput.equals(this.input.playerInput)) {
            this.networkHandler.sendPacket(new PlayerInputC2SPacket(this.input.playerInput));
            this.lastPlayerInput = this.input.playerInput;
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    public void postwrapperPlayerMovementSentTick(CallbackInfo ci) {
        onPostPlayerMovementTick((ClientPlayerEntity) (Object) this);
    }

    @Unique
    private void onPostPlayerMovementTick(ClientPlayerEntity player) {
        var iter = usedIterator;
        usedIterator = null;
        if (iter != null) {
            while (iter.hasPrevious()) {
                var prev = iter.previous();
                prev.postProgress(player);
                if (!prev.stillWrap(player)) {
                    iter.remove();
                }
            }
        }
    }

    @Unique
    public void addMovementPacketWrapper(ProgressWrapper<ClientPlayerEntity> wrapper) {
        headNode.insertAfter(wrapper);
    }

    @Override
    public boolean checkGliding() {
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
}
