package me.matl114.gui;

import java.awt.*;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public interface Constants {
    // fixme: value wrong
    Color SLOT_COLOR = new Color(139, 139, 139);
    ////    Color SLOT_HIGHLIGHT =;
    //    Colors.
    // fixme : value wrong
    Color SLOT_HIGHLIGHT_COLOR = new Color(128, 128, 128);
    int SLOT_HIGHLIGHT_INT = -2130706433;

    public static final Identifier SEARCH_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/search");

    public static final Identifier LIST_TAG_SPRITE = new Identifier("slimefunhelper", "gui/list_tag");

    public static final List<Text> SEARCH_REGISTRY_TOOLTIPS = List.of(Text.literal("从注册标中选择"), Text.literal("选择后点击确认"));

    public static final Text OPEN_LIST_EDIT_TEXT = Text.literal("点击编辑列表");

    public static final List<Text> OPEN_LIST_EDIT_TOOLTIPS = List.of(Text.literal("点击打开 列表编辑界面"));

    public static final List<Text> OPEN_LIST_PREVIEW_TOOLTIPS = List.of(Text.literal("点击打开 列表预览"));

    public static final Identifier EXPAND_GUI_ON_SPRITE = new Identifier("slimefunhelper", "gui/triangle");
    public static final Identifier EXPAND_GUI_OFF_SPRITE = new Identifier("slimefunhelper", "gui/triangle_90");
}
