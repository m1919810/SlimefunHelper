package me.matl114.hacks.modules.inv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.complex.invcache.InventoryViewScreen;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.single.ConfirmingWidgetScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.versioned.api.VItem;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public class KitManager extends BaseModule {
    // todo: ktiManager
    public static KitManager INSTANCE;

    public KitManager() {
        super("KitManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public final ModulePath root = makePath(Configs.INV_CONFIG, "kit-manager");
    public FileStorage fileStorage;
    public Map<String, Kit> kitMap = new HashMap<>();

    {
        fileStorage = FileManager.getInstance().getInternalStorage("kit.nbt");
        kitMap = fileStorage.read(KIT_MAP, LinkedHashMap::new);
    }

    public void updateKitMap() {
        fileStorage.write(KIT_MAP, kitMap);
    }

    public void setKitList(List<Kit> kits) {
        kitMap.clear();
        for (Kit kit : kits) {
            kitMap.put(kit.name, kit);
        }
        updateKitMap();
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        SubScreenWidget kitEditEntry = new SubScreenWidget(0, dblank, dx, dy);
        kitEditEntry.addDrawableChild(createRefKeyLabel(
                () -> Text.translatable("widget.kit-manager.kit-save-map"),
                () -> ChatUtils.parseTooltipsTranslation("widget.kit-manager.kit-save-map.tooltips", "暂无介绍"),
                indexWidth,
                dy));
        kitEditEntry.addDrawableChild(createExecuteButton(
                "widget.kit-manager.open-kit-list",
                ButtonAction.run(this::openKitEditScreen),
                indexWidth + blankWidth,
                0,
                dx - indexWidth - blankWidth,
                dy));
        acceptor.accept(kitEditEntry);
    }

    public void openKitEditScreen() {
        List<MutableRecord> records = kitMap.values().stream()
                .map(s -> MutableRecord.of(Kit.KEYS, s))
                .collect(Collectors.toCollection(ArrayList::new));
        ListEntryWidgetController mutableList = ListEntryWidgetController.mutable(
                records, () -> MutableRecord.of(Kit.KEYS, Kit.EMPTY), (v) -> createEditWidget(records, v), 30, 250);
        ListModifyWidget listWidget = new ListModifyWidget(mutableList, 0, 0, 330, 260);
        ConfirmingWidgetScreen confirmScreen = new ConfirmingWidgetScreen(
                Text.translatable("widget.kit-manager.open-kit-list.title"), listWidget, () -> true, () -> {
                    List<Kit> newKits =
                            records.stream().map(s -> s.toRecord(Kit.class)).toList();
                    setKitList(newKits);
                });
        confirmScreen.access().openFromCurrent();
    }

    private SubScreenWidget createEditWidget(List<MutableRecord> mutableList, MutableRecord record) {
        SubScreenWidget widget = new SubScreenWidget(0, 0, 250, 20);
        final String nameKey = Kit.KEYS.get(0);
        AttrKeyValue<String> name =
                AttrKeyValue.str("widget.kit-manager.open-kit-list.name", record.getOrPut(nameKey, ""));
        name.addValidator(s -> {
            for (var re : mutableList) {
                if (re != record && Objects.equals(re.get(nameKey), s)) {
                    return false;
                }
            }
            return true;
        });
        name.addListener(s -> record.set(nameKey, s));
        widget.addDrawableChild(name.generateKeyValueInput(0, 0, 30, 5, 65, 20));
        String maxSizeKey = Kit.KEYS.get(2);
        AttrKeyValue<Integer> maxSize =
                AttrKeyValue.integer("widget.kit-manager.open-kit-list.max-size", record.getOrPut(maxSizeKey, 0));
        maxSize.addValidator(Configs.INT_NONNEGATIVE);
        maxSize.addListener(s -> record.set(maxSizeKey, s));

        widget.addDrawableChild(maxSize.generateKeyValueInput(100, 0, 30, 5, 35, 20));
        Runnable reload = () -> {
            name.valueChangeInternal(null, record.getOrPut(nameKey, ""));
            maxSize.valueChangeInternal(null, record.getOrPut(maxSizeKey, 0));
        };
        Function<Kit, Runnable> openViewScreen = (temporaryKit) -> () -> {
            Inventory mutableInventory = createInventory(temporaryKit);
            InventoryViewScreen screen = new InventoryViewScreen(
                    mutableInventory, Text.literal(temporaryKit.name()), new ItemStack(Items.SHULKER_BOX), true);
            screen.access().addCloseFuture(() -> {
                Kit saveKit = saveInventory(temporaryKit.name(), mutableInventory, mutableInventory.size());
                MutableRecord newRecord = MutableRecord.of(Kit.KEYS, saveKit);
                record.replaceMap(newRecord);
                reload.run();
            });
            screen.access().openFromCurrent();
        };
        if (mc.getNetworkHandler() != null) {
            widget.addDrawableChild(ExecutableWidget.instance(170, 0, 40, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items")),
                            ButtonAction.run(() -> {
                                Kit temporaryKit = record.toRecord(Kit.class);
                                openViewScreen.apply(temporaryKit).run();
                            }))));
            widget.addDrawableChild(ExecutableWidget.instance(210, 0, 40, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items.import")),
                            ButtonAction.run(() -> {
                                if (mc.player != null) {
                                    Kit saveKit = saveInventory(
                                            name.getOriginValue(),
                                            mc.player.getInventory(),
                                            InventoryUtils.getPlayerInvSize());
                                    openViewScreen.apply(saveKit).run();
                                }
                            }))));
        } else {
            widget.addDrawableChild(ExecutableWidget.instance(170, 0, 80, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.translatable("widget.kit-manager.open-kit-list.items.error")),
                            ButtonAction.empty())));
        }
        return widget;
    }

    public Inventory createInventory(Kit kit) {
        List<IndexEntry<ItemStack>> list = kit.toItem();
        int maxSize = kit.maxSize();
        ItemStack[] stackArray = new ItemStack[maxSize];
        Arrays.fill(stackArray, ItemStack.EMPTY);
        for (var re : list) {
            if (re.index() >= 0 && re.index() < maxSize) {
                stackArray[re.index()] = re.val();
            }
        }

        return InventoryUtils.createInventory(stackArray);
    }

    public Kit saveInventory(String name, Inventory inventory, int maxSize) {
        List<IndexEntry<ItemStack>> stack = InventoryUtils.getInventoryEntries(inventory);
        return Kit.fromItem(name, stack, maxSize);
    }

    public static Codec<Map<String, Kit>> KIT_MAP = Codec.list(Kit.CODEC)
            .xmap(
                    s -> {
                        Map<String, Kit> map = new LinkedHashMap<>();
                        for (var re : s) {
                            map.put(re.name(), re);
                        }
                        return map;
                    },
                    v -> v.values().stream().toList())
            .fieldOf("kit-map")
            .codec();

    public static record Kit(String name, List<IndexEntry<NbtCompound>> itemNBT, int maxSize) {
        public static final Kit EMPTY = new Kit("", List.of(), 0);

        public static List<String> KEYS = List.of("name", "item-nbt", "max-size");
        public static Codec<Kit> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.STRING.fieldOf("name").forGetter(Kit::name),
                        Codec.list(InventoryUtils.NBT_STACK_WITH_SLOT_CODEC)
                                .fieldOf("items")
                                .forGetter(Kit::itemNBT),
                        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("maxSize").forGetter(Kit::maxSize))
                .apply(oinstance, Kit::new));

        public static Kit fromItem(String name, List<IndexEntry<ItemStack>> itemNBT, int maxSize) {
            return new Kit(
                    name,
                    itemNBT.stream()
                            .filter(s -> !s.val().isEmpty())
                            .map(s -> {
                                return new IndexEntry<>(
                                        s.index(), VItem.getInstance().toNbt(s.val()));
                            })
                            .toList(),
                    maxSize);
        }

        public List<IndexEntry<ItemStack>> toItem() {
            return itemNBT.stream()
                    .map(s -> {
                        return new IndexEntry<>(s.index(), VItem.getInstance().fromNbt(s.val()));
                    })
                    .toList();
        }
    }
}
