package me.matl114.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class NetworkUtils {
    public static ByteBuf createBytebuf() {
        return Unpooled.buffer();
    }
}
