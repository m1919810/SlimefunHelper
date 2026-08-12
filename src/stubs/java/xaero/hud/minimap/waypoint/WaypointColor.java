package xaero.hud.minimap.waypoint;

import net.minecraft.text.Text;

public enum WaypointColor {
    BLACK(Text.translatable("gui.xaero_black"), '0', -16777216),
    DARK_BLUE(Text.translatable("gui.xaero_dark_blue"), '1', -16777046),
    DARK_GREEN(Text.translatable("gui.xaero_dark_green"), '2', -16733696),
    DARK_AQUA(Text.translatable("gui.xaero_dark_aqua"), '3', -16733526),
    DARK_RED(Text.translatable("gui.xaero_dark_red"), '4', -5636096),
    DARK_PURPLE(Text.translatable("gui.xaero_dark_purple"), '5', -5635926),
    GOLD(Text.translatable("gui.xaero_gold"), '6', -22016),
    GRAY(Text.translatable("gui.xaero_gray"), '7', -5592406),
    DARK_GRAY(Text.translatable("gui.xaero_dark_gray"), '8', -11184811),
    BLUE(Text.translatable("gui.xaero_blue"), '9', -11184641),
    GREEN(Text.translatable("gui.xaero_green"), 'a', -11141291),
    AQUA(Text.translatable("gui.xaero_aqua"), 'b', -11141121),
    RED(Text.translatable("gui.xaero_red"), 'c', -65536),
    PURPLE(Text.translatable("gui.xaero_purple"), 'd', -43521),
    YELLOW(Text.translatable("gui.xaero_yellow"), 'e', -171),
    WHITE(Text.translatable("gui.xaero_white"), 'f', -1);

    private WaypointColor(Text name, char format, int hex) {}

    public Text getName() {
        return null;
    }

    public char getFormat() {
        return 0;
    }

    public int getHex() {
        return 0;
    }
}
