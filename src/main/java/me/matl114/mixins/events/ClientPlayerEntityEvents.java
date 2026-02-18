package me.matl114.mixins.events;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.utils.collections.LinkNode;
import me.matl114.utils.entity.LegalMovementManager;
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
import net.minecraft.stat.StatHandler;
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
            boolean lastSneaking,
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
        if (!Listener.getPlayerKeyboardInputTick().isEmpty()) {
            Listener.getPlayerKeyboardInputTick().handleValue(new Event<>(this.input, false, false));
        }
        getLegalMovementManager().postInputTick();
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasVehicle()Z"),
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
}
