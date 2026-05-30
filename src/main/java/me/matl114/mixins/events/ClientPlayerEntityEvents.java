package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import java.util.Objects;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityEvents extends AbstractClientPlayerEntity implements ClientPlayerEntityAccess {
    @Shadow
    private double lastX;

    @Shadow
    private double lastZ;

    @Shadow
    private double lastBaseY;

    @Shadow
    private float lastPitch;

    @Shadow
    private float lastYaw;

    @Shadow
    public Input input;

    @Shadow
    public abstract boolean shouldSlowDown();

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

    @Shadow
    private boolean lastSneaking;

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
        this.lastBaseY = 0;
        this.lastZ = 0;
    }

    public void resyncRot() {
        this.lastPitch = 0;
        this.lastYaw = 0;
    }

    public void resyncInput() {
        resyncSneak();
        resyncSprint();
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
            boolean lastSneaking,
            boolean lastSprinting,
            CallbackInfo ci) {
        this.movementManager = new LegalMovementManager();
        this.addTickWrapper(this.movementManager);
        Listener.getPlayerInitConfiguration().broadcast((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
    }

    @Inject(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    public void onPostInputTick(CallbackInfo ci, @Local(ordinal = 0) float f) {
        PlayerInputUtils.Input currentInput = PlayerInputUtils.of(this.input);
        if (!Listener.getPlayerKeyboardInputTick().isEmpty()) {
            Listener.getPlayerKeyboardInputTick().handleValue(new Event<>(this.input, false, false));
        }
        getLegalMovementManager().postInputTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
        // changed, update movementVector
        PlayerInputUtils.Input newInput = PlayerInputUtils.of(this.input);
        if (!Objects.equals(currentInput, newInput)) {
            this.input.movementForward = newInput.forwardSpeed();
            this.input.movementSideways = newInput.sidewaysSpeed();
            if (this.shouldSlowDown()) {
                this.input.movementForward *= f;
                this.input.movementSideways *= f;
            }
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
                onPostPlayerMovementTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
            }
        } else {
            if (!this.movementManager.preMovementProgress((ClientPlayerEntity) (AbstractClientPlayerEntity) this)) {
                ci.cancel();
                onPostPlayerMovementTick((ClientPlayerEntity) (AbstractClientPlayerEntity) this);
            }
        }
    }

    @Unique
    public void onPlayerInputPackets() {}

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

    @Unique
    private boolean checkElytra() {
        ItemStack itemStack = this.getEquippedStack(EquipmentSlot.CHEST);
        if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
            this.startFallFlying();
            return true;
        }
        return false;
    }

    @Override
    public boolean checkFallFlying() {
        boolean fallflying = this.isFallFlying();
        boolean shouldSwitch = false;
        if (!fallflying) {
            shouldSwitch = !this.isOnGround()
                    && !this.isFallFlying()
                    && !this.isTouchingWater()
                    && !this.hasStatusEffect(StatusEffects.LEVITATION)
                    && checkElytra();
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
                startFallFlying();
                return true;
            } else {
                return false;
            }
        } else {
            if (switchFlag) {
                stopFallFlying();
                return true;
            } else {
                return false;
            }
        }
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
    private boolean rewriteElytra1(boolean original) {
        return true;
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/item/ElytraItem;isUsable(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean rewriteElytra2(boolean original) {
        return true;
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
}
