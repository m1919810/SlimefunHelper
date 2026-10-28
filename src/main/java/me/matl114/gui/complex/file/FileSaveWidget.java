package me.matl114.gui.complex.file;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Selects a destination; the callback writes the file and decides the overwrite policy. */
public class FileSaveWidget extends SubScreenWidget {
    private final FileTreeWidget tree;
    private final BaseAttrKeyValue<String> fileName;

    public FileSaveWidget(
            int x,
            int y,
            int width,
            int height,
            File root,
            String initialName,
            Predicate<String> namePredicate,
            Consumer<File> onConfirm,
            ElementFactory factory) {
        super(x, y, width, height);
        Objects.requireNonNull(namePredicate);
        Objects.requireNonNull(onConfirm);
        fileName = AttrKeyValue.str("", Objects.requireNonNull(initialName));
        fileName.addValidator(name -> isValidFileName(name) && namePredicate.test(name));
        fileName.validateAndUpdate();
        tree = factory.createFileTree(root, 0, 0, width, height - 46);
        addDrawableChild(tree);
        addDrawableChild(factory.createText("widget.file.filename", 2, height - 44, width - 4, 18));
        addDrawableChild(factory.createInput(fileName, TextAlign.LEFT, 2, height - 24, width - 88, 20));
        addDrawableChild(factory.createExecuteButton(
                () -> Text.translatable("widget.file.save"),
                () -> List.of(Text.translatable("widget.file.save_hint")),
                (element, widget, button) -> {
                    if (button != 0) return false;
                    File target = getTargetFile();
                    File directory = tree.getSelectedFile();
                    // Recheck at confirmation: the disk may have changed since the last snapshot.
                    if (target != null && directory != null && directory.isDirectory() && !target.isDirectory()) {
                        onConfirm.accept(target);
                    }
                    return true;
                },
                () -> getTargetFile() != null,
                width - 82,
                height - 24,
                80,
                20));
    }

    public FileTreeWidget getTree() {
        return tree;
    }

    public String getFileName() {
        return fileName.getInput();
    }

    public void setFileName(String name) {
        fileName.setInput(Objects.requireNonNull(name));
    }

    /** Returns null until a directory and a valid filename have been supplied. No disk I/O. */
    @Nullable
    public File getTargetFile() {
        if (!tree.isDirectorySelected() || !fileName.isValidate()) return null;
        return new File(tree.getSelectedFile(), getFileName());
    }

    static boolean isValidFileName(String name) {
        if (name.isBlank()
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0) {
            return false;
        }
        try {
            Path path = Path.of(name);
            return !path.isAbsolute() && path.getNameCount() == 1;
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
