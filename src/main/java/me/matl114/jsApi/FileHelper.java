package me.matl114.jsApi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import me.matl114.utils.ApiMethod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.crash.CrashException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

public class FileHelper {
    @ApiMethod
    public static File getConfigFolder() {
        return FabricLoader.getInstance().getConfigDirectory();
    }

    @ApiMethod
    public static File getGameFolder() {
        return FabricLoader.getInstance().getGameDirectory();
    }

    @ApiMethod
    public static File getFile(String path) {
        return new File(path);
    }
    /**
     * Gets or creates a file at the specified path.
     * If the parent directory doesn't exist, it will be created.
     * If the file doesn't exist, it will be created.
     *
     * @param path The path to the file
     * @return The File object
     * @throws IOException if the file or directory cannot be created
     */
    @ApiMethod
    public static File getOrCreateFile(String path) throws IOException {
        File file = new File(path);
        return getOrCreateFile(file);
    }

    /**
     * Gets or creates a file.
     * If the parent directory doesn't exist, it will be created.
     * If the file doesn't exist, it will be created.
     *
     * @param file The file to get or create
     * @return The File object
     * @throws IOException if the file or directory cannot be created
     */
    @ApiMethod
    public static File getOrCreateFile(File file) throws IOException {
        if (!file.getParentFile().exists()) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        if (!file.exists()) {
            if (file.createNewFile()) {
                return file;
            } else {
                throw new IOException(file.toPath().toString() + " create failed");
            }
        } else {
            return file;
        }
    }

    /**
     * Ensures that the parent directory of the specified file exists.
     * Creates the parent directory if it doesn't exist.
     *
     * @param file The file whose parent directory should be ensured
     * @throws IOException if the parent directory cannot be created
     */
    @ApiMethod
    public static void ensureParentDir(File file) throws IOException {
        if (!file.getParentFile().exists()) {
            Files.createDirectories(file.getParentFile().toPath());
        }
    }

