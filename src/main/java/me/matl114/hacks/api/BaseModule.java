package me.matl114.hacks.api;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.commands.MainCommand;
import me.matl114.events.channels.ListenerPoint;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.hacks.utils.Named;
import me.matl114.hacks.utils.NamedConsumer;
import me.matl114.hacks.utils.NamedPredicate;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.IHotKey;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.managers.input.SimpleInputManager;
import me.matl114.utils.commands.commandGroup.AbstractMainCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

public abstract class BaseModule implements ModuleGuiProvider<SubScreenWidget>, ModuleListProvider {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    public String name;

    public BaseModule() {
        this.name = this.getClass().getSimpleName();
    }

    public BaseModule(String name) {
        this.name = name;
    }

    protected boolean lastActiveFlag = false;
    protected boolean removed = false;

    public boolean isActive() {
        return lastActiveFlag;
    }

    public boolean isRemoved() {
        return removed;
    }

    protected FlagRef bindedFlag = null;
    protected static final String REASON_BIND = "module binding";
    protected static final String REASON_LISTENER = "event listener";
    protected static final String REASON_VALIDATOR = "config validator";
    protected static final String REASON_UPDATE_LISTENER = "config update listener";
    protected static final String REASON_COMMAND = "command bootstrap";
    protected static final String REASON_CUSTOM = "custom wrapper";

    public static ModulePath makePath(Config config, String c) {
        return new ModulePath(config, c.split("\\."));
    }

    public static String[] makePath(String c) {
        return c.split("\\.");
    }
    // bind the Module's status to the Flag
    public final void bindFlag(FlagRef flagRef) {
        if (bindedFlag != null) {
            removeBindFlag();
        }
        bindedFlag = flagRef;
        if (flagRef != null) {
            flagRef.addUpdateListenerWithUpdate(new NamedConsumer<>(this, this::updateActiveStatus, REASON_BIND));
        }
    }

    private void removeBindFlag() {
        if (bindedFlag != null) {
            bindedFlag.removeUpdateListener(s -> this.isOwner(s, REASON_BIND));
            bindedFlag = null;
        }
    }

    private void removeBindHotkey() {
        registeredModuleEntry.clear();
    }

    // this is called via the bindedFlag
    protected final void updateActiveStatus(boolean active) {
        if (lastActiveFlag != active) {
            lastActiveFlag = active;
            if (active) {
                onEnableModule();
            } else {
                onDisableModule();
            }
        }
    }

    public boolean checkNull() {
        return mc.player == null || mc.world == null;
    }
    // module enable and disable
    // note that it might be called outside the game, so you have check basic vars
    @MustBeInvokedByOverriders
    public void onEnableModule() {}

    @MustBeInvokedByOverriders
    public void onDisableModule() {}

    // this is managed by ModuleManager
    @MustBeInvokedByOverriders
    public void onCreate() {
        registerAll();
    }

    @MustBeInvokedByOverriders
    public void onRemove() {
        if (removed) {
            throw new IllegalStateException("Removed twice");
        }
        removeBindFlag();
        removeBindHotkey();
        unregisterAll();
        removed = true;
    }

    // this is for convenience
    @MustBeInvokedByOverriders
    public final <T extends BaseModule> T register(ModuleManager manager) {
        manager.registerModule(this);
        return (T) this;
    }

    @MustBeInvokedByOverriders
    public final void unregister(ModuleManager manager) {
        manager.unregisterModule(this);
    }
    // this is also for convenience
    private final Set<ListenerPoint<?>> registeredPoints = new LinkedHashSet<>();
    // todo; make this hand-register
    private final List<ModuleEntry> registeredModuleEntry = new ArrayList<>();

    public Stream<ModuleEntry> getModuleEntries() {
        return registeredModuleEntry.stream();
    }

