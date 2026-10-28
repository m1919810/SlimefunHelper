package me.matl114.gui.complex.file;

import static me.matl114.managers.input.KeyCode.KEY_DELETE;
import static me.matl114.managers.input.KeyCode.KEY_ENTER;
import static me.matl114.managers.input.KeyCode.KEY_KP_ENTER;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.MouseClickAction;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.basic.ScrollableListWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.SelectBackgroundElement;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.plugins.MainPlugin;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** File display and single selection. All public methods must be called on the client thread. */
public class FileTreeWidget extends SubScreenWidget {
    private static final long REFRESH_INTERVAL_NANOS = 500_000_000L;
    private static final long DOUBLE_CLICK_INTERVAL_NANOS = 400_000_000L;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_SIZE = ROW_HEIGHT;
    // Windows stock icons, exported as standalone 16 x 16 PNGs.
    private static final Identifier FILE_ICON_TEXTURE = MainPlugin.identifier("textures/custom/file_tree_file.png");
    private static final Identifier FOLDER_ICON_TEXTURE = MainPlugin.identifier("textures/custom/file_tree_folder.png");
    private static final Identifier OPEN_FOLDER_ICON_TEXTURE =
            MainPlugin.identifier("textures/custom/file_tree_folder_open.png");
    private final File root;
    private final ElementFactory factory;
    private final DynamicListWidget rows;
    private final ScrollableListWidget scroll;
    private final Map<File, FileNameWidget> nameDelegates = new HashMap<>();
    private final Set<File> expanded = new HashSet<>();
    private Consumer<File> onSelection = file -> {};
    private CompletableFuture<Snapshot> refreshTask;
    private long lastRefreshStarted;
    private boolean dirty = true;
    private Node tree;
    private SelectedEntry selectedEntry;
    private RenameEdit renameEdit;
    private Matrix4f inverseTreeTransform;
    private Text refreshError;
    private File lastClickedFile;
    private long lastClickedAt;
    private File pendingDeselection;

    public FileTreeWidget(int x, int y, int width, int height, File root) {
        this(x, y, width, height, root, DefaultElementFactory.INSTANCE);
    }