    /**
     * Copies a file from one location to another.
     * The destination file will be created if it doesn't exist, or overwritten if it does.
     *
     * @param from The source file
     * @param to The destination path
     * @throws IOException if the source file doesn't exist or the copy operation fails
     */
    @ApiMethod
    public static void copyFile(File from, String to) throws IOException {
        if (from.exists()) {
            File toFile = new File(to);
            ensureParentDir(toFile);
            Files.copy(from.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IOException(from + " does not exist");
        }
    }

    /**
     * Copies a resource from the classpath to a file location.
     * The destination file will be created if it doesn't exist, or overwritten if it does.
     *
     * @param resource The classpath resource path (without leading slash)
     * @param to The destination file path
     * @throws IOException if the resource doesn't exist or the copy operation fails
     */
    public static void copyFile(String resource, String to) throws IOException {
        copyFile(FileHelper.class, resource, to);
    }

    /**
     * Copies a resource from the classpath to a file location.
     * The destination file will be created if it doesn't exist, or overwritten if it does.
     *
     * @param clazz The classpath in jar
     * @param resource The classpath resource path (without leading slash)
     * @param to The destination file path
     * @throws IOException if the resource doesn't exist or the copy operation fails
     */
    public static void copyFile(Class<?> clazz, String resource, String to) throws IOException {
        File toFile = new File(to);
        ensureParentDir(toFile);
        Files.copy(clazz.getResourceAsStream("/" + resource), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Recursively copies a folder from the classpath to a destination directory.
     * This method handles both regular directories and JAR file resources.
     *
     * @param from The classpath resource directory path
     * @param toPath The destination directory path
     * @throws IOException if the source directory doesn't exist or the copy operation fails
     */
    public static void copyFolderRecursively(String from, String toPath) throws IOException {
        copyFolderRecursively(FileHelper.class, from, toPath);
    }

    /**
     * Recursively copies a folder from the classpath to a destination directory.
     * This method handles both regular directories and JAR file resources.
     *
     * @param clazz class in the jar file
     * @param from The classpath resource directory path
     * @param toPath The destination directory path
     * @throws IOException if the source directory doesn't exist or the copy operation fails
     */
    public static void copyFolderRecursively(Class<?> clazz, String from, String toPath) throws IOException {
        ClassLoader classLoader = clazz.getClassLoader();
        URI uri = null;
        try {
            uri = classLoader.getResource(from).toURI();
        } catch (URISyntaxException e) {
            throw new IOException(e.getMessage());
        } catch (NullPointerException e) {
            throw new IOException(e.getMessage());
        }

        if (uri == null) {
            throw new IOException("something is wrong directory or files missing");
        }

        /** jar case */
        URL jar = clazz.getProtectionDomain().getCodeSource().getLocation();
        // jar.toString() begins with file:
        // i want to trim it out...
        Path jarFile = Paths.get(
                URLDecoder.decode(jar.toString(), StandardCharsets.UTF_8).substring("file:".length()));
        FileSystem fs = FileSystems.newFileSystem(jarFile, Map.of());
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(fs.getPath(from));
        Path to = new File(toPath).toPath();
        for (Path p : directoryStream) {
            InputStream is = clazz.getResourceAsStream("/" + p.toString());
            Path target = to.resolve(p.toString());

            if (!Files.exists(target)) {
                if (!Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                Files.copy(is, target);
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param folder The directory to delete
     * @return true if the deletion was successful, false otherwise
     */
    @ApiMethod
    public static boolean deleteDirectory(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    // Recursive call to delete files and subfolders
                    if (!deleteDirectory(file)) {
                        return false;
                    }
                }
            }
        }

        // Delete the folder itself
        return folder.delete();
    }

    /**
     * Gets an InputStream for a classpath resource.
     *
     * @param resource The classpath resource path (without leading slash)
     * @return An InputStream for the resource, or null if the resource doesn't exist
     */
    public static InputStream readResource(String resource) {
        return FileHelper.class.getResourceAsStream("/" + resource);
    }

    /**
     * Gets an InputStream for a classpath resource.
     *
     * @param clazz The classpath in jar file
     * @param resource The classpath resource path (without leading slash)
     * @return An InputStream for the resource, or null if the resource doesn't exist
     */
    public static InputStream readResource(Class<?> clazz, String resource) {
        return clazz.getResourceAsStream("/" + resource);
    }

    /**
     * Gets an InputStream for a file at the specified path.
     *
     * @param path The file path
     * @return An InputStream for the file
     * @throws RuntimeException if the file doesn't exist or cannot be opened
     */
    public static InputStream readFile(String path) {
        File file = new File(path);
        return readFile(file);
    }

    /**
     * Gets an InputStream for a file.
     *
     * @param file The file to read
     * @return An InputStream for the file
     * @throws RuntimeException if the file doesn't exist or cannot be opened
     */
    public static InputStream readFile(File file) {
        if (!isAFile(file)) {
            throw new RuntimeException("File does not exists: " + file);
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException fil) {
            throw new RuntimeException(fil);
        }
    }

    /**
     * Reads a classpath resource as a string using UTF-8 encoding.
     *
     * @param resource The classpath resource path (without leading slash)
     * @return The content of the resource as a string
     * @throws RuntimeException if the resource cannot be read
     */
    public static String readResourceString(String resource) {
        try (var inputStream = readResource(resource)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if a file exists and is a regular file.
     *
     * @param file The file to check
     * @return true if the file exists and is a regular file, false otherwise
     */
    @ApiMethod
    public static boolean isAFile(File file) {
        return file.exists() && file.isFile();
    }

    /**
     * Checks if a file exists and is a directory.
     *
     * @param file The file to check
     * @return true if the file exists and is a directory, false otherwise
     */
    @ApiMethod
    public static boolean isAFolder(File file) {
        return file.exists() && file.isDirectory();
    }

    /**
     * Reads a classpath resource and parses it as JSON.
     *
     * @param resource The classpath resource path (without leading slash)
     * @return The parsed JSON element
     * @throws RuntimeException if the resource cannot be read or parsed
     */
    public static JsonElement readResourceJson(String resource) {
        try (InputStream is = readResource(resource)) {
            JsonReader reader = new JsonReader(new InputStreamReader(is));
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a file as a string using UTF-8 encoding.
     *
     * @param path The file path
     * @return The content of the file as a string
     * @throws RuntimeException if the file cannot be read
     */
    @ApiMethod
    public static String readFileString(String path) {
        return readFileString(new File(path));
    }

    /**
     * Reads a file as a string using UTF-8 encoding.
     *
     * @param str The file to read
     * @return The content of the file as a string
     * @throws RuntimeException if the file cannot be read
     */
    @ApiMethod
    public static String readFileString(File str) {
        try (var inputStream = readFile(str)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public static void writeFileString(File str, String string) {
        writeFileString(str, string, false);
    }

    @ApiMethod
    public static void writeFileString(File str, String string, boolean append) {
        try {
            Path path = str.toPath();
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            if (append) {
                Files.writeString(path, string, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(path, string, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a file and parses it as JSON.
     *
     * @param str The file path
     * @return The parsed JSON element
     * @throws RuntimeException if the file cannot be read or parsed
     */
    @ApiMethod
    public static JsonElement readFileJson(String str) {
        return readFileJson(new File(str));
    }

    /**
     * Reads a file and parses it as JSON.
     *
     * @param str The file to read
     * @return The parsed JSON element
     * @throws RuntimeException if the file cannot be read or parsed
     */
    @ApiMethod
    public static JsonElement readFileJson(File str) {
        try (InputStream is = readFile(str)) {
            JsonReader reader = new JsonReader(new InputStreamReader(is));
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Gson PRETTY_GSON = createDefaultGsonBuilder() // 可选：序列化null值
            .create();

    private static final Gson COMPACT_GSON = createCompactGsonBuilder().create();

    /**
     * Writes a JsonElement to a file with pretty formatting.
     *
     * @param file The file to write to
     * @param json The JsonElement to write
     */
    @ApiMethod
    public static void writeJson(File file, JsonElement json) {
        writeJson(file, json, true);
    }

    /**
     * Writes a JsonElement to a file.
     *
     * @param file The file to write to
     * @param json The JsonElement to write
     * @param pretty Whether to use pretty formatting
     */
    @ApiMethod
    public static void writeJson(File file, JsonElement json, boolean pretty) {
        try {
            ensureParentDir(file);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                Gson gson = pretty ? PRETTY_GSON : COMPACT_GSON;
                gson.toJson(json, writer);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Writes a JsonElement to a file with pretty formatting.
     *
     * @param path The path to the file
     * @param json The JsonElement to write
     */
    @ApiMethod
    public static void writeJson(String path, JsonElement json) {
        writeJson(new File(path), json, true);
    }

    /**
     * Writes a JsonElement to a file.
     *
     * @param path The path to the file
     * @param json The JsonElement to write
     * @param pretty Whether to use pretty formatting
     *
     */
    @ApiMethod
    public static void writeJson(String path, JsonElement json, boolean pretty) {
        writeJson(new File(path), json, pretty);
    }

    /**
     * Writes a JsonElement to a file with custom Gson configuration.
     *
     * @param file The file to write to
     * @param json The JsonElement to write
     * @param gson Custom Gson instance for serialization
     */
    @ApiMethod
    public static void writeJson(File file, JsonElement json, Gson gson) {
        try {
            ensureParentDir(file);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(json, writer);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Appends a JsonElement to an existing JSON file.
     * If the file doesn't exist, creates it with the given JsonElement.
     * Note: This is only meaningful for JSON arrays or objects that can be merged.
     *
     * @param file The file to append to
     * @param json The JsonElement to append
     * @param pretty Whether to use pretty formatting
     * @throws IOException if the file cannot be read or written
     */
    @ApiMethod
    public static void appendJson(File file, JsonElement json, boolean pretty) throws IOException {
        JsonElement existing = file.exists() ? readFileJson(file) : null;

        if (existing == null || existing.isJsonNull()) {
            // File doesn't exist or is empty, create new
            writeJson(file, json, pretty);
        } else if (existing.isJsonArray() && json.isJsonArray()) {
            // Merge arrays
            existing.getAsJsonArray().addAll(json.getAsJsonArray());
            writeJson(file, existing, pretty);
        } else if (existing.isJsonObject() && json.isJsonObject()) {
            // Merge objects (overwrites duplicate keys)
            json.getAsJsonObject().entrySet().forEach(entry -> existing.getAsJsonObject()
                    .add(entry.getKey(), entry.getValue()));
            writeJson(file, existing, pretty);
        } else {
            // Cannot merge different types, replace
            writeJson(file, json, pretty);
        }
    }

    private static final Yaml YAML = createDefaultYaml();

    private static final Yaml COMPACT_YAML = createCompressedYaml();

    @ApiMethod
    public static <T> T readYamlString(String yamlString) {
        return YAML.load(yamlString);
    }

    public static <T> T readYaml(InputStream inputStream) {
        return YAML.load(inputStream);
    }

    @ApiMethod
    public static <T> T readFileYaml(File file) {
        try (var fileReader = readFile(file)) {
            return readYaml(fileReader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public static <T> T readFileYaml(String filePath) {
        return readFileYaml(new File(filePath));
    }

    public static <T> T readResourceYaml(String resource) {
        try (var resourceIn = readResource(resource)) {
            return readYaml(resourceIn);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public static <T> T readYaml(InputStream inputStream, Class<T> clazz) {
        return YAML.loadAs(inputStream, clazz);
    }

    @ApiMethod
    public static <T> T readFileYaml(File file, Class<T> clazz) {
        try (var fileReader = readFile(file)) {
            return readYaml(fileReader, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public static <T> T readFileYaml(String filePath, Class<T> clazz) {
        return readFileYaml(new File(filePath), clazz);
    }

    public static <T> T readResourceYaml(String resource, Class<T> tClass) {
        try (var resourceIn = readResource(resource)) {
            return readYaml(resourceIn, tClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public static <T> String dumpYaml(T object) {
        return YAML.dump(object);
    }

    @ApiMethod
    public static <T, W> String dumpYamlAsMap(T object) {
        return YAML.dumpAsMap(object);
    }

    @ApiMethod
    public static <T> void writeYaml(File path, T object, boolean pretty) {
        try {
            ensureParentDir(path);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
                (pretty ? YAML : COMPACT_YAML).dump(object, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ApiMethod
    public NbtElement readNbtFile(String filepath, boolean compressed) throws IOException {
        return readNbtFile(new File(filepath), compressed);
    }

    @ApiMethod
    public NbtElement readNbtFile(File file, boolean compressed) throws IOException {
        if (!file.exists()) {
            throw new IOException("NBT file does not exist: " + file);
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            if (compressed) {
                return NbtIo.readCompressed(inputStream, NbtSizeTracker.ofUnlimitedBytes());
            } else {
                try (DataInputStream dataInput = new DataInputStream(inputStream)) {
                    return NbtIo.readCompound(dataInput, NbtSizeTracker.ofUnlimitedBytes());
                }
            }
        } catch (CrashException e) {
            throw new IOException("Failed to parse NBT file: " + file, e.getCause());
        }
    }

    @ApiMethod
    public void writeNbtFile(String file, NbtCompound nbt, boolean compressed) throws IOException {
        writeNbtFile(new File(file), nbt, compressed);
    }

    @ApiMethod
    public void writeNbtFile(File file, NbtCompound nbt, boolean compressed) throws IOException {
        ensureParentDir(file);

        try (OutputStream outputStream = new FileOutputStream(file)) {
            if (compressed) {
                NbtIo.writeCompressed(nbt, outputStream);
            } else {
                try (DataOutputStream dataOutput = new DataOutputStream(outputStream)) {
                    NbtIo.writeCompound(nbt, dataOutput);
                }
            }
        }
    }

    /**
     * Reads the entire binary content of a file into a byte array.
     * This is suitable for small to medium-sized files.
     *
     * @param file The file to read
     * @return The binary content as a byte array
     * @throws RuntimeException if the file cannot be read
     */
    @ApiMethod
    public static byte[] readBinaryFile(File file) {
        if (!isAFile(file)) {
            throw new RuntimeException("File does not exist: " + file);
        }

        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read binary file: " + file, e);
        }
    }

    /**
     * Reads the entire binary content of a file into a byte array.
     *
     * @param path The path to the file
     * @return The binary content as a byte array
     */
    @ApiMethod
    public static byte[] readBinaryFile(String path) {
        return readBinaryFile(new File(path));
    }

    /**
     * Writes binary data to a file.
     *
     * @param file The file to write to
     * @param data The binary data to write
     * @param append Whether to append to the file (false will overwrite)
     * @throws RuntimeException if the file cannot be written
     */
    @ApiMethod
    public static void writeBinaryFile(File file, byte[] data, boolean append) {
        try {
            ensureParentDir(file);
            StandardOpenOption[] options = append
                    ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

            Files.write(file.toPath(), data, options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write binary file: " + file, e);
        }
    }

    /**
     * Writes binary data to a file (overwrites existing content).
     *
     * @param file The file to write to
     * @param data The binary data to write
     */
    @ApiMethod
    public static void writeBinaryFile(File file, byte[] data) {
        writeBinaryFile(file, data, false);
    }

    /**
     * Writes binary data to a file.
     *
     * @param path The path to the file
     * @param data The binary data to write
     * @param append Whether to append to the file (false will overwrite)
     */
    @ApiMethod
    public static void writeBinaryFile(String path, byte[] data, boolean append) {
        writeBinaryFile(new File(path), data, append);
    }

    // =========================== 辅助方法 ===========================
    /**
     * Creates a custom GsonBuilder with default settings.
     * Can be used to create custom Gson instances for specific needs.
     *
     * @return A configured GsonBuilder
     */
    private static GsonBuilder createDefaultGsonBuilder() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls();
    }

    /**
     * Creates a compact GsonBuilder (no pretty printing).
     *
     * @return A configured GsonBuilder for compact output
     */
    private static GsonBuilder createCompactGsonBuilder() {
        return new GsonBuilder().disableHtmlEscaping().serializeNulls();
    }

    protected static class CustomConstructor extends Constructor {

        public CustomConstructor(LoaderOptions loadingConfig) {
            this(loadingConfig, CustomConstructor.class.getClassLoader());
        }

        public CustomConstructor(LoaderOptions loadingConfig, ClassLoader lookup) {
            super(loadingConfig);
            this.loader = lookup;
        }

        ClassLoader loader;

        @Override
        protected Class<?> getClassForName(String name) throws ClassNotFoundException {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException e) {
                return super.getClassForName(name);
            }
        }
    }

    private static Yaml createDefaultYaml() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // Block风格，美观
        dumperOptions.setIndent(2); // 缩进2个空格
        dumperOptions.setPrettyFlow(true); // 美化流风格
        dumperOptions.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak()); // 使用平台换行符

        // Loader配置
        LoaderOptions loaderOptions = new LoaderOptions();

        Representer representer = new Representer(dumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(new CustomConstructor(loaderOptions), representer, dumperOptions, loaderOptions);
    }

    /**
     * 创建压缩的YAML处理器
     * 特性：
     * - 紧凑格式（Flow风格或最小化Block）
     * - 无缩进或最小缩进
     * - 不换行（尽量）
     * - 适合存储或网络传输
     */
    private static Yaml createCompressedYaml() {
        DumperOptions dumperOptions = new DumperOptions();
        LoaderOptions loaderOptions = new LoaderOptions();
        return new Yaml(
                new CustomConstructor(loaderOptions), new Representer(dumperOptions), dumperOptions, loaderOptions);
    }

    /**
     * 创建美观的YAML处理器
     * 特性：
     * - 4空格缩进
     * - 双引号标量
     * - 适合展示和文档
     */
    private static Yaml createPrettyYaml() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(4); // 4空格缩进
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setAllowUnicode(true);
        dumperOptions.setSplitLines(true);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED); // 双引号
        dumperOptions.setWidth(80); // 80字符宽度
        dumperOptions.setLineBreak(DumperOptions.LineBreak.UNIX);

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setAllowRecursiveKeys(false);

        Representer representer = new Representer(dumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        representer.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);

        return new Yaml(new CustomConstructor(loaderOptions), representer, dumperOptions, loaderOptions);
    }

    /**
     * 创建安全的YAML处理器
     * 特性：
     * - 严格的安全限制
     * - 防止YAML炸弹
     * - 适合处理不可信输入
     */
    private static Yaml createSafeYaml() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setAllowUnicode(true);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        // 严格的安全配置
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(10); // 严格限制别名数
        loaderOptions.setAllowRecursiveKeys(false);
        loaderOptions.setProcessComments(false); // 不处理注释

        // 使用安全的Constructor，限制可加载的类
        SafeConstructor constructor = new SafeConstructor(loaderOptions);

        Representer representer = new Representer(dumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        return new Yaml(constructor, representer, dumperOptions, loaderOptions);
    }
}
