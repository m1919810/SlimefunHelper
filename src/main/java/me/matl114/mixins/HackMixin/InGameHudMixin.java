package me.matl114.mixins.HackMixin;

import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Environment(EnvType.CLIENT)
@Mixin(value = InGameHud.class, priority = 10)
public abstract class InGameHudMixin {
    @Shadow
    @Final
    private DebugHud debugHud;
    @Unique
    private boolean tmpValue3;
    @Unique
    private boolean tmpValue;
    @Unique
    private boolean tmpValue2;

    @Unique
    private static final Config.FlagRef disableWurstHud = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_REJECT_WURST);

    @Inject(at = @At("HEAD"),
        method = "renderPlayerList(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
    private void rejectWurstHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci){
        if(disableWurstHud.get()){
            this.tmpValue3 = true;
            this.tmpValue2 = MinecraftClient.getInstance().options.hudHidden;
            MinecraftClient.getInstance().options.hudHidden = false;
            if(!debugHud.shouldShowDebugHud()){
                this.tmpValue = true;
                debugHud.toggleDebugHud();
            }else {
                this.tmpValue = false;
            }
        }
    }
    @Inject(method = "renderPlayerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getScoreboard()Lnet/minecraft/scoreboard/Scoreboard;", shift = At.Shift.AFTER))
    private void resetHudData(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci){
        if(tmpValue3){
            tmpValue3 = false;
            MinecraftClient.getInstance().options.hudHidden = this.tmpValue2;
            if(this.tmpValue){
                debugHud.toggleDebugHud();
            }
        }
    }

}
