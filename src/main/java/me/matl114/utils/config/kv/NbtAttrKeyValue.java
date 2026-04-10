package me.matl114.utils.config.kv;

import java.util.function.Consumer;
import java.util.function.Function;
import lombok.experimental.Accessors;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.utils.config.BaseAttrKeyValue;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.visitor.NbtOrderedStringFormatter;

@Accessors(chain = true)
public class NbtAttrKeyValue<W> extends BaseAttrKeyValue<NbtElement> {
    protected final Function<NbtElement, W> nbtParser;

    public NbtAttrKeyValue<W> setEnableNull(boolean val) {
        this.enableNull = val;
        return this;
    }

    protected boolean enableNull = false;

    public NbtAttrKeyValue(String key, NbtElement value, Function<NbtElement, W> function) {
        super(key, value == null ? null : value.copy(), AttrKeyValues.NBT_FACTORY);
        this.nbtParser = function;
        addValidator(s -> {
            if (s != null) {
                return extraParse(s);
            } else {
                return enableNull;
            }
        });
    }

    private boolean extraParse(NbtElement element) {
        try {
            nbtParser.apply(element);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public void applyFormatting(Consumer<String> callback) {
        if (validate) {
            try {
                valueChange(null, new NbtOrderedStringFormatter().apply(this.getOriginValue()));
                callback.accept(this.getValue());
            } catch (Throwable e) {
            }
        }
    }

    public ContentDelegateWidget<EditBoxWidget> generateEditBox(int x, int y, int dx, int dy) {
        //            EditBoxWidget widget = new EditBoxWidget(MinecraftClient.getInstance().textRenderer, x,y,
        // dx,dy, Text.empty(), Text.empty());
        //            widget.setText(this.value);
        //            widget.setChangeListener((val)->valueChange(null, val));
        //            TextFieldAccess.of(widget).setBorderColorProvider();
        return McWidgetHelpers.createMultiLineEditBox(
                x,
                y,
                dx,
                dy,
                (ed, val) -> valueChange(null, val),
                this.getValue(),
                McWidgetHelpers.getWrongRedTextBoxColorProvider(() -> validate));
        //            return widget;
    }
}
