package me.matl114.gui.complex.config;

import java.util.ArrayList;
import java.util.List;
import me.matl114.gui.ConditionalWidget;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.MapRef;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;

public class ConfigMultiLevelList extends DynamicListWidget {
    private static final int INDENT_WIDTH = 12;

    private final ModulePath path;
    private final WidgetUtils.ConfigScreenLayout layout;
    private final ElementFactory factory;

    public ConfigMultiLevelList(
            ModulePath path,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        this(path, layout, factory, 0, false);
    }

    private ConfigMultiLevelList(
            ModulePath path,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory,
            int depth,
            boolean withHeader) {
        super(depth * INDENT_WIDTH, 0, Math.max(1, layout.totalWidth() - depth * INDENT_WIDTH));
        this.path = path;
        this.layout = layout;
        this.factory = factory;
        build(withHeader);
    }

    public static DrawableWidget create(
            ModulePath path,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        Ref<?> ref = resolve(path);
        if (!(ref instanceof MapRef)) {
            if (ref == null) {
                throw new IllegalArgumentException("Config path does not exist: " + path.asString());
            }
            return WidgetUtils.createKeyValueWidget(
                    ref, path.asString(), layout, factory);
        }
        return new ConfigMultiLevelList(path, layout, factory);
    }

    private static Ref<?> resolve(ModulePath path) {
        String[] splits = path.toPath();
        Config config = path.getConfig();
        return splits.length == 0 ? config.asRef() : config.get(splits);
    }

    private void build(boolean withHeader) {
        Ref<?> ref = resolve(path);
        if (!(ref instanceof MapRef mapRef)) {
            return;
        }
        WidgetUtils.ConfigScreenLayout currentLayout = layout.forWidth(getWidth());
        List<ConditionalWidget> children = new ArrayList<>();
        for (String key : mapRef.getKeys()) {
            ModulePath childPath = path.add(key);
            Ref<?> childRef = resolve(childPath);
            if (childRef instanceof MapRef) {
                children.add(new ConditionalWidget(new ConfigMultiLevelList(
                                childPath,
                                layout,
                                factory,
                                path.toPath().length + 1,
                                true)));
            } else if (childRef != null) {
                children.add(new ConditionalWidget(WidgetUtils.createKeyValueWidget(
                                childRef,
                                childPath.asString(),
                                currentLayout,
                                factory)));
            }
        }
        if (withHeader) {
            int headerHeight = 12;
            DrawableWidget header = factory.createLabel(
                    () -> Text.translatable(path.asString()),
                    List::of,
                    0,
                    2,
                    getWidth() - headerHeight,
                    headerHeight,
                    true);
            WidgetUtils.addCollapsibleGroup(
                    this,
                    header,
                    ValueAccessor.holder(true),
                    children,
                    factory,
                    getWidth() - headerHeight,
                    2,
                    headerHeight,
                    headerHeight);
        } else {
            WidgetUtils.addConditionalWidgets(this, children);
        }
    }
}
