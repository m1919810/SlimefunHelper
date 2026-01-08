package me.matl114.mixins.RenderMixin;

import me.matl114.ModConfig;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.renders.implement.SlimefunRender;
import me.matl114.utils.ItemStackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(value = ItemStack.class, priority = 10000)
public abstract class ItemStackMixin {
    private ItemStack cast(){
        return (ItemStack)(Object) this;
    }
    @Unique
    private final Config.FlagRef tooltipsDisplay = Configs.MODEL_CONFIG.getBoolean(Configs.ENABLE_SF_TOOLTIPS);
    @Inject(method = "getTooltip", at = @At(value = "RETURN"))
    public void changeTooltip(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        if(tooltipsDisplay.get()){
            final String id = ItemStackUtils.getSfId(cast());
            if (id == null) {
                return;
            }
            final List<Text> lore = cir.getReturnValue();
            //final Identifier identifier = Registries.ITEM.getId(this.getItem());
            boolean found=false;
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i).getString();
                if (("§9§oMinecraft").equals(line)) {
                    lore.set(i, SlimefunRender.modShow());
                    found=true;
                }
            }
            if(!found){
                lore.add(SlimefunRender.modShow());
            }
            lore.add(Text.literal("粘液物品ID: ").formatted(Formatting.GRAY).append(Text.literal(id).formatted(Formatting.GREEN)));
            SlimefunRender.handleGCEInfo(id,(ItemStack)(Object)this,lore);
            SlimefunRender.handleCLTInfo(id,(ItemStack)(Object)this,lore);
        }
    }
}
