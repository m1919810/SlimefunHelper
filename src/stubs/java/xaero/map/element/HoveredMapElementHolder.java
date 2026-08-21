package xaero.map.element;

import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

public class HoveredMapElementHolder<E, C> implements IRightClickableElement {
    public ArrayList<RightClickOption> getRightClickOptions() {
        return null;
    }
    public E getElement() {
        return null;
    }
}
