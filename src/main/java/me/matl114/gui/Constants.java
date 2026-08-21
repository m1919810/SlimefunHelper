package me.matl114.gui;

import com.google.common.collect.ImmutableMap;
import java.awt.*;
import java.util.List;
import java.util.Map;
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

    public static final Identifier SEARCH_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/search");

    public static final Identifier FORMATTING_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/format");

    public static final Identifier LIST_TAG_SPRITE = new Identifier("slimefunhelper", "gui/list_tag");

    public static final Identifier EDITOR_SPRITE = new Identifier("slimefunhelper", "gui/editor");

    public static final Identifier REMOVE_SPRITE = new Identifier("slimefunhelper", "gui/remove");

    public static List<Text> searchRegistryTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.search-registry.tooltips", "");
    }

    public static final Text OPEN_LIST_EDIT_TEXT = Text.translatable("widget.gui.constants.open-list-edit");

    public static List<Text> openListEditTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.open-list-edit.tooltips", "");
    }

    public static List<Text> openListPreviewTooltips() {
        return ChatUtils.parseTooltipsTranslation("widget.gui.constants.open-list-preview.tooltips", "");
    }

    public static final Identifier EXPAND_GUI_ON_SPRITE = new Identifier("slimefunhelper", "gui/triangle");
    public static final Identifier EXPAND_GUI_OFF_SPRITE = new Identifier("slimefunhelper", "gui/triangle_90");

    public static final Map<EquipmentSlot, Identifier> EMPTY_SLOT_TO_SPRITE =
            ImmutableMap.<EquipmentSlot, Identifier>builder()
                    .put(EquipmentSlot.MAINHAND, new Identifier("slimefunhelper", "gui/empty_main_hand_slot"))
                    .put(EquipmentSlot.OFFHAND, new Identifier("slimefunhelper", "gui/empty_armor_slot_shield"))
                    .put(EquipmentSlot.FEET, new Identifier("slimefunhelper", "gui/empty_armor_slot_boots"))
                    .put(EquipmentSlot.LEGS, new Identifier("slimefunhelper", "gui/empty_armor_slot_leggings"))
                    .put(EquipmentSlot.CHEST, new Identifier("slimefunhelper", "gui/empty_armor_slot_chestplate"))
                    .put(EquipmentSlot.HEAD, new Identifier("slimefunhelper", "gui/empty_armor_slot_helmet"))
                    .build();
}
