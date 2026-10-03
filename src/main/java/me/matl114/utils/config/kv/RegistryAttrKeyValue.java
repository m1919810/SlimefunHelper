package me.matl114.utils.config.kv;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.RegistryChooseScreen;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class RegistryAttrKeyValue<T> extends BaseAttrKeyValue<T> {
    @Getter
    Registry<T> registry;

    public RegistryAttrKeyValue(String key, @Nonnull T value, Registry<T> registry) {
        super(key, value, RegistryAttrKeyValue::generateRegistryValueWidget, stringifyFactory(registry));
        this.registry = registry;
    }

    public RegistryAttrKeyValue(String key, String value, Registry<T> registry, @Nullable T origin) {
        super(
                key,
                Optional.ofNullable(origin),
                RegistryAttrKeyValue::generateRegistryValueWidget,
                stringifyFactory(registry));
        this.registry = registry;
        setInput(value);
    }

    public static <T> WrapperFactory<String, T> stringifyFactory(Registry<T> registry) {
        return WrapperFactory.of(
                s -> {
                    Identifier id = Identifier.tryParse(s);
                    var val = registry.getOrEmpty(id);
                    if (val.isPresent()) {
                        return val.get();
                    } else {
                        throw WrapperFactory.PARSE_FAILURE;
                    }
                },
                v -> registry.getId(v).toString());
    }

    public static <T> DrawableWidget generateRegistryValueWidget(
            ElementFactory factory, AttrKeyValue<T> attr, int x, int y, int inputDx, int dy) {
        if (attr instanceof RegistryAttrKeyValue keyValue) {
            return generateTextInputWithRegistrySearch(factory, keyValue.registry, attr, x, y, inputDx, dy);
        } else {
            return BaseAttrKeyValue.generateTextInputValueWidget(factory, attr, x, y, inputDx, dy);
        }
    }

    public static <T, W> DrawableWidget generateTextInputWithRegistrySearch(
            Registry<T> registry, AttrKeyValue<W> attr, int x, int y, int inputDx, int dy) {
        return generateTextInputWithRegistrySearch(
                DefaultElementFactory.INSTANCE, registry, attr, x, y, inputDx, dy);
    }

    public static <T, W> DrawableWidget generateTextInputWithRegistrySearch(
            ElementFactory factory, Registry<T> registry, AttrKeyValue<W> attr, int x, int y, int inputDx, int dy) {
        var icon = RegistryDisplays.getIcon(registry);
        RenderHandler showHandler = new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                if (attr.isValidate()) {
                    try {
                        String input = attr.getInput();
                        if (Objects.equals(input, "minecraft:default")) {
                            int startIndexX = (element.getTextureHeight() - 16) / 2;
                            int startIndexY = startIndexX;
                            icon.render(startIndexX, startIndexY, context, null);
                        } else {
                            Identifier identifier = Identifier.tryParse(attr.getInput());
                            T value = registry.get(identifier);
                            int startIndexX = (element.getTextureHeight() - 16) / 2;
                            int startIndexY = startIndexX;
                            icon.render(startIndexX, startIndexY, context, value);
                        }
                    } catch (Throwable e) {
                    }
                }
            }
        };
        var show = factory.createElement(showHandler, null, null, 0, 0, dy, dy);
        var select = factory.createElement(
                IconElement.fixedGui(
                                Constants.SEARCH_TEXTURE_SPRITE,
                                ButtonAction.run(() -> openRegistrySearch(registry, attr)))
                        .withTooltips(TooltipHandler.of(Constants.searchRegistryTooltips())),
                null,
                null,
                inputDx - dy,
                0,
                dy,
                dy);
        return new SubScreenWidget(x, y, inputDx, dy)
                .addDrawableChild(show)
                .addDrawableChild(factory.createInput(attr, dy, 0, inputDx - 2 * dy, dy))
                .addDrawableChild(select);
    }

    private static <T, W> void openRegistrySearch(Registry<T> registry, AttrKeyValue<W> attr) {
        ScreenAccess.of(new RegistryChooseScreen<>(registry, (var) -> {
                    if (var != null) {
                        attr.setInput(registry.getId(var).toString());
                    }
                }))
                .openFromCurrent();
    }
}
