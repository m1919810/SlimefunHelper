package me.matl114.SlimefunMixin.HotkeyMixin;

import me.matl114.HotKeyUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class ScreenButtonMixin extends Screen {
    @Shadow protected int x;

    @Shadow protected int backgroundWidth;

    @Shadow protected int y;

    protected ScreenButtonMixin(Text title) {
        super(title);
        Debug.info("this should not be called");
    }
    private static final int buttonHeight=12;
//    @Inject(method = "<init>",at=@At("RETURN"))
//    public void injectInit(CallbackInfo ci) {
//        Debug.stackTrace();
//    }
    @Inject(method = "init",at=@At("RETURN"))
    public void initButton(CallbackInfo info) {
        if((Screen)this instanceof CreativeInventoryScreen) {
            return;
        }
        HashMap<String,Runnable> buttonTasks=HotKeys.getButtonTaskManager().getTasks();
        HashMap<String,Runnable> buttonToggles= HotKeys.getButtonToggleManager().getToggles();
        int line=0;
        int buttonWidth=(backgroundWidth/4)-1;
        int size1=buttonTasks.size();
        line+=((size1-1)/4)+1;
        int size2=buttonToggles.size();
        line+=((size2-1)/4)+1;
        int y0=-(buttonHeight+2)*line-6;
        int x0=0;
        for(Map.Entry<String ,Runnable> entry:buttonTasks.entrySet()) {
            addDrawableChild(ButtonWidget
                    .builder(Text.literal(entry.getKey()), b ->entry.getValue().run())
                    .dimensions(this.x+x0*(buttonWidth+1) , this.y +y0, buttonWidth, buttonHeight).build());
            x0+=1;
            if(x0==4){
                x0=0;
                y0+=(buttonHeight+2);
            }
        }
        if(x0!=0){
            y0+=(buttonHeight+2);
            x0=0;
        }
        y0+=6;
        for(Map.Entry<String ,Runnable> entry:buttonToggles.entrySet()) {
            addDrawableChild(ButtonWidget
                    .builder(Text.literal(entry.getKey()), b ->entry.getValue().run())
                    .dimensions(this.x+x0*(buttonWidth+1) , this.y +y0, buttonWidth, buttonHeight).build());
            x0+=1;
            if(x0==4){
                x0=0;
                y0+=(buttonHeight+2);
            }
        }

    }
}
