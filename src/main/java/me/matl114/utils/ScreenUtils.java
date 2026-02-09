package me.matl114.utils;

import com.google.common.collect.Maps;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.utils.collections.Point;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.navigation.GuiNavigationType;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
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
    public  static Point getMouseCoord(MinecraftClient client) {
        return getMouseCoord(client,client.mouse);
    }
    public static Point getMouseCoord(MinecraftClient client, Mouse mouse) {
        Window window = client.getWindow();
        int mouseX = (int) (mouse.getX() * (double) window.getScaledWidth() / (double) window.getWidth());
        int mouseY = (int) (mouse.getY() * (double) window.getScaledHeight() / (double) window.getHeight());
        return new Point(mouseX, mouseY);
    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static ItemStack getSelectingOrHandItem(){
        if(mc.player ==null)return null;
        if(mc.currentScreen instanceof HandledScreen<?> s){
            Point mouseCoord= ScreenUtils.getMouseCoord(mc);
            Slot slot= HandledScreenAccess.of(s).reallyGetSlotAt(mouseCoord.x,mouseCoord.y);
            if(slot!=null){
                return slot.getStack();
            }
        }else{
            return mc.player.getStackInHand(Hand.MAIN_HAND);
        }
        return null;
    }

    public static boolean hasShiftDown(){
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isToggle(int keyCode) {
        return keyCode == 257 || keyCode == 32 || keyCode == 335;
    }

    public static int getCurrentModifiers() {
        var windowHandle = mc.getWindow().getHandle();
        if (windowHandle == 0) {
            return 0;
        }

        int modifiers = 0;

        // 检查 Shift 键
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
            org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
        }

        // 检查 Control 键
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
            org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
        }

        // 检查 Alt 键
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
            org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
        }

        // 检查 Windows/Command 键
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
            org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;
        }

        // 检查 Caps Lock
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_CAPS_LOCK) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_CAPS_LOCK;
        }

        // 检查 Num Lock
        if (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_NUM_LOCK) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            modifiers |= org.lwjgl.glfw.GLFW.GLFW_MOD_NUM_LOCK;
        }

        return modifiers;
    }

    //internal methods from MCClient

    public static void wrapScreenError(Runnable task, String errorTitle, String screenName) {
        try {
            task.run();
        } catch (Throwable var6) {
            Throwable throwable = var6;
            CrashReport crashReport = CrashReport.create(throwable, errorTitle);
            CrashReportSection crashReportSection = crashReport.addElement("Affected screen");
            crashReportSection.add("Screen name", () -> {
                return screenName;
            });
            throw new CrashException(crashReport);
        }
    }

    public static void simulateKeyAction(Screen screen, int key, int scancode, int action, int modifiers){
        if (screen != null) {
            switch (key) {
                case 258:
                    mc.setNavigationType(GuiNavigationType.KEYBOARD_TAB);
                case 259:
                case 260:
                case 261:
                default:
                    break;
                case 262:
                case 263:
                case 264:
                case 265:
                    mc.setNavigationType(GuiNavigationType.KEYBOARD_ARROW);
            }
        }
        KeyInput keyInput = new KeyInput(key, scancode, modifiers);
        if (action == 1 && (!(screen instanceof KeybindsScreen) || ((KeybindsScreen)screen).lastKeyCodeUpdateTime <= Util.getMeasuringTimeMs() - 20L)) {
            if (mc.options.fullscreenKey.matchesKey(keyInput)) {
                mc.getWindow().toggleFullscreen();
                mc.options.getFullscreen().setValue(mc.getWindow().isFullscreen());
                return;
            }
        }

        boolean bl3;

        if (screen != null) {
            boolean[] bls = new boolean[]{false};
            wrapScreenError(() -> {
                if (action != 1 && action != 2) {
                    if (action == 0) {
                        bls[0] = screen.keyReleased(keyInput);
                    }
                } else {
                    InputUtil.Key key2;
                    screen.applyKeyPressNarratorDelay();
                    bls[0] = screen.keyPressed(keyInput);
                    if(bls[0]){
                        if (mc.currentScreen == null) {
                            key2 = InputUtil.fromKeyCode(keyInput);
                            KeyBinding.setKeyPressed(key2, false);
                        }
                    }
                }

            }, "keyPressed event handler", screen.getClass().getCanonicalName());
            if (bls[0]) {

                return;
            }
        }

        InputUtil.Key key2;
        boolean var10000;
        label184: {
            key2 = InputUtil.fromKeyCode(keyInput);
            bl3 = screen == null;
            if (!bl3) {
                label180: {
                    Screen var13 = screen;
                    if (var13 instanceof GameMenuScreen) {
                        GameMenuScreen gameMenuScreen = (GameMenuScreen)var13;
                        if (!gameMenuScreen.shouldShowMenu()) {
                            break label180;
                        }
                    }

                    var10000 = false;
                    break label184;
                }
            }

            var10000 = true;
        }

        boolean bl4 = var10000;
        if (action == 0) {
            KeyBinding.setKeyPressed(key2, false);

        } else {
            boolean bl5 =  InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 292);

            if (bl3) {
                if (bl5) {
                    KeyBinding.setKeyPressed(key2, false);
                } else {
                    KeyBinding.setKeyPressed(key2, true);
                    KeyBinding.onKeyPressed(key2);
                }
            }

        }
    }

    public static void simulateMouseButton(@Nonnull Screen screen, int button, int action, int mods){
        if (screen != null) {
            mc.setNavigationType(GuiNavigationType.MOUSE);
        }
        MouseInput mouseInput = new MouseInput(button, mods);
        boolean bl = action == 1;
        final Mouse mouse = mc.mouse;
        MouseInput i = mouse.modifyMouseInput(mouseInput, bl);
        if (bl) {

            mouse.activeButton = i;
        } else if (mouse.activeButton != null) {

            mouse.activeButton = null;
        }


        boolean[] bls = new boolean[]{false};
        if (mc.getOverlay() == null) {
            double d = mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
            double e = mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
            Click click = new Click(d, e, mouseInput);
            if (bl) {
                screen.applyMousePressScrollNarratorDelay();
                wrapScreenError(() -> {
                    long l = Util.getMeasuringTimeMs();
                    boolean bl2 = mouse.lastMouseClick != null && l  - mouse.lastMouseClick.time() < 250L &&
                        //remove screen check
                       // mouse.lastMouseClick.screen() == screen &&
                        mouse.lastMouseButton == button;
                    bls[0] = screen.mouseClicked(click, bl2);
                    if(bls[0]){
                        mouse.lastMouseClick = new Mouse.MouseClickTime(l, screen);
                        mouse.lastMouseButton = button;
                    }
                }, "mouseClicked event handler", screen.getClass().getCanonicalName());
            } else {
                wrapScreenError(() -> {
                    bls[0] = screen.mouseReleased(click);
                }, "mouseReleased event handler", screen.getClass().getCanonicalName());
            }
        }


    }

    public static void simulateMouseScroll(@Nonnull Screen screen, double horizontal, double vertical){
        boolean bl = (Boolean)mc.options.getDiscreteMouseScroll().getValue();
        double d = (Double)mc.options.getMouseWheelSensitivity().getValue();
        double e = (bl ? Math.signum(horizontal) : horizontal) * d;
        double f = (bl ? Math.signum(vertical) : vertical) * d;
        if (mc.getOverlay() == null) {
            if (screen != null) {
                double g = mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
                double h = mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
                screen.mouseScrolled(g, h, e, f);
                screen.applyMousePressScrollNarratorDelay();
            } else if (mc.player != null) {
                // FUCK YOU , IT IS DEPRECATED , GET OUT OF MY WORLD, I DON'T WANT TO EAT SHIT
//                if (mc.mouse.eventDeltaHorizontalWheel != 0.0 && Math.signum(e) != Math.signum(mc.mouse.eventDeltaHorizontalWheel)) {
//                    mc.mouse.eventDeltaHorizontalWheel = 0.0;
//                }
//
//                if (mc.mouse.eventDeltaVerticalWheel != 0.0 && Math.signum(f) != Math.signum(mc.mouse.eventDeltaVerticalWheel)) {
//                    mc.mouse.eventDeltaVerticalWheel = 0.0;
//                }
//
//                mc.mouse.eventDeltaHorizontalWheel += e;
//                mc.mouse.eventDeltaVerticalWheel += f;
//                int i = (int)mc.mouse.eventDeltaHorizontalWheel;
//                int j = (int)mc.mouse.eventDeltaVerticalWheel;
//                if (i == 0 && j == 0) {
//                    return;
//                }
//
//                mc.mouse.eventDeltaHorizontalWheel -= (double)i;
//                mc.mouse.eventDeltaVerticalWheel -= (double)j;
//                int k = j == 0 ? -i : j;
//                if (mc.player.isSpectator()) {
//                    if (mc.inGameHud.getSpectatorHud().isOpen()) {
//                        mc.inGameHud.getSpectatorHud().cycleSlot(-k);
//                    } else {
//                        float l = MathHelper.clamp(mc.player.getAbilities().getFlySpeed() + (float)j * 0.005F, 0.0F, 0.2F);
//                        mc.player.getAbilities().setFlySpeed(l);
//                    }
//                } else {
//                    mc.player.getInventory().scrollInHotbar((double)k);
//                }
            }
        }
    }

}
