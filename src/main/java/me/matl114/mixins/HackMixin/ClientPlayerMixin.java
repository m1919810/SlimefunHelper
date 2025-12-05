package me.matl114.mixins.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.BidirectionalIterator;
import lombok.Getter;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.MovTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LegalMovementManager;
import me.matl114.utils.UtilClass.LinkNode;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
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
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

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

    @Shadow
    private boolean lastOnGround;
    @Shadow
    private int ticksSinceLastPositionPacketSent;

    @Unique
    private boolean forceNoFall;
    public boolean isForceNoFall(){
        return forceNoFall;
    }
    public void setForceNoFall(boolean fall){
        this.forceNoFall = fall;
    }

    public ClientPlayerMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);

    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onClientPlayerInitConfiguration(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler, StatHandler stats, ClientRecipeBook recipeBook, boolean lastSneaking, boolean lastSprinting, CallbackInfo ci){
        this.movementManager = new LegalMovementManager();
        this.addTickWrapper(this.movementManager);
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

    @Shadow public abstract void move(MovementType movementType, Vec3d movement);

    @Shadow protected abstract void sendMovementPackets();

    @Shadow public Input input;

    @Shadow protected abstract boolean canSprint();

    @Shadow private boolean lastSprinting;
    @Shadow private boolean lastSneaking;

    @Shadow public abstract boolean isSneaking();

    @Unique
    @Getter
    public HandledScreen keepedInv=null;
    @Getter
    public ScreenHandler keepedInvHandler=null;
    @Unique boolean forceCloseInv=false;
    @Unique
    public LegalMovementManager movementManager;
    @Unique
    public LegalMovementManager getLegalMovementManager(){
        return this.movementManager;
    }

    @Unique
    public void clearKeepedInventory(boolean closeInv){
        //todo closeInv log
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
    private static final Config.FlagRef noEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT_FORCE);
    @Unique
    private static final Config.FlagRef doForceNoEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT_FORCE);
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
    private static final Config.FlagRef overrdeSpeed = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    @Unique
    private static final Config.DoubleRef speedValue = Configs.MOV_CONFIG.getDouble(Configs.MOVE_SPEED_WALK_VAL);
    @Unique
    public double getAttributeValue(RegistryEntry<EntityAttribute> attribute){
        if(attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED && overrdeSpeed.get()){
            return speedValue.get();
        }
        return super.getAttributeValue(attribute);
    }

    @Unique
    private static final Config.FlagRef noSlowUseItem = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN_USEITEM);
    @Unique
    private static final Config.FlagRef noSlowSneak = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN_SNEAK);
    @Unique
    private static final Config.FlagRef noWithBlockSlow = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW);

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z") )
    public boolean noSlowUsingItem(boolean original){
        if(noSlowUseItem.get()){
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;shouldSlowDown()Z"))
    public boolean noSlowSneak(boolean original){
        if(noSlowSneak.get()){
            return false;
        }
        return original;
    }

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    public void onPostInputTick(CallbackInfo ci){
        if(!Listener.getPlayerKeyboardInputTick().isEmpty()){
            Listener.getPlayerKeyboardInputTick().handleValue(new Event<>(this.input, false, false));
        }
        getLegalMovementManager().postInputTick();
    }

    @Override
    protected float getVelocityMultiplier(){
        if(noWithBlockSlow.get()){
            return 1.0f;
        }
        return super.getVelocityMultiplier();
    }


    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    protected void grantAllClientPermissions(CallbackInfoReturnable<Integer> cir){
        cir.setReturnValue(4);
    }
    @Unique
    private static final Config.DoubleRef reachDistance=Configs.MINE_CONFIG.getDouble(Configs.MINE_FASTBREAK_REACH);

    @Override
    public double getBlockInteractionRange() {
        //todo need fix
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.REACH)){
            return reachDistance.get();
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


    public void resyncSprint(){
        this.lastSprinting = !this.isSprinting();
    }
    public void resyncSneak(){
        this.lastSneaking = !this.isSneaking();
    }

    public void resyncPos(){
        this.lastX =0;
        this.lastZ =0;
        this.lastBaseY = 0;
    }
    public void resyncRot(){
        this.lastPitch = 0;
        this.lastYaw = 0;
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
        Event<ClientPlayerEntity> event = new Event<>((ClientPlayerEntity) (AbstractClientPlayerEntity)this, true);
        Listener.getClientPlayerSendMovementPoint().handleValue(event);
        if(!event.isCancelled()){
            var iter = LinkNode.iterator(headNode);
            while (iter.hasNext()){
                var next = iter.next();
                next.preProgress((ClientPlayerEntity) (Object)this);
            }
            usedIterator = iter;
        }

    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal = 0, shift = At.Shift.BEFORE), cancellable = true)
    public void preVehiclePackets(CallbackInfo ci){
        if(!this.movementManager.preInputProgress((ClientPlayerEntity) (AbstractClientPlayerEntity)this)){
            ci.cancel();
        }
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendMovementPackets()V"), cancellable = true)
    public void preMovementPackets(CallbackInfo ci){
        if(!this.movementManager.preMovementProgress((ClientPlayerEntity) (AbstractClientPlayerEntity)this)){
            ci.cancel();;
        }
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    public void postwrapperPlayerMovementSentTick(CallbackInfo ci){
        var iter = usedIterator;
        usedIterator = null;
        if(iter != null)
            while (iter.hasPrevious()){
                var prev = iter.previous();
                prev.postProgress((ClientPlayerEntity) (Object)this);
                if(!prev.stillWrap((ClientPlayerEntity) (Object)this)){
                    iter.remove();
                }
            }
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;jump()V", ordinal = 0))
    public void onCancelJumpAfterToggle(ClientPlayerEntity instance){
    }
    @Unique
    private static final Config.IntRef moveTimer = Configs.MOV_CONFIG.getInt(Configs.MOVE_TICK_TIMER);
    // multiply movements timer
    //todo: speeding up with more packets, not big speed (timer speedup

    @Override
    public void travel(Vec3d movementInput){
        super.travel(movementInput);
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_SPEED_TIMER)){
            for (int i=0; i < moveTimer.get(); ++i){
                this.sendMovementPackets();
                super.travel(movementInput);
            }
        }
    }

    @Unique
    private static final Config.FlagRef legalDirectional = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_ALL_DIRECTION_SPRINT);

    //for directional sprint

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V", ordinal = 3, shift = At.Shift.AFTER))
    private void allDirectionSprint(CallbackInfo ci){
        //backward
        //fixme : can not auto toggle sprint if current is not sprinting and sprinting button pressed
        if(legalDirectional.get() && !isSprinting() && this.input.movementForward < -0.8F && MovTasks.enableSprintDirectionalThisTick){
            //check ticket
            boolean otherReason = !this.canSprint() || this.horizontalCollision && !this.collidedSoftly || this.isTouchingWater() && !this.isSubmergedInWater();
            if(!otherReason){
                //set sprint true if only because of no movement forward
                setSprinting(true);
            }
        }

    }

    //for directional sprint
    @Inject(method = "isWalking", at = @At("HEAD"), cancellable = true)
    protected void seenWalkingBackAsWalking(CallbackInfoReturnable<Boolean> cir){

        if(legalDirectional.get()){
             if(!this.isSubmergedInWater() && (double)this.input.movementForward <= -0.8F && MovTasks.enableSprintDirectionalThisTick){
                 //check ticket
                 cir.setReturnValue(true);
             }
        }
    }

    private static final Config.FlagRef portalGui = Configs.TEST_CONFIG.getBoolean(Configs.PORTAL_GUI);

    @ModifyExpressionValue(method = "tickNausea", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"))
    private boolean onPortalGui(boolean original){
        if(portalGui.get())return true;
        return original;
    }


    //redirection conflict with viafabricplus
//    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
//    private boolean allDirectionSprint(Input instance){
//        if(legalDirectional.get()){
//            return true;
//        }
//        return instance.hasForwardMovement();
//    }
}
