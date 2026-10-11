package me.matl114.gui.presets.choices;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import me.matl114.gui.ConditionalWidget;
import me.matl114.gui.FilterService;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.ScrollableListWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.utils.config.EntityTypeEntrySet;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

/** Editor for an entity set grouped by the entity types' spawn groups. */
public class EntityTypeEntrySetScreen extends ConfirmingBigScreen {
    private static final int WIDTH = 240;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 12;

    private final Consumer<EntityTypeEntrySet> callback;
    private final ElementFactory factory;
    private final Set<SpawnGroup> groups;
    private final Set<EntityType<?>> additional;
    private final Set<EntityType<?>> excluded;
    private final ValueAccessor<String> filterInput = ValueAccessor.holder("");
    private final ValueAccessor<Boolean> regexFilter = ValueAccessor.holder(false);

    private DynamicListWidget sectionList;
    private ScrollableListWidget sectionViewport;

    public EntityTypeEntrySetScreen(EntityTypeEntrySet initial, Consumer<EntityTypeEntrySet> callback) {
        this(initial, callback, DefaultElementFactory.INSTANCE);
    }

    public EntityTypeEntrySetScreen(
            EntityTypeEntrySet initial, Consumer<EntityTypeEntrySet> callback, ElementFactory factory) {
        super(Text.literal("Entity type set"));
        this.callback = callback;
        this.factory = factory;
        this.groups = new LinkedHashSet<>(initial.groups());
        this.additional = new LinkedHashSet<>(initial.additional());
        this.excluded = new LinkedHashSet<>(initial.excluded());
    }

    @Override
    protected boolean canConfirm(me.matl114.gui.basic.ElementHandler elementHandler) {
        return true;
    }

    @Override
    protected void onConfirmButton() {
        if (callback != null) {
            callback.accept(new EntityTypeEntrySet(groups, additional, excluded));
        }
        close();
    }

    @Override
    protected void init() {
        super.init();

        sectionList = new DynamicListWidget(0, 0, WIDTH);
        for (SpawnGroup group : SpawnGroup.values()) {
            List<EntityType<?>> entityTypes = Registries.ENTITY_TYPE.stream()
                    .filter(entityType -> entityType.getSpawnGroup() == group)
                    .toList();
            if (!entityTypes.isEmpty()) {
                addGroupSection(group, entityTypes);
            }
        }

        int viewportHeight = Math.max(1, getContentHeight() - 26);
        sectionViewport = new ScrollableListWidget(0, 4, WIDTH, viewportHeight);
        sectionViewport.addScrollingWidget(sectionList);

        DynamicListWidget content = new DynamicListWidget(0, 0, WIDTH);
        content.addDrawableChild(
                FilterService.createFilterWithRegex(filterInput, regexFilter, this::refreshSections, 0, 0, WIDTH, 20));
        content.addDrawableChild(sectionViewport);
        new ContentDelegateWidget<>(
                        this.x + this.backgroundWidth / 2 - WIDTH / 2,
                        this.y + CONTENT_START_Y,
                        WIDTH,
                        getContentHeight())
                .setContentDelegate(content)
                .addTo(this);
    }

    private void addGroupSection(SpawnGroup group, List<EntityType<?>> entityTypes) {
        DynamicListWidget entityList = new DynamicListWidget(0, 0, WIDTH);
        for (EntityType<?> entityType : entityTypes) {
            DrawableWidget row = createEntityRow(group, entityType);
            entityList.addDrawableChild(new DynamicContentWidget<>(() -> matches(entityType) ? row : null, 0, 0));
        }
        SubScreenWidget headerSub = new SubScreenWidget(0, 0, WIDTH - HEADER_HEIGHT, HEADER_HEIGHT);
        factory.createLabel(
                        () -> Text.literal(group.getName()),
                        List::of,
                        HEADER_HEIGHT + 2,
                        0,
                        WIDTH - 2 * HEADER_HEIGHT,
                        HEADER_HEIGHT,
                        true)
                .addToSub(headerSub);
        createGroupButton(group).addToSub(headerSub);

        WidgetUtils.addCollapsibleGroup(
                sectionList,
                headerSub,
                ValueAccessor.holder(true),
                List.of(new ConditionalWidget(() -> hasMatchingEntity(entityTypes), entityList)),
                factory,
                WIDTH - HEADER_HEIGHT,
                0,
                HEADER_HEIGHT,
                HEADER_HEIGHT);
    }

