package me.matl114.gui.itemEdit;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import me.matl114.access.TextFieldAccess;
import me.matl114.bukkitUtiils.ItemStackHelper;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.ConfirmingBigScreen;
import me.matl114.gui.config.KeyValueInputWidget;
import me.matl114.gui.config.ListEntryWidgetController;
import me.matl114.gui.config.ListModifyWidget;
import me.matl114.hackUtils.ChatTasks;
import me.matl114.hackUtils.InvTasks;
import me.matl114.hackUtils.SlimefunTasks;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.NbtOrderedStringFormatter;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.injection.At;
import org.w3c.dom.Attr;
import oshi.util.tuples.Triplet;

import java.util.*;
import java.util.function.Consumer;

public class ItemEditScreen extends ConfirmingBigScreen {
    protected static final MinecraftClient mc  = MinecraftClient.getInstance();
    protected ItemStack itemStack;
    protected boolean validSaveState = true;
    protected State state = null;
    protected Consumer<ItemStack> callback;
    public ItemEditScreen(Text title, ItemStack itemStack, Consumer<ItemStack> callback) {
        super(title);
        this.itemStack = itemStack.copy();
        this.callback  = callback;
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return validSaveState;
    }

    @Override
    protected void onConfirmButton() {
        setState(null);
        this.close();
        if(this.callback == null){
            applyChangeToInventory();
        }else {
            callback.accept(this.itemStack);
        }
    }

    @Override
    protected void onCloseButton() {
        setState(null);
        this.close();
    }

    public void syncItemStack(){
        var re = this.processingSubScreen.getDelegate();
        if(re != null){
            re.saveChanges();
        }else {
            validSaveState = true;
        }
    }

    public void executeSave(){
        syncItemStack();
        if(validSaveState){
            SlimefunTasks.handleSaveItem(itemStack.copy());
        }else {
            Debug.chat(Text.literal("当前的编辑参数存在问题,不能保存为物品"));
        }
    }
    public void executeCmdCopy(){
        syncItemStack();
        if(validSaveState){
            InvTasks.copyGiveCommand(itemStack.copy());
        }else {
            Debug.chat(Text.literal("当前的编辑参数存在问题,不能保存为物品"));
        }
    }
    public void applyChangeToInventory(){
        if(mc.player != null ){
            if(mc.player.isCreative()){
                Debug.info("check slot",  mc.player.getInventory().selectedSlot);
                int slot = mc.player.getInventory().selectedSlot;
                InvTasks.setCreativeInventory(this.itemStack, slot);
            }else {
                Debug.chat(Text.literal("并非创造模式,无法实施物品改变;正在尝试使用give指令").formatted(Formatting.YELLOW));
                String command = InvTasks.createGiveCommand(itemStack.copy());
                if(command.length() >= 256){
                    Debug.chat(Text.literal("指令过长,请手动执行以避免被踢出服务器!").formatted(Formatting.RED));
                    Debug.chat(Text.literal("指令已经被拷贝到了剪切板中!").formatted(Formatting.RED));
                    mc.keyboard.setClipboard(command);
                }else {
                    ChatTasks.sendMessage(command, true);
                }
            }
        }
    }

    ExecutableWidget saveItem;
    ExecutableWidget copyCommand;
    ExecutableWidget editor;
    ExecutableWidget snbt;
    ExecutableWidget guide;