    public <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler) {
        registerListener(listener, handler, 0);
    }

    public <W> void registerListener(ListenerPoint<W> listener, Predicate<W> handler) {
        registerListener(listener, handler, 0);
    }

    public <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler, int p) {
        listener.registerHandler(new NamedConsumer<>(this, handler, REASON_LISTENER), p);
        registeredPoints.add(listener);
    }

    public <W> void registerListener(ListenerPoint<W> listener, Predicate<W> handler, int p) {
        listener.registerHandler(new NamedPredicate<>(this, handler, REASON_LISTENER), p);
        registeredPoints.add(listener);
    }

    public void registerCommandBootstrap(Consumer<MainCommand> handler) {
        MainCommand.registerCommandBootstrap(new NamedBootstrap<>(this, handler, REASON_COMMAND));
    }

    public void registerCommand(Supplier<AbstractMainCommand> factory) {
        registerCommandBootstrap(s -> s.registerAsCommand(factory.get()));
    }

    public void registerAsSubCommand(String name, Supplier<AbstractMainCommand> factory) {
        registerCommandBootstrap(s -> s.registerAsSubCommand(name, factory.get()));
    }
    // you should put listeners here
    @MustBeInvokedByOverriders
    public void registerAll() {}

    // listeners will be automatically unregistered in onRemove
    public <W> void unregisterAll() {
        registeredPoints.forEach(s -> s.unregisterHandler(this::isOwner));
        registeredPoints.clear();
        registeredConfigRefs.forEach(s -> s.ref.removeUpdateListener(this::isOwner));
        registeredConfigRefs.forEach(s -> s.ref.removeValidator(this::isOwner));
        registeredConfigRefs.forEach(s -> {
            if (s.ref instanceof ListRef list) {
                list.removeElementValidator(this::isOwner);
            }
        });
        registeredConfigRefs.clear();
        registeredHotkeys.forEach(s -> s.setInputHandler(SimpleHotKey.InputHandler.EMPTY));
        registeredHotkeys.clear();
        MainCommand.unregisterCommandBootstrap(this::isOwner);
    }

    private final Set<WrapperConfigRef<?>> registeredConfigRefs = new LinkedHashSet<>();

    private final Set<IHotKey> registeredHotkeys = new LinkedHashSet<>();

    public <T> WrapperSettingBuilder<T> builder(Config config, Class<T> type) {
        return new WrapperSettingBuilder<>(config.asRef(), config, type, this);
    }

    public <T> WrapperSettingBuilder<T> builder(Config config, String[] path, Class<T> type) {
        return new WrapperSettingBuilder<>(config.asRef(), config, type, this).path(path);
    }

    public <T> WrapperSettingBuilder<T> builder(ModulePath path, Class<T> type) {
        return builder(path.getConfig(), path.toPath(), type);
    }

    public WrapperSettingBuilder<Boolean> flagBuilder(Config config, String... path) {
        return builder(config, Boolean.class).path(path).defaultValue(false);
    }

    public WrapperSettingBuilder<Boolean> flagBuilder(ModulePath path) {
        return flagBuilder(path.getConfig(), path.toPath());
    }

    public WrapperSettingBuilder<Integer> intBuilder(ModulePath path) {
        return builder(path.getConfig(), path.toPath(), IntRef.TYPE);
    }

    public WrapperSettingBuilder<Double> doubleBuilder(ModulePath path) {
        return builder(path.getConfig(), path.toPath(), DoubleRef.TYPE);
    }

    public WrapperSettingBuilder<MultiKeyBind> hotkey(Config config, String... path) {
        return builder(config, MultiKeyBind.class).path(path);
    }

    public WrapperSettingBuilder<MultiKeyBind> hotkey(ModulePath path) {
        return hotkey(path.getConfig(), path.toPath());
    }

    public WrapperSettingBuilder<MultiKeyBind> hotkey(Config config, String[] path, MultiKeyBind defaultValue) {
        return builder(config, MultiKeyBind.class).path(path).defaultValue(defaultValue);
    }

    public WrapperSettingBuilder<MultiKeyBind> hotkey(ModulePath path, MultiKeyBind defaultValue) {
        return hotkey(path.getConfig(), path.toPath(), defaultValue);
    }

    public WrapperSettingBuilder<MultiKeyBind> toggleConfigHotkey(
            Config config, String[] path, MultiKeyBind defaultValue) {
        return builder(config, MultiKeyBind.class)
                .path(path)
                .defaultValue(defaultValue)
                .registerHotkey(TaskManagers.getToggleHandler(Configs.TOGGLE_CONFIG, path));
    }

    public WrapperSettingBuilder<MultiKeyBind> moduleEntry(
            ModulePath hotkeyPath, MultiKeyBind defaultValue, ModulePath togglePath) {
        return moduleEntry(hotkeyPath.getConfig(), hotkeyPath.toPath(), defaultValue, togglePath.toPath());
    }

    public WrapperSettingBuilder<MultiKeyBind> moduleEntry(
            ModulePath hotkeyPath, MultiKeyBind defaultValue, ModulePath togglePath, Supplier<Text> descriptor) {
        return moduleEntry(hotkeyPath.getConfig(), hotkeyPath.toPath(), defaultValue, togglePath.toPath(), descriptor);
    }

    public WrapperSettingBuilder<MultiKeyBind> moduleEntry(
            Config config, String[] hotkeyPath, MultiKeyBind defaultValue, String[] togglePath) {
        return new WrapperModuleSettingBuilder(
                        config.asRef(), config, this, new ModuleEntry(config, togglePath, hotkeyPath))
                .defaultValue(defaultValue)
                .registerHotkey(TaskManagers.getToggleHandler(config, togglePath))
                .registerModuleEntry();
    }

    public WrapperSettingBuilder<MultiKeyBind> moduleEntry(
            Config config,
            String[] hotkeyPath,
            MultiKeyBind defaultValue,
            String[] togglePath,
            Supplier<Text> descriptor) {
        return new WrapperModuleSettingBuilder(
                        config.asRef(),
                        config,
                        this,
                        new MetaDataModuleEntry(config, togglePath, hotkeyPath, descriptor))
                .defaultValue(defaultValue)
                .registerHotkey(TaskManagers.getToggleHandler(config, togglePath))
                .registerModuleEntry();
    }

    public WrapperSettingBuilder<MultiKeyBind> toggleHotkey(
            Config config, String[] path, MultiKeyBind defaultValue, String[] togglePath) {
        return new WrapperSettingBuilder<>(config.asRef(), config, KeyBindRef.TYPE, this)
                .path(path)
                .defaultValue(defaultValue)
                .registerHotkey(TaskManagers.getToggleHandler(config, togglePath));
    }

    public WrapperSettingBuilder<MultiKeyBind> toggleHotkey(
            Config config, ModulePath path, MultiKeyBind defaultValue, ModulePath togglePath) {
        return toggleHotkey(config, path.toPath(), defaultValue, togglePath.toPath());
    }

    public WrapperSettingBuilder<Boolean> toggle(Config config, String... path) {
        // automatically hide toggle flags because they are always internal,
        return builder(config, Boolean.class).path(path).defaultValue(false).hideConfig();
    }

    public IHotKey getHotkey(String... path) {
        return SimpleInputManager.getInstance().getHotkey(String.join(".", path));
    }

    public <T extends Ref<?>> T registerConfig(T ref) {
        registerConfigWrapper(new WrapperConfigRef(ref));
        return ref;
    }

    public <T> void registerConfigWrapper(WrapperConfigRef<T> ref) {
        registeredConfigRefs.add(ref);
    }

    public void registerHotkey(IHotKey register) {
        registeredHotkeys.add(register);
    }

    // for removal convenience
    protected <W> boolean isOwner(Object c) {
        return (c instanceof Named named && named.getOwner() == this);
    }

    protected <W> boolean isOwner(Object c, String name) {
        return (c instanceof Named named
                && named.getOwner() == this
                && Objects.equals(name, named.getRegisterReason()));
    }

    protected <W> Consumer<W> wrap(Consumer<W> consumer) {
        return new NamedConsumer<>(this, consumer, REASON_CUSTOM);
    }

    protected <W> Predicate<W> wrap(Predicate<W> predicate) {
        return new NamedPredicate<>(this, predicate, REASON_CUSTOM);
    }

    // todo: remake config screen
    @Override
    public SubScreenWidget createGui(int x, int y, int dx, int dy) {
        return null;
    }

    @Override
    public void saveGui(SubScreenWidget gui) {}

    public static Text getModuleMeta(Enum<?> enumReff){
        ConfigEnum configEnum = (ConfigEnum) enumReff;
        return Text.translatable("module-meta." + configEnum.getConfigEnumType().replace("_", "-") + "." + enumReff.name().toLowerCase(Locale.ROOT));
    }

    public static Supplier<Text> moduleMeta(Supplier<EnumRef<?>> enumReff){
        return new Supplier<Text>() {
            String suffix;
            @Override
            public Text get() {
                if(suffix == null){
                    ConfigEnum configEnum = enumReff.get().get();
                    suffix = "module-meta." + configEnum.getConfigEnumType().replace("_", "-") + ".";
                }
                return Text.translatable(suffix + enumReff.get().get().cast().name().toLowerCase(Locale.ROOT));
            }
        };
    }

    // named consumer to mark who's owner
    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class WrapperConfigRef<T> {
        Ref<T> ref;
        boolean hideInConfig = false;

        public WrapperConfigRef(Ref<T> ref) {
            this.ref = ref;
        }
    }

    public static class WrapperSettingBuilder<W> extends Config.SettingBuilder<W> {
        BaseModule module;
        WrapperConfigRef<W> wrapperConfig;
        IHotKey hotkey;

        public WrapperConfigRef<W> getWrapper() {
            if (wrapperConfig == null) {
                wrapperConfig = new WrapperConfigRef<>(getRef());
            }
            return wrapperConfig;
        }

        public WrapperSettingBuilder(MapRef ref, Config rootConfig, Class<W> clazz, BaseModule module) {
            super(ref, rootConfig, clazz);
            this.module = module;
        }

        public WrapperSettingBuilder<W> listValidator(Predicate<String> va) {
            if (getRef() instanceof ListRef lsR) {
                lsR.addElementValidator(new NamedPredicate<>(this.module, va, REASON_VALIDATOR));
            } else {
                throw new UnsupportedOperationException("Not a list");
            }
            return this;
        }

        public WrapperSettingBuilder<W> validator(Predicate<W> va) {
            super.validator(new NamedPredicate<>(this.module, va, REASON_VALIDATOR));
            return this;
        }

        public WrapperSettingBuilder<W> updateListener(Consumer<W> va) {
            super.updateListener(new NamedConsumer<>(this.module, va, REASON_UPDATE_LISTENER));
            return this;
        }

        @Override
        public WrapperSettingBuilder<W> path(String... path) {
            return (WrapperSettingBuilder<W>) super.path(path);
        }

        @Override
        public WrapperSettingBuilder<W> defaultValue(W val) {
            return (WrapperSettingBuilder<W>) super.defaultValue(val);
        }

        public WrapperSettingBuilder<W> registerHotkey(SimpleHotKey.InputHandler path) {
            var re = (WrapperSettingBuilder<W>) super.registerHotkey(path);
            this.hotkey = SimpleInputManager.getInstance().getHotkey(String.join(".", this.path));
            return re;
        }

        @Override
        public <W1 extends Ref<W>> WrapperSettingBuilder<W> apply(Consumer<W1> va) {
            return (WrapperSettingBuilder<W>) super.apply(va);
        }

        // for gui building
        // todo: create it later
        public WrapperSettingBuilder<W> hideConfig() {
            getWrapper().hideInConfig = true;
            return this;
        }

        public WrapperSettingBuilder<W> showConfig() {
            getWrapper().hideInConfig = false;
            return this;
        }

        public WrapperSettingBuilder<W> registerModuleEntry() {
            throw new UnsupportedOperationException();
        }
        //

        @Override
        public <W1 extends Ref<W>> W1 build() {
            W1 re = super.build();
            this.module.registerConfigWrapper(this.getWrapper());
            if (this.hotkey != null) {
                this.module.registerHotkey(this.hotkey);
            }
            return re;
        }
    }

    public static class WrapperModuleSettingBuilder extends WrapperSettingBuilder<MultiKeyBind> {
        ModuleEntry moduleEntry;

        public WrapperModuleSettingBuilder(MapRef ref, Config rootConfig, BaseModule module, ModuleEntry moduleEntry) {
            super(ref, rootConfig, KeyBindRef.TYPE, module);
            this.moduleEntry = moduleEntry;
            this.path(moduleEntry.hotkeyPath);
        }

        boolean registered = false;

        public WrapperSettingBuilder<MultiKeyBind> registerModuleEntry() {
            registered = true;
            return this;
        }

        public <W2 extends Ref<MultiKeyBind>> W2 build() {
            W2 val = super.build();
            if (registered) {
                this.module.registeredModuleEntry.add(this.moduleEntry);
            }
            return val;
        }
    }

    public <T> T cast() {
        return (T) this;
    }

    @AllArgsConstructor
    public static class NamedBootstrap<T> implements MainCommand.Bootstrap, Named<T> {
        T name;
        public Consumer<MainCommand> delegate;
        public String registerReason;

        @Override
        public void onCommandReload(MainCommand command) {
            delegate.accept(command);
        }

        @Override
        public T getOwner() {
            return name;
        }

        @Override
        public String getRegisterReason() {
            return registerReason;
        }
    }
}
