package me.matl114.mixins.HotkeyMixin;

import me.matl114.access.HandledScreenAccess;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ButtonElement;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.hackUtils.InvTasks;
import me.matl114.managers.HotKeys;
import me.matl114.SlimefunHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class ScreenButtonMixin extends Screen implements HandledScreenAccess {
    @Shadow protected int x;

    @Shadow protected int backgroundWidth;

    @Shadow protected int y;

    @Shadow protected abstract void init();

    protected ScreenButtonMixin(Text title) {
        super(title);

    }
    private static final int buttonHeight=12;
//    @Inject(method = "<init>",at=@At("RETURN"))
//    public void injectInit(CallbackInfo ci) {
//        Debug.stackTrace();
//    }
    @Unique
    private TextFieldWidget sharedArgument;
    @Unique
    private TextFieldWidget sharedArgument2;
    @Unique
    public void updateSharedArgument(String var1, String var2) {
        if (sharedArgument != null&&var1!=null&&!var1.equals(sharedArgument.getText())) {
            sharedArgument.setText(var1);
        }
        if (sharedArgument2!=null&&var2!=null&&!var2.equals(sharedArgument2.getText())) {
            sharedArgument2.setText(var2);
        }
    }

    @Inject(method = "init",at=@At("RETURN"))
    public void initButton(CallbackInfo info) {
        int xv, yv;
        Screen screen = (Screen) this;
        if(screen instanceof CreativeInventoryScreen handled) {
            xv = this.x;
            yv = InvTasks.resizeCreativeYv(this.y);
        }else {
            xv = this.x;
            yv = this.y;
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
        for(Map.Entry<String ,Runnable> entry:buttonToggles.entrySet()) {
            final String key = entry.getKey();
            final Runnable stateChange = entry.getValue();
            ExecutableWidget widget = ExecutableWidget.instance(xv+x0*(buttonWidth+1) , yv +y0, buttonWidth, buttonHeight)
                .setElementHandler(new ButtonElement(TextProvider.of(Text.literal(key)), ((element, widget1, mouseButton) -> {
                    stateChange.run();
                    widget1.setAlpha(HotKeys.getButtonToggleManager().getState(key)? 1.0f: 0.4f);
                    return true;
                })))
                .addTo(this);
            widget.setAlpha(HotKeys.getButtonToggleManager().getState(key)? 1.0f: 0.4f);
            x0+=1;
            if(x0==4){
                x0=0;
                y0+=(buttonHeight+2);
            }
        }
        //换行
        x0 = 0;
        y0 = buttonHeight+ 2;
        for(Map.Entry<String ,Runnable> entry:buttonTasks.entrySet()) {
            final Runnable task = entry.getValue();
            ExecutableWidget.instance(xv+x0*(buttonWidth+1) , yv -y0, buttonWidth, buttonHeight)
                    .setElementHandler(new ButtonElement(TextProvider.of(Text.literal(entry.getKey())), ButtonAction.run(task)))
                .addTo(this)
            ;
            x0+=1;
            if(x0==4){
                x0=0;
                y0+=(buttonHeight+2);
            }
        }

    }

}
