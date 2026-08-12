package me.matl114.gui.complex.slimefun;

import java.util.List;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.utils.ChatUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SlimefunScreen extends GenericBackGroundScreen {
    public SlimefunScreen(Text title) {
        super(title, 240, 320);
        this.titleLabel = title;
    }

    protected DrawableWidget guideBackground;
    protected DrawableWidget rtypeBackground;
    protected DrawableWidget vanillaBackground;
    protected ExecutableWidget guideIcon;
    protected ExecutableWidget rtypeIcon;
    protected ExecutableWidget closeButton;
    protected ExecutableWidget searchButton;
    protected ExecutableWidget vanillaIcon;
    protected ExecutableWidget saveItemIcon;
    protected static Identifier CANCEL_GUI_TEXTURE = new Identifier("minecraft", "container/beacon/cancel");
    private static final Identifier SEARCH_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/search");

    protected List<Text> getSearchButtonTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.slimefun-screen.search-default.tooltips", "");
    }

    protected void initBackground() {
        this.guideBackground = DisplayWidget.instance(this.x - 23, this.y + 12, 26, 26)
                .setRenderHandler(PlateElement.instance())
                .addTo(this);
        this.rtypeBackground = DisplayWidget.instance(this.x - 23, this.y + 38, 26, 26)
                .setRenderHandler(PlateElement.instance())
                .addTo(this);
        this.vanillaBackground = DisplayWidget.instance(this.x - 23, this.y + 64, 26, 26)
                .setRenderHandler(PlateElement.instance())
                .addTo(this);
        DisplayWidget.instance(this.x - 23, this.y + 90, 26, 26)
                .setRenderHandler(PlateElement.instance())
                .addTo(this);
        this.closeButton = ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
                .setInputHandler(InputHandler.run(this::close))
                .setRenderHandler(PlateElement.instance()
                        .combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE, 4, 4, 18, 18))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-screen.close-screen.tooltips", ""))))
                .addTo(this);
        this.searchButton = ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 38, 26, 26)
                .setInputHandler(InputHandler.run(this::close))
                .setRenderHandler(PlateElement.instance()
                        .combineRender(RenderHandler.ofGuiTextures(SEARCH_TEXTURE_SPRITE, 4, 4, 18, 18))
                        .withTooltips(TooltipHandler.of(this::getSearchButtonTooltips)))
                .addTo(this);
        super.initBackground();
    }

    protected void init() {
        super.init();
        this.guideIcon = ExecutableWidget.instance(this.x - 19, this.y + 16, 18, 18)
                .setRenderHandler(SlotElement.instance(SlimefunTasks.GUIDE_ICON)
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-screen.all-item.tooltips", ""))))
                .addTo(this);
        this.rtypeIcon = ExecutableWidget.instance(this.x - 19, this.y + 42, 18, 18)
                .setRenderHandler(SlotElement.instance(SlimefunTasks.RTYPE_ICON)
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-screen.all-type.tooltips", ""))))
                .addTo(this);
        this.vanillaIcon = ExecutableWidget.instance(this.x - 19, this.y + 68, 18, 18)
                .setRenderHandler(SlotElement.instance(SlimefunTasks.VTYPE_ICON)
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-screen.all-vanilla.tooltips", ""))))
                .addTo(this);
        this.saveItemIcon = ExecutableWidget.instance(this.x - 19, this.y + 94, 18, 18)
                .setRenderHandler(SlotElement.instance(SlimefunTasks.SAVED_ICON)
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slimefun-screen.all-custom.tooltips", ""))))
                .addTo(this);
        this.guideIcon.setInputHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openMainGuideMenu));
        this.rtypeIcon.setInputHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openCraftTypeMenu));
        this.vanillaIcon.setInputHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openVanillaRecipesMenu));
        this.saveItemIcon.setInputHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openSaveItemMenu));
    }
}
