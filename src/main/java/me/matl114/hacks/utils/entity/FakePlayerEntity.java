package me.matl114.hacks.utils.entity;

import com.mojang.authlib.GameProfile;
import java.util.function.Consumer;
import me.matl114.accessors.events.ClientConnectionAccess;
import me.matl114.utils.DamageUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Vec3d;

public class FakePlayerEntity extends OtherClientPlayerEntity {
    boolean hasPhysics = true;
    Consumer<FakePlayerEntity> tickTask;

    public FakePlayerEntity(ClientWorld clientWorld, GameProfile gameProfile) {
        super(clientWorld, gameProfile);
        setId(-getId());
    }

    public void copyDataFrom(PlayerEntity player) {
        setPosition(player.getPos());
        setWorld(player.getEntityWorld());
        setPitch(player.getPitch());
        setYaw(player.getYaw());
        resetPosition();
    }

    public void copyEquipmentFrom(PlayerEntity player) {
        getInventory().clone(player.getInventory());
    }

    public void setFreeze(boolean freeze) {
        hasPhysics = !freeze;
    }

    public void setTickTask(Consumer<FakePlayerEntity> tickTask) {
        this.tickTask = tickTask;
    }

    public float damage(float rawDamage, DamageSource damageSource) {
        if (this.isInvulnerableTo(damageSource)) {
            return 0.0F;
        }
        DamageUtils.DamageContext context = DamageUtils.fromPlayer(this).build();
        rawDamage = DamageUtils.getDamageAfterDifficulty(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = applyHurtTimeDamage(rawDamage, damageSource);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = DamageUtils.getDamageAfterArmorReduce(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = DamageUtils.getDamageAfterEffectAndProtection(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        DamageUtils.damageOrAbsorption(this, damageSource, rawDamage);
        if (this.isDead()) {
            if (!this.tryUseTotem(damageSource)) {
                this.onDeath(damageSource);
            } else {
                ClientConnectionAccess.of(MinecraftClient.getInstance()
                                .getNetworkHandler()
                                .getConnection())
                        .handlePacket(new EntityStatusS2CPacket(this, EntityStatuses.USE_TOTEM_OF_UNDYING));
            }
        }
        return rawDamage;
    }

    public float applyHurtTimeDamage(float currentVal, DamageSource source) {
        if ((float) this.timeUntilRegen > 10.0F && !source.isIn(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (currentVal <= this.lastDamageTaken) {
                return 0.0F;
            }
            float newAmount = currentVal - this.lastDamageTaken;
            this.lastDamageTaken = currentVal;
            return newAmount;
        } else {
            this.lastDamageTaken = currentVal;
            this.timeUntilRegen = 20;
            this.maxHurtTime = 10;
            this.hurtTime = this.maxHurtTime;
            return currentVal;
        }
    }

    @Override
    public boolean isOnGround() {
        if (hasPhysics) {
            return super.isOnGround();
        } else {
            return true;
        }
    }

    @Override
    public Vec3d getVelocity() {
        if (hasPhysics) {
            return super.getVelocity();
        } else {
            return Vec3d.ZERO;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tickTask != null) {
            tickTask.accept(this);
        }
    }
}
