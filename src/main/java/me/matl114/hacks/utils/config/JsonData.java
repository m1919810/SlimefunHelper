package me.matl114.hacks.utils.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.AttrKeyValues;
import net.minecraft.util.dynamic.Codecs;

public record JsonData(JsonElement data) implements NBTParsable<JsonData> {
    public static final NBTType<JsonData> TYPE = new NBTType<>(
            JsonData.class,
            Codecs.JSON_ELEMENT.xmap(JsonData::new, JsonData::data),
            BaseAttrKeyValue.getWidgetFactory(),
            AttrKeyValues.JSON_ELEMENT_FACTORY.concat(WrapperFactory.of(JsonData::new, JsonData::data)),
            new JsonData(new JsonObject()));

    @Override
    public NBTType<JsonData> type() {
        return TYPE;
    }

    @Nullable
    public JsonObject jsonObject() {
        return data instanceof JsonObject ? (JsonObject) data : null;
    }

    @Nonnull
    public JsonObject jsonOrCreate() {
        return data instanceof JsonObject ? (JsonObject) data : new JsonObject();
    }
}
