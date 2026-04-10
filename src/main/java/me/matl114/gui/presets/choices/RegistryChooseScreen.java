package me.matl114.gui.presets.choices;

import java.util.function.Consumer;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.presets.lists.ListRegistrySelectWidget;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RegistryChooseScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    Consumer<T> callback;
    protected static final int WIDTH = 240;

    public RegistryChooseScreen(Registry<T> registry, Consumer<T> callback) {
        this(registry, callback, "");
    }

    public RegistryChooseScreen(Registry<T> registry, Consumer<T> callback, String filterInput) {
        super(Text.empty());
        this.registry = registry;
        this.callback = callback;
        setTitleLabel(Text.literal("从注册表中选择注册项").formatted(Formatting.AQUA));
        // reset user input, so it is more convenient for user to select a registry value,
        FilterService.currentUserInput = filterInput;
        this.selectSubScreen =
                ListRegistrySelectWidget.registry(this.registry, 0, CONTENT_START_Y + 20, WIDTH, 240, 20);
    }

    protected ListRegistrySelectWidget<T> selectSubScreen;
    protected ContentDelegateWidget<ListRegistrySelectWidget<T>> delegate;

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return selectSubScreen.getSelectedRegistry() != null;
    }

    @Override
    public void close() {
        // reset input,
        FilterService.currentUserInput = "";
        super.close();
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
        this.delegate = new ContentDelegateWidget<>(this.x + this.backgroundWidth / 2 - WIDTH / 2, this.y, WIDTH, 240)
                .setContentDelegate(this.selectSubScreen)
                .addTo(this);
    }
}
