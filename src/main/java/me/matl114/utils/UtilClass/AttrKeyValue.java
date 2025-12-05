package me.matl114.utils.UtilClass;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.KeyValueInputWidget;
import me.matl114.managers.Config;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.NbtOrderedStringFormatter;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
    public SubScreenWidget generateKeyValueInput(int x, int y, int keyDx, int blankDx, int inputDx, int dy){
        return new KeyValueInputWidget<>(x, y, keyDx + blankDx + inputDx, dy, keyDx, blankDx, inputDx, this);
//        return new SubScr eenWidget(x, y, keyDx + blankDx + inputDx, dy)
//            .addDrawableChild(generateTextField(keyDx + blankDx ,0, inputDx, dy))
//            .addDrawableChild(
//                DisplayWidget.instance(0,0, keyDx, dy)
//                    .setRenderHandler(new ButtonElement(TextProvider.of(Text.translatableWithFallback(this.keyName, this.keyName)), ButtonAction.empty()))
//            );

    }
    public ContentDelegateWidget<TextFieldWidget> generateTextField(int x, int y, int dx, int dy){
        return McWidgetHelpers.createTextFieldEditBox(x, y, dx, dy, (ed, val)->valueChange(null, val), this.value, McWidgetHelpers.getWrongRedTextBoxColorProvider(()->validate));
    }

    public static  AttrKeyValue<?> ofConfigValue(String key, Object object){
        //todo need fix
        if(object instanceof Config.Ref<?> refs){
            return refs.createKeyValue(key);
        }else {
            throw new UnsupportedOperationException();
        }
//        if(object instanceof AtomicBoolean bool){
//            return bool(key, bool.get());
//        }else if(object instanceof AtomicInteger integer){
//            return integer(key, integer.get());
//        }else if(object instanceof AtomicDouble atomicDouble){
//            return doub(key, atomicDouble.get());
//        }else if (object instanceof Config.StringRef ref){
//            return str(key, ref.getValue());
//        }else if (object instanceof Config.EnumRef<?> enumRef){
//            return (enumMap(key, enumRef.getValue(), enumRef.getValue().getMap()));
//        }
//        else if (object instanceof AtomicReference<?> ref){
//            throw new UnsupportedOperationException();
//        }else {
//            throw new UnsupportedOperationException();
//        }
    }

    public static AttrKeyValue<Boolean> bool(String key){
        return bool(key, false);
    }
    public static class Bool extends AttrKeyValue<Boolean>{
        public Bool(String key, Boolean value) {
            super(key, value);
        }

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
    }
    public static AttrKeyValue<Boolean> bool(String key, boolean value){
        return new Bool(key, value);
    }

    public static class IntAttrKeyValue extends AttrKeyValue<Integer> {
        public IntAttrKeyValue(String key, int value) {
            super(key, value);
        }

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

        public int clampInput(int val){
            return val;
        }
        @Override
        public Class<Integer> identifier() {
            return Integer.class;
        }
    }

    public static class ClampedIntAttrKeyValue extends IntAttrKeyValue{
        @Getter
        int min;
        @Getter
        int max;

        public ClampedIntAttrKeyValue(String key, int value, int min, int max) {
            super(key, value);
            this.min = min;
            this.max  = max;
        }

        public int clampInput(int val){
            return MathHelper.clamp(val, min, max);
        }

        @Override
        public boolean validateAndUpdate() {
            try{
                int val = Integer.parseInt(this.value);
                if(val >= min && val <= max){
                    this.originValue = val;
                    return true;
                }
                return false;
            }catch (Throwable e){
                return false;
            }
        }
    }

    public static AttrKeyValue<Integer> integer(String key, int val){
        return new IntAttrKeyValue(key, val);
    }
    public static AttrKeyValue<Integer> clampedInt(String key, int val, int from, int to){
        return new ClampedIntAttrKeyValue(key, val, from, to);
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

    public static <T> EnumAttrKeyValue<T> enumMap(String key, T val, Map<String, T> finiteValueMap){
        return new EnumAttrKeyValue<>(key, val, finiteValueMap).setIdentifier(val == null? Enum.class : val.getClass());
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

    public static AttrKeyValue<Identifier> identifier(String key, Identifier id){
        return new IdentifierAttrKeyValue(key, id);
    }

    public static class IdentifierAttrKeyValue extends AttrKeyValue<Identifier>{

        public IdentifierAttrKeyValue(String key, Identifier value) {
            super(key, value);
        }

        @Override
        public boolean validateAndUpdate() {
            try{
                int index = this.value.indexOf(":");
                if(index <= 0)return false;
                Identifier id =  Identifier.tryParse(this.value);
                if(id != null){
                    this.originValue = id;
                    return true;
                }else return false;
            }catch (Throwable e){
                return false;
            }
        }

        @Override
        public String updateValue(Identifier val) {
            return val.toString();
        }

        @Override
        public Class identifier() {
            return Identifier.class;
        }
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

    @Accessors(chain = true)
    public static class EnumAttrKeyValue<T> extends AttrKeyValue<T>{
        protected Map<String,T> finiteValueMap;
        public Map<String, T> getValueMap(){
            return finiteValueMap;
        }
        @Setter
        Class identifier = Enum.class;
        public EnumAttrKeyValue(String key, T value, Map<String, T> finiteValueMap) {
            super(key, finiteValueMap.entrySet().stream().filter(entry-> Objects.equals(value, entry.getValue())).findAny().map(Map.Entry::getKey) ,value);
            this.finiteValueMap = finiteValueMap;
        }

        @Override
        public boolean validateAndUpdate() {
            T val = finiteValueMap.get(this.value);
            if(val != null){
                this.originValue = val;
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
            return identifier;
        }

        public ExecutableWidget generateSwitchingButton(int x, int y, int dx, int dy, Consumer<EnumAttrKeyValue<T>> changelistener){
            if( Displayable.class.isAssignableFrom( identifier)){
                Map<String, Displayable> valueMap = (Map<String, Displayable>) (this).getValueMap();
                List<Pair<String, Displayable>> flattenMap = valueMap.entrySet().stream().map((entry)-> new Pair<>(entry.getKey(), entry.getValue())).toList();
                int choices = flattenMap.size();
                Preconditions.checkArgument(choices > 0);
                String val = this.getValue();
                int index = -1;
                for (int i=0 ; i< choices; ++ i){
                    if(Objects.equals(val, flattenMap.get(i).getFirst())){
                        index = i;
                        break;
                    }
                }
                if(index == -1){
                    this.valueChange(this, flattenMap.get(0).getFirst());
                    index = 0;
                }
                AtomicInteger integer = new AtomicInteger();
                integer.set(index);
                return ExecutableWidget.instance(x + 1, y + 1, dx -2, dy -2)
                    .setElementHandler(
                        new ButtonElement((ign)-> flattenMap.get(integer.get()).getSecond().getDisplay(), ButtonAction.run(()->{
                            int index0 = integer.get();
                            index0 = (index0 +1)%choices;
                            integer.set(index0);
                            this.valueChange(this, flattenMap.get(index0).getFirst());
                            changelistener.accept(this);
                        }))
                            .withTooltips(TooltipHandler.of(List.of(Text.translatable(this.getKeyName()))))
                    );
                   // .addToSub(this);
            }else {
                List<String> flattenMap = ((AttrKeyValue.EnumAttrKeyValue<T>)this).getValueMap().keySet().stream().toList();
                int choices = flattenMap.size();
                Preconditions.checkArgument(choices > 0);
                int index = flattenMap.indexOf(this.getValue());
                if(index == -1){
                    this.valueChange(this, flattenMap.get(0));
                    index = 0;
                }
                AtomicInteger integer = new AtomicInteger();
                integer.set(index);
                return ExecutableWidget.instance(x + 1, y +1, dx -2, dy -2)
                    .setElementHandler(
                        new ButtonElement((ign)-> Text.literal(flattenMap.get(integer.get())), ButtonAction.run(()->{
                            int index0 = integer.get();
                            index0 = (index0 +1)%choices;
                            integer.set(index0);
                            this.valueChange(this, flattenMap.get(index0));
                            changelistener.accept(this);
                        }))
                            .withTooltips(TooltipHandler.of(List.of(Text.literal(this.getKeyName()))))
                    );

            }
        }

    }
    @Accessors(chain = true)
    public static class NbtAttrKeyValue<W> extends AttrKeyValue<NbtElement>{
        protected final Function<NbtElement, W> nbtParser;
        public NbtAttrKeyValue<W> setEnableNull(boolean val){
            this.enableNull = val;
            return this;
        }
        protected boolean enableNull = false;
        public NbtAttrKeyValue(String key, NbtElement value, Function<NbtElement, W> function) {
            super(key, value == null? null : value.copy());
            this.nbtParser = function;
        }

        private boolean extraParse(){
            try{
                nbtParser.apply(this.originValue);
                return true;
            }catch (Throwable e){
                return false;
            }
        }

        public void applyFormatting(Consumer<String> callback){
            if(validate){
                try{
                    this.value = new NbtOrderedStringFormatter().apply(this.originValue);
                    callback.accept(this.value);
                }catch (Throwable e){
                }
            }
        }

        @Override
        public boolean validateAndUpdate() {
            try{
                if(enableNull){
                    if(this.value == null|| this.value.isEmpty()){
                        this.originValue = null;
                        return extraParse();
                    }
                }
                this.originValue = (new StringNbtReader(new StringReader(this.value))).parseElement();
                this.validate = true;
                return extraParse();
            }catch (Throwable e){
                this.validate = false;
                return false;
            }
        }

        @Override
        public String updateValue(NbtElement val) {
            return val == null? "": new StringNbtWriter().apply(val);
        }

        @Override
        public Class identifier() {
            return NbtElement.class;
        }

        public ContentDelegateWidget<EditBoxWidget> generateEditBox(int x, int y, int dx, int dy){
//            EditBoxWidget widget = new EditBoxWidget(MinecraftClient.getInstance().textRenderer, x,y, dx,dy, Text.empty(), Text.empty());
//            widget.setText(this.value);
//            widget.setChangeListener((val)->valueChange(null, val));
//            TextFieldAccess.of(widget).setBorderColorProvider();
            return McWidgetHelpers.createMultiLineEditBox(x, y, dx, dy, (ed, val)->valueChange(null, val), this.value, McWidgetHelpers.getWrongRedTextBoxColorProvider(()->validate));
//            return widget;
        }
    }


}