package me.matl114.gui.complex.config;

import java.util.List;
import java.util.function.BooleanSupplier;
import me.matl114.gui.Constants;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.MapRef;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

public class ConfigMultiLevelList extends DynamicListWidget {
    private static final int DEFAULT_INDENT_WIDTH = 20;

    private final ModulePath path;
    private final WidgetUtils.ConfigScreenLayout layout;
    private final ElementFactory factory;

    public ConfigMultiLevelList(ModulePath path, WidgetUtils.ConfigScreenLayout layout, ElementFactory factory) {
        this(path, layout, factory, true, DEFAULT_INDENT_WIDTH);
    }

    public ConfigMultiLevelList(
            ModulePath path,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory,
            boolean initiallyExpanded,
            int indentWidth) {
        super(0, 0, Math.max(1, layout.totalWidth()));
        this.path = path;
        this.layout = layout;
        this.factory = factory;
        build(indentWidth, initiallyExpanded);
    }

    public static DrawableWidget create(
            ModulePath path, WidgetUtils.ConfigScreenLayout layout, ElementFactory factory) {
        Ref<?> ref = resolve(path);
        if (!(ref instanceof MapRef)) {
            if (ref == null) {
                throw new IllegalArgumentException("Config path does not exist: " + path.asString());
            }
            return WidgetUtils.createKeyValueWidget(ref, path.asString(), layout, factory);
        }
        return new ConfigMultiLevelList(path, layout, factory);
    }

    private static Ref<?> resolve(ModulePath path) {
        String[] splits = path.toPath();
        Config config = path.getConfig();
        return splits.length == 0 ? config.asRef() : config.get(splits);
    }

    private void build(int indentWidth, boolean initiallyExpanded) {
        Ref<?> ref = resolve(path);
        if (!(ref instanceof MapRef mapRef)) {
            return;
        }
        addMapEntries(path, mapRef, indentWidth, initiallyExpanded, () -> true);
    }

    private void addMapEntries(
            ModulePath currentPath,
            MapRef mapRef,
            int indentWidth,
            boolean initiallyExpanded,
            BooleanSupplier visible) {
        for (String key : mapRef.getKeys()) {
            ModulePath childPath = currentPath.add(key);
            Ref<?> childRef = resolve(childPath);
            if (childRef instanceof MapRef childMap) {
                ValueAccessor<Boolean> expanded = ValueAccessor.holder(initiallyExpanded);
                addGroupHeader(childPath, indentWidth, visible, expanded);
                BooleanSupplier childrenVisible = () -> visible.getAsBoolean() && expanded.getValue();
                addMapEntries(childPath, childMap, indentWidth, initiallyExpanded, childrenVisible);
            } else if (childRef != null) {
                int depth = childPath.toPath().length - 1;
                WidgetUtils.ConfigScreenLayout childLayout = layoutForDepth(indentWidth, depth);
                addVisibleWidget(
                        indentWidget(
                                WidgetUtils.createKeyValueWidget(childRef, childPath.asString(), childLayout, factory),
                                depth * indentWidth),
                        visible);
            }
        }
    }

    private void addGroupHeader(
            ModulePath groupPath, int indentWidth, BooleanSupplier visible, ValueAccessor<Boolean> expanded) {
        int depth = groupPath.toPath().length - 1;
        int indent = Math.min(getWidth(), depth * indentWidth);
        int groupWidth = Math.max(1, getWidth() - indent);
        int headerHeight = 12;
        DrawableWidget header = factory.createLabel(
                () -> {
                    String configKey = groupPath.getConfig().getTranslationKey();
                    String configPath =
                            groupPath.getConfig().getRegistryKey().getValue().getPath();
                    return Text.translatable(
                            configKey.substring(0, configKey.length() - configPath.length()) + groupPath.asString());
                },
                List::of,
                0,
                2,
                Math.max(1, groupWidth - headerHeight),
                headerHeight,
                true);
        SubScreenWidget headerSubScreen = new SubScreenWidget(indent, 0, groupWidth, headerHeight + 2);
        headerSubScreen.addDrawableChild(ExecutableWidget.instance(0, 0, groupWidth, headerHeight + 2)
                .setElementHandler(new AbstractElement()
                        .withInputHandler(InputHandler.clickRun(() -> expanded.setValue(!expanded.getValue())))));
        headerSubScreen.addDrawableChild(header);
        headerSubScreen.addDrawableChild(ExecutableWidget.instance(
                        groupWidth - headerHeight, 2, headerHeight, headerHeight)
                .setRenderHandler(new AbstractElement()
                        .combineRender(factory.createLabel(TextProvider.of(Text.empty()), TooltipHandler.of(List.of())))
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(Colors.WHITE);
                            context.drawGuiTexture(
                                    expanded.getValue()
                                            ? Constants.EXPAND_GUI_ON_SPRITE
                                            : Constants.EXPAND_GUI_OFF_SPRITE,
                                    2,
                                    2,
                                    0,
                                    8,
                                    8);
                            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        })));
        addVisibleWidget(headerSubScreen, visible);
    }

    private void addVisibleWidget(DrawableWidget widget, BooleanSupplier visible) {
        addDrawableChild(new DynamicContentWidget<>(() -> visible.getAsBoolean() ? widget : null, 0, 0));
    }

    private WidgetUtils.ConfigScreenLayout layoutForDepth(int indentWidth, int depth) {
        int availableWidth = Math.max(1, getWidth() - Math.min(getWidth(), depth * indentWidth));
        int minimumWidth = layout.indexWidth() + layout.blankWidth() + 1;
        return layout.forWidth(Math.max(minimumWidth, availableWidth));
    }

    private DrawableWidget indentWidget(DrawableWidget widget, int indent) {
        return new SubScreenWidget(indent, 0, widget.getWidth(), widget.getHeight()).addDrawableChild(widget);
    }
}
