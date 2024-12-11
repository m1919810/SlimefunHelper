package me.matl114.ManageUtils;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SelectScreen extends Screen {
    private HashMap<String,Runnable> selectors;
    private static final int Width=420;
    private static final int buttonHeight=20;
    private int buttonPerLine;
    public SelectScreen(HashMap<String, Runnable> selectors, int buttonPerLine, Text title) {
        super(title);
        this.selectors=selectors;
        this.buttonPerLine=buttonPerLine;
    }
    protected void init() {
        super.init();
        AtomicInteger y=new AtomicInteger(1);
        AtomicInteger x=new AtomicInteger(0);
        int buttonWidth=(width/buttonPerLine)-10;
        selectors.forEach((key, value) -> {
            int x0=(buttonWidth+10)*x.get();
            int y0=buttonHeight*y.get();
            addDrawableChild(ButtonWidget
                    .builder(Text.literal(key), b ->value.run())
                    .dimensions(10+x0 ,y0 , buttonWidth, buttonHeight).build());
            //
            if(x.incrementAndGet()>buttonPerLine){
                x.set(0);
                y.addAndGet(buttonHeight);
            }
        });
    }
}
