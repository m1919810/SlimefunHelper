package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import java.util.Locale;
import java.util.Objects;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtElement;

public class NBTRef<T extends NBTParsable<T>> extends LazilyRegisterTypeRef<T, NbtElement> {

    private NBTType<T> type;

    public NBTRef(T nbtR) {
        super(nbtR.type().typeName(), (T) nbtR);
        this.type = nbtR.type();
    }

    public NBTRef(String value) {
        super(value);
        // value should be like nbt:nbttype_name:nbt_value

    }

    @Override
    protected void tryRegisterType(T value) {
        value.registerNBTType();
    }

    @Override
    protected NbtElement toLazy(T val) {
        return val.toNbt();
    }

    @Override
    protected NbtElement fromStringToLazy(String string) {
        return VNbt.getInstance().readNbtNoRegistry(string);
    }

    @Override
    protected String fromLazyToString(NbtElement val) {
        return VNbt.getInstance().writeNbt(val);
    }

    @Override
    protected String prefix() {
        return "nbt";
    }

    protected void tryResolve() {
        if (this.resolved) return;
        var re = NBTParsable.registeredParsableTypes.get(enumType);
        if (re == null) {
            this.resolved = false;
            return;
        }
        this.type = (NBTType<T>) re;
        try {
            T val = this.type.parse(this.enumValue);
            this.resolved = true;
            this.set(val);
        } catch (Throwable e) {
            throw new RuntimeException("Raw NBT value could not be parsed into type " + type.typeName
                    + ", which may be caused by a corrupted config file: "
                    + (this.configReference == null ? "Unknown" : this.configReference.getConfigName()));
        }
    }

    private void initializeAndCheckNbtType(NBTParsable obj) {
        NBTType type1 = obj.type();
        if (!Objects.equals(enumType, type1.typeName().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("NBT type mismatch : " + enumType + " and " + type1.typeName());
        }
        if (!resolved) {
            obj.registerNBTType();
            tryResolve();
        }
    }

    @Override
    protected T validateAndCast(Object val) {
        NBTParsable parsable = (NBTParsable) val;
        if (!resolved) {
            initializeAndCheckNbtType(parsable);
        }
        T configEnum = (T) val;

        Preconditions.checkArgument(
                Objects.equals(enumType, configEnum.type().typeName().toLowerCase(Locale.ROOT)), "Nbt type mismatch !");
        return configEnum;
    }

    public static <T extends NBTParsable<T>> NBTRef<T> fromString(String value) {
        if (value.startsWith("nbt:")) {
            try {
                return new NBTRef<>(value);
            } catch (Throwable e) {
                Debug.info("Parse config as nbt Selection failed: ", value, ", Error Message: ", e.getMessage());
            }
        }
        return null;
    }

    public final <W> boolean isSameTypeWith(Ref<W> ref) {
        if (super.isSameTypeWith(ref)) {
            if (resolved && ref instanceof NBTRef<?> nbtref && nbtref.resolved) {
                NBTParsable parsable = nbtref.get();
                if (!get().isSameType(parsable)) {
                    return false;
                }
            }
            return true;
        } else return false;
    }

    @Override
    public AttrKeyValue<T> _createKeyValue0(String key) {
        if (!resolved) {
            tryResolve();
        }
        if (resolved) {
            return type.createAttrKeyValue(key, get());
        } else {
            throw new IllegalStateException("Access to a nbt type before it is registered");
            // return (AttrKeyValue<T>)(AttrKeyValue) new NbtAttrKeyValue<>(key, nbtValue, this::validateNBTRaw);
        }
    }
}
