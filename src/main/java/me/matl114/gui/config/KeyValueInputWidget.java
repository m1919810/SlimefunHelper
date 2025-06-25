package me.matl114.gui.config;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Pair;
import me.matl114.access.ScreenAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.utils.Debug;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;


import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class KeyValueInputWidget<T> extends SubScreenWidget {
    AttrKeyValue<T> keyValueHolder;
    int dkey;

    public KeyValueInputWidget(int x, int y, int dx, int dy, int dKey, AttrKeyValue<T> kv) {
        super(x, y, dx, dy);
        this.dkey = dKey;
        this.keyValueHolder = kv;
        init();
    }
    DisplayWidget keyLabel;
    DrawableWidget interactPlace;
    private static final Identifier SEARCH_TEXTURE = new Identifier("slimefunhelper", "textures/gui/search.png");
    private static final List<Text> SEARCH_TOOLTIPS = List.of(
       Text.literal(  "从注册标中选择"),
       Text.literal( "选择后点击确认")
    );
    private void openRegistrySearch(Registry<T> registry, TextFieldWidget widget){

        ScreenAccess.of(new RegistryChooseScreen<>(registry, (var)->widget.setText(registry.getId(var).toString()))).openFromCurrent();
    }
    protected void valueChange(){

    }
    protected void init(){
        this.keyLabel = DisplayWidget.instance(1,1, dkey -1, dy -1)
            .setRenderHandler(LabelElement.instance(Text.literal(this.keyValueHolder.getKeyName())))
            .addToSub(this)
            ;
        Class<?> id = this.keyValueHolder.identifier();
        if(id == Boolean.class){
            AttrKeyValue<Boolean> bol = (AttrKeyValue<Boolean>) this.keyValueHolder;
            //use button
            this.interactPlace = ExecutableWidget.instance(dkey +1, 1, dy -2, dy- 2)
                .setElementHandler(
                    IconElement.stateGuiPredicate(
                        ButtonElement.BUTTON,
                        ButtonElement.BUTTON_INACTIVE,
                        ButtonAction.run(()-> bol.valueChange(this, String.valueOf (!bol.getOriginValue()))),
                        (bl)->bol.validateValue()
                    )
                )
                .addToSub(this)
            ;
        }else if(id == Registry.class){
            Registry<T> thisRegistry =Objects.requireNonNull (((AttrKeyValue.RegistryAttrKeyValue<T>)this.keyValueHolder).getRegistry());
            ContentDelegateWidget<TextFieldWidget> interactPlace = McWidgetHelpers.createTextFieldEditBox(
                dkey + 1, 1, dx - dkey - dy -2, dy -2, this.keyValueHolder, this.keyValueHolder.getValue(), McWidgetHelpers.getWrongRedTextBoxColorProvider(this.keyValueHolder::isValidate)
            )
                .addToSub(this);
            this.interactPlace = interactPlace;
            TextFieldWidget widget = interactPlace.getDelegate();
            ExecutableWidget.instance(dx - dy +1, 1, dy -1, dy -1)
                .setElementHandler(
                    IconElement.fixed(SEARCH_TEXTURE, ButtonAction.run(()->this.openRegistrySearch(thisRegistry, widget)))
                        .withTooltips(TooltipHandler.of(SEARCH_TOOLTIPS))
                )
                .addToSub(this);

        }else if(id == Enum.class){
            List<String> flattenMap = ((AttrKeyValue.EnumAttrKeyValue<T>)this.keyValueHolder).getValueMap().keySet().stream().toList();
            int choices = flattenMap.size();
            Preconditions.checkArgument(choices > 0);
            int index = flattenMap.indexOf(this.keyValueHolder.getValue());
            if(index == -1){
                this.keyValueHolder.valueChange(this, flattenMap.get(0));
                index = 0;
            }
            AtomicInteger integer = new AtomicInteger();
            ExecutableWidget.instance(dkey + 1, 1, dx - dkey -2, dy -2)
                .setElementHandler(
                    new ButtonElement((ign)-> Text.literal(flattenMap.get(integer.get())), ButtonAction.run(()->{
                        int index0 = integer.get();
                        index0 = (index0 +1)%choices;
                        integer.set(index0);
                        this.keyValueHolder.valueChange(this, flattenMap.get(index0));
                    }))
                        .withTooltips(TooltipHandler.of(List.of(Text.literal(this.keyValueHolder.getKeyName()))))
                )
                .addToSub(this);
        }else{
            this.interactPlace = McWidgetHelpers.createTextFieldEditBox(
                    dkey + 1, 1, dx - dkey - 2, dy -2, this.keyValueHolder, this.keyValueHolder.getValue(), McWidgetHelpers.getWrongRedTextBoxColorProvider(this.keyValueHolder::isValidate)
                )
                .addToSub(this);
        }
    }
}
