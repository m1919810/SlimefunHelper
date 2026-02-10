package me.matl114.gui;

import java.util.List;
import java.util.function.Function;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.SubScreenWidget;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;

public class GridSubScreen<W extends Element & Drawable & Selectable> extends SubScreenWidget {
    final int elementDx;
    final int elementDy;
    final int entryAtWidth;
    final int entryAtHeight;
    final int startX;
    final int startY;
    final ContentDelegateWidget<W>[] gridWidgets;

    public GridSubScreen(int x, int y, int dx, int dy, int elementDx, int elementDy) {
        super(x, y, dx, dy);
        this.elementDx = elementDx;
        this.elementDy = elementDy;
        dx -= 2;
        this.entryAtWidth = Math.max(1, (dx) / elementDx);
        this.startX = 1 + (((dx) % elementDx) / 2);
        dy -= 2;
        this.entryAtHeight = Math.max(1, (dy) / elementDy);
        this.startY = 1 + (((dy) % elementDy) / 2);
        this.gridWidgets = new ContentDelegateWidget[entryAtHeight * entryAtWidth];
    }

    public int getEntryPerPage() {
        return this.gridWidgets.length;
    }

    public <R> void refreshPage(List<R> values, Function<R, W> function, int page) {
        int sizeOfEntries = values.size();
        int pageIndex = (page - 1) * (gridWidgets.length);
        for (int y0 = 0; y0 < entryAtHeight; ++y0) {
            for (int x0 = 0; x0 < entryAtWidth; ++x0) {
                int index = y0 * entryAtWidth + x0;
                if (gridWidgets[index] == null) {
                    this.gridWidgets[index] = new ContentDelegateWidget(
                                    startX + elementDx * x0, startY + elementDy * y0, elementDx, elementDy)
                            .addToSub(this);
                }
                ;
                int valueIndex = pageIndex + index;
                if (valueIndex >= sizeOfEntries) {
                    this.gridWidgets[index].setContentDelegate(null);
                } else {
                    this.gridWidgets[index].setContentDelegate(function.apply(values.get(valueIndex)));
                }
            }
        }
    }
}
