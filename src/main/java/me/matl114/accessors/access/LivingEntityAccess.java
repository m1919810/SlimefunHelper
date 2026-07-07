package me.matl114.accessors.access;

import java.util.Map;
import me.matl114.accessors.events.EntityAccess;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public interface LivingEntityAccess<T extends LivingEntity> extends EntityAccess<T> {
    public void setJumpingCooldown(int cooldown);

    static <T extends LivingEntity> LivingEntityAccess<T> of(T val) {
        return (LivingEntityAccess<T>) val;
    }

    float getJumpUpwardSpeed(float strength);

    public Map<EquipmentSlot, ItemStack> getClientLastEquipmentSnapshot();

    public void tickEquipment();

    public void updateEquipmentAttributeChange();
}
