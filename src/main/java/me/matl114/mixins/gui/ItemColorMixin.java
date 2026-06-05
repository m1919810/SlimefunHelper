package me.matl114.mixins.render;

import me.matl114.hacks.ModelTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(ItemColors.class)
public abstract class ItemColorMixin {
    // actually , sodium overrides this render system, so it will be hard for us to inject our code here
    @ModifyArgs(
            method = "create",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/color/item/ItemColors;register(Lnet/minecraft/client/color/item/ItemColorProvider;[Lnet/minecraft/item/ItemConvertible;)V",
                            ordinal = -1))
    private static void injectColorProvider2(Args args) {
        ItemColorProvider provider = args.get(0);
        args.set(0, (ItemColorProvider) ((stack, tintIndex) -> {
            int injectResult = ModelTasks.getNewStyleModel().shouldEnableNewStyle(stack) ? -1 : -999;
            if (injectResult != -999) return injectResult;
            return provider.getColor(stack, tintIndex);
        }));
    }
}
