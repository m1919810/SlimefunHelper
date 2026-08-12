package me.matl114.gui.presets.choices;

import java.util.Set;
import java.util.function.Consumer;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.presets.lists.ListRegistryMultiSelectWidget;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RegistrySelectScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    Consumer<Set<T>> callback;
    protected static final int WIDTH = 240;

    public RegistrySelectScreen(Registry<T> registry, Set<T> currentSelection, Consumer<Set<T>> callback) {
        super(Text.empty());
        this.registry = registry;
        this.callback = callback;
        setTitleLabel(
                Text.translatable("widget.gui.registry-select-screen.title").formatted(Formatting.AQUA));
        this.selectSubScreen = ListRegistryMultiSelectWidget.registry(
                this.registry, currentSelection, ValueAccessor.holder(""), 0, CONTENT_START_Y + 20, WIDTH, 240, 20);
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
    protected void init() {
        super.init();
        this.delegate = new ContentDelegateWidget<>(this.x + this.backgroundWidth / 2 - WIDTH / 2, this.y, WIDTH, 240)
                .setContentDelegate(this.selectSubScreen)
                .addTo(this);
    }
}