    private DrawableWidget createGroupButton(SpawnGroup group) {
        return ExecutableWidget.instance(1, 0, HEADER_HEIGHT, HEADER_HEIGHT)
                .setElementHandler(factory.createButton(
                        TextProvider.of(Text.empty()),
                        TooltipHandler.of(List.of()),
                        ButtonAction.run(() -> toggleGroup(group)),
                        () -> groups.contains(group),
                        () -> groups.contains(group)));
    }

    private void toggleGroup(SpawnGroup group) {
        if (!groups.add(group)) {
            groups.remove(group);
        }
    }

    private DrawableWidget createEntityRow(SpawnGroup group, EntityType<?> entityType) {
        int buttonSize = Math.min(11, Math.max(1, ROW_HEIGHT - 2));
        int contentX = buttonSize + 2;
        Identifier identifier = Registries.ENTITY_TYPE.getId(entityType);
        RenderHandler render = RegistryDisplays.of(
                Registries.ENTITY_TYPE, entityType, RegistryDisplays.getDisplay(entityType), identifier);
        render = render.combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            if (isEntitySelected(group, entityType)) {
                RenderHandler.drawHighLightBox(context, -contentX, 0, WIDTH, ROW_HEIGHT, Colors.WHITE);
            }
        });
        DrawableWidget selectionButton = ExecutableWidget.instance(
                        1, (ROW_HEIGHT - buttonSize) / 2, buttonSize, buttonSize)
                .setElementHandler(factory.createButton(
                        TextProvider.of(Text.empty()),
                        TooltipHandler.of(List.of()),
                        ButtonAction.run(() -> toggleEntity(group, entityType)),
                        () -> isEntitySelected(group, entityType),
                        () -> isEntitySelected(group, entityType)))
                .setPriority(1);
        DrawableWidget entry = factory.createElement(
                render,
                null,
                ButtonAction.run(() -> toggleEntity(group, entityType)),
                contentX,
                0,
                WIDTH - contentX,
                ROW_HEIGHT);
        return new SubScreenWidget(0, 0, WIDTH, ROW_HEIGHT)
                .addDrawableChild(selectionButton)
                .addDrawableChild(entry);
    }

    private void toggleEntity(SpawnGroup group, EntityType<?> entityType) {
        if (isEntitySelected(group, entityType)) {
            additional.remove(entityType);
            if (groups.contains(group)) {
                excluded.add(entityType);
            } else {
                excluded.remove(entityType);
            }
        } else {
            excluded.remove(entityType);
            if (!groups.contains(group)) {
                additional.add(entityType);
            } else {
                additional.remove(entityType);
            }
        }
    }

    private boolean isEntitySelected(SpawnGroup group, EntityType<?> entityType) {
        return (groups.contains(group) || additional.contains(entityType)) && !excluded.contains(entityType);
    }

    private boolean hasMatchingEntity(List<EntityType<?>> entityTypes) {
        return entityTypes.stream().anyMatch(this::matches);
    }

    private boolean matches(EntityType<?> entityType) {
        String filter = filterInput.getValue();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Identifier identifier = Registries.ENTITY_TYPE.getId(entityType);
        String id = identifier == null ? entityType.toString() : identifier.toString();
        String path = identifier == null ? id : identifier.getPath();
        String display = RegistryDisplays.getDisplay(entityType).getString();
        if (regexFilter.getValue()) {
            try {
                return Pattern.matches(filter, path) || Pattern.matches(filter, display);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return FilterService.nameMatch(id, filter) || FilterService.nameMatch(display, filter);
    }

    private void refreshSections(String ignored, boolean ignoredRegex) {
        if (sectionViewport != null) {
            sectionViewport.clearScrollingWidget();
            sectionViewport.addScrollingWidget(sectionList);
        }
    }
}
