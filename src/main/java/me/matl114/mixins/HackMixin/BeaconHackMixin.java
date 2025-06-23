package me.matl114.mixins.HackMixin;

import me.matl114.access.HandledScreenAccess;
import me.matl114.hackUtils.InvTasks;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.BeaconEffectSelectButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(BeaconScreen.class)
@Environment(EnvType.CLIENT)
public class BeaconHackMixin  extends HandledScreen<BeaconScreenHandler> {
    public BeaconHackMixin(BeaconScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Shadow
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {

    }
    private static final Text TEXT_LEVEL_1 = Text.literal("第一等级: ");
    private static final Text TEXT_LEVEL_2 = Text.literal("第二等级: ");
    @Unique
    private static BeaconEffectSelectButton buttonLevel1 ;
    @Unique
    private static BeaconEffectSelectButton buttonLevel2;
    @Inject(method = "init", at = @At("RETURN"))
    public void onBeaconScreenInit(CallbackInfo ci){
        int scx = this.x; int scy = this.y;
        buttonLevel1 = new BeaconEffectSelectButton(scx + 167 - 23, scy + 47 + 26, 22, 22, TEXT_LEVEL_1);
        addDrawableChild(buttonLevel1);
         buttonLevel2 = new BeaconEffectSelectButton(scx + 167  +1, scy + 47 + 26, 22, 22, TEXT_LEVEL_2);
        addDrawableChild(buttonLevel2);
        AtomicReference<ButtonWidget> buttonTrigger = new AtomicReference<>(
            ButtonWidget.builder(Text.literal("Sent packet"),
                    (b)->{
                        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateBeaconC2SPacket(Optional.ofNullable(buttonLevel1.getCurrentEffect()),Optional.ofNullable(buttonLevel2.getCurrentEffect()) ));
                        Debug.chat(Text.literal("成功发送了信标设置!"));
                    })
                .tooltip(Tooltip.of(Text.literal("点击上方选效果,点此强制修改信标")))
                .dimensions(scx + 167 - 23 , scy + 47 + 48, 46, 10).build()
        );
        addDrawableChild(buttonTrigger.get());
    }

}
