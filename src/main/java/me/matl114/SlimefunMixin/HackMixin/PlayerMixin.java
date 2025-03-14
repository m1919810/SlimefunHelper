package me.matl114.SlimefunMixin.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import me.matl114.Access.ClientPlayerAccess;
import me.matl114.HackUtils.MovTasks;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin extends AbstractClientPlayerEntity implements ClientPlayerAccess {

    @Final
    @Shadow
    public ClientPlayNetworkHandler networkHandler;

    public PlayerMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);

    }


    //    @Unique
//    private ScreenHandler keepedInventoryHandler=null;
    @Shadow
    public abstract void closeScreen();


    @Shadow @Final protected MinecraftClient client;

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
    public double getAttributeValue(EntityAttribute attribute){
        if(attribute == EntityAttributes.GENERIC_MOVEMENT_SPEED && overrdeSpeed.get()){
            return speedValue.get();
        }
        return super.getAttributeValue(attribute);
    }
    @Inject(method = "tick",at = @At("HEAD"))
    public void onVelocityUpdateWithModification(CallbackInfo ci){
        MovTasks.onMoving((ClientPlayerEntity)(Object)this);
    }


}
