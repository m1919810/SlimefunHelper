package me.matl114.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;

@ApiMethod
public class NetworkUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static ByteBuf createBytebuf() {
        return Unpooled.buffer();
    }

    public static int generateNextSequence() {
        var re = mc.world.getPendingUpdateManager().incrementSequence();
        int seq = re.sequence;
        re.close();
        return seq;
    }

    public static void restoreSequence(int sequenceRestore) {
        var re = mc.world.getPendingUpdateManager();
        if (re.sequence == sequenceRestore) {
            --re.sequence;
        }
    }
}
