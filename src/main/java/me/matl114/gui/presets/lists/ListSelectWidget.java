package me.matl114.gui.presets.lists;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.util.Colors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class ListSelectWidget<W> extends ScrollableListWidget {
    protected W selected;
    List<W> list;
    int entryHeight;
    List<W> filterList;
    Function<W, RenderHandler> renderFactory;
    BiPredicate<W, String> filter;
    boolean modifiable = true;
    ValueAccessor<String> filterInput;

    public ListSelectWidget(
            List<W> lst,
            Function<W, RenderHandler> renderFactory,
            ValueAccessor<String> filterInput,
            BiPredicate<W, String> filter,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        super(x, y, dx, dy);
        this.list = lst;
        this.entryHeight = height;
        this.filter = filter;
        this.renderFactory = renderFactory;
        this.filterInput = filterInput;
        init();
    }

    public ListSelectWidget<W> filter(Predicate<W> fil) {
        return filter((s, b) -> fil.test(s));
    }

    public ListSelectWidget<W> filter(BiPredicate<W, String> fil) {
        if (this.filter != fil) {
            this.filter = fil;
            updateFilterList();
        }
        return this;
    }

    protected void refreshList() {
        clearScrollingWidget();
        int size = this.filterList.size();
        boolean matchSelect = false;
        for (var i = 0; i < size; ++i) {
            addScrollingWidget(generateEntry(i));
            if (this.filterList.get(i) == this.selected) {
                matchSelect = true;
            }
        }
        if (!matchSelect) this.selected = null;
    }

    protected DrawableWidget generateEntry(int index) {
        W triplet = this.filterList.get(index);
        ExecutableWidget shitWidget = ExecutableWidget.instance(0, this.entryHeight * index, this.dx, this.entryHeight);
        RenderHandler renderHandler = renderFactory.apply(triplet);
        renderHandler =
                renderHandler.combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                    if (triplet == selected) {
                        RenderHandler.drawHighLightBox(context, 0, 0, this.dx, this.entryHeight, Colors.WHITE);
                    }
                });
        InputHandler mouseHandler = InputHandler.run(() -> {
            if (modifiable) {
                this.selected = triplet;
            }
        });
        shitWidget.setInputHandler(mouseHandler).setRenderHandler(renderHandler);
        return shitWidget;
    }

    public void updateFilterList() {
        String currentInput = this.filterInput.getValue();
        filterList = list.stream().filter((v) -> filter.test(v, currentInput)).toList();
        refreshList();
    }

    protected void init() {
        int textHeight = Math.min(20, this.entryHeight);
        this.scrollableBorder.addDrawableChild(FilterService.createFilter(
                this.filterInput, this::updateFilterList, 1, -textHeight + 1, dx - 2, textHeight - 2));
        this.updateFilterList();
    }
}
