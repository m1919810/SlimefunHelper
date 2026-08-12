package me.matl114.mixins.interfaces;

import javax.annotation.Nullable;
import me.matl114.accessors.interfaces.EntityInventory;
import me.matl114.hacks.InteractionTasks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler>
        implements EntityInventory<VillagerEntity> {
    @Unique
    VillagerEntity owner;

    public MerchantScreenMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Nullable
    @Override
    @Unique
    public VillagerEntity getOwner() {
        return owner;
    }

    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;<init>(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V",
                            shift = At.Shift.AFTER))
    private void onInit(MerchantScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        owner = (VillagerEntity) InteractionTasks.predictScreenFrom(e -> e instanceof VillagerEntity);
        if (this.handler instanceof EntityInventory.Handler handler1) {
            handler1.sync(this);
        }
    }
}