    protected static final Identifier SAVE_TEXTURE = new Identifier("slimefunhelper", "textures/gui/save.png");
    protected static final List<Text> SAVE_TOOLTIPS = List.of(
        Text.literal("点击保存当前状态到保存物品中")

    );
    protected static final Identifier COPYCMD_TEXTURE = new Identifier("slimefunhelper", "textures/gui/copy_command.png");
    protected static final List<Text> GIVE_TOOLTIPS = List.of(
        Text.literal("点击拷贝该物品的/give指令")
    );
    protected static final Identifier EDITOR_TEXTURE = new Identifier("slimefunhelper", "textures/gui/editor.png");
    protected static final List<Text> EDITOR_TOOLTIPS = List.of(
        Text.literal("点击打开NBT编辑器")
    );
    protected static final List<Text> SNBT_TOOLTIPS = List.of(
        Text.literal("点击打开SNBT编辑器")
    );
    protected static final Identifier SNBT_TEXTURE = new Identifier("slimefunhelper", "textures/gui/snbt_editor.png");
    protected static final List<Text> GUIDE_TOOLTIPS = List.of(
        Text.literal("点击打开 物品配方记录书")
    );
    protected static final Identifier GUIDE_TEXTURE = new Identifier("slimefunhelper", "textures/gui/list_tag.png");
    protected ItemProcessingSubScreen currentSubScreen;
    ContentDelegateWidget<EditBoxWidget> optionalMultiLine;
    ContentDelegateWidget<ItemProcessingSubScreen> processingSubScreen ;
    protected void setState(State state){
        if(this.currentSubScreen != null){
            this.currentSubScreen.saveChanges();
        }
        if(state == null){
            setTitleLabel(Text.literal("错误! 你是怎么打开这个界面的!").formatted(Formatting.RED));
            this.state = null;
            this.currentSubScreen = null;
        }else{
            var oldState = this.state;
            this.state = state;
            if(oldState != this.state){
                this.currentSubScreen = generateCurrentStateScreen();
            }
        }
        refreshScreen();
    }
    protected void refreshScreen(){
        this.optionalMultiLine.setContentDelegate(null);
        this.processingSubScreen.setContentDelegate(this.currentSubScreen);
        if(this.currentSubScreen != null){
            this.currentSubScreen.refreshScreen();
        }
    }

    protected ItemProcessingSubScreen generateCurrentStateScreen(){
        try{
            return switch (this.state){
                case SNBT -> new SnbtItemProcessingSubScreen();
                case EDITOR -> new ItemAttributeProcessingSubScreen();
                case NBT_TREE -> null;
            };
        }catch (Throwable e){
            this.close();
            Debug.chat( Text.literal( "An Error occured while opening item editor: ").formatted(Formatting.RED),e.getMessage());
            Debug.info(e);
        }
        return null;
    }
    @Override
    protected void init() {
        super.init();
        //子屏幕大小400 * content_height - 60
        //40 ~ 60,给上方的按钮
        //60 ~ conten_end_y给下面的屏幕
        //如果大小正常的画,应该是 400 * 270
        this.optionalMultiLine = new ContentDelegateWidget<>(0,0,0,0)
            .addTo(this);
        this.processingSubScreen = new ContentDelegateWidget<ItemProcessingSubScreen>(this.x + CONTENT_START_X, this.y + CONTENT_START_Y + 20, this.backgroundWidth - 2* CONTENT_START_X, this.content_end_y - CONTENT_START_Y - 20)
            .addTo(this)
            ;
        //生成切换按钮
        this.saveItem = ExecutableWidget.instance(
            this.x + CONTENT_START_X + 1, this.y + CONTENT_START_Y + 1, 18, 18
        )
            .setElementHandler(
                IconElement.fixed(SAVE_TEXTURE, ButtonAction.run(this::executeSave))
                    .withTooltips(TooltipHandler.of(SAVE_TOOLTIPS))
            )
            .addTo(this)
        ;
        this.copyCommand = ExecutableWidget.instance(
            this.x + CONTENT_START_X + 21, this.y + CONTENT_START_Y + 1, 18, 18
        )
            .setElementHandler(
                IconElement.fixed(COPYCMD_TEXTURE, ButtonAction.run(this::executeCmdCopy))
                    .withTooltips(TooltipHandler.of(GIVE_TOOLTIPS))
            )
            .addTo(this)
        ;
        this.editor = ExecutableWidget.instance(
                this.x + CONTENT_START_X + 41, this.y + CONTENT_START_Y + 1, 18, 18
            )
            .setElementHandler(
                IconElement.fixed(EDITOR_TEXTURE, ButtonAction.run(()->this.setState(State.EDITOR)))
                    .withTooltips(TooltipHandler.of(EDITOR_TOOLTIPS))
            )
            .addTo(this)
        ;
        this.snbt = ExecutableWidget.instance(
                this.x + CONTENT_START_X + 61, this.y + CONTENT_START_Y + 1, 18, 18
            )
            .setElementHandler(
                IconElement.fixed(SNBT_TEXTURE, ButtonAction.run(()->this.setState(State.SNBT)))
                    .withTooltips(TooltipHandler.of(SNBT_TOOLTIPS))
            )
            .addTo(this)
        ;
        this.guide = ExecutableWidget.instance(
                this.x + CONTENT_START_X + 81, this.y + CONTENT_START_Y + 1, 18, 18
            )
            .setElementHandler(
                IconElement.fixed(GUIDE_TEXTURE, ButtonAction.run(SlimefunTasks::handleClickGuideIcon))
                    .withTooltips(TooltipHandler.of(GUIDE_TOOLTIPS))
            )
            .addTo(this)
        ;

        setState(this.state == null? State.SNBT : this.state);
    }

