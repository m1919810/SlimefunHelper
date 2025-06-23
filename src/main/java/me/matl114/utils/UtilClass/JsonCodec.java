package me.matl114.utils.UtilClass;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public interface JsonCodec<T> extends JsonSerializer<T>, JsonDeserializer<T> {
}
