package me.matl114.gui.elements;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.utils.ChatUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class PageButtonElement extends ButtonElement {
    private IntSupplier maxPage;
    private IntSupplier pageGetter;
    private int delta;

    public static PageButtonElement prev(IntSupplier maxPage, IntSupplier page, IntConsumer set) {
        return new PageButtonElement(
                ChatUtils.parseTooltipsTranslation("widget.gui.page-button-element.prev.tooltips", ""),
                maxPage,
                page,
                set,
                -1);
    }

    public static PageButtonElement next(IntSupplier maxPage, IntSupplier page, IntConsumer set) {
        return new PageButtonElement(
                ChatUtils.parseTooltipsTranslation("widget.gui.page-button-element.next.tooltips", ""),
                maxPage,
                page,
                set,
                1);
    }

    protected static final Identifier ARROW_LEFT_SPRITE = new Identifier("slimefunhelper", "gui/arrow_left");
    protected static final Identifier ARROW_RIGHT_SPRITE = new Identifier("slimefunhelper", "gui/arrow_right");

    public PageButtonElement(List<Text> pageSwitch, int maxPage, AtomicInteger page, boolean left) {
        this(pageSwitch, maxPage, page::get, page::set, left ? -1 : 1);
    }

    public PageButtonElement(
            List<Text> pageSwitch, int maxPage, IntSupplier pageGetter, IntConsumer pageSetter, int delta) {
        this(pageSwitch, () -> maxPage, pageGetter, pageSetter, delta);
    }

    public PageButtonElement(
            List<Text> pageSwitch, IntSupplier maxPage, IntSupplier pageGetter, IntConsumer pageSetter, int delta) {
        super(TextProvider.of(null), ((element, widget, mouseButton) -> {
            int pageNow = pageGetter.getAsInt();
            int nextPage = MathHelper.clamp(pageNow + delta, 1, maxPage.getAsInt());
            pageSetter.accept(nextPage);
            return true;
        }));
        this.maxPage = maxPage;
        this.pageGetter = pageGetter;
        this.delta = delta;
        this.withTooltips(new TooltipHandler(pageSwitch));
    }

    public void renderTexture(VDrawContext context, DrawableWidget element, boolean highlight) {
        int tobe = pageGetter.getAsInt() + delta;
        boolean inactive = tobe <= 0 || tobe > maxPage.getAsInt();
        context.drawGuiTexture(
                (inactive ? BUTTON_INACTIVE : (highlight ? BUTTON_HIGHLIGHT : BUTTON)),
                0,
                0,
                element.getTextureWidth(),
                element.getTextureHeight());
        float x = (0.125f * element.getTextureWidth());
        float y = (0.125f * element.getTextureHeight());
        context.drawGuiTexture(
                delta < 0 ? ARROW_LEFT_SPRITE : ARROW_RIGHT_SPRITE,
                (int) x,
                (int) y,
                0,
                (int) (element.getTextureWidth() - x),
                (int) (element.getTextureHeight() - y));
    }
}
