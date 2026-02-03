package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class EnumRef<T extends ConfigEnum> extends ObjectRef<T>{
    public final String enumType;
    public String enumValue;
    public boolean resolved;
    public EnumRef(ConfigEnum enumR){
        super((T) enumR);
        ConfigEnum.ensureRegistered(enumR.cast().getClass());

        this.enumType = enumR.getConfigEnumType();
        this.enumValue = enumR.cast().name();
        this.resolved = true;
    }
    public EnumRef(String value){
        super(null);
        //value should be like enum:configEnumsthclaass_name:value
        String[] splite = value.split(":");
        Preconditions.checkArgument(splite.length == 3 && Objects.equals("enum", splite[0]));
        //todo:
        this.enumType = splite[1];
        this.enumValue = splite[2];
        tryResolve();
    }

    private void tryResolve(){
        if(this.resolved)return;
        var re = ConfigEnum.registeredConfigs.get(enumType);
        if(re == null) {
            this.resolved = false;
            return;
        }
        var val = re.get(enumValue);
        Preconditions.checkNotNull(val, "Unregistered enum value %s in enum type %s with %s".formatted(enumValue, enumType, re.toString()));
        this.resolved = true;
        this.set((T) val);
    }

    public void setEnumType(Class<? extends Enum> clazz){
        if(!Objects.equals(enumType, clazz.getSimpleName().toLowerCase(Locale.ROOT))){
            throw new IllegalArgumentException("Enum type mismatch the class name: " +enumType + " and " + clazz);
        }
        if(!resolved){
            ConfigEnum.ensureRegistered(clazz);
            tryResolve();
        }

    }

    @Override
    public void set(T val) {
        super.set(val);
        enumValue = this.get().cast().name();
    }

    @Override
    protected T validateAndCast(Object val) {
        if(!resolved){
            setEnumType((Class<? extends Enum>) val.getClass());
        }
        T configEnum = (T) val;

        Preconditions.checkArgument(Objects.equals(enumType, configEnum.getConfigEnumType()), "Enum type mismatch !");
        return configEnum;
    }

    public static EnumRef<ConfigEnum> fromString(String value){
        if(value.startsWith("enum:")){
            try{
                return new EnumRef<>(value);
            }catch (Throwable e){
                Debug.info("Parse config as Enum Selection failed: ", value, ", Error Message: ", e.getMessage());
            }
        }
        return null;
    }


    public Object getAsPrimitive(){
        return "enum:" + enumType +":" + enumValue;
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof EnumRef what && Objects.equals(what.enumType, enumType);
    }

    @Override
    public <W> boolean copyValueTo(Ref<W> otherRef) {
        if(otherRef instanceof EnumRef<?> what && Objects.equals(what.enumType, this.enumType)){
            if(!this.resolved){
                tryResolve();
            }
            if(this.resolved){
                ((EnumRef<T>) otherRef).set(this.get());
            }else{
                ((EnumRef<T>) otherRef).enumValue = this.enumValue;
            }

            return true;

        }
        return false;
    }

    @Override
    public T get() {
        if(resolved){
            return super.get();
        }else{
            tryResolve();
            T val = super.get();
            if(val != null){
                return val;
            }else{
                throw new IllegalStateException("Access to a config enum instance before it is registered");
            }
        }
    }

    @Override
    public AttrKeyValue<T> _createKeyValue0(String key) {
        if(resolved){
            return (AttrKeyValue<T>) AttrKeyValue.enumMap(key, this.getValue(), this.getValue().getMap());
        }else{
            return AttrKeyValue.enumMap(key, null, Map.of());
        }
    }
}