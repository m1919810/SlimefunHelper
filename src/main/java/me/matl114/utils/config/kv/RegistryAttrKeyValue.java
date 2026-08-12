package me.matl114.utils.config.kv;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.RegistryChooseScreen;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
        valueChange(null, value);
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
            AttrKeyValue<T> attr, int x, int y, int inputDx, int dy) {
        if (attr instanceof RegistryAttrKeyValue keyValue) {
            return generateTextInputWithRegistrySearch(keyValue.registry, attr, x, y, inputDx, dy);
        } else {
            return BaseAttrKeyValue.generateTextInputValueWidget(attr, x, y, inputDx, dy);
        }
    }

    public static <T, W> DrawableWidget generateTextInputWithRegistrySearch(
            Registry<T> registry, AttrKeyValue<W> attr, int x, int y, int inputDx, int dy) {

        ContentDelegateWidget<TextFieldWidget> interactPlace = McWidgetHelpers.createTextFieldEditBox(
                dy,
                0,
                inputDx - 2 * dy,
                dy,
                attr,
                attr.getValue(),
                McWidgetHelpers.getWrongRedTextBoxColorProvider(attr::isValidate));
        TextFieldWidget widget = interactPlace.getDelegate();
        var icon = RegistryDisplays.getIcon(registry);
        var show = DisplayWidget.instance(0, 0, dy, dy).setRenderHandler(new RenderHandler() {
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
                        String input = attr.getValue();
                        if (Objects.equals(input, "minecraft:default")) {
                            int startIndexX = (element.getTextureHeight() - 16) / 2;
                            int startIndexY = startIndexX;
                            icon.render(startIndexX, startIndexY, context, null);
                        } else {
                            Identifier identifier = Identifier.tryParse(attr.getValue());
                            T value = registry.get(identifier);
                            int startIndexX = (element.getTextureHeight() - 16) / 2;
                            int startIndexY = startIndexX;
                            icon.render(startIndexX, startIndexY, context, value);
                        }
                    } catch (Throwable e) {
                    }
                }
            }
        });
        var select = ExecutableWidget.instance(inputDx - dy, 0, dy, dy)
                .setElementHandler(IconElement.fixedGui(
                                Constants.SEARCH_TEXTURE_SPRITE,
                                ButtonAction.run(() -> openRegistrySearch(registry, widget)))
                        .withTooltips(TooltipHandler.of(Constants.searchRegistryTooltips())));
        return new SubScreenWidget(x, y, inputDx, dy)
                .addDrawableChild(show)
                .addDrawableChild(interactPlace)
                .addDrawableChild(select);
    }

    private static <T> void openRegistrySearch(Registry<T> registry, TextFieldWidget widget) {

        ScreenAccess.of(new RegistryChooseScreen<>(registry, (var) -> {
                    if (var != null) {
                        widget.setText(registry.getId(var).toString());
                    }
                }))
                .openFromCurrent();
    }
}
