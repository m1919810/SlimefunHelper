package me.matl114.gui.config;

import me.matl114.gui.FilterService;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.function.Consumer;

public class RegistrySelectScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    Consumer<Set<T>> callback;
    protected static final int WIDTH = 240;
    public RegistrySelectScreen(Registry<T> registry, Set<T> currentSelection, Consumer<Set<T>> callback) {
        super(Text.empty());
        this.registry = registry;
        this.callback =callback;
        setTitleLabel(Text.literal("从注册表中选择注册项").formatted(Formatting.AQUA));
        //reset user input, so it is more convenient for user to select a registry value,
        FilterService.currentUserInput = "";
        this.selectSubScreen = ListRegistryMultiSelectWidget.registry(this.registry, currentSelection, 0, CONTENT_START_Y + 20, WIDTH, 240, 20);
    }
    ListRegistryMultiSelectWidget<T> selectSubScreen;
    ContentDelegateWidget<ListRegistryMultiSelectWidget<T>> delegate;

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return true;
    }

    @Override
    public void close() {
        super.close();
        //reset input,
        FilterService.currentUserInput = "";
    }

    @Override
    protected void onConfirmButton() {
        this.close();
        Set<T> val = selectSubScreen.getSelectedRegistries();
        if(val != null && callback != null){
            callback.accept(val);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.delegate =new ContentDelegateWidget<>(this.x + this.backgroundWidth/2 - WIDTH /2, this.y ,WIDTH, 240)
            .setContentDelegate(this.selectSubScreen)
            .addTo(this)
        ;
    }
}
