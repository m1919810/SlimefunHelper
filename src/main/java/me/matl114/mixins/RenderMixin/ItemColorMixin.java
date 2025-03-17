package me.matl114.mixins.RenderMixin;

import me.matl114.renders.RenderMain;
import me.matl114.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(ItemColors.class)
public abstract class ItemColorMixin {
    //actually , sodium overrides this render system, so it will be hard for us to inject our code here
//    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
//    public void getRenderColorOverride(ItemStack item, int tintIndex, CallbackInfoReturnable<Integer> cir) {
//        if(RenderMain.shouldStopVanillaColoring(item)){
//           cir.setReturnValue(-1);
//           return;
//        }
//
//    }
    @Unique
    private static int injectGlobalItemColoring(ItemStack item, int tintIndex) {
        if(RenderMain.shouldStopVanillaColoring(item)){
            return -1;
        }
        return -999;
    }

//    @ModifyArg(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/color/item/ItemColors;register(Lnet/minecraft/client/color/item/ItemColorProvider;[Lnet/minecraft/item/ItemConvertible;)V", ordinal = -1), index = 0)
//    private static ItemColorProvider injectColorProvider(ItemColorProvider provider) {
//        Debug.info("check 1");
//        return ;
//    }
    @ModifyArgs(method = "create",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/color/item/ItemColors;register(Lnet/minecraft/client/color/item/ItemColorProvider;[Lnet/minecraft/item/ItemConvertible;)V", ordinal = -1))
    private static void injectColorProvider2(Args args) {
//        Debug.info("check 2");
//        Debug.stackTrace();
//        Debug.info(args.size());
//        Debug.info((Object[]) args.get(1));
        ItemColorProvider provider = args.get(0);
        ItemConvertible[] items = args.get(1);
        ItemColorProvider itemSpecificProvider = RenderMain.getSpecificItemColorProvider(items);
        if(itemSpecificProvider == null){
            args.set(0, (ItemColorProvider)((stack, tintIndex) -> {
                int injectResult = injectGlobalItemColoring(stack, tintIndex);
                if(injectResult != -999)return injectResult;
                return provider.getColor(stack, tintIndex);
            }));
        }else {
            args.set(0, (ItemColorProvider)((stack, tintIndex) -> {
                int injectResult = injectGlobalItemColoring(stack, tintIndex);
                if(injectResult != -999)return injectResult;
                injectResult = itemSpecificProvider.getColor(stack, tintIndex);
                if(injectResult != -999)return injectResult;
                return provider.getColor(stack, tintIndex);
            }));
        }

    }
//    @ModifyArgs(method = "register",at = @At("HEAD"))
//    private void injectColorProvider3(Args args) {
//
//    }
}
