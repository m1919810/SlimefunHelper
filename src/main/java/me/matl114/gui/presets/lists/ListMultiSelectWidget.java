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
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
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
    ValueAccessor<Boolean> regexFilterInput;
    boolean showFilter = true;
    boolean modifiable = true;
    protected ElementFactory elementFactory;

    public Set<W> buildSelected() {
        return list.entrySet().stream()
                .filter(i -> i.getValue().get() == Boolean.TRUE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static ListMultiSelectWidget<String> stringCollection(
            List<String> list, Set<String> currentSelection, int x, int y, int dx, int dy, int height) {
        return new ListMultiSelectWidget<String>(
                list,
                currentSelection,
                (str, bl) -> RenderHandler.ofAutoScaleText(Text.translatable(str), Colors.WHITE),
                ValueAccessor.holder(""),
                FilterService.STRING_FILTER,
                x,
                y,
                dx,
                dy,
                height);
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
        this(
                lst,
                currentSelection,
                renderFactory,
                filterInput,
                filter,
                x,
                y,
                dx,
                dy,
                height,
                ValueAccessor.holder(false),
                true,
                DefaultElementFactory.INSTANCE);
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
            int height,
            ValueAccessor<Boolean> regexFilterInput,
            boolean showFilter) {
        this(
                lst,
                currentSelection,
                renderFactory,
                filterInput,
                filter,
                x,
                y,
                dx,
                dy,
                height,
                regexFilterInput,
                showFilter,
                DefaultElementFactory.INSTANCE);
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
            int height,
            ValueAccessor<Boolean> regexFilterInput,
            boolean showFilter,
            ElementFactory elementFactory) {
        super(x, y, dx, dy);
        this.list = new LinkedHashMap<>();
        for (var shit : lst) {
            this.list.put(
                    shit,
                    AttrKeyValue.bool("widget.gui.list-multi-select-widget.selected", currentSelection.contains(shit)));
        }
        this.entryHeight = height;
        this.filter = filter;
        this.filterInput = filterInput;
        this.regexFilterInput = regexFilterInput;
        this.showFilter = showFilter;
        this.renderFactory = renderFactory;
        this.elementFactory = elementFactory;
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
        int buttonSize = Math.min(11, Math.max(1, this.entryHeight - 2));
        int contentX = buttonSize + 2;
        RenderHandler renderHandler = renderFactory.apply(triplet.getKey(), attrKeyValue);
        renderHandler =
                renderHandler.combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                    if (attrKeyValue.get() == Boolean.TRUE) {
                        RenderHandler.drawHighLightBox(context, -contentX, 0, this.dx, this.entryHeight, Colors.WHITE);
                    }
                });
        Runnable toggleSelection = () -> {
            if (modifiable) {
                attrKeyValue.accept(attrKeyValue.get() != Boolean.TRUE);
            }
            // Do not resort when the value changes because the player may click accidentally.
        };
        ValueAccessor<Boolean> selectionValue = ValueAccessor.of(attrKeyValue::get, attrKeyValue::accept);
        DrawableWidget selectionButton = ExecutableWidget.instance(
                        1, (this.entryHeight - buttonSize) / 2, buttonSize, buttonSize)
                .setElementHandler(elementFactory.createToggleButton(
                        () -> Text.empty(),
                        () -> List.of(Text.literal(
                                attrKeyValue.get() == Boolean.TRUE ? "Remove selection" : "Add selection")),
                        selectionValue))
                .setPriority(1);
        DrawableWidget entry = ExecutableWidget.instance(contentX, 0, this.dx - contentX, this.entryHeight)
                .setInputHandler(InputHandler.run(toggleSelection))
                .setRenderHandler(renderHandler);
        return new SubScreenWidget(0, this.entryHeight * index, this.dx, this.entryHeight)
                .addDrawableChild(selectionButton)
                .addDrawableChild(entry);
    }

    public void updateFilterList() {
        Comparator<Map.Entry<W, AttrKeyValue<Boolean>>> comparator = (o1, o2) -> {
            if (o1.getValue().get() && !o2.getValue().get()) {
                return -1;
            } else if (!o1.getValue().get() && o2.getValue().get()) {
                return 1;
            } else {
                return 0;
            }
        };
        filterList = list.entrySet().stream()
                .filter(i -> filter == null
                        || filter.isAccepted(i.getKey(), filterInput.getValue(), regexFilterInput.getValue()))
                .sorted(comparator)
                .toList();

        refreshList();
    }

    public void selectAllShown() {
        this.filterList.forEach(s -> s.getValue().accept(true));
    }

    public void unselectAllShown() {
        this.filterList.forEach(s -> s.getValue().accept(false));
    }

    protected void init() {
        int textHeight = Math.min(20, this.entryHeight);
        if (showFilter) {
            var filterValue = FilterService.createFilterValue(filterInput, this::updateFilterList);
            this.scrollableBorder.addDrawableChild(
                    ExecutableWidget.instance(1, -textHeight + 1, dx - 1 - 4 * textHeight, textHeight - 2)
                            .setElementHandler(elementFactory.createInput(filterValue)));
            this.scrollableBorder.addDrawableChild(elementFactory.createExecuteButton(
                    () -> Text.literal("×"),
                    () -> ChatUtils.parseTooltipsTranslation("widget.gui.filter-service.reset-filter.tooltips", ""),
                    ButtonAction.run(() -> filterValue.setInput("")),
                    dx - 4 * textHeight,
                    -textHeight + 1,
                    textHeight - 2,
                    textHeight - 2));
            ValueAccessor<Boolean> regexAccessor = ValueAccessor.of(regexFilterInput::getValue, value -> {
                regexFilterInput.setValue(value);
                updateFilterList();
            });
            this.scrollableBorder.addDrawableChild(
                    ExecutableWidget.instance(dx - 3 * textHeight, -textHeight + 1, textHeight - 2, textHeight - 2)
                            .setElementHandler(elementFactory.createToggleButton(
                                    () -> Text.literal("(.*)"),
                                    () -> ChatUtils.parseTooltipsTranslation(
                                            "widget.gui.filter-service.use-regex.tooltips", ""),
                                    regexAccessor)));
            SubScreenWidget widget =
                    new SubScreenWidget(dx - 2 * textHeight, -textHeight + 1, 2 * textHeight, textHeight - 2);
            widget.addDrawableChild(elementFactory.createElement(
                    IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(this::selectAllShown))
                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                    "widget.gui.list-multi-select-widget.select-all.tooltips", ""))),
                    null,
                    null,
                    1,
                    0,
                    textHeight - 2,
                    textHeight - 2));
            widget.addDrawableChild(elementFactory.createElement(
                    IconElement.fixedGui(Constants.REMOVE_SPRITE, ButtonAction.run(this::unselectAllShown))
                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                    "widget.gui.list-multi-select-widget.unselect-all.tooltips", ""))),
                    null,
                    null,
                    1 + textHeight,
                    0,
                    textHeight - 2,
                    textHeight - 2));
            this.scrollableBorder.addDrawableChild(widget);
        }
        this.updateFilterList();
    }
}
