package me.matl114.gui.presets.single;

import me.matl114.gui.GenericScreen;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicSubScreenWidget;
import net.minecraft.text.Text;

public class CenterScreen extends GenericScreen {
    DrawableWidget widget;

    public CenterScreen(DrawableWidget widget) {
        super(Text.empty(), 0, 0);
        this.widget = widget;
    }

    @Override
    protected void init0() {
        super.init0();
        this.x = 0;
        this.y = 0;
    }

    @Override
    protected void init() {
        super.init();
        DynamicSubScreenWidget dynamic = WidgetUtils.createCenterScreenWidget(widget, this.width, this.height);
        dynamic.addTo(this);
    }
}