    public FileTreeWidget(int x, int y, int width, int height, File root, ElementFactory factory) {
        super(x, y, width, height);
        if (width < 120 || height < 80) {
            throw new IllegalArgumentException("File tree requires at least 120 x 80 pixels");
        }
        this.root = root.toPath().toAbsolutePath().normalize().toFile();
        this.factory = Objects.requireNonNull(factory);
        expanded.add(this.root);
        rows = new DynamicListWidget(0, 0, width - 16);
        int toolbarButtonWidth = (width - 12) / 5;
        addDrawableChild(createToolbarButton(
                "widget.file.new_folder", ButtonAction.run(this::createNewFolder), 2, toolbarButtonWidth));
        addDrawableChild(createToolbarButton(
                "widget.file.rename", ButtonAction.run(this::beginRename), toolbarButtonWidth + 4, toolbarButtonWidth));
        addDrawableChild(createToolbarButton(
                "widget.file.delete",
                ButtonAction.run(this::deleteSelection),
                toolbarButtonWidth * 2 + 6,
                toolbarButtonWidth));
        addDrawableChild(createToolbarButton(
                "widget.file.open_selected",
                ButtonAction.run(() -> {
                    resetClickSequence();
                    openInFileBrowser(getSelectedFile());
                }),
                toolbarButtonWidth * 3 + 8,
                toolbarButtonWidth));
        addDrawableChild(createToolbarButton(
                "widget.file.select_root",
                ButtonAction.run(() -> {
                    if (tree != null && tree.exists() && tree.directory()) {
                        resetClickSequence();
                        select(this.root, true);
                    }
                }),
                toolbarButtonWidth * 4 + 10,
                toolbarButtonWidth));
        scroll = new ScrollableListWidget(2, ROW_HEIGHT + 2, width - 16, height - 42);
        scroll.addScrollingWidget(rows);
        addDrawableChild(DisplayWidget.instance(scroll.getX(), scroll.getY(), scroll.getWidth(), scroll.getHeight())
                .setRenderHandler(new AbstractElement()
                        .setShowTooltips(false)
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, highlight) -> context.fill(
                                0, 0, element.getTextureWidth(), element.getTextureHeight(), 0xD0101010))));
        addDrawableChild(scroll);
        addDrawableChild(factory.createText(this::selectionText, List::of, 2, height - 20, width - 4, ROW_HEIGHT));
    }

    private ExecutableWidget createToolbarButton(String translationKey, ButtonAction action, int x, int width) {
        return ExecutableWidget.instance(x, 0, width, ROW_HEIGHT)
                .setElementHandler(factory.createButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(List.of()),
                        action,
                        () -> false,
                        () -> true));
    }

    public File getRoot() {
        return root;
    }

    @Nullable
    public File getSelectedFile() {
        return selectedEntry == null ? null : selectedEntry.file();
    }

    /** Uses the most recent disk snapshot and does not perform disk I/O. */
    public boolean isDirectorySelected() {
        return selectedEntry != null && selectedEntry.directory();
    }

    /** The listener receives null on explicit clearing or removal of the selected entry. */
    public void setSelectionListener(Consumer<File> listener) {
        onSelection = Objects.requireNonNull(listener);
    }

    public void clearSelection() {
        resetClickSequence();
        select(null, false);
    }

    private void resetClickSequence() {
        lastClickedFile = null;
        lastClickedAt = 0;
        pendingDeselection = null;
    }

    private void flushPendingDeselection(long now) {
        if (pendingDeselection != null && now - lastClickedAt > DOUBLE_CLICK_INTERVAL_NANOS) {
            if (pendingDeselection.equals(getSelectedFile())) clearSelection();
            else resetClickSequence();
        }
    }

    private void clickEntry(Node node, SelectBackgroundElement selection) {
        long now = System.nanoTime();
        flushPendingDeselection(now);
        boolean doubleClick = node.file().equals(lastClickedFile) && now - lastClickedAt <= DOUBLE_CLICK_INTERVAL_NANOS;
        if (doubleClick) {
            // Double clicks take priority over deselection.
            if (node.directory()) {
                if (expanded.remove(node.file())) dirty = true;
                else {
                    expanded.add(node.file());
                    requestDiskRefresh();
                }
            } else {
                openInFileBrowser(node.file());
            }
            resetClickSequence();
        } else {
            pendingDeselection = null;
            if (node.file().equals(getSelectedFile())) pendingDeselection = node.file();
            else selection.toggleSelection();
            lastClickedFile = node.file();
            lastClickedAt = now;
        }
    }

    private void openInFileBrowser(@Nullable File file) {
        if (file == null) return;
        File directory = file.isDirectory() ? file : file.getParentFile();
        if (directory != null) Util.getOperatingSystem().open(directory);
    }

    private void createNewFolder() {
        resetClickSequence();
        File parent = isDirectorySelected() ? getSelectedFile() : root;
        try {
            for (int suffix = 0; ; suffix++) {
                String name = suffix == 0 ? "new_folder" : "new_folder_" + suffix;
                try {
                    Files.createDirectory(parent.toPath().resolve(name));
                    expanded.add(parent);
                    requestDiskRefresh();
                    return;
                } catch (FileAlreadyExistsException exception) {
                    // Atomically try the next name; existing files and directories are both occupied names.
                }
            }
        } catch (IOException | SecurityException exception) {
            Debug.chat(Text.translatable("widget.file.new_folder_failed", exception.getMessage())
                    .formatted(Formatting.RED));
        }
    }

    private void deleteSelection() {
        resetClickSequence();
        File file = getSelectedFile();
        if (file == null || file.equals(root)) return;
        deleteSelected(file);
    }

    private void deleteSelected(File file) {
        Path target = file.toPath().toAbsolutePath().normalize();
        if (target.equals(root.toPath()) || !target.startsWith(root.toPath())) return;
        CompletableFuture.runAsync(() -> {
                    try {
                        // Do not follow symbolic links when deleting a directory and its contents.
                        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes)
                                    throws IOException {
                                Files.delete(path);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                                    throws IOException {
                                if (exception != null) throw exception;
                                Files.delete(directory);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException | SecurityException exception) {
                        throw new CompletionException(exception);
                    }
                })
                .whenComplete((unused, error) -> MinecraftClient.getInstance().execute(() -> {
                    if (error == null) {
                        File selected = getSelectedFile();
                        if (selected != null && selected.toPath().startsWith(target)) clearSelection();
                        expanded.removeIf(directory -> directory.toPath().startsWith(target));
                        Debug.chat(Text.translatable("widget.file.delete_success", file.getName())
                                .formatted(Formatting.GREEN));
                    } else {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause()
                                : error;
                        Debug.chat(Text.translatable("widget.file.delete_failed", cause.getMessage())
                                .formatted(Formatting.RED));
                    }
                    requestDiskRefresh();
                }));
    }

    private void beginRename() {
        resetClickSequence();
        SelectedEntry entry = selectedEntry;
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (entry == null || entry.delegate() == null || renameEdit != null || screen == null) return;
        if (nameDelegates.get(entry.file()) != entry.delegate()) return;
        BaseAttrKeyValue<String> name = AttrKeyValue.str("", entry.file().getName());
        name.addValidator(FileSaveWidget::isValidFileName);
        name.validateAndUpdate();
        DrawableWidget input =
                factory.createInput(name, TextAlign.LEFT, 0, 0, entry.delegate().getWidth(), ROW_HEIGHT);
        RenameEdit edit = new RenameEdit(entry, entry.delegate().getDelegate(), input, name, screen);
        renameEdit = edit;
        entry.delegate().setContentDelegate(input);
        input.setFocused(true);

        // Observe raw presses so clicks outside this tree (including other screen widgets) also commit.
        Predicate<Event<MouseClickAction>> mouseListener = event -> {
            if (renameEdit != edit) return false;
            if (MinecraftClient.getInstance().currentScreen != screen) {
                cancelRename(edit);
                return false;
            }
            if (event.context().action() == GLFW.GLFW_PRESS && !isInsideRenameInput(edit)) {
                finishRename(edit);
                return false;
            }
            return true;
        };
        Listener.getMouseButton().registerHandler(mouseListener);
        ScreenAccess.of(screen).addCloseFuture(() -> {
            cancelRename(edit);
            Listener.getMouseButton().unregisterHandler(handler -> handler == mouseListener);
        });
    }

    private boolean isInsideRenameInput(RenameEdit edit) {
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX()
                * client.getWindow().getScaledWidth()
                / client.getWindow().getWidth();
        double mouseY = client.mouse.getY()
                * client.getWindow().getScaledHeight()
                / client.getWindow().getHeight();
        if (inverseTreeTransform == null || edit.entry().delegate().inverseTransform == null) return false;
        Vector3f treePoint = inverseTreeTransform.transformPosition((float) mouseX, (float) mouseY, 0, new Vector3f());
        if (treePoint.x < scroll.getX()
                || treePoint.x >= scroll.getX() + scroll.getWidth()
                || treePoint.y < scroll.getY()
                || treePoint.y >= scroll.getY() + scroll.getHeight()) return false;
        Vector3f inputPoint = edit.entry()
                .delegate()
                .inverseTransform
                .transformPosition((float) mouseX, (float) mouseY, 0, new Vector3f());
        return inputPoint.x >= 0
                && inputPoint.x < edit.input().getWidth()
                && inputPoint.y >= 0
                && inputPoint.y < edit.input().getHeight();
    }

    private void cancelRename(RenameEdit edit) {
        if (renameEdit != edit) return;
        edit.input().setFocused(false);
        edit.entry().delegate().setContentDelegate(edit.original());
        renameEdit = null;
    }

    private void finishRename(RenameEdit edit) {
        if (renameEdit != edit) return;
        cancelRename(edit);
        File oldFile = edit.entry().file();
        String newName = edit.name().getInput();
        if (oldFile.getName().equals(newName)) return;
        if (!edit.name().isValidate()) {
            Debug.chat(Text.translatable("widget.file.rename_invalid").formatted(Formatting.RED));
            return;
        }
        try {
            File newFile = Files.move(oldFile.toPath(), oldFile.toPath().resolveSibling(newName))
                    .toFile();
            // Keep expanded descendants attached when their parent directory is renamed.
            Set<File> movedDirectories = new HashSet<>();
            for (File directory : expanded) {
                Path path = directory.toPath();
                movedDirectories.add(
                        path.startsWith(oldFile.toPath())
                                ? newFile.toPath()
                                        .resolve(oldFile.toPath().relativize(path))
                                        .toFile()
                                : directory);
            }
            expanded.clear();
            expanded.addAll(movedDirectories);
            nameDelegates.remove(oldFile);
            nameDelegates.put(newFile, edit.entry().delegate());
            select(newFile, edit.entry().directory());
        } catch (IOException | SecurityException exception) {
            Debug.chat(Text.translatable("widget.file.rename_failed", exception.getMessage())
                    .formatted(Formatting.RED));
        }
        requestDiskRefresh();
    }

    @Override
    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        inverseTreeTransform = new Matrix4f(context.getMatrices().peek3D()).invert();
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameEdit != null && (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER)) {
            finishRename(renameEdit);
            return true;
        }
        if (renameEdit != null) renameEdit.input().setFocused(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Add after all other widgets in the outermost container so inputs consume keys first. */
    public DrawableWidget createShortcutWidget() {
        return ExecutableWidget.instance(0, 0, 0, 0).setInputHandler(InputHandler.keyPress((element, keyCode) -> {
            if (keyCode != KEY_DELETE) return false;
            deleteSelection();
            return true;
        }));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renameEdit == null) return super.charTyped(chr, modifiers);
        renameEdit.input().setFocused(true);
        return renameEdit.input().charTyped(chr, modifiers);
    }

    private void select(@Nullable File file, boolean directory) {
        boolean changed = !Objects.equals(getSelectedFile(), file) || isDirectorySelected() != directory;
        selectedEntry = file == null ? null : new SelectedEntry(file, directory, nameDelegates.get(file));
        if (changed) {
            pendingDeselection = null;
            onSelection.accept(file);
        }
    }

    private Text selectionText() {
        if (refreshError != null) return refreshError;
        File selectedFile = getSelectedFile();
        return selectedFile == null
                ? Text.translatable("widget.file.no_selection")
                : Text.translatable(
                        "widget.file.selected",
                        selectedFile.equals(root)
                                ? "."
                                : root.toPath()
                                        .relativize(selectedFile.toPath())
                                        .toString());
    }

    @Override
    public void render0(VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        flushPendingDeselection(System.nanoTime());
        refreshFromDisk();
        if (dirty) {
            rows.setSelected(null);
            rows.clearChildren();
            Set<File> visibleFiles = new HashSet<>();
            if (tree == null) {
                rows.addDrawableChild(factory.createText("widget.file.loading", 0, 0, rows.getWidth(), ROW_HEIGHT));
            } else if (tree.error() != null) {
                rows.addDrawableChild(factory.createText(tree.error(), 0, 0, rows.getWidth(), ROW_HEIGHT));
            } else {
                for (Node child : tree.children()) addNode(child, 0, visibleFiles);
            }
            nameDelegates.keySet().retainAll(visibleFiles);
            if (renameEdit != null
                    && !nameDelegates.containsKey(renameEdit.entry().file())) cancelRename(renameEdit);
            if (selectedEntry != null) select(selectedEntry.file(), selectedEntry.directory());
            dirty = false;
        }
        if (renameEdit != null) renameEdit.input().setFocused(true);
        super.render0(context, mouseX, mouseY, delta, disableSelect);
    }

    private void requestDiskRefresh() {
        lastRefreshStarted = 0;
        dirty = true;
    }

    private void refreshFromDisk() {
        if (refreshTask != null && refreshTask.isDone()) {
            Snapshot snapshot;
            try {
                snapshot = refreshTask.join();
                refreshError = null;
            } catch (CompletionException exception) {
                snapshot = null;
                refreshError = Text.translatable("widget.file.refresh_failed").formatted(Formatting.RED);
            }
            refreshTask = null;
            if (snapshot != null) {
                if (!Objects.equals(tree, snapshot.tree())) {
                    tree = snapshot.tree();
                    dirty = true;
                }
                // A worker result must not invalidate a newer selection.
                if (Objects.equals(getSelectedFile(), snapshot.selection())) {
                    if (!snapshot.selectionExists()) clearSelection();
                    else if (getSelectedFile() != null) select(getSelectedFile(), snapshot.selectionDirectory());
                }
            }
        }
        long now = System.nanoTime();
        if (refreshTask == null && (lastRefreshStarted == 0 || now - lastRefreshStarted >= REFRESH_INTERVAL_NANOS)) {
            Set<File> openDirectories = Set.copyOf(expanded);
            File selection = getSelectedFile();
            lastRefreshStarted = now;
            // No UI state is read or changed by the worker; only immutable snapshots cross threads.
            refreshTask = CompletableFuture.supplyAsync(() -> new Snapshot(
                    scan(root, openDirectories, new HashSet<>()),
                    selection,
                    selection != null && selection.exists(),
                    selection != null && selection.isDirectory()));
        }
    }

    private static Node scan(File file, Set<File> openDirectories, Set<Path> ancestors) {
        boolean directory = file.isDirectory();
        if (!file.exists()) return new Node(file, directory, false, List.of(), "widget.file.missing");
        if (!directory || !openDirectories.contains(file)) {
            return new Node(file, directory, true, List.of(), null);
        }
        Path realPath;
        try {
            realPath = file.toPath().toRealPath();
        } catch (IOException | SecurityException exception) {
            return new Node(file, true, true, List.of(), "widget.file.unreadable");
        }
        if (!ancestors.add(realPath)) return new Node(file, true, true, List.of(), "widget.file.cycle");
        try {
            File[] files = file.listFiles();
            if (files == null) return new Node(file, true, true, List.of(), "widget.file.unreadable");
            List<Node> children = new ArrayList<>();
            for (File child : files) children.add(scan(child, openDirectories, ancestors));
            children.sort(Comparator.comparing(Node::directory)
                    .reversed()
                    .thenComparing(node -> node.file().getName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(node -> node.file().getName()));
            return new Node(file, true, true, List.copyOf(children), null);
        } finally {
            ancestors.remove(realPath);
        }
    }

    private void addNode(Node node, int depth, Set<File> visibleFiles) {
        int indent = Math.min(depth, (rows.getWidth() - 64) / BUTTON_SIZE) * BUTTON_SIZE;
        int width = rows.getWidth() - indent;
        // Indent the entire row, starting at its button slot.
        SubScreenWidget row = new SubScreenWidget(indent, 0, width, ROW_HEIGHT);
        visibleFiles.add(node.file());
        FileNameWidget delegate = nameDelegates.computeIfAbsent(
                node.file(), file -> new FileNameWidget(BUTTON_SIZE, width - BUTTON_SIZE));
        SelectBackgroundElement selection = factory.createSelectorTab(
                ValueAccessor.of(() -> selectedEntry != null && selectedEntry.delegate() == delegate, selected -> {
                    if (selected) select(node.file(), node.directory());
                    else select(null, false);
                }));
        TooltipHandler prefixTooltips =
                TooltipHandler.of(node.directory() ? List.of(Text.translatable("widget.file.expand")) : List.of());
        row.addDrawableChild(ExecutableWidget.instance(0, 0, BUTTON_SIZE, ROW_HEIGHT)
                .setElementHandler(new AbstractElement()
                        .withElement(factory.createButton(
                                TextProvider.of(Text.empty()),
                                prefixTooltips,
                                (element, widget, button) -> {
                                    if (button != 0
                                            || !node.exists()
                                            || !node.file().exists()) return false;
                                    if (node.directory()) {
                                        resetClickSequence();
                                        if (!expanded.remove(node.file())) {
                                            expanded.add(node.file());
                                            requestDiskRefresh();
                                        }
                                        dirty = true;
                                    } else {
                                        clickEntry(node, selection);
                                    }
                                    return true;
                                },
                                () -> node.directory() && expanded.contains(node.file()),
                                () -> node.directory() && expanded.contains(node.file())))
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, highlight) -> {
                            context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                            Identifier icon = node.directory()
                                    ? (expanded.contains(node.file()) ? OPEN_FOLDER_ICON_TEXTURE : FOLDER_ICON_TEXTURE)
                                    : FILE_ICON_TEXTURE;
                            context.getMatrices().pushMatrix();
                            context.getMatrices().translate((BUTTON_SIZE - 8) / 2.0F, (ROW_HEIGHT - 8) / 2.0F);
                            context.getMatrices().scale(0.5F, 0.5F);
                            context.drawTexture(icon, 0, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
                            context.getMatrices().popMatrix();
                            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        })
                        .withTooltips(prefixTooltips)));
        String name = node.file().getName();
        Text label = Text.literal((name.isEmpty() ? node.file().getPath() : name) + (node.directory() ? "/" : ""))
                .formatted(node.directory() ? Formatting.YELLOW : Formatting.WHITE);
        if (node.error() != null) label = label.copy().append(Text.literal(" !").formatted(Formatting.RED));
        List<Text> tooltips = new ArrayList<>();
        if (node.error() != null) tooltips.add(Text.translatable(node.error()).formatted(Formatting.RED));
        // File icons and folder buttons occupy the same slot, keeping sibling names aligned.
        if (renameEdit == null || renameEdit.entry().delegate() != delegate) {
            delegate.setContentDelegate(ExecutableWidget.instance(0, 0, width - BUTTON_SIZE, ROW_HEIGHT)
                    .setElementHandler(selection
                            .withElement(factory.createText(
                                    TextProvider.of(label), TooltipHandler.of(tooltips), TextAlign.LEFT))
                            .withTooltips(TooltipHandler.of(tooltips))
                            .withInputHandler((element, mouseX, mouseY, button) -> {
                                if (button != 0) return false;
                                if (node.exists() && node.file().exists()) clickEntry(node, selection);
                                return true;
                            })));
        }
        row.addDrawableChild(delegate);
        rows.addDrawableChild(row);
        if (node.directory() && expanded.contains(node.file())) {
            for (Node child : node.children()) addNode(child, depth + 1, visibleFiles);
        }
    }

    private class FileNameWidget extends ContentDelegateWidget<DrawableWidget> {
        private Matrix4f inverseTransform;

        private FileNameWidget(int x, int width) {
            super(x, 0, width, ROW_HEIGHT);
        }

        @Override
        public void renderInDefaultMatrix(
                VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
            if ((selectedEntry != null && selectedEntry.delegate() == this)
                    || (renameEdit != null && renameEdit.entry().delegate() == this)) {
                inverseTransform = new Matrix4f(context.getMatrices().peek3D()).invert();
            }
            super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        }
    }

    private record SelectedEntry(File file, boolean directory, FileNameWidget delegate) {}

    private record RenameEdit(
            SelectedEntry entry,
            DrawableWidget original,
            DrawableWidget input,
            BaseAttrKeyValue<String> name,
            Screen screen) {}

    private record Node(File file, boolean directory, boolean exists, List<Node> children, String error) {}

    private record Snapshot(Node tree, File selection, boolean selectionExists, boolean selectionDirectory) {}
}
