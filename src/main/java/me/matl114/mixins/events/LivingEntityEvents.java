package me.matl114.mixins.events;

import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.utils.AttributeUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityEvents extends Entity
        implements EntityAccess<LivingEntity>, LivingEntityAccess<LivingEntity> {

    public LivingEntityEvents(EntityType<?> type, World world) {
        super(type, world);
    }

    @Unique
    private Map<EquipmentSlot, ItemStack> clientLastEquipmentSnapshot;

    @Unique
    public Map<EquipmentSlot, ItemStack> getClientLastEquipmentSnapshot() {
        if (clientLastEquipmentSnapshot == null) {
            Map<EquipmentSlot, ItemStack> clientLastEquipmentSnapshotMap = new EnumMap<>(EquipmentSlot.class);
            for (var re : EquipmentSlot.values()) {
                clientLastEquipmentSnapshotMap.put(re, ItemStack.EMPTY);
            }
            clientLastEquipmentSnapshot = clientLastEquipmentSnapshotMap;
        }
        return clientLastEquipmentSnapshot;
    }

    @Unique
    public void tickEquipment() {
        var map = getClientLastEquipmentSnapshot();
        for (var re : EquipmentSlot.values()) {
            map.put(re, getEquippedStack(re));
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;isRemoved()Z",
                            shift = At.Shift.BEFORE))
    public void onTickEquipment(CallbackInfo ci) {
        if ((Entity) (Object) this instanceof PlayerEntity) {
            tickEquipment();
        }
    }

    @Unique
    Integer nextJumpCooldown;

    @Shadow
    private int jumpingCooldown;

    @Shadow
    protected int fallFlyingTicks;

    @Shadow
    public abstract ItemStack getEquippedStack(EquipmentSlot slot);

    @Shadow
    public abstract boolean areItemsDifferent(ItemStack stack, ItemStack stack2);

    @Shadow
    public abstract AttributeContainer getAttributes();

    @Shadow
    @Final
    private AttributeContainer attributes;

    @WrapOperation(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;jump()V"))
    private void onJump(LivingEntity instance, Operation<Void> original) {
        if ((Entity) this == ((Entity) MinecraftClient.getInstance().player)) {
            // 10 sec
            Event<Integer> jumpEvent = new Event<>(10, true, true);
            Listener.getPlayerNotFlyJumpPoint().handleValue(jumpEvent);
            nextJumpCooldown = jumpEvent.context();
            if (jumpEvent.isCancelled()) {

            } else {
                original.call(instance);
            }
        } else {
            original.call(instance);
        }
    }

    @Inject(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;getWorld()Lnet/minecraft/world/World;",
                            ordinal = 5,
                            shift = At.Shift.BEFORE))
    private void overrideJumpCooldown(CallbackInfo ci) {
        if (nextJumpCooldown != null) {
            jumpingCooldown = nextJumpCooldown;
            nextJumpCooldown = null;
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z",
                            shift = At.Shift.BEFORE))
    private void onWriteFlyingTicks(CallbackInfo ci) {
        if (checkClientPlayer()) {
            Event<Integer> fallFlyingEvent = new Event<>(this.fallFlyingTicks + 1, true, true);
            Listener.getPlayerFallFlyingTick().handleValue(fallFlyingEvent);
            if (fallFlyingEvent.isCancelled()) {
                this.fallFlyingTicks -= 1;
            } else {
                this.fallFlyingTicks = fallFlyingEvent.context() - 1;
            }
        }
    }

    @Unique
    public final void updateEquipmentAttributeChange() {
        getClientLastEquipmentSnapshot();
        Map<EquipmentSlot, ItemStack> map = null;
        EquipmentSlot[] var2 = EquipmentSlot.values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            EquipmentSlot equipmentSlot = var2[var4];
            ItemStack itemStack = clientLastEquipmentSnapshot.get(equipmentSlot);
            ItemStack itemStack2 = this.getEquippedStack(equipmentSlot);
            if (this.areItemsDifferent(itemStack, itemStack2)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentSlot, itemStack2);
                AttributeContainer attributeContainer = this.getAttributes();
                if (!itemStack.isEmpty()) {
                    itemStack.applyAttributeModifiers(equipmentSlot, (attribute, modifier) -> {
                        EntityAttributeInstance entityAttributeInstance =
                                attributeContainer.getCustomInstance(attribute);
                        if (entityAttributeInstance != null) {
                            entityAttributeInstance.removeModifier(modifier);
                        }

                        EnchantmentHelper.removeLocationBasedEffects(
                                itemStack, (LivingEntity) (Entity) this, equipmentSlot);
                    });
                }
            }
        }

        if (map != null) {
            Iterator var9 = map.entrySet().iterator();

            while (var9.hasNext()) {
                Map.Entry<EquipmentSlot, ItemStack> entry = (Map.Entry) var9.next();
                EquipmentSlot equipmentSlot2 = (EquipmentSlot) entry.getKey();
                ItemStack itemStack3 = (ItemStack) entry.getValue();
                if (!itemStack3.isEmpty()) {
                    itemStack3.applyAttributeModifiers(equipmentSlot2, (registryEntry, entityAttributeModifier) -> {
                        EntityAttributeInstance entityAttributeInstance =
                                this.attributes.getCustomInstance(registryEntry);
                        if (entityAttributeInstance != null) {
                            entityAttributeInstance.removeModifier(entityAttributeModifier.id());
                            entityAttributeInstance.addTemporaryModifier(entityAttributeModifier);
                        }
                    });
                }
            }
            tickEquipment();

            if (ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(20, 8)) {
                AttributeUtils.overrideViaAttributes(getClientLastEquipmentSnapshot(), this.getAttributes());
            }
        }

        return;
    }
}
