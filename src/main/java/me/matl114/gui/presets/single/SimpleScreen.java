package me.matl114.gui.presets.single;

import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import net.minecraft.text.Text;

public class SimpleScreen extends GenericBackGroundScreen {
    DrawableWidget widget;

    public SimpleScreen(Text title, int backgroundWidth, int backgroundDefaultHeight, DrawableWidget widget) {
        super(title, backgroundWidth, backgroundDefaultHeight);
        this.widget = widget;
    }

    @Override
    protected void init() {
        super.init();

        new ContentDelegateWidget<>(this.x, this.y, 0, 0)
                .setContentDelegate(this.widget)
                .addTo(this);
    }
}
