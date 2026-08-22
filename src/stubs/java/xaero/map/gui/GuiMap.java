package xaero.map.gui;

import java.util.ArrayList;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.map.MapProcessor;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public class GuiMap extends Screen implements IRightClickableElement {
    private MapProcessor mapProcessor;
    private int rightClickX;
    private int rightClickY;
    private int rightClickZ;
    private RegistryKey<World> rightClickDim;
    private GuiRightClickMenu rightClickMenu;
    private MapTileSelection mapTileSelection;

    protected GuiMap(Text title) {
        super(title);
    }

    public ArrayList<RightClickOption> getRightClickOptions() {
        return new ArrayList<>();
    }

    public void onRightClickClosed() {}
}
