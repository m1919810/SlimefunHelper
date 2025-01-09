package me.matl114.SlimefunMixin.DebugMixin;

import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BlazeEntity.class)
public abstract class BlazeMixin {
   // @Inject(method = "<init>",at=@At("RETURN"))
    private void onInit(EntityType entityType, World world, CallbackInfo ci) {
        Debug.info("on Client Blaze create");
        Debug.stackTrace();
    }
}