    protected abstract class ItemProcessingSubScreen extends SubScreenWidget {

        public ItemProcessingSubScreen() {
            super(0,0, 0,0);
        }
        protected abstract void saveChanges();
        protected abstract void refreshScreen();
    }
    protected class SnbtItemProcessingSubScreen extends ItemProcessingSubScreen{

        public SnbtItemProcessingSubScreen() {
            super();
            ItemEditScreen.this.setTitleLabel(Text.literal("Snbt 编辑器").formatted(Formatting.GREEN));
            init();
        }
        boolean stringFormatRight = true;
        String compoundString;
        NbtCompound compoundItem;
        ItemStack lastResult;
        ExecutableWidget formatButton;
        EditBoxWidget widget ;
        protected static final Identifier FORMAT_TEXTURE = new Identifier("slimefunhelper", "textures/gui/format.png");
        protected boolean validateAndUpdate(){
            try{
                compoundItem = StringNbtReader.parse(this.compoundString);
                stringFormatRight = true;
                return true;
            }catch (Throwable e){
                stringFormatRight = false;
                return false;
            }
        }
        protected boolean validateItemStack(){
            if(validateAndUpdate()){
                try{
                    ItemStack decode = ItemStack.fromNbt(this.compoundItem);
                    this.lastResult = decode;
                    ItemEditScreen.this.validSaveState = true;
                    return true;
                }catch (Throwable e){
                }

            }
            ItemEditScreen.this.validSaveState = false;
            return false;
        }


        protected static final Gson jsonStringify = new Gson();
        protected static final Gson jsonFormatStringify = new GsonBuilder().setPrettyPrinting().create();
        protected void formattingJsonString(){
            if(!stringFormatRight){
                Debug.info("Illegal call from ? ,Flag is not true");
                Debug.stackTrace();
            }else {
                try{
                    String value = new NbtOrderedStringFormatter().apply(this.compoundItem);
                    this.compoundString = value;
                    widget.setText(value);
                }catch (Throwable e){

                }
            }
        }
        protected void error(){
            throw new RuntimeException("Error while parsing itemStack snbt");
        }
        protected void updateString(String value){
            this.compoundString = value;
            //update available flag;
            this.validateItemStack();
        }
        protected void init(){
            //transform item to json
            this.lastResult = ItemEditScreen.this.itemStack.copy();
            NbtCompound compound = this.lastResult.writeNbt(new NbtCompound());

            this.compoundItem = compound;
            this.compoundString = new StringNbtWriter().apply(this.compoundItem);
            if(!validateItemStack()){
                error();
            }
            this.formatButton = ExecutableWidget.instance(141, -19, 18, 18)
                .setElementHandler(
                    IconElement.fixed(FORMAT_TEXTURE, ButtonAction.run(this::formattingJsonString))
                        .withActiveActionCondition((icon)->{
                            if(icon instanceof IconElement){
                                if(this.stringFormatRight){
                                    this.formatButton.setAlpha(1.0f);
                                    return true;
                                }else {
                                    this.formatButton.setAlpha(0.4f);
                                    return false;
                                }
                            }else return true;
                        })
                )
                .addToSub(this)
            ;

            //init snbt edit box, init formatting button
        }

        @Override
        protected void saveChanges() {
            validateItemStack();
            ItemEditScreen.this.itemStack = this.lastResult .copy();

        }

