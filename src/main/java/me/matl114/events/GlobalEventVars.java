package me.matl114.events;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashReport;

public class GlobalEventVars {
    public static CrashReport crashReport = null;
    public static Event<MinecraftClient> crashReportEvent = null;
    public static AtomicInteger cmd = new AtomicInteger(0);

    public int getModCnt() {
        return cmd.incrementAndGet();
    }
}
