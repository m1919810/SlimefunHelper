package me.matl114.managers.config;

import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.config.AttrKeyValue;

public class KeyBindRef extends ObjectRef<MultiKeyBind>{
    public static final Class<MultiKeyBind> TYPE = MultiKeyBind.class;

    public KeyBindRef(MultiKeyBind object) {
        super(object);
    }

    @Override
    public Object getAsPrimitive() {
        return get().asString();
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof KeyBindRef;
    }

    @Override
    public <W> boolean copyValueTo(Ref<W> otherRef) {
        if(otherRef instanceof KeyBindRef stringRef){
            stringRef.set(this.get());
            return true;
        }
        return false;
    }

    @Override
    public AttrKeyValue<MultiKeyBind> _createKeyValue0(String key) {
        //todo
        return AttrKeyValue.keyBind(key, this.get());
    }

    @Override
    protected MultiKeyBind validateAndCast(Object val) {
        return (MultiKeyBind) val;
    }

    public static KeyBindRef fromString(String val){
        if(val.startsWith("hotkey:")){
            try{
                return new KeyBindRef(new MultiKeyBind(val));
            }catch (Throwable e){
            }
        }
        return null;
    }
}