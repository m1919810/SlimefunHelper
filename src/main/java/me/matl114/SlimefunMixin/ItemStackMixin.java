package me.matl114.SlimefunMixin;

import me.matl114.ModConfig;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(value = ItemStack.class, priority = 10000)
public abstract class ItemStackMixin {
    @Shadow @Nullable
    public abstract NbtCompound getNbt();
    @Inject(method = "getTooltip", at = @At(value = "RETURN"))
    public void changeTooltip(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir) {
        if(ModConfig.isEnableToolTipsDisplay()){
            final String id = SlimefunUtils.getSfId(getNbt());
            if (id == null) {
                return;
            }
            final List<Text> lore = cir.getReturnValue();
            //final Identifier identifier = Registries.ITEM.getId(this.getItem());
            boolean found=false;
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i).getString();
                if (("§9§oMinecraft").equals(line)) {
                    lore.set(i, SlimefunUtils.modShow());
                    found=true;
                }
            }
            if(!found){
                lore.add(SlimefunUtils.modShow());
            }
            lore.add(Text.literal("粘液物品ID: ").formatted(Formatting.GRAY).append(Text.literal(id).formatted(Formatting.GREEN)));
            SlimefunUtils.handleGCEInfo(id,(ItemStack)(Object)this,lore);
            SlimefunUtils.handleCLTInfo(id,(ItemStack)(Object)this,lore);
        }
    }
}
