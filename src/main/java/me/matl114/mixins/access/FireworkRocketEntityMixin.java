package me.matl114.mixins.access;

import java.util.OptionalInt;
import me.matl114.accessors.access.FireworkRocketEntityAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin extends Entity implements FireworkRocketEntityAccess {
    @Shadow
    @Final
    private static TrackedData<OptionalInt> SHOOTER_ENTITY_ID;

    @Shadow
    private int life;

    public FireworkRocketEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Unique
    public boolean isFallFlyingAccelerator() {
        return this.dataTracker.get(SHOOTER_ENTITY_ID).isPresent();
    }

    @Unique
    public int getLiveTicks() {
        return this.life;
    }
}
