package me.matl114.hacks.modules.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class NewStyleModel extends BaseModule {
    public static final String[] ENABLE_ENCHANTBOOK_NEWSTYLE = {"new-style-item", "enable-enchant-book"};
    public static final String[] ENABLE_NEWSTYLE_ITEM = {"new-style-item", "enable-new-style-item"};
    public static final String[] ENABLE_NEW_STYLE_NBT = {"new-style-item", "enable-new-style-nbt"};

    public NewStyleModel() {}

    public final FlagRef enableEnchant = builder(Configs.MODEL_CONFIG, ENABLE_ENCHANTBOOK_NEWSTYLE, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableNewVersion = builder(Configs.MODEL_CONFIG, ENABLE_NEWSTYLE_ITEM, Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef enableNewVersionNbt = builder(Configs.MODEL_CONFIG, ENABLE_NEW_STYLE_NBT, Boolean.class)
            .defaultValue(true)
            .build();

    public static String PATH_OF_NEW_VERSION = "new-version";
    public static String NAMESPACE = "slimefunhelper";

    public static final String MODEL_PATH = "enchanted_book/";
    public static final String MAX_VALUE = "_max";
    public static final String OVER_MAX_VALUE = "_over";

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getCustomModelOverride(), this::onModelOverride);
        registerListener(RenderListener.getResourceReload(), this::onRefreshCache);
    }

    private Map<Identifier, Optional<ItemModel>> cache = new HashMap<>();

    private Map<Item, Optional<ItemModel>> cacheItem = new HashMap<>();

    public void onModelOverride(Event<ItemModel> event) {
        if (event.context != null) return;
        ItemStack item = event.getArgs(0);
        if (enableEnchant.get()) {
            ItemEnchantmentsComponent list = ItemStackUtils.getStoredEnchantment(item);
            if (list != null && !list.isEmpty()) {
                var optional = list.getEnchantmentEntries().stream().findFirst();
                if (optional.isPresent()) {
                    var entry = optional.get();
                    Enchantment enchantment = entry.getKey().value();
                    Optional<RegistryKey<Enchantment>> identifier =
                            entry.getKey().getKey();
                    if (enchantment != null && identifier.isPresent()) {
                        Identifier identifier2 = identifier.get().getValue();
                        int maxValue = enchantment.getMaxLevel();
                        int level = entry.getIntValue();
                        if (level == 0) return;

                        Identifier id = (level == 1
                                ? new Identifier(NAMESPACE, MODEL_PATH + identifier2.getPath())
                                : (level == maxValue
                                        ? new Identifier(NAMESPACE, MODEL_PATH + identifier2.getPath() + MAX_VALUE)
                                        : (level > maxValue
                                                ? new Identifier(
                                                        NAMESPACE, MODEL_PATH + identifier2.getPath() + OVER_MAX_VALUE)
                                                : new Identifier(
                                                        NAMESPACE, MODEL_PATH + identifier2.getPath() + "_" + level))));
                        Optional<ItemModel> modelId = cache.computeIfAbsent(id, RenderListener::getModModel);
                        modelId.ifPresent(event::context);
                    }
                }
            }
        }
        if (shouldEnableNewStyle(item)) {
            var model = cacheItem.get(item.getItem());
            if (model != null && model.isPresent()) {
                event.context(model.get());
            }
        }
    }

    public void onRefreshCache(Event<ResourceManager> event) {
        cache.clear();
        cacheItem.clear();
        for (Item item : Registries.ITEM) {
            Identifier id = new Identifier(
                    NAMESPACE,
                    PATH_OF_NEW_VERSION + "/" + Registries.ITEM.getId(item).getPath());
            Optional<ItemModel> modelId = RenderListener.getModModel(id);
            // todo: what?
            if (modelId.isPresent()) {
                cacheItem.put(item, modelId);
                Debug.info("Loading new-version model", id);
            }
        }
    }

    public boolean shouldEnableNewStyle(ItemStack item) {
        return (enableNewVersion.get() && cacheItem.containsKey(item.getItem()))
                || (enableNewVersionNbt.get() && isNewVersion(item));
    }

    public static ItemStack ofNewVersion(ItemStack stack) {
        ItemStackUtils.updateCustomData(stack, nbtCompound -> nbtCompound.putBoolean(PATH_OF_NEW_VERSION, true));
        return stack;
    }

    public static boolean isNewVersion(ItemStack stack) {
        return ItemStackUtils.getCustomDataReadOnly(stack).contains(PATH_OF_NEW_VERSION);
    }

    public final Map<Item, ItemModel> NEW_VERSION_ITEMS = new HashMap<>();
}
