package me.matl114.utils;

import java.awt.*;
import java.io.IOException;
import me.matl114.utils.process.NotificationServerProcess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

public class WindowUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final Util.OperatingSystem OP = Util.getOperatingSystem();
    private static final String DEFAULT_TITLE = "SlimefunHelper";
    private static final String MESSAGE_BOX_SCRIPT =
            "Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show($env:SLIMEFUNHELPER_MESSAGE, $env:SLIMEFUNHELPER_TITLE) | Out-Null";

    public static boolean isWindowsSystem() {
        return OP == Util.OperatingSystem.WINDOWS;
    }

    public static boolean createScriptNotificationWindow(String title, String message) {
        if (!isWindowsSystem()) {
            return false;
        }
        String actualTitle = normalizeTitle(title);
        String actualMessage = normalizeMessage(message);
        return runAsync("sfh-window-notification", () -> showWindowsMessageBox(actualTitle, actualMessage));
    }

    public static boolean createNotificationTrayWindow(String title, String message) {
        return NotificationServerProcess.Bootstrap.notify(title, message);
    }

    private static boolean runAsync(String threadName, Runnable task) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? DEFAULT_TITLE : title;
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message;
    }

    private static void showWindowsMessageBox(String title, String message) {
        startWindowsProcess(MESSAGE_BOX_SCRIPT, title, message);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void startWindowsProcess(String script, String title, String message) {
        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script);
        builder.environment().put("SLIMEFUNHELPER_TITLE", title);
        builder.environment().put("SLIMEFUNHELPER_MESSAGE", message);
        try {
            builder.start();
        } catch (IOException ignored) {
        }
    }
}
