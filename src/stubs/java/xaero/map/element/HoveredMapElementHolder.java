package xaero.map.element;

import java.util.ArrayList;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public class HoveredMapElementHolder<E, C> implements IRightClickableElement {
    public ArrayList<RightClickOption> getRightClickOptions() {
        return null;
    }

    public E getElement() {
        return null;
    }
}
