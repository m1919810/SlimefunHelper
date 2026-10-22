package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import me.matl114.managers.api.AutoRegisterType;
import me.matl114.managers.input.IHotKey;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.managers.input.SimpleInputManager;

public class ConfigSettingBuilder<T> {
    protected final Config rootConfig;

    public ConfigSettingBuilder(Config rootConfig, Class<T> clazz) {
        this.clazz = clazz;
        this.rootConfig = rootConfig;
        AutoRegisterType.registerClassSupport(clazz);
    }

    protected final Class<T> clazz;
    protected String[] path;
    protected Ref<T> ref;

    @Nullable
    protected Optional<T> defaultValue;

    Runnable postTask;

    protected Ref<T> getRef() {
        Preconditions.checkNotNull(ref);
        return ref;
    }

    public ConfigSettingBuilder<T> path(String... path) {
        this.path = path;
        this.rootConfig.visitPath(String.join(".", path));
        return this;
    }

    public ConfigSettingBuilder<T> defaultValue(T val) {
        this.defaultValue = Optional.ofNullable(val);
        if (ref != null) {
            var instance = Refs.wrapInstance(val);
            if (!instance.isSameTypeWith(ref)) {
                ref = null;
            }
        }
        if (ref == null) {
            var instance = Refs.wrapInstance(val);
            ref = (Ref<T>) rootConfig.getOrCreate(instance, path);
            if (ref == instance) {
                rootConfig.markForSave();
            }
        }
        ref.setDefaultValue(val);
        return this;
    }

    public ConfigSettingBuilder<T> validator(Predicate<T> va) {

        addPost(() -> {
            // validate default value
            Preconditions.checkArgument(
                    va.test(this.defaultValue.orElse(null)),
                    "config default value validation failure: {0}",
                    String.join(".", this.path));
            // validate current value
            if (!va.test(getRef().getValue())) {
                getRef().setValue(this.defaultValue.orElse(null));
            }
            getRef().addValidator(va);
        });
        return this;
    }

    public ConfigSettingBuilder<T> updateListener(Consumer<T> va) {
        addPost(() -> {
            getRef().addUpdateListenerWithUpdate(va);
        });
        return this;
    }

    public ConfigSettingBuilder<T> registerHotkey(SimpleHotKey.InputHandler handler) {
        if (ref instanceof KeyBindRef keyBindRef) {
            String pathHotkey = String.join(".", this.path);
            IHotKey hotKey = SimpleInputManager.getInstance().getHotkey(pathHotkey);
            if (hotKey instanceof SimpleHotKey simple) {
                // keep track, and
                simple.setInputHandler(handler);
                ((ConfigSettingBuilder<MultiKeyBind>) this).updateListener(simple::setKeyCodes);
                return this;
            } else {
                MultiKeyBind defaultKeyBind = this.defaultValue == null
                        ? new MultiKeyBind("")
                        : (MultiKeyBind) this.defaultValue.orElse(null);
                SimpleHotKey hotKey1 = new SimpleHotKey(path, defaultKeyBind);
                hotKey1.setInputHandler(handler);
                SimpleInputManager.getInstance().registerHotKeys(hotKey1);
                // register here
                ((ConfigSettingBuilder<MultiKeyBind>) this).updateListener(hotKey1::setKeyCodes);
                return this;
            }
        } else {
            throw new IllegalArgumentException("Not a hotkey");
        }
    }

    public <W extends Ref<T>> ConfigSettingBuilder<T> apply(Consumer<W> va) {
        addPost(() -> {
            va.accept((W) Objects.requireNonNull(getRef()));
        });
        return this;
    }

    public <W extends Ref<T>> W build() {
        Objects.requireNonNull(defaultValue);
        var ref1 = (W) Objects.requireNonNull(getRef());
        ref1.setConfigReference(rootConfig);
        if (postTask != null) {
            postTask.run();
        }
        return ref1;
    }

    protected void addPost(Runnable runnable) {
        if (postTask == null) {
            postTask = runnable;
        } else {
            Runnable currentPost = postTask;
            postTask = () -> {
                currentPost.run();
                runnable.run();
            };
        }
    }
}
