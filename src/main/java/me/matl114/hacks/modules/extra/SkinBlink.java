package me.matl114.hacks.modules.extra;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.BoundedPrimitiveFlagMap;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Arm;

public class SkinBlink extends BaseModule {
    public SkinBlink() {
        bindFlag(enable);
    }

    public final ModulePath path = makePath(Configs.TEST_CONFIG, "other.skin-blink");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final IntRef delay = intBuilder(path.add("delay")).defaultValue(20).build();

    public final FlagRef model =
            builder(path.add("switch-model"), Boolean.class).defaultValue(true).build();

    public final NBTRef<PlayerModelPartSelectSet> modelSet = builder(
                    path.add("switch-model-parts"), PlayerModelPartSelectSet.class)
            .defaultValue(new PlayerModelPartSelectSet())
            .build();

    public final FlagRef arm = flagBuilder(path.add("switch-arm")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        if (needRestore) {
            restore();
            mc.options.sendClientSettings();
        }
    }

    boolean needRestore;
    Map<PlayerModelPart, Boolean> models = new EnumMap<>(PlayerModelPart.class);
    Arm currentArm = null;
    int lastDelay = 0;

    public void onTick(Event<ClientPlayerEntity> eventTick) {
        if (enable.get()) {
            if (++lastDelay > delay.get()) {
                lastDelay = 0;
                if (needRestore) {
                    restore();
                } else {
                    blink();
                }
                mc.options.sendClientSettings();
            }
        }
    }

    public void restore() {
        needRestore = false;
        for (var entry : models.entrySet()) {
            mc.options.setPlayerModelPart(entry.getKey(), entry.getValue());
        }
        if (currentArm != null) {
            mc.options.getMainArm().setValue(currentArm);
            currentArm = null;
        }
    }

    public void blink() {
        needRestore = true;
        models.clear();
        currentArm = null;
        if (model.get()) {
            var modelSet = this.modelSet.get();
            for (var re : PlayerModelPart.values()) {
                if (modelSet.getState(re)) {
                    boolean bl = mc.options.isPlayerModelPartEnabled(re);
                    models.put(re, bl);
                    mc.options.setPlayerModelPart(re, !bl);
                }
            }
        }
        if (arm.get()) {
            currentArm = mc.options.getMainArm().getValue();
            mc.options.getMainArm().setValue(currentArm.getOpposite());
        }
    }

    public static class PlayerModelPartSelectSet extends BoundedPrimitiveFlagMap<PlayerModelPart>
            implements NBTParsable<PlayerModelPartSelectSet> {
        public static final NBTType<PlayerModelPartSelectSet> TYPE =
                createEnumMap(PlayerModelPartSelectSet.class, PlayerModelPart.class, PlayerModelPartSelectSet::new);

        public PlayerModelPartSelectSet(
                List<PlayerModelPart> keys, Map<PlayerModelPart, Boolean> map, NBTType<Boolean> type) {
            super(keys, map, type);
        }

        public PlayerModelPartSelectSet() {
            super(PlayerModelPart.class);
        }

        @Override
        public NBTType<PlayerModelPartSelectSet> type() {
            return TYPE.cast();
        }
    }
}
