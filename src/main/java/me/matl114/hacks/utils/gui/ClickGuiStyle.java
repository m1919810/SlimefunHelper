package me.matl114.hacks.utils.gui;

import java.util.Objects;
import java.util.function.BiConsumer;
import me.matl114.hacks.modules.task.ClickGui;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A registered ClickGui layout and the action that opens it. */
public final class ClickGuiStyle {
    private final Identifier id;
    private final Text text;
    private final BiConsumer<ClickGui, ClickGui.ClickGuiMetaData> opener;

    public ClickGuiStyle(Identifier id, BiConsumer<ClickGui, ClickGui.ClickGuiMetaData> opener) {
        this.id = Objects.requireNonNull(id);
        this.text = Text.translatable("widget.click-gui-style." + id.getPath());
        this.opener = Objects.requireNonNull(opener);
    }

    public Identifier id() {
        return id;
    }

    public Text text() {
        return text;
    }

    public BiConsumer<ClickGui, ClickGui.ClickGuiMetaData> opener() {
        return opener;
    }
}
