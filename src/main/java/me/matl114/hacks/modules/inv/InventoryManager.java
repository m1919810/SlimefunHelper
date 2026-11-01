package me.matl114.hacks.modules.inv;

import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;

public class InventoryManager extends BaseModule {
    public int lastSyncId = 0;

    public HandledScreen<?> currentOpenScreen;
    public ScreenHandler currentOpenScreenHandler;

    public static InventoryManager INSTANCE;

    public InventoryManager() {
        super("InventoryManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(OpenScreenS2CPacket.class), this::onOpenScreen);
        registerListener(Listener.getPacketPoint().getChannel(OpenMountScreenS2CPacket.class), this::onOpenMountScreen);
        registerListener(Listener.getPostOpenHandledScreen(), this::onOpenHandledScreen, Integer.MIN_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(CloseScreenS2CPacket.class),
                this::onCloseScreen,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(CloseHandledScreenC2SPacket.class),
                this::onCloseHandledScreen,
                Integer.MAX_VALUE);

        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ScreenHandlerSlotUpdateS2CPacket.class),
                this::onScreenHandlerSlotUpdate);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(InventoryS2CPacket.class), this::onInventoryUpdate);

        registerListener(Listener.getGameJoinPoint(), this::onGameJoin);
        registerListener(Listener.getServerLeavePoint(), this::resetTrackedScreen);
        registerListener(Listener.getWorldSwitchPoint(), this::resetTrackedScreen);
    }

    public ScreenHandler getCurrentServerScreenHandler(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        return currentOpenScreenHandler != null ? currentOpenScreenHandler : player.currentScreenHandler;
    }

    public Screen getCurrentServerScreen(ClientPlayerEntity player) {
        return getCurrentServerOpeningScreen(player);
    }

    public HandledScreen<?> getCurrentServerOpeningScreen(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        HandledScreen<?> nowScreen = currentOpenScreen;
        if (nowScreen == null && MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> handled) {
            nowScreen = handled;
        }
        return nowScreen instanceof CreativeInventoryScreen ? null : nowScreen;
    }

    private void resetTrackedScreen() {
        currentOpenScreen = null;
        currentOpenScreenHandler = null;
    }

    private void onOpenScreen(Event<OpenScreenS2CPacket> packet) {
        lastSyncId = packet.context.getSyncId();
    }

    private void onOpenMountScreen(Event<OpenMountScreenS2CPacket> packet) {
        lastSyncId = packet.context.getSyncId();
    }

    private void onOpenHandledScreen(Event<HandledScreen<?>> event) {
        resetTrackedScreen();
        HandledScreen<?> screen = event.context;
        if (screen instanceof CreativeInventoryScreen) {
            return;
        }
        currentOpenScreenHandler = screen.getScreenHandler();
        if (screen.getScreenHandler() == mc.player.currentScreenHandler) {
            currentOpenScreen = screen;
        } else {
            currentOpenScreen = null;
        }
    }

    private void onCloseScreen(Event<CloseScreenS2CPacket> event) {
        if (event.isCancelled()) return;
        int syncId = event.context.getSyncId();
        if (currentOpenScreenHandler != null && currentOpenScreenHandler.syncId == syncId) {
            resetTrackedScreen();
        }
    }

    private void onCloseHandledScreen(Event<CloseHandledScreenC2SPacket> event) {
        if (event.isCancelled()) return;
        if (currentOpenScreenHandler != null && currentOpenScreenHandler.syncId == event.context.getSyncId()) {
            resetTrackedScreen();
        }
    }

    private void onScreenHandlerSlotUpdate(Event<ScreenHandlerSlotUpdateS2CPacket> event) {
        ClientPlayerEntity player = mc.player;
        ScreenHandler handler = currentOpenScreenHandler;
        if (player != null
                && handler != null
                && handler != player.currentScreenHandler
                && event.context.getSyncId() == handler.syncId) {
            handler.setStackInSlot(event.context.getSlot(), event.context.getRevision(), event.context.getStack());
        }
    }

    private void onInventoryUpdate(Event<InventoryS2CPacket> event) {
        ClientPlayerEntity player = mc.player;
        ScreenHandler handler = currentOpenScreenHandler;
        if (player != null
                && handler != null
                && handler != player.currentScreenHandler
                && event.context.syncId() == handler.syncId) {
            handler.updateSlotStacks(event.context.revision(), event.context.contents(), event.context.cursorStack());
        }
    }

    private void onGameJoin() {
        lastSyncId = 0;
        resetTrackedScreen();
    }

    public void executePredictInventoryAction(Inventory topInventory, Consumer<ScreenHandler> callback) {
        int nextPredictedIndex = (lastSyncId % 100) + 1;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ScreenHandler fakeScreenHandler = new GenericContainerScreenHandler(
                ScreenUtils.getGenericScreenType(topInventory.size()),
                nextPredictedIndex,
                player.getInventory(),
                topInventory,
                ((topInventory.size() - 1) / 9) + 1);
        ScreenHandler handler = player.currentScreenHandler;
        try {
            player.currentScreenHandler = fakeScreenHandler;
            callback.accept(fakeScreenHandler);
        } finally {
            player.currentScreenHandler = handler;
        }
    }
}
