package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
//to avoid clash with other
@Mixin(value = ItemStack.class, priority = 10000)
public abstract class ItemStackEvents {
    @Inject(method = "getTooltip", at = @At(value = "RETURN"))
    public void onTooltip(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir){
        List<Text> tooltip = cir.getReturnValue();
        Event<List<Text>> event = new Event<>(tooltip, false, false, (ItemStack)(Object) this, type.isAdvanced(), type.isCreative());
        RenderListener.getTooltipShow().handleValue(event);
    }
}
