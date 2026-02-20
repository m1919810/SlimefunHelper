package me.matl114.jsApi;

import java.util.List;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

@ApiMethod
public class ScreenHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Nonnull
    public static Object createInventoryView(HandledScreen s) {
        return JsMacrosBridge.getInstance().wrap(s);
    }

    @Nonnull
    public static Object createServerInventoryView() {
        ClientPlayerEntity player = mc.player;
        HandledScreen<?> handledScreen = ClientPlayerAccess.of(player).getServerOpeningScreen();
        // create backpack inventory if null
        return handledScreen != null
                ? createInventoryView(handledScreen)
                : JsMacrosBridge.getInstance().createInventory();
    }

    public static ScreenHandler getScreenHandler(Object handled) {
        return unwrapHandler(handled);
    }

    private static ScreenHandler unwrapHandler(Object obj) {
        if (obj instanceof ScreenHandler sh) {
            return sh;
        } else {
            return JsHelper.unwrap(obj, HandledScreen.class).getScreenHandler();
        }
    }

    public static int getSyncId(Object screen) {
        return unwrapHandler(screen).syncId;
    }

    public static boolean isInPlayerInventory(Object handled, int slotIndex) {
        return unwrapHandler(handled).slots.get(slotIndex).inventory instanceof PlayerInventory;
    }

    public static boolean isInContainerInventory(Object handled, int slotIndex) {
        return !isInPlayerInventory(handled, slotIndex);
    }

    public static boolean canPlaceInSlot(Object handled, int slotIndex, Object itemStack) {
        Slot slot = unwrapHandler(handled).slots.get(slotIndex);
        ItemStack stack = JsHelper.unwrap(itemStack, ItemStack.class);
        return slot.canInsert(stack);
    }

    public static boolean canTakeFromSlot(Object handled, int slotIndex) {
        Slot slot = unwrapHandler(handled).slots.get(slotIndex);
        return slot.canTakeItems(mc.player);
    }

    public static List<Slot> getScreenSlots(Object handled) {
        return unwrapHandler(handled).slots;
    }

    public static void setSlotItem(Slot slot, ItemStack stack) {
        slot.setStack(stack);
    }

    public static ItemStack getSlotItem(Slot slot) {
        return slot.getStack();
    }

    public static String getScreenName(Screen s) {
        if (s == null) {
            return null;
        } else if (s instanceof HandledScreen) {
            if (s instanceof GenericContainerScreen) {
                return String.format(
                        "%d Row Chest",
                        ((GenericContainerScreenHandler) ((GenericContainerScreen) s).getScreenHandler()).getRows());
            } else if (s instanceof Generic3x3ContainerScreen) {
                return "3x3 Container";
            } else if (s instanceof AnvilScreen) {
                return "Anvil";
            } else if (s instanceof BeaconScreen) {
                return "Beacon";
            } else if (s instanceof BlastFurnaceScreen) {
                return "Blast Furnace";
            } else if (s instanceof BrewingStandScreen) {
                return "Brewing Stand";
            } else if (s instanceof CraftingScreen) {
                return "Crafting Table";
            } else if (s instanceof EnchantmentScreen) {
                return "Enchanting Table";
            } else if (s instanceof FurnaceScreen) {
                return "Furnace";
            } else if (s instanceof GrindstoneScreen) {
                return "Grindstone";
            } else if (s instanceof HopperScreen) {
                return "Hopper";
            } else if (s instanceof LoomScreen) {
                return "Loom";
            } else if (s instanceof MerchantScreen) {
                return "Villager";
            } else if (s instanceof ShulkerBoxScreen) {
                return "Shulker Box";
            } else if (s instanceof SmithingScreen) {
                return "Smithing Table";
            } else if (s instanceof SmokerScreen) {
                return "Smoker";
            } else if (s instanceof CartographyTableScreen) {
                return "Cartography Table";
            } else if (s instanceof StonecutterScreen) {
                return "Stonecutter";
            } else if (s instanceof InventoryScreen) {
                return "Survival Inventory";
            } else if (s instanceof HorseScreen) {
                return "Horse";
            } else {
                return s instanceof CreativeInventoryScreen
                        ? "Creative Inventory"
                        : s.getClass().getName();
            }
        } else if (s instanceof ChatScreen) {
            return "Chat";
        } else {
            Text t = s.getTitle();
            String ret = "";
            if (t != null) {
                ret = t.getString();
            }

            if (ret.equals("")) {
                ret = "unknown";
            }

            return ret;
        }
    }
}
