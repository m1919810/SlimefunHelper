package me.matl114.renderer.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.matl114.plugins.MainPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontLoader;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public final class LocalFontTextDrawer extends ResourceFontTextDrawer implements AutoCloseable {
    private static final AtomicLong FONT_INSTANCES = new AtomicLong();
    private static final Identifier FONT_LOCATION = MainPlugin.identifier("local-font.ttf");
    public static final float DEFAULT_FONT_SIZE = 11.0F;
    private static final float OVERSAMPLE = 4.0F;
    private static final net.minecraft.client.font.TrueTypeFontLoader.Shift SHIFT =
            new net.minecraft.client.font.TrueTypeFontLoader.Shift(0.0F, 1.0F);

    private final FontStorage storage;
    private final Font provider;
    private final float textHeight;

    public LocalFontTextDrawer(Path path) {
        this(path, DEFAULT_FONT_SIZE);
    }

    public LocalFontTextDrawer(Path path, float fontSize) {
        this(load(path, fontSize, MainPlugin.identifier("local-font/" + FONT_INSTANCES.getAndIncrement())));
    }

    private LocalFontTextDrawer(LoadedFont loadedFont) {
        super(loadedFont.renderer(), loadedFont.fontId());
        this.storage = loadedFont.storage();
        this.provider = loadedFont.provider();
        this.textHeight = loadedFont.fontSize();
    }

    @Override
    public float getTextHeight() {
        return textHeight;
    }

    @Override
    public void close() {
        storage.close();
        provider.close();
    }

    private static LoadedFont load(Path path, float fontSize, Identifier fontId) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Font file does not exist: " + path);
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            FontLoader loader =
                    new net.minecraft.client.font.TrueTypeFontLoader(FONT_LOCATION, fontSize, OVERSAMPLE, SHIFT, "");
            Font provider = loader.build()
                    .left()
                    .orElseThrow(() -> new IllegalStateException("Failed to create TTF loader"))
                    .load(new LocalFontResourceManager(client.getResourceManager(), FONT_LOCATION, path));
            FontStorage storage = new FontStorage(client.getTextureManager(), fontId);
            storage.setFonts(
                    List.of(new Font.FontFilterPair(
                            provider, net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),
                    Set.of());
            TextRenderer renderer = new TextRenderer(id -> storage, false);
            return new LoadedFont(fontId, provider, storage, renderer, fontSize);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load local font: " + path, e);
        }
    }

    public static List<FontFile> scanFonts() {
        Set<Path> roots = new HashSet<>();
        String userHome = System.getProperty("user.home", "");
        String javaHome = System.getProperty("java.home", "");
        String systemRoot = System.getenv("SystemRoot");

        addRoot(roots, Path.of(javaHome, "lib", "fonts"));
        switch (Util.getOperatingSystem()) {
            case WINDOWS -> {
                addRoot(roots, Path.of(userHome, "AppData", "Local", "Microsoft", "Windows", "Fonts"));
                if (systemRoot != null) addRoot(roots, Path.of(systemRoot, "Fonts"));
            }
            case OSX -> {
                addRoot(roots, Path.of(userHome, "Library", "Fonts"));
                addRoot(roots, Path.of("/System/Library/Fonts"));
            }
            default -> {
                addRoot(roots, Path.of(userHome, ".local", "share", "fonts"));
                addRoot(roots, Path.of(userHome, ".fonts"));
                addRoot(roots, Path.of("/usr/share/fonts"));
            }
        }

        Map<String, FontFile> fonts = new HashMap<>();
        Set<Path> visited = new HashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(LocalFontTextDrawer::isTtf)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .filter(visited::add)
                        .map(LocalFontTextDrawer::inspect)
                        .filter(Objects::nonNull)
                        .forEach(
                                font -> fonts.putIfAbsent(font.displayName().toLowerCase(java.util.Locale.ROOT), font));
            } catch (IOException ignored) {
            }
        }
        List<FontFile> result = new ArrayList<>(fonts.values());
        result.sort(Comparator.comparing(FontFile::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(font -> font.path().toString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static Optional<FontFile> findFont(String selection) {
        if (selection == null || selection.isBlank()) return Optional.empty();
        String normalized = selection.trim();
        try {
            Path path = Path.of(normalized).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && isTtf(path)) {
                return Optional.ofNullable(inspect(path));
            }
        } catch (InvalidPathException ignored) {
        }
        List<FontFile> fonts = scanFonts();
        Optional<FontFile> exactName = fonts.stream()
                .filter(font -> font.displayName().equalsIgnoreCase(normalized))
                .findFirst();
        if (exactName.isPresent()) return exactName;
        return fonts.stream()
                .filter(font -> font.path().getFileName().toString().equalsIgnoreCase(normalized)
                        || font.family().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private static FontFile inspect(Path path) {
        try {
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, path.toFile());
            String family = font.getFamily();
            int fontStyle = font.getStyle();
            String style = fontStyle == (java.awt.Font.BOLD | java.awt.Font.ITALIC)
                    ? "Bold Italic"
                    : fontStyle == java.awt.Font.BOLD
                            ? "Bold"
                            : fontStyle == java.awt.Font.ITALIC ? "Italic" : "Regular";
            return new FontFile(family, style, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isTtf(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".ttf");
    }

    private static void addRoot(Set<Path> roots, Path path) {
        if (path != null) roots.add(path.toAbsolutePath().normalize());
    }

    public record FontFile(String family, String style, Path path) {
        public String displayName() {
            return family + " " + style;
        }

        @Override
        public String toString() {
            return displayName() + " (" + path + ")";
        }
    }

    private record LoadedFont(
            Identifier fontId, Font provider, FontStorage storage, TextRenderer renderer, float fontSize) {}

    private static final class LocalFontResourceManager implements ResourceManager {
        private final ResourceManager delegate;
        private final Identifier resourceId;
        private final Path path;
        private final ResourcePack pack;

        private LocalFontResourceManager(ResourceManager delegate, Identifier location, Path path) {
            this.delegate = delegate;
            this.resourceId = location.withPrefixedPath("font/");
            this.path = path;
            this.pack = new LocalFontResourcePack(resourceId, path);
        }

        @Override
        public Set<String> getAllNamespaces() {
            Set<String> namespaces = new HashSet<>(delegate.getAllNamespaces());
            namespaces.add(resourceId.getNamespace());
            return namespaces;
        }

        @Override
        public java.util.Optional<Resource> getResource(Identifier id) {
            if (resourceId.equals(id)) return Optional.of(new Resource(pack, InputSupplier.create(path)));
            return delegate.getResource(id);
        }

        @Override
        public List<Resource> getAllResources(Identifier id) {
            if (resourceId.equals(id)) {
                List<Resource> resources = new ArrayList<>(delegate.getAllResources(id));
                resources.add(new Resource(pack, InputSupplier.create(path)));
                return resources;
            }
            return delegate.getAllResources(id);
        }

        @Override
        public Map<Identifier, Resource> findResources(
                String startingPath, Predicate<Identifier> allowedPathPredicate) {
            Map<Identifier, Resource> resources =
                    new java.util.HashMap<>(delegate.findResources(startingPath, allowedPathPredicate));
            if (resourceId.getPath().startsWith(startingPath) && allowedPathPredicate.test(resourceId)) {
                resources.put(resourceId, new Resource(pack, InputSupplier.create(path)));
            }
            return resources;
        }

        @Override
        public Map<Identifier, List<Resource>> findAllResources(
                String startingPath, Predicate<Identifier> allowedPathPredicate) {
            Map<Identifier, List<Resource>> resources =
                    new java.util.HashMap<>(delegate.findAllResources(startingPath, allowedPathPredicate));
            if (resourceId.getPath().startsWith(startingPath) && allowedPathPredicate.test(resourceId)) {
                List<Resource> matches = new ArrayList<>(resources.getOrDefault(resourceId, List.of()));
                matches.add(new Resource(pack, InputSupplier.create(path)));
                resources.put(resourceId, matches);
            }
            return resources;
        }

        @Override
        public Stream<ResourcePack> streamResourcePacks() {
            return Stream.concat(delegate.streamResourcePacks(), Stream.of(pack));
        }
    }

    private static final class LocalFontResourcePack implements ResourcePack {
        private final Identifier resourceId;
        private final Path path;

        private static final ResourcePackInfo INFO = new ResourcePackInfo(
                "slimefunhelper-local-font",
                Text.literal("SlimefunHelper local font"),
                ResourcePackSource.NONE,
                Optional.empty());

        private LocalFontResourcePack(Identifier resourceId, Path path) {
            this.resourceId = resourceId;
            this.path = path;
        }

        @Override
        public InputSupplier<InputStream> openRoot(String... segments) {
            return null;
        }

        @Override
        public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            return type == ResourceType.CLIENT_RESOURCES && resourceId.equals(id) ? InputSupplier.create(path) : null;
        }

        @Override
        public void findResources(
                ResourceType type, String namespace, String prefix, ResourcePack.ResultConsumer consumer) {
            if (type == ResourceType.CLIENT_RESOURCES
                    && resourceId.getNamespace().equals(namespace)
                    && resourceId.getPath().startsWith(prefix)) {
                consumer.accept(resourceId, InputSupplier.create(path));
            }
        }

        @Override
        public Set<String> getNamespaces(ResourceType type) {
            return Set.of("slimefunhelper");
        }

        @Override
        public <T> T parseMetadata(net.minecraft.resource.metadata.ResourceMetadataSerializer<T> metadataSerializer) {
            return null;
        }

        @Override
        public ResourcePackInfo getInfo() {
            return INFO;
        }

        @Override
        public void close() {}
    }
}