        @Override
        protected void refreshScreen() {
            //refresh widget with absolute coord
            this.widget = new EditBoxWidget(mc.textRenderer, ItemEditScreen.this.processingSubScreen.getX() +10,ItemEditScreen.this.processingSubScreen.getY()+10, ItemEditScreen.this.processingSubScreen.getWidth() - 20, ItemEditScreen.this.processingSubScreen.getHeight() -20, Text.empty(), Text.empty());
            widget.setText(this.compoundString);
            widget.setChangeListener(this::updateString);
            TextFieldAccess.of(widget).setBorderColorProvider(McWidgetHelpers.getWrongRedTextBoxColorProvider(()->ItemEditScreen.this.validSaveState));
            ItemEditScreen.this.optionalMultiLine.setContentDelegate(widget);
        }
    }
    //如果大小正常的画,应该是 400 * 270
    protected class ItemAttributeProcessingSubScreen extends ItemProcessingSubScreen{
        public ItemAttributeProcessingSubScreen(){
            super();
            this.stackTemplate = itemStack.copy();
            init();
        }
        ItemStack stackTemplate;
        ItemAttrSubSubScreen currentSubSubScreen;
        ContentDelegateWidget<ItemAttrSubSubScreen> delegateWidget;
        ItemAttr currentAttr = null;
        protected static final Text REFRESH_DISPLAY_LABEL =Text.literal("点击下方刷新");
        protected static final int SELECT_WIDTH = 120;
        protected void setCurrentAttr(ItemAttr attr){
            if(this.currentSubSubScreen != null){
                this.currentSubSubScreen.saveChanges();
            }
            if(attr == null){
                this.currentSubSubScreen = null;
            }else{
                var oldState = this.currentAttr;
                this.currentAttr = attr;
                if(oldState != this.currentAttr){
                    this.currentSubSubScreen = generateCurrentAttrScreen();
                }
            }
            this.delegateWidget.setContentDelegate(this.currentSubSubScreen);
            if(this.currentSubSubScreen != null){
                this.currentSubSubScreen.refreshScreen();
            }
        }
        protected ItemAttrSubSubScreen generateCurrentAttrScreen(){
            return switch (this.currentAttr){
                case BASIC -> new ItemBasicAttributeSubSubScreen();
                case DISPLAY -> new ItemDisplaySubSubScreen();
                case ENCHANTMENT -> new ItemEnchantListSubSubScreen();
                case ATTRIBUTE -> new ItemAttributeModifiersSubSubScreen();
                default -> null;
            };
        }
        protected void init(){
            this.delegateWidget = new ContentDelegateWidget<>(SELECT_WIDTH, 0, 0,0);
            setCurrentAttr(this.currentAttr == null? ItemAttr.BASIC : this.currentAttr);
            refreshScreen();
        }

        @Override
        protected void saveChanges() {
            if(this.currentSubSubScreen != null){
                this.currentSubSubScreen.saveChanges();
            }
            itemStack = stackTemplate.copy();
        }
        //fixme 窗口resize之后自下而上对齐的图标混乱
        @Override
        protected void refreshScreen() {
            this.children.clear();
            for (var attr: ItemAttr.values()){
                Text selectedText = Text.literal(attr.display).formatted(Formatting.YELLOW);
                Text unselectedText = Text.literal(attr.display);
                ExecutableWidget.instance(20, attr.ordinal() * 30, 80, 30)
                    .setElementHandler(
                        new ButtonElement((el)-> {
                            return currentAttr == attr ? selectedText : unselectedText;
                        }, ButtonAction.run(()->setCurrentAttr(attr)))
                    )
                    .addToSub(this);
            }
            DisplayWidget.instance(30, ItemEditScreen.this.processingSubScreen.getHeight() - 90, 60, 20)
                .setRenderHandler(LabelElement.instance(REFRESH_DISPLAY_LABEL))
                .addToSub(this);
            ExecutableWidget.instance(30, ItemEditScreen.this.processingSubScreen.getHeight() - 70, 60,60)
                .setElementHandler(
                    new SlotElement(InventoryUtils.createReadOnlyInventory(()->this.stackTemplate),0,(it,bt)->{
                        this.saveChanges();
                        return true;
                    } )
                )
                .addToSub(this);
            this.delegateWidget.addToSub(this);
            setCurrentAttr(this.currentAttr);
        }
        //320 * 270
        protected abstract class ItemAttrSubSubScreen extends SubScreenWidget{

            public ItemAttrSubSubScreen() {
                super(0, 0, 0,0);
            }

            protected abstract void saveChanges();

            protected abstract void refreshScreen() ;
        }

        protected class ItemBasicAttributeSubSubScreen extends ItemAttrSubSubScreen{
            AttrKeyValue<Item> item;
            AttrKeyValue<Integer> count;
            AttrKeyValue<Boolean> unbreakable;
            AttrKeyValue<Integer> damage;
            AttrKeyValue<String> sfid;
            ItemHideFlags flags;
            {
                init();
            }
            protected static final int BASIC_DKEY = 50;
            protected static class ItemHideFlags{
                //true means hide
                EnumMap<ItemStack.TooltipSection, Boolean> flagMapper;

