package me.matl114.mixins.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import lombok.Getter;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.MovTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.UtilClass.LinkNode;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin extends AbstractClientPlayerEntity implements ClientPlayerAccess {

    @Final
    @Shadow
    public ClientPlayNetworkHandler networkHandler;
    @Shadow
    private double lastX;
    @Shadow
    private double lastBaseY;
    @Shadow
    private double lastZ;
    @Shadow
    private boolean lastOnGround;
    @Shadow
    private int ticksSinceLastPositionPacketSent;

    public PlayerMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);

    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onClientPlayerInitConfiguration(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler, StatHandler stats, ClientRecipeBook recipeBook, boolean lastSneaking, boolean lastSprinting, CallbackInfo ci){
        Listener.getPlayerInitConfiguration().handleValue((ClientPlayerEntity)(AbstractClientPlayerEntity) this);
    }


    //    @Unique
//    private ScreenHandler keepedInventoryHandler=null;
    @Shadow
    public abstract void closeScreen();


    @Shadow @Final protected MinecraftClient client;

    @Shadow private float lastYaw;
    @Shadow private float lastPitch;

    @Shadow public abstract void tick();

    @Unique
    @Getter
    public HandledScreen keepedInv=null;
    @Getter
    public ScreenHandler keepedInvHandler=null;
    @Unique boolean forceCloseInv=false;
    @Unique
    public void clearKeepedInventory(boolean closeInv){
        keepedInv=null;
        ScreenHandler handler=keepedInvHandler;
        keepedInvHandler=null;
        if(closeInv){
            forceCloseInv=true;
            try{
                ((ClientPlayerEntity)(Object)this).closeHandledScreen();
            }catch (Throwable e){
                e.printStackTrace();
            }
            finally {
                forceCloseInv=false;
            }
        }
    }




    @Inject(method="closeHandledScreen",at=@At(value = "HEAD"),cancellable = true)
    public void closeHandledScreen(CallbackInfo ci) {
        if(!this.forceCloseInv&& HotKeys.getButtonToggleManager().getState(HotKeys.KEEP_INV)) {
            if(this.client.currentScreen instanceof HandledScreen handled) {
                keepedInv= handled;
                this.keepedInvHandler=((ClientPlayerEntity)(Object)this).currentScreenHandler;
                this.closeScreen();
                ci.cancel();
            }
        }
    }
    @Unique
    private static final AtomicBoolean noEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT_FORCE);
    @Unique
    private static final AtomicBoolean doForceNoEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT_FORCE);
//    @Unique
//    public boolean hasStatusEffect(StatusEffect effect){
//        Debug.info("hasEffect");
//        if(noEffect.get() && doForceNoEffect.get() && (effect== StatusEffects.BLINDNESS ||effect== StatusEffects.DARKNESS)){
//            Debug.info("no");
//            return false;
//        }
//        return super.hasStatusEffect(effect);
//    }
    @Unique
    public boolean canHaveStatusEffect(StatusEffectInstance effect){

        if(noEffect.get() && doForceNoEffect.get() &&(effect.getEffectType()== StatusEffects.BLINDNESS ||effect.getEffectType()== StatusEffects.DARKNESS) ){
            return false;

        }

        return super.canHaveStatusEffect(effect);
    }
    @Unique
    private static final AtomicBoolean overrdeSpeed = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    @Unique
    private static final AtomicDouble speedValue = Configs.MOV_CONFIG.getDouble(Configs.MOVE_SPEED_WALK_VAL);
    @Unique
    public double getAttributeValue(RegistryEntry<EntityAttribute> attribute){
        if(attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED && overrdeSpeed.get()){
            return speedValue.get();
        }
        return super.getAttributeValue(attribute);
    }
    @Inject(method = "tick",at = @At("HEAD"))
    public void onVelocityUpdateWithModification(CallbackInfo ci){
        MovTasks.onMoving((ClientPlayerEntity)(Object)this);
    }
    @Unique
    private static final AtomicBoolean noSlot = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN);
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z") )
    public boolean noSlowUsingItem(ClientPlayerEntity instance){
        if(noSlot.get()){
            return false;
        }
        return instance.isUsingItem();
    }
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldSlowDown()Z"))
    public boolean noSlowSneak(ClientPlayerEntity instance){
        if(noSlot.get()){
            return false;
        }
        return instance.shouldSlowDown();
    }

    @Override
    protected float getVelocityMultiplier(){
        if(noSlot.get()){
            return 1.0f;
        }
        return super.getVelocityMultiplier();
    }

    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    protected void grantAllClientPermissions(CallbackInfoReturnable<Integer> cir){
        cir.setReturnValue(4);
    }
    @Unique
    private static final AtomicDouble reachDistance=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_REACH);

    @Override
    public double getBlockInteractionRange() {
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            return reachDistance.floatValue();
        }
        return super.getBlockInteractionRange();
    }

    @Override
    public double getEntityInteractionRange() {
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            return reachDistance.floatValue();
        }
        return super.getEntityInteractionRange();
    }
    @Unique
    public void syncPitchYaw(){
        this.lastYaw = getYaw();
        this.lastPitch = getPitch();
    }
    @Unique
    public void syncLocationPackets(){
        double d = this.getX() - this.lastX;
        double e = this.getY() - this.lastBaseY;
        double f = this.getZ() - this.lastZ;
        double g = (double)(this.getYaw() - this.lastYaw);
        double h = (double)(this.getPitch() - this.lastPitch);

        boolean bl2 = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || this.ticksSinceLastPositionPacketSent > 20;
        boolean bl3 = g != 0.0 || h != 0.0;
        if (bl2 && bl3) {
            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch(), this.isOnGround()));
        } else if (bl2) {
            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(this.getX(), this.getY(), this.getZ(), this.isOnGround()));
        } else if (bl3) {
            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(this.getYaw(), this.getPitch(), this.isOnGround()));
        } else if (this.lastOnGround != this.isOnGround()) {
            this.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(this.isOnGround()));
        }

        if (bl2) {
            this.lastX = this.getX();
            this.lastBaseY = this.getY();
            this.lastZ = this.getZ();
            this.ticksSinceLastPositionPacketSent = 0;
        }

        if (bl3) {
            this.lastYaw = this.getYaw();
            this.lastPitch = this.getPitch();
        }
        this.lastOnGround = this.isOnGround();
    }
    @Unique
    public void addMovementPacketWrapper(ProgressWrapper<ClientPlayerEntity> wrapper){
        headNode.insertAfter(wrapper);
    }


    @Unique
    private final LinkNode<ProgressWrapper<ClientPlayerEntity>> headNode = LinkNode.createHead();
    @Unique
    private BidirectionalIterator<ProgressWrapper<ClientPlayerEntity>> usedIterator;
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.AFTER))
    public void prewrappedPlayerMovementSentTick(CallbackInfo ci){
        var iter = LinkNode.iterator(headNode);
        while (iter.hasNext()){
            var next = iter.next();
            next.preProgress((ClientPlayerEntity) (Object)this);
        }
        usedIterator = iter;
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    public void postwrapperPlayerMovementSentTick(CallbackInfo ci){
        var iter = usedIterator;
        usedIterator = null;
        while (iter.hasPrevious()){
            var prev = iter.previous();
            prev.postProgress((ClientPlayerEntity) (Object)this);
            if(!prev.stillWrap((ClientPlayerEntity) (Object)this)){
                iter.remove();
            }
        }
    }

}
