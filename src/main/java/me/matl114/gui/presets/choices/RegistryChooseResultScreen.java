package me.matl114.gui.presets.choices;

import com.google.common.base.Predicates;
import java.util.List;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.presets.lists.ListRegistrySelectWidget;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RegistryChooseResultScreen<T> extends ConfirmingBigScreen {
    Registry<T> registry;
    protected static final int WIDTH = 240;

    public RegistryChooseResultScreen(Registry<T> registry, List<T> selects, String showString) {
        super(Text.empty());
        this.registry = registry;
        setTitleLabel(Text.literal("注册表选择结果").formatted(Formatting.AQUA));
        // reset user input, so it is more convenient for user to select a registry value,
        FilterService.currentUserInput = showString;
        this.selectSubScreen = (ListRegistrySelectWidget<T>)
                ListRegistrySelectWidget.registry(selects, this.registry, 0, CONTENT_START_Y + 20, WIDTH, 240, 20)
                        .filter(Predicates.alwaysTrue());
    }

    ListRegistrySelectWidget<T> selectSubScreen;
    ContentDelegateWidget<ListRegistrySelectWidget<T>> delegate;

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return true;
    }

    @Override
    public void close() {
        super.close();
        // reset input,
        FilterService.currentUserInput = "";
    }

    @Override
    protected void onConfirmButton() {
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
