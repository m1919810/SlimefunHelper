package me.matl114.hacks.utils.enums;

import me.matl114.managers.config.ConfigEnum;
import me.matl114.utils.InventoryUtils;

public enum GhostHandMode implements ConfigEnum {
    INV_SWAP(InventoryUtils.getPlayerInvSize()),
    HOT_BAR_ONLY(9),
    INV_CLICK(InventoryUtils.getPlayerInvSize());
    ;

    final int searchSize;

    GhostHandMode(int size) {
        searchSize = size;
    }

    @Override
    public String getConfigEnumType() {
        return "ghost_hand_mode";
    }

    public int getSearchSize(boolean offHand) {
        return offHand ? InventoryUtils.getPlayerInvSize() : searchSize;
    }
}
