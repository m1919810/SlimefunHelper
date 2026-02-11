package me.matl114.gui.slimefun;

import java.util.List;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.*;
import me.matl114.hacks.SlimefunTasks;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    protected static final List<Text> ALL_ITEM =
            List.of(Text.literal("点击查看全部记录粘液物品"), Text.literal("在粘液书中打开配方页时进行保存"), Text.literal("不会保存未解锁完毕的物品"));
    protected static final List<Text> ALL_TYPE = List.of(Text.literal("点击查看全部记录配方类型"));
    protected static final List<Text> ALL_VANILLA = List.of(Text.literal("点击查看全部原版配方"));
    protected static final List<Text> ALL_CUSTOM =
            List.of(Text.literal("点击查看全部保存物品"), Text.literal("使用CTRL+A+左键在物品栏中保存物品"), Text.literal("或者使用明确标明的按钮保存"));
    protected static final List<Text> CLOSE_SCREEN = List.of(Text.literal("点击关闭屏幕,退出至正常页面"));
    protected static final List<Text> SEARCH_DEFAULT =
            List.of(Text.literal("当前屏幕不支持搜索功能!").formatted(Formatting.RED));
    protected static Identifier CANCEL_GUI_TEXTURE = new Identifier("minecraft", "container/beacon/cancel");
    private static final Identifier SEARCH_TEXTURE = new Identifier("slimefunhelper", "textures/gui/search.png");

    protected List<Text> getSearchButtonTooltips() {
        return SEARCH_DEFAULT;
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
                .setMouseHandler(InputHandler.run(this::close))
                .setRenderHandler(PlateElement.instance()
                        .combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE, 4, 4, 18, 18))
                        .withTooltips(TooltipHandler.of(CLOSE_SCREEN)))
                .addTo(this);
        this.searchButton = ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 38, 26, 26)
                .setMouseHandler(InputHandler.run(this::close))
                .setRenderHandler(PlateElement.instance()
                        .combineRender(RenderHandler.ofPositionResource(SEARCH_TEXTURE, 4, 4, 18, 18))
                        .withTooltips(TooltipHandler.of(this::getSearchButtonTooltips)))
                .addTo(this);
        super.initBackground();
    }

    protected void init() {
        super.init();
        this.guideIcon = ExecutableWidget.instance(this.x - 19, this.y + 16, 18, 18)
                .setRenderHandler(
                        SlotElement.instance(SlimefunTasks.GUIDE_ICON).withTooltips(TooltipHandler.of(ALL_ITEM)))
                .addTo(this);
        this.rtypeIcon = ExecutableWidget.instance(this.x - 19, this.y + 42, 18, 18)
                .setRenderHandler(
                        SlotElement.instance(SlimefunTasks.RTYPE_ICON).withTooltips(TooltipHandler.of(ALL_TYPE)))
                .addTo(this);
        this.vanillaIcon = ExecutableWidget.instance(this.x - 19, this.y + 68, 18, 18)
                .setRenderHandler(
                        SlotElement.instance(SlimefunTasks.VTYPE_ICON).withTooltips(TooltipHandler.of(ALL_VANILLA)))
                .addTo(this);
        this.saveItemIcon = ExecutableWidget.instance(this.x - 19, this.y + 94, 18, 18)
                .setRenderHandler(
                        SlotElement.instance(SlimefunTasks.SAVED_ICON).withTooltips(TooltipHandler.of(ALL_CUSTOM)))
                .addTo(this);
        this.guideIcon.setMouseHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openMainGuideMenu));
        this.rtypeIcon.setMouseHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openCraftTypeMenu));
        this.vanillaIcon.setMouseHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openVanillaRecipesMenu));
        this.saveItemIcon.setMouseHandler(InputHandler.run(SlimefunTasks.getSlimefunGuide()::openSaveItemMenu));
    }
}
