package me.matl114.gui.presets.choices;

import java.util.function.Consumer;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.presets.lists.ListRegistrySelectWidget;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RegistryChooseScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    Consumer<T> callback;
    ValueAccessor<String> filterInput;
    protected static final int WIDTH = 240;

    public RegistryChooseScreen(Registry<T> registry, Consumer<T> callback) {
        this(registry, callback, "");
    }

    public RegistryChooseScreen(Registry<T> registry, Consumer<T> callback, String filterInput) {
        super(Text.empty());
        this.registry = registry;
        this.callback = callback;
        setTitleLabel(
                Text.translatable("widget.gui.registry-choose-screen.title").formatted(Formatting.AQUA));
        this.filterInput = ValueAccessor.holder(filterInput);
    }

    protected ListRegistrySelectWidget<T> selectSubScreen;

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return selectSubScreen.getSelectedRegistry() != null;
    }

    @Override
    protected void onConfirmButton() {
        T val = selectSubScreen.getSelectedRegistry();
        if (callback != null) {
            callback.accept(val);
        }
        // move to here
        this.close();
    }

    @Override
    protected void init() {
        super.init();
        var selectSubScreen = ListRegistrySelectWidget.registry(
                this.registry, this.filterInput, 0, CONTENT_START_Y + 20, WIDTH, getContentHeight() - 20, 20);
        this.selectSubScreen = selectSubScreen;
        new ContentDelegateWidget<>(this.x + this.backgroundWidth / 2 - WIDTH / 2, this.y, WIDTH, getContentHeight())
                .setContentDelegate(selectSubScreen)
                .addTo(this);
    }
}
