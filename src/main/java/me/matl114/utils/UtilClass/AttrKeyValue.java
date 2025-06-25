package me.matl114.utils.UtilClass;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.val;
import net.minecraft.client.gui.Selectable;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public abstract class AttrKeyValue<T> implements PropertyTracker<Object, String> {
    public AttrKeyValue(String key, T value){
        this.keyName = key;
        this.originValue = value;
        this.value = updateValue(value);
    }
    public AttrKeyValue(String key, String value, T value2, boolean isValidate){
        this.keyName = key;
        this.originValue = value2;
        this.value = value;
        this.validate = isValidate;

    }
    public AttrKeyValue(String key, Optional<String> value, T value2){
        this(key, value.orElse(""), value2, value.isPresent());
    }

    @Getter
    String keyName;
    @Getter
    String value;
    @Getter
    @Nullable
    T originValue;
    @Getter
    boolean validate = true;
    public T validateValue(){
        this.validate = validateAndUpdate();
        return this.originValue;
    }
    public abstract boolean validateAndUpdate();

    public abstract String updateValue(T val);

    public abstract Class identifier();
    @Override
    public void valueChange(Object selectable, String string) {
        this.value = string;
        validateValue();
    }

    public static AttrKeyValue<Boolean> bool(String key, boolean value){
        return new AttrKeyValue<Boolean>(key, value) {
            @Override
            public boolean validateAndUpdate() {
                switch (this.value){
                    case "true"->{
                        this.originValue = true;
                        return true;
                    }
                    case "false"->{
                        this.originValue = false;
                        return true;
                    }
                    default -> {
                        return true;
                    }
                }
            }

            @Override
            public String updateValue(Boolean val) {
                return val == Boolean.TRUE ? "true": "false";
            }

            @Override
            public Class<Boolean> identifier() {
                return Boolean.class;
            }
        };
    }
    public static AttrKeyValue<Integer> integer(String key, int val){
        return new AttrKeyValue<Integer>(key, val) {
            @Override
            public boolean validateAndUpdate() {
                try{
                    this.originValue = Integer.parseInt(this.value);
                    return true;
                }catch (Throwable e){
                    return false;
                }
            }

            @Override
            public String updateValue(Integer val) {
                return val != null? String.valueOf(val): "0";
            }

            @Override
            public Class<Integer> identifier() {
                return Integer.class;
            }
        };
    }
    public static AttrKeyValue<Double> doub(String keyName, double val){
        return new AttrKeyValue<Double>(keyName, val) {
            @Override
            public boolean validateAndUpdate() {
                try {
                    this.originValue = Double.parseDouble(this.value);
                    return true;
                }catch (Throwable e){
                    return false;
                }
            }

            @Override
            public String updateValue(Double val) {
                return val != null? String.valueOf(val): "0.0";
            }

            @Override
            public Class identifier() {
                return Double.class;
            }
        };
    }
    public static <T>  AttrKeyValue<T> registry(String key, Registry<T> registry, T val){
        return new RegistryAttrKeyValue<>(key, val, registry);
    }
    public static <T>  AttrKeyValue<T> openRegistry(String key, Registry<T> registry, String val){
        Identifier identifier = Identifier.tryParse(val);
        T val0;
        if(identifier != null && (val0 = registry.getOrEmpty(identifier).orElse(null)) != null){
            return new RegistryAttrKeyValue<>(key, val0, registry);
        }else {
            return new RegistryAttrKeyValue<>(key, val, registry, null);
        }
    }
    public static AttrKeyValue<String> str(String key, String val){
        return new AttrKeyValue<String>(key, val) {
            @Override
            public boolean validateAndUpdate() {
                this.originValue = this.value;
                return true;
            }

            @Override
            public String updateValue(String val) {
                return val;
            }

            @Override
            public Class<String> identifier() {
                return String.class;
            }
        };
    }

    public static <T> AttrKeyValue<T> enumMap(String key, T val, Map<String, T> finiteValueMap){
        return new EnumAttrKeyValue<>(key, val, finiteValueMap);
    }

    public static <T> AttrKeyValue<T> computeNonnull(String keyName, String value, Function<String, T> valueMapper){
        return new AttrKeyValue<T>(keyName, Optional.ofNullable(value), valueMapper.apply(value)) {
            @Override
            public boolean validateAndUpdate() {
                T val = valueMapper.apply(this.value);
                if(val != null){
                    this.originValue = val;
                    return true;
                }
                return false;
            }

            @Override
            public String updateValue(T val) {
                return "";
            }

            @Override
            public Class identifier() {
                return Function.class;
            }
        };
    }


    public static class RegistryAttrKeyValue<T> extends AttrKeyValue<T>{
        @Getter
        Registry<T> registry;
        public RegistryAttrKeyValue(String key, @Nonnull T value, Registry<T> registry) {
            super(key,registry.getId(value).toString() ,value, true);
            this.registry = registry;
        }

        public RegistryAttrKeyValue(String key, String value, Registry<T> registry,@Nullable T origin){
            super(key, value, origin, origin != null);
            this.registry = registry;
        }

        @Override
        public boolean validateAndUpdate() {
            try{
                int index = this.value.indexOf(":");
                if(index >=0){
                    Identifier id = new Identifier(this.value.substring(0,index), this.value.substring(index+1));
                    var val = registry.getOrEmpty(id);
                    if(val.isPresent()){
                        this.originValue = val.get();
                        return true;
                    }else {
                        return false;
                    }
                }else return false;
            }catch (Throwable e){
                return false;
            }
        }

        @Override
        public String updateValue(T val) {
            return registry.getId(val).toString();
        }

        @Override
        public Class identifier() {
            return Registry.class;
        }
    }

    public static class EnumAttrKeyValue<T> extends AttrKeyValue<T>{
        protected Map<String,T> finiteValueMap;
        public Map<String, T> getValueMap(){
            return finiteValueMap;
        }
        public EnumAttrKeyValue(String key, T value, Map<String, T> finiteValueMap) {
            super(key, finiteValueMap.entrySet().stream().filter(entry-> Objects.equals(value, entry.getValue())).findAny().map(Map.Entry::getKey) ,value);
            this.finiteValueMap = finiteValueMap;
        }

        @Override
        public boolean validateAndUpdate() {
            T val = finiteValueMap.get(this.value);
            if(val != null){
                this.originValue    = val;
                return true;
            }
            return false;
        }

        @Override
        public String updateValue(T val) {
            return finiteValueMap.entrySet().stream().filter(entry-> Objects.equals(val, entry.getValue())).findAny().map(Map.Entry::getKey).orElseThrow();
        }

        @Override
        public Class identifier() {
            return Enum.class;
        }
    }



}