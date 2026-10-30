package me.matl114.hacks.utils.gui;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import me.matl114.gui.GenericScreen;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.ChatUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Main screen for the classic tabbed ClickGui layouts. */
public final class ClassicClickGuiScreen extends GenericScreen {
    private final Map<String, Function<Screen, DrawableWidget>> widgets;
    private final ElementFactory elementFactory;
    private DrawableWidget widget;
    private String selecting;
    private ContentDelegateWidget<DrawableWidget> widgetDelegate;

    public ClassicClickGuiScreen(Map<String, Function<Screen, DrawableWidget>> widgets, ElementFactory elementFactory) {
        super(Text.empty(), 0, 0);
        this.widgets = widgets;
        this.elementFactory = elementFactory;
        setGlobal(this.widgets.keySet().iterator().next());
    }

    @Override
    protected void init0() {
        super.init0();
        this.x = 0;
        this.y = 0;
    }

    public static final int BUTTON_HEIGHT = 12;
    public static final int BUTTON_MAX_WIDTH = 60;

    private void setGlobal(String string) {
        if (!Objects.equals(string, selecting)) {
            selecting = string;
            widget = widgets.get(selecting).apply(this);
            if (widgetDelegate != null) {
                widgetDelegate.setContentDelegate(widget);
            }
        }
    }

    @Override
    protected void refreshWidgets() {
        super.refreshWidgets();
        int size = widgets.size();
        int blank;
        int width;
        if (size * BUTTON_MAX_WIDTH > this.width) {
            blank = 0;
            width = this.width / size;
        } else {
            blank = (this.width - size * BUTTON_MAX_WIDTH) / 2;
            width = BUTTON_MAX_WIDTH;
        }
        int cnt = 0;
        for (String entry : widgets.keySet()) {
            String selection = entry;
            ElementHandler element = elementFactory.createButton(
                    TextProvider.of(
                            Text.translatableWithFallback("widget.click-gui.selection." + selection, selection)),
                    TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                            "widget.click-gui.selection." + selection + ".tooltips", "鏆傛棤浠嬬粛")),
                    ButtonAction.run(() -> this.setGlobal(selection)),
                    () -> true,
                    () -> Objects.equals(this.selecting, selection));
            ExecutableWidget.instance(blank + cnt * width, 0, width, BUTTON_HEIGHT)
                    .setElementHandler(element)
                    .addTo(this);
            cnt += 1;
        }
        String currentSelect = selecting;
        selecting = null;
        setGlobal(currentSelect);
        widgetDelegate = new ContentDelegateWidget<>(0, BUTTON_HEIGHT, this.width, this.height - BUTTON_HEIGHT)
                .setContentDelegate(widget)
                .addTo(this);
    }
}
