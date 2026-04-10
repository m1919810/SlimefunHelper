package me.matl114.versioned.api;

public interface VDataFlag {
    int ID_FLAGS = 0;
    int ON_FIRE_FLAG_INDEX = 0;
    int SNEAKING_FLAG_INDEX = 1;
    int FALL_FLYING_FLAG_INDEX = 7;
    int ID_AIR = 1;
    int ID_CUSTOM_NAME = 2;
    int ID_NAME_VISIBLE = 3;
    int ID_SILENT = 4;
    int ID_NO_GRAVITY = 5;
    int ID_POSE = 6;
    int ID_LIVING_FLAGS = 7;
    int USING_ITEM_FLAG_INDEX = 1;
    int OFFHAND_ACTIVE_FLAG_INDEX = 2;

    // FireworkRockets
    int ID_FIREWORK_ITEM = 8;
    int ID_FIREWORK_SHOOTER_ID = 9;
    int ID_FIREWORK_SHOT_AT_ANGLE = 10;
    // ItemEntity
    int ID_ITEM_ITEMSTACK = 8;
    // Decoration
    int ID_WALL_DECORATION_FACING = 8;
    int ID_ITEM_FRAME_ITEMSTACK = 9;
    int ID_ITEM_FRAME_ROTATION = 10;
}
