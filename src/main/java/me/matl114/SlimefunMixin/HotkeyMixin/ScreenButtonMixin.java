package me.matl114.SlimefunMixin.HotkeyMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import me.matl114.Access.HandledScreenAccess;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

    protected ScreenButtonMixin(Text title) {
        super(title);
        Debug.info("this should not be called");
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
        sharedArgument=new TextFieldWidget(this.textRenderer,this.x,this.y+y0-buttonHeight-2,buttonWidth*2,buttonHeight,Text.literal(HotKeys.SHARED_ARGUMENT.getInternal()));
        sharedArgument.setMaxLength(256);  // 设置最大输入字符数
        sharedArgument.setEditable(true);
        sharedArgument.setText(HotKeys.SHARED_ARGUMENT.getInternal());
        sharedArgument.setChangedListener(HotKeys.SHARED_ARGUMENT::set);
        addDrawableChild(sharedArgument);
        sharedArgument2=new TextFieldWidget(this.textRenderer,this.x+buttonWidth*2,this.y+y0-buttonHeight-2,buttonWidth*2,buttonHeight,Text.literal(HotKeys.SHARED_ARGUMENT_2.getInternal()));
        sharedArgument2.setMaxLength(256);  // 设置最大输入字符数
        sharedArgument2.setEditable(true);
        sharedArgument2.setText(HotKeys.SHARED_ARGUMENT_2.getInternal());
        sharedArgument2.setChangedListener(HotKeys.SHARED_ARGUMENT_2::set);
        addDrawableChild(sharedArgument2);
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
