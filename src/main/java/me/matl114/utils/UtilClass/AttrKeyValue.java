package me.matl114.utils.UtilClass;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.KeyValueInputWidget;
import me.matl114.managers.Config;
import me.matl114.managers.MultiKeyBind;
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
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    final String keyName;
    private String value;
    public String getValue(){
        return value;
    }
    @Getter
    @Nullable
    private T originValue;
    @Getter
    List<Predicate<T>> validators = new ArrayList<>();
    private boolean passValidators(T value){
        try{
            for (var validator : validators){
                if(!validator.test(value)){
                    return false;
                }

            }
            return true;
        }catch (Throwable e){
            return false;
        }
    }
    public boolean setOriginValue(T value){
        if(passValidators(value)){
            this.originValue = value;
            return true;
        }
        return false;
    }

    @Getter
    boolean validate = true;
    public T validateValue(){
        this.validate = validateAndUpdate();
        return this.originValue;
    }
    public abstract boolean validateAndUpdate();

    protected abstract String updateValue(T val);

    public abstract Class identifier();
    @Override
    public void valueChange(Object selectable, String string) {
        this.value = string;
        validateValue();
    }

    public void valueChangeInternal(Object selectable, T val){
        valueChange(selectable, updateValue(val));
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
            switch (this.getValue()){
                case "true"->{
                    return setOriginValue(true);
                }
                case "false"->{
                    return setOriginValue(false);
                }
                default -> {
                    return false;
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
                return setOriginValue(Integer.parseInt(this.getValue()));
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
        final int min;
        @Getter
        final int max;

        public ClampedIntAttrKeyValue(String key, int value, int min, int max) {
            super(key, value);
            this.min = min;
            this.max  = max;
            getValidators().add(i -> i >= this.min && i <= this.max);
        }

        public int clampInput(int val){
            return MathHelper.clamp(val, min, max);
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
                    return setOriginValue(Double.parseDouble(this.getValue()));
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
                return setOriginValue(this.getValue());

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

    public static  AttrKeyValue<MultiKeyBind> keyBind(String key, MultiKeyBind keyBind){
        return new AttrKeyValue<MultiKeyBind>(key, keyBind) {
            @Override
            public boolean validateAndUpdate() {
                if(getValue().startsWith("hotkey:")){
                    try{
                        return setOriginValue(new MultiKeyBind(getValue()));
                    }catch (Throwable e){ }
                }
                return false;
            }

            @Override
            public String updateValue(MultiKeyBind val) {
                return val.asString();
            }

            @Override
            public Class identifier() {
                return MultiKeyBind.class;
            }
        };
    }
    public static class ListAttrKeyValue extends AttrKeyValue<List<String>> {
        static final Gson gson = new Gson();
        static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();
        @Getter
        private final List<Predicate<String>> elementValidators = new ArrayList<>();

        public List<AttrKeyValue<String>> createAttrKeyValueForElements(){
            var list = getOriginValue();
            var size = list.size();
            List<AttrKeyValue<String >> res = new ArrayList<>();
            for (int i = 0; i <size ; ++ i){
                AttrKeyValue<String> str = AttrKeyValue.str(this.getKeyName(), list.get(i));
                str.getValidators().addAll(elementValidators);
                res.add(str);
            }
            return res;
        }
        public AttrKeyValue<String> createNewAttrKeyValueElement(){
            AttrKeyValue<String> str = AttrKeyValue.str(this.getKeyName(), "");
            str.getValidators().addAll(elementValidators);
            return str;
        }
        public ListAttrKeyValue(String key, List<String> value) {
            super(key, value);
        }

        @Override
        public boolean validateAndUpdate() {
            try {
                List<String> json = gson.fromJson(this.getValue(), LIST_TYPE);
                return setOriginValue(json);
            }catch (Throwable e){
                return false;
            }
        }

        @Override
        protected String updateValue(List<String> val) {
            return gson.toJson(val);
        }

        @Override
        public Class identifier() {
            return List.class;
        }
    }
    public static AttrKeyValue<List<String>> list(String key, List<String> list){
        return new ListAttrKeyValue(key, list);
    }

    public static <T> AttrKeyValue<T> computeNonnull(String keyName, String value, Function<String, T> valueMapper){
        return new AttrKeyValue<T>(keyName, Optional.ofNullable(value), valueMapper.apply(value)) {
            @Override
            public boolean validateAndUpdate() {
                T val = valueMapper.apply(this.getValue());
                if(val != null){
                    return setOriginValue(val);
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
                int index = this.getValue().indexOf(":");
                if(index <= 0)return false;
                Identifier id =  Identifier.tryParse(this.getValue().substring(0,index), this.getValue().substring(index+1));
                if(id != null){
                    return setOriginValue(id);
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
                int index = this.getValue().indexOf(":");
                if(index >=0){
                    Identifier id = new Identifier(this.getValue().substring(0,index), this.getValue().substring(index+1));
                    var val = registry.getOrEmpty(id);
                    return val.filter(this::setOriginValue).isPresent();
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
            T val = finiteValueMap.get(this.getValue());
            if(val != null){
                return setOriginValue(val);
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
                nbtParser.apply(this.getOriginValue());
                return true;
            }catch (Throwable e){
                return false;
            }
        }

        public void applyFormatting(Consumer<String> callback){
            if(validate){
                try{
                    valueChange(null, new NbtOrderedStringFormatter().apply(this.getOriginValue()));
                    callback.accept(this.getValue());
                }catch (Throwable e){
                }
            }
        }

        @Override
        public boolean validateAndUpdate() {
            try{
                if(enableNull){
                    if(this.getValue() == null|| this.getValue().isEmpty()){

                        return setOriginValue(null) && extraParse();
                    }
                }
                this.validate = setOriginValue((new StringNbtReader(new StringReader(this.getValue()))).parseElement()) && extraParse();
                return this.validate;
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
            return McWidgetHelpers.createMultiLineEditBox(x, y, dx, dy, (ed, val)->valueChange(null, val), this.getValue(), McWidgetHelpers.getWrongRedTextBoxColorProvider(()->validate));
//            return widget;
        }
    }





}