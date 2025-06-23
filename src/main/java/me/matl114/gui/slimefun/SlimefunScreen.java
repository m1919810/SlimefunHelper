package me.matl114.gui.slimefun;

import me.matl114.gui.GenericScreen;
import me.matl114.gui.basic.*;
import me.matl114.hackUtils.SlimefunTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class SlimefunScreen extends GenericScreen {
    public SlimefunScreen(Text title){
        super(title, 240, 320);
        this.titleLabel = title;

    }
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    public void resetScreen(){
        //schedule refresh
        this.initTabNavigation();
        //mc.executeSync(()->this.init(mc,mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));
    }
    protected static final int TITLE_LABEL_HEIGHT = 12;
    protected static final int TITLE_OCCUPIED = 20;

    protected DrawableWidget background;
    protected DrawableWidget titleWidget;
    protected DrawableWidget guideBackground;
    protected DrawableWidget rtypeBackground;
    protected DrawableWidget vanillaBackground;
    protected ExecutableWidget guideIcon ;
    protected ExecutableWidget rtypeIcon ;
    protected ExecutableWidget closeButton;
    protected ExecutableWidget searchButton;
    protected ExecutableWidget vanillaIcon;
    protected ExecutableWidget saveItemIcon;
    protected static final List<Text> ALL_ITEM =List.of(Text.literal("点击查看全部记录粘液物品"),Text.literal("在粘液书中打开配方页时进行保存"),Text.literal("不会保存未解锁完毕的物品"));
    protected static final List<Text> ALL_TYPE = List.of(Text.literal("点击查看全部记录配方类型"));
    protected static final List<Text> ALL_VANILLA = List.of(Text.literal("点击查看全部原版配方"));
    protected static final List<Text> ALL_CUSTOM = List.of(Text.literal("点击查看全部保存物品"), Text.literal("使用CTRL+A+左键在物品栏中保存物品"),Text.literal("或者使用明确标明的按钮保存"));
    protected static final List<Text> CLOSE_SCREEN = List.of(Text.literal("点击关闭屏幕,退出至正常页面"));
    protected static final List<Text> SEARCH_DEFAULT = List.of(Text.literal("当前屏幕不支持搜索功能!").formatted(Formatting.RED));
    protected static Identifier CANCEL_GUI_TEXTURE = new Identifier("container/beacon/cancel");
    private static final Identifier SEARCH_TEXTURE = new Identifier("slimefunhelper","textures/gui/search.png");
    protected List<Text> getSearchButtonTooltips(){
        return SEARCH_DEFAULT;
    }
    protected void init() {
        super.init();
        //render before background, so depth is not needed
        this.guideBackground = DisplayWidget.instance(this.x - 23, this.y + 12, 26, 26)
            .setRenderHandler(PlateElement.instance())
            .addTo(this)
        ;
        this.rtypeBackground = DisplayWidget.instance(this.x - 23, this.y + 38, 26, 26)
            .setRenderHandler(PlateElement.instance())
            .addTo(this)
        ;
        this.vanillaBackground =DisplayWidget.instance(this.x - 23, this.y + 64, 26, 26)
            .setRenderHandler(PlateElement.instance())
            .addTo(this)
        ;
        DisplayWidget.instance(this.x - 23, this.y+90, 26, 26)
            .setRenderHandler(PlateElement.instance())
            .addTo(this);
        this.closeButton = ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 12, 26, 26)
            .setMouseHandler(MouseHandler.run(this::close))
            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofGuiTextures(CANCEL_GUI_TEXTURE,4, 4, 18,18)).withTooltips(TooltipHandler.of(CLOSE_SCREEN)))
            .addTo(this);
        this.searchButton = ExecutableWidget.instance(this.x + this.backgroundWidth - 3, this.y + 38, 26, 26)
            .setMouseHandler(MouseHandler.run(this::close))
            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofPositionResource(SEARCH_TEXTURE,4, 4, 18, 18)).withTooltips(TooltipHandler.of(this::getSearchButtonTooltips)))
            .addTo(this);
//        this.searchButton = ExecutableWidget.instance( this.x + this.backgroundWidth - 3 , this.y + 38, 26, 26)
//            .setRenderHandler(PlateElement.instance().combineRender(RenderHandler.ofPositionResource(SEARCH_TEXTURE,4, 4, 18, 18)).withTooltips(TooltipHandler.of(this::getSearchButtonTooltips)))
//            .addTo(this)
//        ;
        this.background = DisplayWidget.instance(this.x, this.y, this.backgroundWidth, this.backgroundHeight)
            .setRenderHandler(PlateElement.instance())
            .addTo(this)
        ;
        this.titleWidget = DisplayWidget.instance(this.x + 5, this.y + 5, this.backgroundWidth - 10, TITLE_LABEL_HEIGHT)
            .setRenderHandler(new LabelElement(this::getTitleLabel, Colors.WHITE, 0))
            .addTo(this)
        ;

        this.guideIcon = ExecutableWidget.instance(this.x - 19, this.y +16 , 18, 18)
            .setRenderHandler(
                SlotElement.instance(SlimefunTasks.GUIDE_ICON)
                    .withTooltips(TooltipHandler.of(ALL_ITEM))
            )
            .addTo(this)
        ;
        this.rtypeIcon = ExecutableWidget.instance(this.x - 19, this.y +42 , 18, 18)
            .setRenderHandler(
                SlotElement.instance(SlimefunTasks.RTYPE_ICON)
                    .withTooltips(TooltipHandler.of(ALL_TYPE))
            )
            .addTo(this)
        ;
        this.vanillaIcon = ExecutableWidget.instance(this.x - 19, this.y + 68 , 18, 18)
            .setRenderHandler(
                SlotElement.instance(SlimefunTasks.VTYPE_ICON)
                    .withTooltips(TooltipHandler.of(ALL_VANILLA))
            )
            .addTo(this)
        ;
        this.saveItemIcon= ExecutableWidget.instance(this.x - 19, this.y + 94 , 18, 18)
            .setRenderHandler(
                SlotElement.instance(SlimefunTasks.SAVED_ICON)
                    .withTooltips(TooltipHandler.of(ALL_CUSTOM))
            )
            .addTo(this)
        ;
        this.guideIcon.setMouseHandler(MouseHandler.run(SlimefunTasks::handleClickGuideIcon));
        this.rtypeIcon.setMouseHandler(MouseHandler.run(SlimefunTasks::handleClickRtypeIcon));
        this.vanillaIcon.setMouseHandler(MouseHandler.run(SlimefunTasks::handleClickCraftTableIcon));
        this.saveItemIcon.setMouseHandler(MouseHandler.run(SlimefunTasks::handleClickSaveItemIcon));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return true;
    }
}
