package me.matl114.hooks.mixin.baritone;

import baritone.behavior.InventoryBehavior;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.modules.move.BaritoneFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(InventoryBehavior.class)
public abstract class InventoryBehaviourMixin {
    @ModifyExpressionValue(
            method = {
                "a(ZLjava/util/function/Predicate;)Z",
                "Lbaritone/behavior/InventoryBehavior;throwaway(ZLjava/util/function/Predicate;)Z"
            },
            at = @At(value = "FIELD", target = "Lbaritone/api/Settings$Setting;value:Ljava/lang/Object;"),
            require = 0)
    private Object modifyInventoryCheck(Object original) {
        if (BaritoneFix.INSTANCE.enableInventoryFireworks.get()) {
            return true;
        }
        return original;
    }
}
