package xaero.map.gui;

import java.util.ArrayList;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public interface IRightClickableElement {
    ArrayList<RightClickOption> getRightClickOptions();
}
