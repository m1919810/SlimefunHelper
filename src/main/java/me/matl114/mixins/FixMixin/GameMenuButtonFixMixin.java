package me.matl114.mixins.FixMixin;

import me.matl114.hackUtils.Tasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameMenuScreen.class)
public abstract class GameMenuButtonFixMixin extends Screen {

    protected GameMenuButtonFixMixin(Text title) {
        super(title);
    }
    @Inject(method = "init", at = @At("RETURN"))
    protected void relocateWidgets(CallbackInfo ci){
        Tasks.relocateGameMenuButtons(this);
    }

}
