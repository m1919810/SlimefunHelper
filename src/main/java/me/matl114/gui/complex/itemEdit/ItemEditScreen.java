package me.matl114.gui.complex.itemEdit;

import static net.minecraft.component.DataComponentTypes.*;

import com.google.common.base.Preconditions;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.bukkit.BukkitItemStackUtils;
import me.matl114.gui.Constants;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.KeyValueInputWidget;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.SlimefunTasks;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.kv.NbtAttrKeyValue;
import me.matl114.versioned.api.VItem;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class ItemEditScreen extends ConfirmingBigScreen {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    protected ItemStack itemStack;
    protected State state = null;
    protected Consumer<ItemStack> callback;
    protected static int CONTENT_START_X = 20;

    public ItemEditScreen(Text title, ItemStack itemStack, Consumer<ItemStack> callback) {
        super(title);
        this.itemStack = itemStack.copy();
        this.callback = callback;
    }

    protected boolean canConfirm() {
        var re = this.processingSubScreen.getDelegate();
        return re == null || re.canConfirm();
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return canConfirm();
    }

    @Override
    protected void onConfirmButton() {
        setState(null);
        this.close();
        if (this.callback == null) {
            applyChangeToInventory();
        } else {
            callback.accept(this.itemStack);
        }
    }

    @Override
    protected void onCloseButton() {
        setState(null);
        this.close();
    }

    public void syncItemStack() {
        var re = this.processingSubScreen.getDelegate();
        if (re != null) {
            re.saveChanges();
        }
    }

    public void executeSave() {
        syncItemStack();
        if (canConfirm()) {
            InvTasks.getSaveItem().addSaveItem(itemStack.copy());
        } else {
            Debug.chat(Text.translatable("widget.gui.item-edit-screen.save.error"));
        }
    }

    public void executeCmdCopy() {
        syncItemStack();
        if (canConfirm()) {
            InvTasks.copyGiveCommand(itemStack.copy());
        } else {
            Debug.chat(Text.translatable("widget.gui.item-edit-screen.save.error"));
        }
    }

    public void applyChangeToInventory() {
        if (mc.player != null) {
            if (mc.interactionManager != null
                    && mc.interactionManager.getCurrentGameMode().isCreative()) {
                Debug.chat(Text.translatable("widget.gui.item-edit-screen.save.apply-changes.creative")
                        .formatted(Formatting.GREEN));
                int slot = InventoryUtils.getSelectedSlot();
                InvTasks.setCreativeInventory(this.itemStack, slot);
            } else {
                Debug.chat(Text.translatable("widget.gui.item-edit-screen.save.apply-changes.command")
                        .formatted(Formatting.YELLOW));
                String command = InvTasks.createGiveCommand(itemStack.copy());
                if (command.length() >= 256) {
                    Debug.chat(Text.translatable("widget.gui.item-edit-screen.save.apply-changes.command.too-long")
                            .formatted(Formatting.RED));
                    mc.keyboard.setClipboard(command);
                } else {
                    ChatTasks.sayMessage(command, true);
                }
            }
        }
    }

    ExecutableWidget saveItem;
    ExecutableWidget copyCommand;
    ExecutableWidget editor;
    ExecutableWidget snbt;
    ExecutableWidget guide;

    protected static final Identifier SAVE_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/save");

    protected static final Identifier COPYCMD_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/copy_command");
    protected static final Identifier EDITOR_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/editor");

    protected static final Identifier SNBT_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/snbt_editor");
    protected static final Identifier GUIDE_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/list_tag");
    protected ItemProcessingSubScreen currentSubScreen;

    ContentDelegateWidget<EditBoxWidget> optionalMultiLine;
    ContentDelegateWidget<ItemProcessingSubScreen> processingSubScreen;

    protected void setState(State state) {
        if (this.currentSubScreen != null) {
            this.currentSubScreen.saveChanges();
        }
        if (state == null) {
            setTitleLabel(Text.translatable("widget.gui.item-edit-screen.state.error-title")
                    .formatted(Formatting.RED));
            this.state = null;
            this.currentSubScreen = null;
        } else {
            var oldState = this.state;
            this.state = state;
            if (oldState != this.state) {
                this.currentSubScreen = generateCurrentStateScreen();
            }
        }
        refreshScreen();
    }

    protected void refreshScreen() {
        this.optionalMultiLine.setContentDelegate(null);
        this.processingSubScreen.setContentDelegate(this.currentSubScreen);
        if (this.currentSubScreen != null) {
            this.currentSubScreen.refreshScreen();
        }
    }

    protected ItemProcessingSubScreen generateCurrentStateScreen() {
        try {
            return switch (this.state) {
                case SNBT -> new SnbtItemProcessingSubScreen();
                case EDITOR -> new ItemAttributeProcessingSubScreen();
                case NBT_TREE -> null;
            };
        } catch (Throwable e) {
            this.close();
            Debug.chat(
                    Text.translatable("widget.gui.item-edit-screen.open.error").formatted(Formatting.RED),
                    e.getMessage());
            Debug.info(e);
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();
        // 子屏幕大小400 * content_height - 60
        // 40 ~ 60,给上方的按钮
        // 60 ~ conten_end_y给下面的屏幕
        // 如果大小正常的画,应该是 400 * 270
        this.optionalMultiLine = new McWidgetHelpers.TextContentDelegateWidget<>(0, 0, null).addTo(this);
        this.processingSubScreen = new ContentDelegateWidget<ItemProcessingSubScreen>(
                        this.x + CONTENT_START_X,
                        this.y + CONTENT_START_Y + 20,
                        this.backgroundWidth - 2 * CONTENT_START_X,
                        this.content_end_y - CONTENT_START_Y - 20)
                .addTo(this);
        // 生成切换按钮
        this.saveItem = ExecutableWidget.instance(this.x + CONTENT_START_X + 1, this.y + CONTENT_START_Y + 1, 18, 18)
                .setElementHandler(IconElement.fixedGui(SAVE_TEXTURE_SPRITE, ButtonAction.run(this::executeSave))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.item-edit-screen.operate.save-item.tooltips", ""))))
                .addTo(this);
        this.copyCommand = ExecutableWidget.instance(
                        this.x + CONTENT_START_X + 21, this.y + CONTENT_START_Y + 1, 18, 18)
                .setElementHandler(IconElement.fixedGui(COPYCMD_TEXTURE_SPRITE, ButtonAction.run(this::executeCmdCopy))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.item-edit-screen.operate.copy-give-command.tooltips", ""))))
                .addTo(this);
        this.editor = ExecutableWidget.instance(this.x + CONTENT_START_X + 41, this.y + CONTENT_START_Y + 1, 18, 18)
                .setElementHandler(
                        IconElement.fixedGui(EDITOR_TEXTURE_SPRITE, ButtonAction.run(() -> this.setState(State.EDITOR)))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.item-edit-screen.operate.switch-to-nbt-editor.tooltips", ""))))
                .addTo(this);
        this.snbt = ExecutableWidget.instance(this.x + CONTENT_START_X + 61, this.y + CONTENT_START_Y + 1, 18, 18)
                .setElementHandler(
                        IconElement.fixedGui(SNBT_TEXTURE_SPRITE, ButtonAction.run(() -> this.setState(State.SNBT)))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.item-edit-screen.operate.switch-to-snbt-editor.tooltips", ""))))
                .addTo(this);
        this.guide = ExecutableWidget.instance(this.x + CONTENT_START_X + 81, this.y + CONTENT_START_Y + 1, 18, 18)
                .setElementHandler(IconElement.fixedGui(
                                GUIDE_TEXTURE_SPRITE,
                                ButtonAction.run(SlimefunTasks.getSlimefunGuide()::openMainGuideMenu))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.item-edit-screen.operate.open-slimefun-guide.tooltips", ""))))
                .addTo(this);

        setState(this.state == null ? State.EDITOR : this.state);
    }

    protected abstract class ItemProcessingSubScreen extends SubScreenWidget {

        public ItemProcessingSubScreen() {
            super(0, 0, 0, 0);
        }

        protected abstract void saveChanges();

        protected abstract boolean canConfirm();

        protected abstract void refreshScreen();
    }

    protected class SnbtItemProcessingSubScreen extends ItemProcessingSubScreen {

        public SnbtItemProcessingSubScreen() {
            super();
            ItemEditScreen.this.setTitleLabel(
                    Text.translatable("widget.gui.item-edit-screen.snbt-editor").formatted(Formatting.GREEN));
            init();
        }

        NbtAttrKeyValue<ItemStack> itemAttrValue;
        ItemStack lastResult;
        ExecutableWidget formatButton;
        EditBoxWidget widget;
        protected static final Identifier FORMAT_TEXTURE_SPRITE = Constants.FORMATTING_TEXTURE_SPRITE;

        protected ItemStack validateItemStack(NbtElement element) {
            // may throw
            ItemStack decode = VItem.getInstance().fromNbt((NbtCompound) element);
            Preconditions.checkArgument(decode != ItemStack.EMPTY);
            this.lastResult = decode;
            return decode;
        }

        protected void error() {
            throw new RuntimeException("Error while parsing itemStack snbt");
        }

        protected void init() {
            // transform item to json
            this.lastResult = ItemEditScreen.this.itemStack.copy();
            NbtElement compound = VItem.getInstance().toNbt(this.lastResult);
            this.itemAttrValue = new NbtAttrKeyValue<>("", compound, this::validateItemStack);
            if (!itemAttrValue.validateAndUpdate()) {
                error();
            }
            this.widget = this.itemAttrValue
                    .generateEditBox(
                            ItemEditScreen.this.processingSubScreen.getX() + 10,
                            ItemEditScreen.this.processingSubScreen.getY() + 10,
                            ItemEditScreen.this.processingSubScreen.getTextureWidth() - 20,
                            ItemEditScreen.this.processingSubScreen.getTextureHeight() - 20)
                    .getDelegate();
            this.formatButton = ExecutableWidget.instance(141, -19, 18, 18)
                    .setElementHandler(IconElement.fixedGui(FORMAT_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                this.itemAttrValue.applyFormatting((str) -> {
                                    if (this.widget != null) widget.setText(str);
                                });
                            }))
                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                    "widget.gui.item-edit-screen.formatter.tooltips", "")))
                            .withActiveActionCondition((icon) -> {
                                if (icon instanceof IconElement) {
                                    if (this.itemAttrValue.isValidate()) {
                                        this.formatButton.setAlpha(1.0f);
                                        return true;
                                    } else {
                                        this.formatButton.setAlpha(0.4f);
                                        return false;
                                    }
                                } else return true;
                            }))
                    .addToSub(this);

            // init snbt edit box, init formatting button
        }

        @Override
        protected void saveChanges() {
            this.itemAttrValue.validateAndUpdate();
            ItemEditScreen.this.itemStack = this.lastResult.copy();
        }

        @Override
        protected boolean canConfirm() {
            return this.itemAttrValue.isValidate();
        }

        @Override
        protected void refreshScreen() {
            // refresh widget with absolute coord
            this.widget = this.itemAttrValue
                    .generateEditBox(
                            ItemEditScreen.this.processingSubScreen.getX() + 10,
                            ItemEditScreen.this.processingSubScreen.getY() + 10,
                            ItemEditScreen.this.processingSubScreen.getTextureWidth() - 20,
                            ItemEditScreen.this.processingSubScreen.getTextureHeight() - 20)
                    .getDelegate();
            ItemEditScreen.this.optionalMultiLine.setContentDelegate(this.widget);
        }
    }
    // 如果大小正常的画,应该是 400 * 270
    protected class ItemAttributeProcessingSubScreen extends ItemProcessingSubScreen {
        public ItemAttributeProcessingSubScreen() {
            super();
            this.stackTemplate = itemStack.copy();
            ItemEditScreen.this.setTitleLabel(
                    Text.translatable("widget.gui.item-edit-screen.nbt-editor").formatted(Formatting.GREEN));
            init();
        }

        ItemStack stackTemplate;
        ItemAttrSubSubScreen currentSubSubScreen;
        ContentDelegateWidget<ItemAttrSubSubScreen> delegateWidget;
        ItemAttr currentAttr = null;

        protected static final int SELECT_WIDTH = 120;

        protected void setCurrentAttr(ItemAttr attr) {
            if (this.currentSubSubScreen != null) {
                this.currentSubSubScreen.saveChanges();
            }
            if (attr == null) {
                this.currentSubSubScreen = null;
            } else {
                var oldState = this.currentAttr;
                this.currentAttr = attr;
                if (oldState != this.currentAttr) {
                    this.currentSubSubScreen = generateCurrentAttrScreen();
                }
            }
            this.delegateWidget.setContentDelegate(this.currentSubSubScreen);
            if (this.currentSubSubScreen != null) {
                this.currentSubSubScreen.refreshScreen();
            }
        }

        protected ItemAttrSubSubScreen generateCurrentAttrScreen() {
            return switch (this.currentAttr) {
                case BASIC -> new ItemBasicAttributeSubSubScreen();
                case DISPLAY -> new ItemDisplaySubSubScreen();
                case ENCHANTMENT -> new ItemEnchantListSubSubScreen();
                case ATTRIBUTE -> new ItemAttributeModifiersSubSubScreen();
                case COMPONENTS -> new ItemComponentModifySubSubScreen();
                default -> null;
            };
        }

        protected void init() {
            this.delegateWidget = new ContentDelegateWidget<>(SELECT_WIDTH, 0, 0, 0);
            setCurrentAttr(this.currentAttr == null ? ItemAttr.BASIC : this.currentAttr);
            refreshScreen();
        }

        @Override
        protected void saveChanges() {
            if (this.currentSubSubScreen != null) {
                this.currentSubSubScreen.saveChanges();
            }
            itemStack = stackTemplate.copy();
        }

        @Override
        protected boolean canConfirm() {
            return true;
        }

        @Override
        protected void refreshScreen() {
            this.clearChildren();
            for (var attr : ItemAttr.values()) {
                Text selectedText = Text.translatable(attr.display).formatted(Formatting.YELLOW);
                Text unselectedText = Text.translatable(attr.display);
                ExecutableWidget.instance(20, attr.ordinal() * 30, 80, 30)
                        .setElementHandler(new ButtonElement(
                                (el) -> {
                                    return currentAttr == attr ? selectedText : unselectedText;
                                },
                                ButtonAction.run(() -> setCurrentAttr(attr))))
                        .addToSub(this);
            }
            DisplayWidget.instance(30, ItemEditScreen.this.processingSubScreen.getTextureHeight() - 90, 60, 20)
                    .setRenderHandler(LabelElement.instance(
                            Text.translatable("widget.gui.item-edit-screen.nbt-editor.refresh-item")))
                    .addToSub(this);
            ExecutableWidget.instance(30, ItemEditScreen.this.processingSubScreen.getTextureHeight() - 70, 60, 60)
                    .setElementHandler(new SlotElement(
                            InventoryUtils.createReadOnlyOneItemInventory(() -> this.stackTemplate), 0, (it, bt) -> {
                                this.saveChanges();
                                return true;
                            }))
                    .addToSub(this);
            this.delegateWidget.addToSub(this);
            setCurrentAttr(this.currentAttr);
        }
        // 320 * 270
        protected abstract class ItemAttrSubSubScreen extends SubScreenWidget {

            public ItemAttrSubSubScreen() {
                super(0, 0, 0, 0);
            }

            protected abstract void saveChanges();

            protected abstract void refreshScreen();
        }

        protected class ItemBasicAttributeSubSubScreen extends ItemAttrSubSubScreen {
            AttrKeyValue<Item> item;
            AttrKeyValue<Integer> count;
            AttrKeyValue<Boolean> unbreakable;
            AttrKeyValue<Integer> damage;
            AttrKeyValue<String> sfid;
            ProfileComponent lastComponent;
            AttrKeyValue<String> skullHashProfile;
            ItemHideFlags flags;

            {
                init();
            }

            protected static final int BASIC_DKEY = 50;

            protected static class ItemHideFlags {
                // true means hide
                ItemStack sample;

                @SuppressWarnings("all")
                public ItemHideFlags(ItemStack stack) {
                    sample = stack.copy();
                }

                public DrawableWidget factory(int x, int y) {
                    SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, 0, 0);
                    subScreenWidget.addDrawableChild(DisplayWidget.instance(1, 1, 50 - 1, 20 - 1)
                            .setRenderHandler(LabelElement.instance(
                                    Text.translatable("widget.gui.item-edit-screen.nbt-editor.generic.hide-flag"))));
                    var flags = ItemStackUtils.getHideFlags();
                    for (int index = 0; index < flags.length; ++index) {
                        var sec = flags[index];
                        subScreenWidget.addDrawableChild(ExecutableWidget.instance(
                                        50 + 1 + 20 * index, 1, 20 - 2, 20 - 2)
                                .setElementHandler(IconElement.statedGuiPredicate(
                                                ButtonElement.BUTTON,
                                                ButtonElement.BUTTON_INACTIVE,
                                                ButtonAction.run(() -> sec.setHideFlag(sample, !sec.isHide(sample))),
                                                (bl) -> sec.isHide(sample))
                                        .withTooltips(TooltipHandler.of(List.of(Text.literal(sec.displayName()))))));
                    }
                    return subScreenWidget;
                }

                public void applyChange(ItemStack stack) {
                    // clear hideFlags
                    for (var entry : ItemStackUtils.getHideFlags()) {
                        entry.setHideFlag(stack, entry.isHide(sample));
                    }
                }
            }

            protected void init() {
                this.item = AttrKeyValue.registry(
                        "widget.gui.item-edit-screen.nbt-editor.generic.item-id",
                        Registries.ITEM,
                        stackTemplate.getItem());
                this.count = AttrKeyValue.integer(
                        "widget.gui.item-edit-screen.nbt-editor.generic.count", stackTemplate.getCount());
                this.damage = AttrKeyValue.integer(
                        "widget.gui.item-edit-screen.nbt-editor.generic.durability", stackTemplate.getDamage());
                String sfid = ItemStackUtils.getSfId(stackTemplate);
                this.sfid = AttrKeyValue.str(
                        "widget.gui.item-edit-screen.nbt-editor.generic.sf-id", sfid == null ? "" : sfid);
                this.unbreakable = AttrKeyValue.bool(
                        "widget.gui.item-edit-screen.nbt-editor.generic.unbreakable-flag",
                        ItemStackUtils.getIsUnbreakable(stackTemplate));
                this.flags = new ItemHideFlags(stackTemplate);
                ProfileComponent component = ItemStackUtils.getInPatch(stackTemplate, PROFILE);
                this.lastComponent = component;
                String hash = "";

                if (component != null) {
                    hash = BukkitItemStackUtils.getHashFromProfile(component);
                }
                if (hash == null) hash = "";
                this.skullHashProfile =
                        AttrKeyValue.str("widget.gui.item-edit-screen.nbt-editor.generic.skull-hash", hash);

                new KeyValueInputWidget<>(30, 0, 240, 20, 50, this.item)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
                new KeyValueInputWidget<>(30, 30, 240, 20, 50, this.count)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
                new KeyValueInputWidget<>(30, 60, 240, 20, 50, this.damage)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
                new KeyValueInputWidget<>(30, 90, 240, 20, 50, this.sfid)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
                new KeyValueInputWidget<>(30, 120, 240, 20, 50, this.unbreakable)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
                this.flags.factory(30, 150).addToSub((ItemBasicAttributeSubSubScreen) this);
                new KeyValueInputWidget<>(30, 180, 240, 20, 50, this.skullHashProfile)
                        .addToSub((ItemBasicAttributeSubSubScreen) this);
            }

            @Override
            protected void saveChanges() {
                if (stackTemplate.getItem() != this.item.getOriginValue()) {
                    stackTemplate = ItemStackUtils.withTypeChange(stackTemplate, this.item.getOriginValue());
                }
                stackTemplate.setCount(count.getOriginValue());
                ItemStackUtils.setDamage(stackTemplate, damage.getOriginValue());
                ItemStackUtils.setSfId(stackTemplate, sfid.getOriginValue());
                ItemStackUtils.setUnbreakable(stackTemplate, this.unbreakable.getOriginValue());
                this.flags.applyChange(stackTemplate);
                String hash = this.skullHashProfile.getOriginValue();
                if (hash != null && !hash.isEmpty()) {
                    if (lastComponent != null) {
                        PropertyMap map = BukkitItemStackUtils.buildPropertyMap(
                                VRecord.getGameProfileProperties(lastComponent), hash);
                        ItemStackUtils.setOrRemoveChange(
                                stackTemplate, PROFILE, VRecord.withProperty(lastComponent, map));
                    } else {
                        // generate empty
                        ItemStackUtils.setOrRemoveChange(
                                stackTemplate, PROFILE, BukkitItemStackUtils.buildPlayerHeadProfileCSCoreLib(hash));
                    }
                } else {
                    // empty hash remove
                    if (lastComponent != null) {
                        ItemStackUtils.setOrRemoveChange(
                                stackTemplate, PROFILE, VRecord.withProperty(lastComponent, VRecord.createProperty()));
                    } else {
                        ItemStackUtils.setOrRemoveChange(stackTemplate, PROFILE, null);
                    }
                }
            }

            @Override
            protected void refreshScreen() {}
        }

        protected class ItemDisplaySubSubScreen extends ItemAttrSubSubScreen {

            {
                init();
            }

            AttrKeyValue<String> displayName;
            List<AttrKeyValue<String>> lores;

            protected void init() {
                Text text = ItemStackUtils.getCustomName(stackTemplate);
                String textCode = text == null ? "" : ChatUtils.textToString(text);
                displayName = AttrKeyValue.str("widget.gui.item-edit-screen.nbt-editor.display.custom-name", textCode);
                lores = new ArrayList<>();
                List<Text> loreComp = ItemStackUtils.getLore(stackTemplate);
                for (var txt : loreComp) {
                    lores.add(AttrKeyValue.str(
                            "widget.gui.item-edit-screen.nbt-editor.display.lore",
                            txt == null ? "" : ChatUtils.textToString(txt)));
                }
                new KeyValueInputWidget<>(10, 0, 260, 20, 50, this.displayName).addToSub(this);
                new ListModifyWidget(
                                ListEntryWidgetController.mutable(
                                        this.lores,
                                        () -> AttrKeyValue.str(
                                                "widget.gui.item-edit-screen.nbt-editor.display.lore", ""),
                                        (str) -> new KeyValueInputWidget<>(0, 0, 180, 20, 30, str),
                                        20,
                                        180),
                                10,
                                40,
                                260,
                                ItemEditScreen.this.processingSubScreen.getTextureHeight() - 50)
                        .addToSub(this);
            }

            @Override
            protected void saveChanges() {
                String displayName0 = displayName.getOriginValue();
                Text customName =
                        (displayName0 == null || displayName0.isEmpty()) ? null : ChatUtils.stringToText(displayName0);
                ItemStackUtils.setCustomName(stackTemplate, customName);
                List<Text> texts = new ArrayList<>();
                for (var s : this.lores) {
                    String displayName1 = s.getOriginValue();
                    Text customName1 = (displayName1 == null || displayName1.isEmpty())
                            ? null
                            : ChatUtils.stringToText(displayName1);
                    customName1 = customName1 == null ? Text.empty() : customName1;
                    texts.add(customName1);
                }
                ItemStackUtils.setLore(stackTemplate, texts);
            }

            @Override
            protected void refreshScreen() {}
        }

        protected class ItemEnchantListSubSubScreen extends ItemAttrSubSubScreen {

            protected static class ItemEnchantAttrGroup {
                public ItemEnchantAttrGroup(String enchantment, int level) {
                    id = AttrKeyValue.openRegistry(
                            "widget.gui.item-edit-screen.nbt-editor.enchantment.id",
                            ItemStackUtils.registry()
                                    .getOptional(RegistryKeys.ENCHANTMENT)
                                    .orElseThrow(),
                            enchantment);
                    lvl = AttrKeyValue.integer("widget.gui.item-edit.screen.nbt-editor.enchantment.lvl", level);
                }

                AttrKeyValue<Enchantment> id;
                AttrKeyValue<Integer> lvl;

                public DrawableWidget factory() {
                    return new SubScreenWidget(0, 0, 0, 0)
                            .addDrawableChild(new KeyValueInputWidget<>(0, 0, 120, 20, 30, this.id))
                            .addDrawableChild(new KeyValueInputWidget<>(120, 0, 60, 20, 30, this.lvl));
                }

                public Pair<String, Integer> value() {
                    return new Pair<>(this.id.getValue(), this.lvl.getOriginValue());
                }

                public Pair<RegistryEntry<Enchantment>, Integer> entryValue() {
                    try {
                        Enchantment enchantment = this.id.getOriginValue();
                        RegistryEntry<Enchantment> ench = RegistryUtils.getRegistryEntry(
                                ItemStackUtils.registry(), RegistryKeys.ENCHANTMENT, enchantment);
                        return new Pair<>(ench, lvl.getOriginValue());
                    } catch (Throwable e) {
                        return new Pair<>(null, 0);
                    }
                }
            }

            {
                init();
            }

            protected List<ItemEnchantAttrGroup> enchantList;
            protected boolean showInTooltips;

            protected void init() {
                enchantList = new ArrayList<>();
                ItemEnchantmentsComponent component = ItemStackUtils.getItemEnchant(stackTemplate);
                component.getEnchantmentEntries().forEach(var -> {
                    enchantList.add(new ItemEnchantAttrGroup(
                            ItemStackUtils.solveDynamic(var.getKey()).toString(), var.getIntValue()));
                });
                // this.showInTooltips = component.showInTooltip;
                new ListModifyWidget(
                                ListEntryWidgetController.mutable(
                                        enchantList,
                                        () -> new ItemEnchantAttrGroup("minecraft:", 0),
                                        ItemEnchantAttrGroup::factory,
                                        20,
                                        180),
                                10,
                                0,
                                260,
                                ItemEditScreen.this.processingSubScreen.getTextureHeight() - 10)
                        .addToSub(this);
            }

            @Override
            protected void saveChanges() {
                ItemEnchantmentsComponent.Builder builder =
                        new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                // .withShowInTooltip(this.showInTooltips));
                for (var ench : enchantList) {
                    var re = ench.entryValue();
                    if (re.getFirst() != null) {
                        builder.add(re.getFirst(), re.getSecond());
                    }
                }

                ItemStackUtils.applyItemEnchant(stackTemplate, builder.build());
            }

            @Override
            protected void refreshScreen() {}
        }

        protected class ItemAttributeModifiersSubSubScreen extends ItemAttrSubSubScreen {
            protected class ItemAttributeModifierEntry {
                public ItemAttributeModifierEntry(
                        String attribute, EntityAttributeModifier modifier, AttributeModifierSlot slot) {
                    attr = AttrKeyValue.openRegistry(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.name", Registries.ATTRIBUTE, attribute);
                    identifier = AttrKeyValue.identifier(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.uid", modifier.id());
                    modifierValue = AttrKeyValue.doubleVal(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.value", modifier.value());
                    modifierOperation = AttrKeyValue.enumMap(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.op", modifier.operation(), NAME_TO_OPER);
                    optionalSlot = AttrKeyValue.enumMap(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.slot", slot, NAME_TO_OP);
                }

                AttrKeyValue<Identifier> identifier;
                AttrKeyValue<EntityAttribute> attr;
                AttrKeyValue<Double> modifierValue;
                AttrKeyValue<EntityAttributeModifier.Operation> modifierOperation;
                AttrKeyValue<AttributeModifierSlot> optionalSlot;
                protected static final Map<String, AttributeModifierSlot> NAME_TO_OP = new LinkedHashMap<>();
                protected static final Map<String, EntityAttributeModifier.Operation> NAME_TO_OPER =
                        new LinkedHashMap<>();

                static {
                    for (var re : AttributeModifierSlot.values()) {
                        NAME_TO_OP.put(re.asString(), re);
                    }
                    NAME_TO_OPER.put(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.op.add",
                            EntityAttributeModifier.Operation.ADD_VALUE);
                    NAME_TO_OPER.put(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.op.multiply-base",
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    NAME_TO_OPER.put(
                            "widget.gui.item-edit-screen.nbt-editor.attribute.op.multiply-total",
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                }

                public ItemAttributeModifierEntry() {
                    this(
                            "minecraft:",
                            new EntityAttributeModifier(
                                    Identifier.tryParse(
                                            "minecraft:" + UUID.randomUUID().toString()),
                                    0.0d,
                                    EntityAttributeModifier.Operation.ADD_VALUE),
                            AttributeModifierSlot.ANY);
                }

                public AttributeModifiersComponent.Entry value() {
                    try {
                        EntityAttribute attribute = this.attr.getOriginValue();
                        if (attribute != null) {
                            RegistryEntry<EntityAttribute> attribute0 =
                                    Registries.ATTRIBUTE.getEntry(this.attr.getOriginValue());
                            if (attribute0 != null && attribute0.value() != null) {
                                EntityAttributeModifier modifier = new EntityAttributeModifier(
                                        identifier.getOriginValue(),
                                        modifierValue.getOriginValue(),
                                        modifierOperation.getOriginValue());
                                AttributeModifierSlot slot = this.optionalSlot.getOriginValue();
                                return new AttributeModifiersComponent.Entry(attribute0, modifier, slot);
                            } else {
                                Debug.info("Attribute null? ", this.attr.getOriginValue(), this.attr.getValue());
                            }
                        }
                    } catch (Throwable e) {
                    }
                    return null;
                }

                public DrawableWidget factory() {
                    return new SubScreenWidget(0, 0, 0, 0)
                            .addDrawableChild(new KeyValueInputWidget<>(0, 0, 180, 20, 50, this.attr))
                            .addDrawableChild(new KeyValueInputWidget<>(0, 20, 80, 20, 20, this.optionalSlot))
                            .addDrawableChild(new KeyValueInputWidget<>(80, 20, 80, 20, 20, this.modifierOperation))
                            .addDrawableChild(new KeyValueInputWidget<>(160, 20, 100, 20, 20, this.modifierValue))
                            .addDrawableChild(new KeyValueInputWidget<>(0, 40, 260, 20, 80, this.identifier));
                }
            }

            {
                init();
            }

            protected List<ItemAttributeModifierEntry> modifierEntries;
            boolean showInTooltips;

            protected void init() {
                this.modifierEntries = new ArrayList<>();
                AttributeModifiersComponent modifiers = ItemStackUtils.getEntityModifier(stackTemplate);
                // this.showInTooltips = modifiers.showInTooltip();
                modifiers.modifiers().forEach(var -> {
                    this.modifierEntries.add(new ItemAttributeModifierEntry(
                            ItemStackUtils.solveDynamic(var.attribute()).toString(), var.modifier(), var.slot()));
                });
                new ListModifyWidget(
                                ListEntryWidgetController.mutable(
                                        this.modifierEntries,
                                        ItemAttributeModifierEntry::new,
                                        ItemAttributeModifierEntry::factory,
                                        60,
                                        180),
                                10,
                                0,
                                260,
                                ItemEditScreen.this.processingSubScreen.getTextureHeight() - 10)
                        .addToSub(this);
            }

            @Override
            protected void saveChanges() {
                ItemStackUtils.applyEntityModifier(
                        stackTemplate,
                        new AttributeModifiersComponent(
                                this.modifierEntries.stream()
                                        .map(ItemAttributeModifierEntry::value)
                                        .filter(Objects::nonNull)
                                        .toList(),
                                this.showInTooltips));
            }

            @Override
            protected void refreshScreen() {}
        }

        protected static enum ItemAttr {
            BASIC("widget.gui.item-edit-screen.nbt-editor.generic"),
            DISPLAY("widget.gui.item-edit-screen.nbt-editor.display"),
            ENCHANTMENT("widget.gui.item-edit-screen.nbt-editor.enchantment"),
            ATTRIBUTE("widget.gui.item-edit-screen.nbt-editor.attribute"),
            COMPONENTS("widget.gui.item-edit-screen.nbt-editor.component");
            String display;

            ItemAttr(String displayName) {
                this.display = displayName;
            }
        }

        protected class ItemComponentModifySubSubScreen extends ItemAttrSubSubScreen {
            protected static class ItemComponentModifyConfirmScreen<T> extends ConfirmingBigScreen {
                NbtAttrKeyValue<Optional<T>> element;
                boolean removal;
                final ComponentType<T> type;
                final Consumer<NbtElement> callback;

                protected ItemComponentModifyConfirmScreen(
                        ComponentType<T> type, NbtElement currentValue, Consumer<NbtElement> callback) {
                    super(Text.empty());
                    this.type = type;
                    this.removal = currentValue == null;
                    this.element = new NbtAttrKeyValue<Optional<T>>("", currentValue, (nbt) -> {
                                if (nbt == null) return Optional.empty();
                                return Optional.of(this.type
                                        .getCodec()
                                        .decode(RegistryOps.of(NbtOps.INSTANCE, ItemStackUtils.registry()), nbt)
                                        .getOrThrow()
                                        .getFirst());
                            })
                            .setEnableNull(true);
                    this.callback = callback;
                    setTitleLabel(Text.translatable(
                                    "widget.gui.item-edit-screen.nbt-editor.component.component-edit-screen.title")
                            .append(Text.literal(String.valueOf(Registries.DATA_COMPONENT_TYPE.getId(type))))
                            .formatted(Formatting.GREEN));
                }

                EditBoxWidget widget;
                ExecutableWidget formatButton;
                ExecutableWidget wikiWidget;

                protected void init() {
                    super.init();
                    URI uri = null;
                    try {
                        String url = "https://zh.minecraft.wiki/w/%E6%95%B0%E6%8D%AE%E7%BB%84%E4%BB%B6#"
                                + Registries.DATA_COMPONENT_TYPE.getId(type).getPath();
                        uri = Util.validateUri(url);
                    } catch (Throwable e) {
                    }
                    URI urlll = uri;
                    Text wikiLink = Text.translatable(
                            "widget.gui.item-edit-screen.nbt-editor.component.component-edit-screen.open-wiki");
                    this.wikiWidget = ExecutableWidget.instance(this.x + 5, this.y + 22, this.backgroundWidth - 10, 12)
                            .setElementHandler(LabelElement.instance(wikiLink)
                                    .withInputHandler(InputHandler.run(() -> {
                                        if (urlll != null) {
                                            Util.getOperatingSystem().open(urlll);
                                        }
                                    }))
                                    .withTooltips(TooltipHandler.of(
                                            () -> List.of(Text.literal(urlll == null ? "" : ("" + urlll))))))
                            .addTo(this);
                    this.widget = this.element
                            .generateEditBox(
                                    ItemComponentModifyConfirmScreen.this.x + CONTENT_START_X + 10,
                                    ItemComponentModifyConfirmScreen.this.y + CONTENT_START_Y + 30,
                                    ItemComponentModifyConfirmScreen.this.backgroundWidth - 2 * CONTENT_START_X - 20,
                                    ItemComponentModifyConfirmScreen.this.content_end_y - CONTENT_START_Y - 40)
                            .<ContentDelegateWidget<EditBoxWidget>>addTo(this)
                            .getDelegate();

                    this.formatButton = ExecutableWidget.instance(
                                    ItemComponentModifyConfirmScreen.this.x + CONTENT_START_X + 1,
                                    ItemComponentModifyConfirmScreen.this.y + CONTENT_START_Y + 1,
                                    18,
                                    18)
                            .setElementHandler(IconElement.fixedGui(
                                            SnbtItemProcessingSubScreen.FORMAT_TEXTURE_SPRITE, ButtonAction.run(() -> {
                                                this.element.applyFormatting((str) -> {
                                                    if (this.widget != null) widget.setText(str);
                                                });
                                            }))
                                    .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                            "widget.gui.item-edit-screen.formatter.tooltips", "")))
                                    .withActiveActionCondition((icon) -> {
                                        if (icon instanceof IconElement) {
                                            if (this.element.isValidate()) {
                                                this.formatButton.setAlpha(1.0f);
                                                return true;
                                            } else {
                                                this.formatButton.setAlpha(0.4f);
                                                return false;
                                            }
                                        } else return true;
                                    }))
                            .addTo(this);
                }

                @Override
                protected boolean canConfirm(ElementHandler elementHandler) {
                    return this.element.isValidate();
                }

                @Override
                protected void onConfirmButton() {
                    callback.accept(this.element.getOriginValue());
                    this.close();
                }
            }

            {
                init();
            }

            protected static class ItemComponent {
                AttrKeyValue<ComponentType<?>> typeId;
                NbtElement optionalComponentData;

                public <T> ItemComponent(ComponentType<T> type, Optional<T> data) {
                    this.typeId = AttrKeyValue.registry(
                            "widget.gui.item-edit-screen.nbt-editor.component.type",
                            Registries.DATA_COMPONENT_TYPE,
                            type);
                    optionalComponentData = data.isPresent()
                            ? type.getCodec()
                                    .encodeStart(RegistryOps.of(NbtOps.INSTANCE, ItemStackUtils.registry()), data.get())
                                    .getOrThrow()
                            : null;
                }

                public ItemComponent(String newId) {
                    this.typeId = AttrKeyValue.openRegistry(
                            "widget.gui.item-edit-screen.nbt-editor.component.type",
                            Registries.DATA_COMPONENT_TYPE,
                            newId);
                    optionalComponentData = null;
                }

                protected void openThisEditScreen() {
                    ComponentType type = typeId.getOriginValue();
                    if (type != null) {
                        ScreenAccess.of(new ItemComponentModifyConfirmScreen<>(
                                        type,
                                        optionalComponentData,
                                        (nbt) -> this.optionalComponentData = nbt == null ? null : nbt.copy()))
                                .openFromCurrent();
                    }
                }

                public DrawableWidget factory() {
                    return new SubScreenWidget(0, 0, 0, 0)
                            .addDrawableChild(new KeyValueInputWidget<>(0, 0, 120, 20, 30, this.typeId))
                            .addDrawableChild(DisplayWidget.instance(120, 0, 30, 20)
                                    .setRenderHandler(new LabelElement(
                                            (el) -> this.optionalComponentData != null
                                                    ? Text.translatable(
                                                                    "widget.gui.item-edit-screen.nbt-editor.component.component-not-empty")
                                                            .formatted(Formatting.GREEN)
                                                    : Text.translatable(
                                                                    "widget.gui.item-edit-screen.nbt-editor.component.component-empty")
                                                            .formatted(Formatting.YELLOW),
                                            Colors.WHITE,
                                            0)))
                            .addDrawableChild(ExecutableWidget.instance(150, 0, 30, 20)
                                    .setElementHandler(new ButtonElement(
                                                    TextProvider.of(
                                                            Text.translatable(
                                                                    "widget.gui.item-edit-screen.nbt-editor.component.open-component-edit")),
                                                    ButtonAction.run(this::openThisEditScreen))
                                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                    "widget.gui.item-edit-screen.nbt-editor.component.open-component-edit.tooltips",
                                                    "")))
                                            .withActiveActionCondition((e) -> this.typeId.isValidate())));
                }

                public void applyChanges(Map<ComponentType<?>, Optional<?>> map0) {
                    try {
                        ComponentType type = this.typeId.getOriginValue();
                        if (type != null) {
                            if (optionalComponentData == null) {
                                map0.put(type, Optional.empty());
                            } else {
                                DataResult<Pair<Object, NbtElement>> value = type.getCodec()
                                        .decode(
                                                RegistryOps.of(NbtOps.INSTANCE, ItemStackUtils.registry()),
                                                optionalComponentData);
                                Object val0 = value.getOrThrow().getFirst();
                                map0.put(type, Optional.ofNullable(val0));
                            }
                        }
                    } catch (Throwable e) {
                    }
                }
            }

            List<ItemComponent> componentList;

            protected void init() {
                this.componentList = stackTemplate.components.getChanges().entrySet().stream()
                        .map(m -> new ItemComponent((ComponentType) m.getKey(), m.getValue()))
                        .collect(Collectors.toCollection(ArrayList::new));
                new ListModifyWidget(
                                ListEntryWidgetController.mutable(
                                        this.componentList,
                                        () -> new ItemComponent("minecraft:"),
                                        ItemComponent::factory,
                                        20,
                                        180),
                                10,
                                0,
                                260,
                                ItemEditScreen.this.processingSubScreen.getTextureHeight() - 10)
                        .addToSub(this);
            }

            @Override
            protected void saveChanges() {
                Reference2ObjectMap<ComponentType<?>, Optional<?>> map0 = new Reference2ObjectArrayMap<>();
                this.componentList.forEach(i -> i.applyChanges(map0));
                stackTemplate.components.setChanges(new ComponentChanges(map0));
            }

            @Override
            protected void refreshScreen() {}
        }
    }

    protected static enum State {
        SNBT,
        NBT_TREE,
        EDITOR;
    }
}
