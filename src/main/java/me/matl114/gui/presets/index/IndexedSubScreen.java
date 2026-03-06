package me.matl114.gui.presets.index;

import java.util.*;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.lists.ListUnmodifiableWidget;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;

public abstract class IndexedSubScreen<T, W extends Element & Drawable & Selectable> extends SubScreenWidget {
    protected List<T> list;
    protected ListUnmodifiableWidget selectedList;
    private ContentDelegateWidget<W> displayedList;
    protected int indexDx;
    protected int buttonDy;
    protected T currentSelected = null;

    protected IndexedSubScreen(List<T> list, int x, int y, int dx, int dy, int indexDx, int indexDy) {
        super(x, y, dx, dy);
        this.list = list;
        this.indexDx = indexDx;
        this.buttonDy = indexDy;
        init();
    }

    protected abstract ElementHandler createIndexHandler(T val);

    public abstract void setGlobal(T config);

    protected abstract W createSelectingDisplayWidget(T val);

    public abstract T getGlobal();

    protected void init() {

        ListEntryWidgetController controller = ListEntryWidgetController.immutable(
                list,
                (str) -> ExecutableWidget.instance(0, 0, this.indexDx, this.buttonDy)
                        .setElementHandler(createIndexHandler(str)),
                this.buttonDy,
                this.indexDx);
        this.selectedList = new ListUnmodifiableWidget(controller, 0, 0, this.indexDx + 4, this.dy).addToSub(this);
        this.displayedList =
                new ContentDelegateWidget<>(this.indexDx, 0, this.dx - this.indexDx, this.dy).addToSub(this);
        T selected = getGlobal();
        selectIndexToDisplay(selected, false);
    }

    public void selectIndexToDisplay(T key, boolean force) {
        if (!Objects.equals(currentSelected, key) || force) {
            if (currentSelected != null) {
                saveSelected();
            }
            currentSelected = key;
            this.displayedList.setContentDelegate(key == null ? null : createSelectingDisplayWidget(key));
        }
    }

    public W getDisplaying() {
        return this.displayedList.getDelegate();
    }

    public abstract void saveSelected();
}
