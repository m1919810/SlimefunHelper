package me.matl114.hacks.modules.interact;

import com.google.common.base.Suppliers;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;

public class GuiInteract extends BaseModule {
    public final ModulePath other = makePath(Configs.INTERACT_CONFIG, "interact-fix.gui-interact");

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreSetScreen(), this::onStoreKeyBindState);
        registerListener(Listener.getPostSetScreen(), this::onResetKeyBind);
        registerListener(Listener.getPreHandleInputEvents(), this::onInput, Integer.MIN_VALUE);
    }

    public final Set<KeyBinding> sets = new HashSet<>();
    public final Supplier<KeyBinding[]> sticks = Suppliers.memoize(() -> {
        return new KeyBinding[] {
            mc.options.useKey, mc.options.attackKey, mc.options.sneakKey, mc.options.sprintKey,
        };
    });

    public final FlagRef enable =
            builder(other.addEnable(), Boolean.class).defaultValue(false).build();
    public final KeyBindRef hotkey = moduleEntry(other.addHotkey(), new MultiKeyBind(), other.addEnable())
            .build();
    public final FlagRef useWhenScreenOpen = builder(other.add("use-tick-when-screen-open"), Boolean.class)
            .defaultValue(true)
            .build();

    public void onStoreKeyBindState(Event<Screen> eventPre) {
        sets.clear();
        if (enable.get() && !eventPre.isCancelled()) {
            for (var re : sticks.get()) {
                if (re.isPressed()) {
                    sets.add(re);
                }
            }
        }
    }

    public void onResetKeyBind(Event<Screen> eventPost) {
        if (enable.get()) {
            if (eventPost.context != null) {
                for (var re : sticks.get()) {
                    if (sets.contains(re)) {
                        re.setPressed(true);
                    }
                }
            } else {
                for (var re : sticks.get()) {
                    KeyBindAccess.of(re).resetKeyState();
                }
            }
        }
        sets.clear();
    }

    public void onInput(Event<Void> event) {
        if (enable.get() && mc.currentScreen != null) {
            if (useWhenScreenOpen.get()) {
                if (event.isCancelled()) {
                    event.cancel(false);
                }
            }

            for (var re : sticks.get()) {
                KeyBindAccess.of(re).resetKeyState();
            }
        }
    }
}
