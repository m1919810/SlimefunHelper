package me.matl114.mixins.HotkeyMixin;

import me.matl114.hackUtils.InvTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MultiplayerScreen.class)
public abstract class ServerScreenConfigMixin extends Screen {
    protected ServerScreenConfigMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void init(CallbackInfo ci){
        addDrawableChild(ButtonWidget
            .builder(Text.literal("http settings"), b -> InvTasks.openConfigScreen(Configs.HTTP_CONFIG))
            //.width(70)
            .dimensions(this.width - 205,5 , 100, 20)
            .build());
    }
}
