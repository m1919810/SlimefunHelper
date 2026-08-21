package me.matl114.gui.presets.lists;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.Constants;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.util.Colors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class ListMultiSelectWidget<W> extends ScrollableListWidget {
    Map<W, AttrKeyValue<Boolean>> list;
    int entryHeight;
    List<Map.Entry<W, AttrKeyValue<Boolean>>> filterList;
    BiFunction<W, AttrKeyValue<Boolean>, RenderHandler> renderFactory;
    FilterService.Filter<W> filter;
    ValueAccessor<String> filterInput;
    boolean modifiable = true;
    boolean useRegexFilter = false;

    public Set<W> buildSelected() {
        return list.entrySet().stream()
                .filter(i -> i.getValue().getOriginValue() == Boolean.TRUE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public ListMultiSelectWidget(
            List<W> lst,
            Set<W> currentSelection,
            BiFunction<W, AttrKeyValue<Boolean>, RenderHandler> renderFactory,
            ValueAccessor<String> filterInput,
            FilterService.Filter<W> filter,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        super(x, y, dx, dy);
        this.list = new LinkedHashMap<>();
        for (var shit : lst) {
            this.list.put(
                    shit,
                    AttrKeyValue.bool("widget.gui.list-multi-select-widget.selected", currentSelection.contains(shit)));
            if (currentSelection.contains(shit)) {
                Debug.info("contains", shit);
            }
        }
        this.entryHeight = height;
        this.filter = filter;
        this.filterInput = filterInput;
        this.renderFactory = renderFactory;
        init();
    }

    protected void refreshList() {
        clearScrollingWidget();
        int size = this.filterList.size();
        for (var i = 0; i < size; ++i) {
            addScrollingWidget(generateEntry(i));
        }
    }

    protected DrawableWidget generateEntry(int index) {
        Map.Entry<W, AttrKeyValue<Boolean>> triplet = this.filterList.get(index);
        var attrKeyValue = triplet.getValue();
        ExecutableWidget shitWidget = ExecutableWidget.instance(0, this.entryHeight * index, this.dx, this.entryHeight);
        RenderHandler renderHandler = renderFactory.apply(triplet.getKey(), attrKeyValue);
        renderHandler =
                renderHandler.combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                    if (attrKeyValue.getOriginValue() == Boolean.TRUE) {
                        RenderHandler.drawHighLightBox(context, 0, 0, this.dx, this.entryHeight, Colors.WHITE);
                    }
                });
        InputHandler mouseHandler = InputHandler.run(() -> {
            if (modifiable) {
                if (attrKeyValue.getOriginValue() == Boolean.TRUE) {
                    attrKeyValue.valueChange(null, "false");
                } else {
                    attrKeyValue.valueChange(null, "true");
                }
            }
            // do not resort when value change
            // because player may do it accidentally
        });
        shitWidget.setInputHandler(mouseHandler).setRenderHandler(renderHandler);
        return shitWidget;
    }

    protected void updateFilterList() {
        Comparator<Map.Entry<W, AttrKeyValue<Boolean>>> comparator = (o1, o2) -> {
            if (o1.getValue().getOriginValue() && !o2.getValue().getOriginValue()) {
                return -1;
            } else if (!o1.getValue().getOriginValue() && o2.getValue().getOriginValue()) {
                return 1;
            } else {
                return 0;
            }
        };
        filterList = list.entrySet().stream()
                .filter(i -> filter == null || filter.isAccepted(i.getKey(), filterInput.getValue(), useRegexFilter))
                .sorted(comparator)
                .toList();

        refreshList();
    }

    protected void selectAllShown() {
        this.filterList.forEach(s -> s.getValue().valueChangeInternal(null, true));
    }

    protected void unselectAllShown() {
        this.filterList.forEach(s -> s.getValue().valueChangeInternal(null, false));
    }

    protected void init() {
        int textHeight = Math.min(20, this.entryHeight);
        this.scrollableBorder.addDrawableChild(FilterService.createFilterWithRegex(
                filterInput,
                ValueAccessor.of(() -> this.useRegexFilter, (bl) -> this.useRegexFilter = bl),
                this::updateFilterList,
                1,
                -textHeight + 1,
                dx - 1 - 2 * textHeight,
                textHeight - 2));
        SubScreenWidget widget =
                new SubScreenWidget(dx - 2 * textHeight, -textHeight + 1, 2 * textHeight, textHeight - 2);
        widget.addDrawableChild(ExecutableWidget.instance(1, 0, textHeight - 2, textHeight - 2)
                .setElementHandler(
                        IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(this::selectAllShown))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.list-multi-select-widget.select-all.tooltips", "")))));

        widget.addDrawableChild(ExecutableWidget.instance(1 + textHeight, 0, textHeight - 2, textHeight - 2)
                .setElementHandler(
                        IconElement.fixedGui(Constants.REMOVE_SPRITE, ButtonAction.run(this::unselectAllShown))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.list-multi-select-widget.unselect-all.tooltips", "")))));
        this.scrollableBorder.addDrawableChild(widget);
        this.updateFilterList();
    }
}