                public ItemHideFlags(ItemStack stack){
                    this.flagMapper = new EnumMap<>(ItemStack.TooltipSection.class);
                    int hideflag = stack.getHideFlags();
                    for (var sec: ItemStack.TooltipSection.values()){
                        flagMapper.put(sec, ((hideflag & sec.getFlag()) != 0));
                    }
                }
                public DrawableWidget factory(int x, int y){
                    SubScreenWidget subScreenWidget =  new SubScreenWidget(x,y,0,0);
                    subScreenWidget.addDrawableChild(
                        DisplayWidget.instance(1,1, 50 -1, 20 -1)
                            .setRenderHandler(LabelElement.instance(Text.literal("是否隐藏")))
                    );
                    for (var sec: ItemStack.TooltipSection.values()){
                        int index = sec.ordinal();
                        subScreenWidget.addDrawableChild(
                            ExecutableWidget.instance(50 +1 + 20 * index, 1, 20 -2, 20- 2)
                                .setElementHandler(
                                    IconElement.stateGuiPredicate(
                                        ButtonElement.BUTTON,
                                        ButtonElement.BUTTON_INACTIVE,
                                        ButtonAction.run(()-> flagMapper.compute(sec, (sec00,b)->!b)),
                                        (bl)->flagMapper.get(sec)
                                    )
                                        .withTooltips(TooltipHandler.of(List.of(Text.literal(sec.name().toLowerCase(Locale.ROOT)))))
                                )
                        );
                    }
                    return subScreenWidget;
                }
                public void applyChange(ItemStack stack){
                    //clear hideFlags
                    if(stack.hasNbt())stack.getNbt().remove("HideFlags");
                    for (var sec: ItemStack.TooltipSection.values()){
                        if(flagMapper.get(sec)){
                            stack.addHideFlag(sec);
                        }
                    }
                }
            }
            protected void init(){
                this.item = AttrKeyValue.registry("物品ID", Registries.ITEM, stackTemplate.getItem());
                this.count = AttrKeyValue.integer("数量", stackTemplate.getCount());
                this.damage = AttrKeyValue.integer("耐久损耗", stackTemplate.getDamage());
                String sfid = ItemStackUtils.getSfId(stackTemplate);
                this.sfid = AttrKeyValue.str("粘液id", sfid == null? "": sfid);
                this.unbreakable = AttrKeyValue.bool( "无法破坏", ItemStackUtils.getIsUnbreakable(stackTemplate));
                this.flags = new ItemHideFlags(stackTemplate);
                new KeyValueInputWidget<>(30, 0,240, 20, 50, this.item)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);
                new KeyValueInputWidget<>(30, 30,240, 20, 50, this.count)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);
                new KeyValueInputWidget<>(30, 60,240, 20, 50, this.damage)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);
                new KeyValueInputWidget<>(30, 90,240, 20, 50, this.sfid)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);
                new KeyValueInputWidget<>(30, 120,240, 20, 50, this.unbreakable)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);
                this.flags.factory(30, 150)
                    .addToSub((ItemBasicAttributeSubSubScreen)this);

            }
            @Override
            protected void saveChanges() {
                if(stackTemplate.getItem() != this.item.getOriginValue()){
                    var origin = stackTemplate;
                    stackTemplate = new ItemStack(this.item.getOriginValue());
                    stackTemplate.setNbt(origin.getNbt());
                }
                stackTemplate.setCount(count.getOriginValue());
                ItemStackUtils.setDamage(stackTemplate, damage.getOriginValue());
                ItemStackUtils.setSfId(stackTemplate, sfid.getOriginValue());
                ItemStackUtils.setUnbreakable(stackTemplate, this.unbreakable.getOriginValue());
                this.flags.applyChange(stackTemplate);
            }


            @Override
            protected void refreshScreen() {

            }
        }
        protected class ItemDisplaySubSubScreen extends ItemAttrSubSubScreen {

            {
                init();
            }
            AttrKeyValue<String> displayName;
            List<AttrKeyValue<String>> lores;
            protected void init(){
                Text text = ItemStackUtils.getCustomName(stackTemplate);
                String textCode = text == null? "": ChatUtils.textToString(text);
                displayName = AttrKeyValue.str("自定义名称", textCode);
                lores = new ArrayList<>();
                List<Text> loreComp = ItemStackUtils.getLore(stackTemplate);
                for (var txt : loreComp){
                    lores.add(AttrKeyValue.str("--", txt == null? "" : ChatUtils.textToString(txt)));
                }
                new KeyValueInputWidget<>(10, 0, 260,20, 50, this.displayName)
                    .addToSub(this);
                new ListModifyWidget(
                    ListEntryWidgetController.mutable(
                        this.lores,
                        ()-> AttrKeyValue.str("--",""),
                        (str)-> new KeyValueInputWidget<>(0, 0, 180, 20, 30, str),
                        20,
                        180
                    ),
                    10, 40, 260, ItemEditScreen.this.processingSubScreen.getHeight() - 50
                )
                    .addToSub(this);
            }
            @Override
            protected void saveChanges() {
                String displayName0 = displayName.getOriginValue();
                Text customName = (displayName0 == null||displayName0.isEmpty())?null: ChatUtils.stringToText(displayName0);
                ItemStackUtils.setCustomName(stackTemplate, customName);
                List<Text> texts = new ArrayList<>();
                for (var s : this.lores){
                    String displayName1 = s.getOriginValue();
                    Text customName1 = (displayName1 == null||displayName1.isEmpty())?null: ChatUtils.stringToText(displayName1);
                    customName1 = customName1 == null? Text.empty(): customName1;
                    texts.add(customName1);
                }
                ItemStackUtils.setLore(stackTemplate, texts);
            }

            @Override
            protected void refreshScreen() {

            }
        }

        protected class ItemEnchantListSubSubScreen extends ItemAttrSubSubScreen{


            protected static class ItemEnchantAttrGroup{
                public ItemEnchantAttrGroup(String enchantment,  int level){
                    id = AttrKeyValue.openRegistry("附魔",Registries.ENCHANTMENT, enchantment);
                    lvl = AttrKeyValue.integer("等级", level);
                }
                AttrKeyValue<Enchantment> id;
                AttrKeyValue<Integer> lvl;
                public DrawableWidget factory(){
                    return new SubScreenWidget(0,0,0,0)
                        .addDrawableChild(
                            new KeyValueInputWidget<>(0, 0, 120, 20, 30, this.id)
                        )
                        .addDrawableChild(
                            new KeyValueInputWidget<>(120, 0, 60, 20, 30, this.lvl)
                        );
                }
                public Pair<String, Integer> value(){
                    return new Pair<>(this.id.getValue(), this.lvl.getOriginValue());
                }
            }
            {
                init();
            }
            protected List<ItemEnchantAttrGroup> enchantList;
            protected void init(){
                enchantList = new ArrayList<>();
                ItemStackUtils.getItemEnchant(stackTemplate)
                    .forEach(var->{
                        enchantList.add(new ItemEnchantAttrGroup(var.getFirst(), var.getSecond()));
                    });
                new ListModifyWidget(
                    ListEntryWidgetController.mutable(
                        enchantList,
                        ()->new ItemEnchantAttrGroup("minecraft:",0),
                        ItemEnchantAttrGroup::factory,
                        20,
                        180
                    ),
                    10, 0,260, ItemEditScreen.this.processingSubScreen.getHeight() - 10
                )
                    .addToSub(this);
            }

            @Override
            protected void saveChanges() {
                ItemStackUtils.applyItemEnchant(stackTemplate, this.enchantList.stream().map(ItemEnchantAttrGroup::value));
            }

            @Override
            protected void refreshScreen() {

            }
        }
        protected class ItemAttributeModifiersSubSubScreen extends ItemAttrSubSubScreen{
            protected class ItemAttributeModifierEntry{
                public ItemAttributeModifierEntry(String attribute, EntityAttributeModifier modifier, EquipmentSlot slot){
                    attr = AttrKeyValue.openRegistry("属性名", Registries.ATTRIBUTE, attribute);
                    modifierComp = modifier.toNbt();
                    modifierValue = AttrKeyValue.doub("值", modifier.getValue());
                    modifierOperation = AttrKeyValue.enumMap("操作", modifier.getOperation(), NAME_TO_OPER);
                    optionalSlot = AttrKeyValue.enumMap("槽位", Optional.ofNullable(slot), NAME_TO_OP);
                }
                NbtCompound modifierComp;
                AttrKeyValue<EntityAttribute> attr;
                AttrKeyValue<Double> modifierValue;
                AttrKeyValue<EntityAttributeModifier.Operation> modifierOperation;
                AttrKeyValue<Optional< EquipmentSlot>> optionalSlot;
                protected static final Map<String, Optional<EquipmentSlot>> NAME_TO_OP = new LinkedHashMap<>();
                protected static final Map<String, EntityAttributeModifier.Operation> NAME_TO_OPER = new LinkedHashMap<>();
                static{
                    NAME_TO_OP.put("全部槽位", Optional.empty());
                    NAME_TO_OP.put("主手",Optional.of(EquipmentSlot.MAINHAND));
                    NAME_TO_OP.put("副手",Optional.of(EquipmentSlot.OFFHAND));
                    NAME_TO_OP.put("头盔",Optional.of(EquipmentSlot.HEAD));
                    NAME_TO_OP.put("胸甲", Optional.of(EquipmentSlot.CHEST));
                    NAME_TO_OP.put("护腿",Optional.of(EquipmentSlot.LEGS));
                    NAME_TO_OP.put("靴子",Optional.of(EquipmentSlot.FEET));
                    NAME_TO_OPER .put("加法", EntityAttributeModifier.Operation.ADDITION);
                    NAME_TO_OPER.put("乘基数", EntityAttributeModifier.Operation.MULTIPLY_BASE);
                    NAME_TO_OPER.put("乘法", EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
                }
                public ItemAttributeModifierEntry(){
                    this("minecraft:", new EntityAttributeModifier(null, 0.0d, EntityAttributeModifier.Operation.ADDITION), null);
                }

                public Triplet<String, EntityAttributeModifier, EquipmentSlot> value(){
                    EntityAttributeModifier modifier = new EntityAttributeModifier(
                        modifierComp.getUuid("UUID"),
                        modifierComp.getString("Name"),
                        modifierValue.getOriginValue(),
                        modifierOperation.getOriginValue()
                    );
                    return new Triplet<>(attr.getValue(), modifier, optionalSlot.getOriginValue().orElse(null));
                }
                public DrawableWidget factory(){
                    return new SubScreenWidget(0,0,0,0)
                        .addDrawableChild(
                            new KeyValueInputWidget<>(0, 0, 180, 20, 50, this.attr)
                        )
                        .addDrawableChild(
                            new KeyValueInputWidget<>(0, 20, 80, 20, 20, this.optionalSlot)
                        )
                        .addDrawableChild(
                            new KeyValueInputWidget<>(80, 20, 80, 20, 20, this.modifierOperation)
                        )
                        .addDrawableChild(
                            new KeyValueInputWidget<>(160, 20, 100, 20, 20, this.modifierValue)
                        );

                }


            }
            {
                init();
            }
            protected List<ItemAttributeModifierEntry> modifierEntries ;
            protected void init(){
                this.modifierEntries = new ArrayList<>();
                ItemStackUtils.getEntityModifier(stackTemplate)
                    .forEach(var->{
                        this.modifierEntries.add(new ItemAttributeModifierEntry(var.getA(), var.getB(), var.getC()));
                    });
                new ListModifyWidget(
                    ListEntryWidgetController.mutable(
                        this.modifierEntries,
                        ItemAttributeModifierEntry::new,
                        ItemAttributeModifierEntry::factory,
                        40, 180
                    ),
                    10, 0, 260, ItemEditScreen.this.processingSubScreen.getHeight() - 10
                )
                    .addToSub(this)
                ;
            }
            @Override
            protected void saveChanges() {
                ItemStackUtils.applyEntityModifier(stackTemplate, this.modifierEntries.stream().map(ItemAttributeModifierEntry::value));
            }

            @Override
            protected void refreshScreen() {

            }
        }


        protected static enum ItemAttr{
            BASIC("基础信息"),
            DISPLAY("物品样式"),
            ENCHANTMENT("物品附魔"),
            ATTRIBUTE("物品属性");
            String display;
            ItemAttr(String displayName){
                this.display = displayName;
            }
        }
    }


    protected static enum State {
        SNBT,
        NBT_TREE,
        EDITOR;
    }
}
