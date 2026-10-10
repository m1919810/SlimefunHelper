package me.matl114.gui;

import com.google.common.collect.ImmutableMap;
import java.awt.*;
import java.util.List;
import java.util.Map;
import me.matl114.plugins.MainPlugin;
import me.matl114.utils.ChatUtils;
import net.minecraft.entity.EquipmentSlot;
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

    public static final Identifier SEARCH_TEXTURE_SPRITE = MainPlugin.identifier("gui/search");

    public static final Identifier FORMATTING_TEXTURE_SPRITE = MainPlugin.identifier("gui/format");

    public static final Identifier LIST_TAG_SPRITE = MainPlugin.identifier("gui/list_tag");

    public static final Identifier EDITOR_SPRITE = MainPlugin.identifier("gui/editor");

    public static final Identifier REMOVE_SPRITE = MainPlugin.identifier("gui/remove");

    public static final Identifier SHIFT_UP_SPRITE = MainPlugin.identifier("gui/move_up");
    public static final Identifier SHIFT_DOWN_SPRITE = MainPlugin.identifier("gui/move_down");

    public static final Identifier ADD_SPRITE = MainPlugin.identifier("gui/add");

    public static final Identifier RESET_BUTTON_SPRITE = MainPlugin.identifier("gui/reset");

    public static final Identifier EDIT_SPRITE = MainPlugin.identifier("gui/edit");

    public static final Identifier SAVE_SPRITE = MainPlugin.identifier("gui/save");
    public static final Identifier COPY_COMMAND_SPRITE = MainPlugin.identifier("gui/copy_command");
    public static final Identifier SNBT_EDITOR_SPRITE = MainPlugin.identifier("gui/snbt_editor");

    public static final Identifier TRADE_ARROW_SPRITE = MainPlugin.identifier("gui/trade_arrow");

    public static final Identifier ARROW_LEFT_SPRITE = MainPlugin.identifier("gui/arrow_left");
    public static final Identifier ARROW_RIGHT_SPRITE = MainPlugin.identifier("gui/arrow_right");

    public static final Identifier BUTTON_SPRITE = MainPlugin.identifier("gui/button");
    public static final Identifier BUTTON_HIGHLIGHT_SPRITE = MainPlugin.identifier("gui/button_highlighted");
    public static final Identifier BUTTON_INACTIVE_SPRITE = MainPlugin.identifier("gui/button_disabled");

    public static final Identifier LOCK_ENABLE_SPRITE = MainPlugin.identifier("gui/lock_enable");
    public static final Identifier LOCK_DISABLE_SPRITE = MainPlugin.identifier("gui/lock_disable");

    public static List<Text> searchRegistryTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.search-registry.tooltips", "");
    }

    public static final String OPEN_LIST_EDIT_KEY = "widget.gui.constants.open-list-edit";

    public static final Text OPEN_LIST_EDIT_TEXT = Text.translatable("widget.gui.constants.open-list-edit");

    public static List<Text> openListEditTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.open-list-edit.tooltips", "");
    }

    public static List<Text> openListPreviewTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.open-list-preview.tooltips", "");
    }

    public static final Identifier EXPAND_GUI_ON_SPRITE = MainPlugin.identifier("gui/triangle");
    public static final Identifier EXPAND_GUI_OFF_SPRITE = MainPlugin.identifier("gui/triangle_90");

    public static final Map<EquipmentSlot, Identifier> EMPTY_SLOT_TO_SPRITE =
            ImmutableMap.<EquipmentSlot, Identifier>builder()
                    .put(EquipmentSlot.MAINHAND, MainPlugin.identifier("gui/empty_main_hand_slot"))
                    .put(EquipmentSlot.OFFHAND, MainPlugin.identifier("gui/empty_armor_slot_shield"))
                    .put(EquipmentSlot.FEET, MainPlugin.identifier("gui/empty_armor_slot_boots"))
                    .put(EquipmentSlot.LEGS, MainPlugin.identifier("gui/empty_armor_slot_leggings"))
                    .put(EquipmentSlot.CHEST, MainPlugin.identifier("gui/empty_armor_slot_chestplate"))
                    .put(EquipmentSlot.HEAD, MainPlugin.identifier("gui/empty_armor_slot_helmet"))
                    .build();
}
