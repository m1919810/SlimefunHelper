package me.matl114.gui.complex.file;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.interfaces.ElementFactory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** File tree plus a predicate-guarded confirmation button. Predicates should be cheap and side-effect free. */
public class FileSelectWidget extends SubScreenWidget {
    private final FileTreeWidget tree;
    private Predicate<File> predicate;

    public FileSelectWidget(
            int x,
            int y,
            int width,
            int height,
            File root,
            Predicate<File> predicate,
            Consumer<File> onConfirm,
            ElementFactory factory) {
        super(x, y, width, height);
        this.predicate = Objects.requireNonNull(predicate);
        Objects.requireNonNull(onConfirm);
        tree = factory.createFileTree(root, 0, 0, width, height - 24);
        addDrawableChild(tree);
        addDrawableChild(factory.createExecuteButton(
                () -> Text.translatable("widget.file.select"),
                () -> List.of(Text.translatable("widget.file.select_hint")),
                (element, widget, button) -> {
                    if (button != 0) return false;
                    File file = getSelectedFile();
                    // Factory button activation controls appearance; guard the action as well.
                    if (file != null && file.exists() && this.predicate.test(file)) onConfirm.accept(file);
                    return true;
                },
                () -> getSelectedFile() != null,
                width - 82,
                height - 22,
                80,
                20));
    }

    public FileTreeWidget getTree() {
        return tree;
    }

    public void setSelectionPredicate(Predicate<File> predicate) {
        this.predicate = Objects.requireNonNull(predicate);
    }

    /** Returns null when the current selection fails the caller's predicate. */
    @Nullable
    public File getSelectedFile() {
        File file = tree.getSelectedFile();
        return file != null && predicate.test(file) ? file : null;
    }
}
