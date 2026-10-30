package me.matl114.gui.presets.choices;

import java.util.Set;
import java.util.function.Consumer;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.presets.lists.ListRegistryMultiSelectWidget;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public class RegistrySelectScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    Consumer<Set<T>> callback;
    ValueAccessor<String> filterInput;
    ElementFactory elementFactory;
    protected static final int WIDTH = 240;

    public RegistrySelectScreen(Registry<T> registry, Set<T> currentSelection, Consumer<Set<T>> callback) {
        this(registry, currentSelection, callback, DefaultElementFactory.INSTANCE);
    }

    public RegistrySelectScreen(
            Registry<T> registry, Set<T> currentSelection, Consumer<Set<T>> callback, ElementFactory elementFactory) {
        super(Text.empty());
        this.registry = registry;
        this.callback = callback;
        this.elementFactory = elementFactory;
        setTitleLabel(Text.translatable("widget.gui.registry-select-screen.title"));
        this.filterInput = ValueAccessor.holder("");
        this.selectSubScreen = ListRegistryMultiSelectWidget.registry(
                this.registry,
                currentSelection,
                this.filterInput,
                0,
                CONTENT_START_Y + 20,
                WIDTH,
                240,
                20,
                this.elementFactory);
    }

    ListRegistryMultiSelectWidget<T> selectSubScreen;
    ContentDelegateWidget<ListRegistryMultiSelectWidget<T>> delegate;

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return true;
    }

    @Override
    protected void onConfirmButton() {
        Set<T> val = selectSubScreen.getSelectedRegistries();
        if (val != null && callback != null) {
            callback.accept(val);
        }
        // move to here
        this.close();
    }

    @Override
    protected void refreshWidgets() {
        super.refreshWidgets();
        this.selectSubScreen = ListRegistryMultiSelectWidget.registry(
                this.registry,
                this.selectSubScreen.getSelectedRegistries(),
                this.filterInput,
                0,
                CONTENT_START_Y + 20,
                WIDTH,
                getContentHeight() - 20,
                20,
                this.elementFactory);
        this.delegate = new ContentDelegateWidget<>(
                        this.x + this.backgroundWidth / 2 - WIDTH / 2, this.y, WIDTH, getContentHeight())
                .setContentDelegate(this.selectSubScreen)
                .addTo(this);
    }
}
