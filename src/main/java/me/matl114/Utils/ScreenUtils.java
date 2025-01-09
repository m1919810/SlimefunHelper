package me.matl114.Utils;

import com.google.common.collect.Maps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.util.Window;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Pair;

import java.util.Map;

public class ScreenUtils {
    private static final Map<ScreenHandlerType<?>, HandledScreens.Provider<?, ?>> PROVIDERS = Maps.newHashMap();
    static {
        register(ScreenHandlerType.GENERIC_9X1, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_9X2, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_9X3, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_9X4, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_9X5, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_9X6, GenericContainerScreen::new);
        register(ScreenHandlerType.GENERIC_3X3, Generic3x3ContainerScreen::new);
        register(ScreenHandlerType.ANVIL, AnvilScreen::new);
        register(ScreenHandlerType.BEACON, BeaconScreen::new);
        register(ScreenHandlerType.BLAST_FURNACE, BlastFurnaceScreen::new);
        register(ScreenHandlerType.BREWING_STAND, BrewingStandScreen::new);
        register(ScreenHandlerType.CRAFTING, CraftingScreen::new);
        register(ScreenHandlerType.ENCHANTMENT, EnchantmentScreen::new);
        register(ScreenHandlerType.FURNACE, FurnaceScreen::new);
        register(ScreenHandlerType.GRINDSTONE, GrindstoneScreen::new);
        register(ScreenHandlerType.HOPPER, HopperScreen::new);
        register(ScreenHandlerType.LECTERN, LecternScreen::new);
        register(ScreenHandlerType.LOOM, LoomScreen::new);
        register(ScreenHandlerType.MERCHANT, MerchantScreen::new);
        register(ScreenHandlerType.SHULKER_BOX, ShulkerBoxScreen::new);
        register(ScreenHandlerType.SMITHING, SmithingScreen::new);
        register(ScreenHandlerType.SMOKER, SmokerScreen::new);
        register(ScreenHandlerType.CARTOGRAPHY_TABLE, CartographyTableScreen::new);
        register(ScreenHandlerType.STONECUTTER, StonecutterScreen::new);
    }
    private static <M extends ScreenHandler, U extends Screen & ScreenHandlerProvider<M>> void register(ScreenHandlerType<? extends M> type, HandledScreens.Provider<M, U> provider) {
        HandledScreens.Provider<?, ?> provider2 = (HandledScreens.Provider)PROVIDERS.put(type, provider);
        if (provider2 != null) {
            throw new IllegalStateException("Duplicate registration for " + Registries.SCREEN_HANDLER.getId(type));
        }
    }
    public static <T extends ScreenHandler> HandledScreens.Provider getProvider(ScreenHandlerType<T> type) {
        return (HandledScreens.Provider)PROVIDERS.get(type);
    }
    public  static Pair<Integer,Integer> getMouseCoord(MinecraftClient client) {
        return getMouseCoord(client,client.mouse);
    }
    public static Pair<Integer,Integer> getMouseCoord(MinecraftClient client, Mouse mouse) {
        Window window = client.getWindow();
        int mouseX = (int) (mouse.getX() * (double) window.getScaledWidth() / (double) window.getWidth());
        int mouseY = (int) (mouse.getY() * (double) window.getScaledHeight() / (double) window.getHeight());
        return new Pair<>(mouseX, mouseY);
    }
}
