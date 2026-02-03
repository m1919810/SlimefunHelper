package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.AttrKeyValue;

import javax.annotation.Nonnull;

@AllArgsConstructor
public class FlagRef extends Ref<Boolean>{
    public static final Class<Boolean> TYPE = Boolean.class;

    boolean flag;
    public FlagRef(Object newFlag){
        this((boolean)newFlag);
    }

    public static FlagRef fromString(String value){
        if("true".equals(value)){
            return new FlagRef(true);
        }else if("false".equals(value)){
            return new FlagRef(false);
        }else return null;
    }

    @Nonnull
    @Override
    public Boolean getValue() {
        return get();
    }

    public boolean get(){
        return flag;
    }

    @Override
    public void setValue(Boolean value) {
        set(value);
    }

    @Override
    public Object getAsPrimitive() {
        return flag? "true": "false";
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof FlagRef;
    }

    @Override
    public <W> boolean copyValueTo(Ref<W> otherRef) {
        if(otherRef instanceof FlagRef flagRef){
            flagRef.set(this.flag);
            return true;
        }else {
            return false;
        }
    }

    @Override
    public AttrKeyValue<Boolean> _createKeyValue0(String key) {
        return AttrKeyValue.bool(key, this.flag);
    }

    public void set(boolean val){
        if(validateUpdateValue(val)){
            this.flag = val;
            callUpdate();
        }
    }
}