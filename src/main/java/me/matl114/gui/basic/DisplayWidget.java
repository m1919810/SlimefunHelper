package me.matl114.gui.basic;

public class DisplayWidget extends DrawableWidget {
    public DisplayWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    public static DisplayWidget instance(int x, int y, int dx, int dy){
        return new DisplayWidget(x,y, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

}
