package me.matl114.managers.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.*;
import me.matl114.utils.Debug;

public class JsonFileStorageImpl extends FileStorageImpl {
    private JsonElement data;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public JsonFileStorageImpl(File file) {
        super(file);
        // 确保父目录存在
        read(); // 初始读取
    }

    @Override
    public <T, W extends T> W as(DynamicOps<T> ops) {
        // 直接返回存储的数据对象，忽略 ops

        return ((W) JsonOps.INSTANCE.convertTo(ops, this.data));
    }

    @Override
    public <T, W extends T> W asReadOnly(DynamicOps<T> ops) {
        // 返回不可修改的视图（如果 data 是 Map/List 可包装，此处简单返回）
        return (ops == JsonOps.INSTANCE ? (W) this.data : (W) JsonOps.INSTANCE.convertTo(ops, this.data));
    }

    @Override
    public <T> void write(T value, DynamicOps<T> ops) {
        // 用新值替换内部数据
        this.data = ops.convertTo(JsonOps.INSTANCE, value);
        this.dirty = true;
    }

    @Override
    public <W> DataResult<W> read(Codec<W> codec) {
        return codec.parse(JsonOps.INSTANCE, this.data);
    }

    @Override
    public <W> DataResult<?> write(Codec<W> codec, W value) {
        DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, value);
        encoded.result().ifPresent(result -> write(result, JsonOps.INSTANCE));
        return encoded;
    }

    @Override
    public void write() {
        ensureParentDir();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
            dirty = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void read() {
        if (!file.exists()) {
            Debug.info("Creating new JsonStorage file at", this.file);
            data = new JsonObject();
            write();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            data = GSON.fromJson(reader, JsonElement.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dirty = false;
    }
}
