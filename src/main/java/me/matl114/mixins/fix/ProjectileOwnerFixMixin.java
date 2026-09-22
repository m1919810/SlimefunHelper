package me.matl114.mixins.fix;

import java.util.OptionalInt;
import me.matl114.accessors.access.ProjectileAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ProjectileEntity.class)
public abstract class ProjectileOwnerFixMixin extends Entity implements ProjectileAccess {
    @Shadow
    protected Entity owner;

    @Shadow
    public abstract void setOwner(Entity owner);

    @Unique
    OptionalInt ownerEid = OptionalInt.empty();

    public ProjectileOwnerFixMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onSpawnPacket", at = @At("RETURN"))
    private void onSpawnPacket(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        if (packet.getEntityData() == 0) {
            ownerEid = OptionalInt.empty();
        } else {
            ownerEid = OptionalInt.of(packet.getEntityData());
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (ownerEid.isPresent() && this.owner == null) {
            var entity = this.getEntityWorld().getEntityById(this.ownerEid.getAsInt());
            if (entity != null) {
                setOwner(entity);
            }
        }
    }

    @Override
    public OptionalInt getOwnerEid() {
        return ownerEid;
    }
}
