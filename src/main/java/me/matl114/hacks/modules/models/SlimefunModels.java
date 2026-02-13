package me.matl114.hacks.modules.models;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.matl114.ModConfig;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.ListRef;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

public class SlimefunModels extends BaseModule {
    public static final String[] SLIMEFUN_MODEL_ID = {"model-config", "enable-slimefun-cmd-override"};
    public static final String[] ITEM_MODEL_OVERRIDE = {"model-config", "enable-item-model-override"};

    public static final String[] AUTO_MODEL_PATTERN = {"slimefun-models", "path-pattern-for-slimefun-model"};

    public SlimefunModels() {}

    public final FlagRef enableCmd = builder(Configs.MODEL_CONFIG, SLIMEFUN_MODEL_ID, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableModel = builder(Configs.MODEL_CONFIG, ITEM_MODEL_OVERRIDE, Boolean.class)
            .defaultValue(true)
            .build();

    public final ListRef autoModelPattern = builder(Configs.MODEL_CONFIG, AUTO_MODEL_PATTERN, ListRef.TYPE)
            .defaultValue(List.of("^slimefunhelper:slimefunitem/.*$", "^slimefunhelper:test/.*$"))
            .listValidator(Configs.REGEX_VALIDATOR)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getResourceReload(), this::onResourceReload);
        registerListener(RenderListener.getAsyncItemModelSupply(), this::onModelSupply);
        registerListener(RenderListener.getCustomModelOverride(), this::onModelOverride);
        registerListener(RenderListener.getItemDataOverrideForModel(), this::onItemOverride);
    }

    private final Map<Identifier, Optional<ItemModel>> modelCache = new HashMap<>();

    public void onResourceReload(Event<ResourceManager> resourceManager) {
        customModelDatas.clear();
        modelCache.clear();
        loadCustomModelDatas();
    }

    public void onModelSupply(Event<Set<Identifier>> event) {
        if (enableModel.get()) {
            event.context().addAll(walkThroughResourcePacks(event.getArgs(0), false));
        }
    }

    public void onModelOverride(Event<Identifier> event) {
        if (event.context != null) return;

        if (enableModel.get()) {
            ItemStack stack = event.getArgs(0);
            NbtCompound nbt = ItemStackUtils.getCustomDataReadOnly(stack);
            try {
                String id = ItemStackUtils.getSfId(nbt);
                if (id != null) {
                    Identifier identifier = customItemModels.get(id);
                    if (identifier != null) {
                        Optional<ItemModel> modelOptional =
                                modelCache.computeIfAbsent(identifier, RenderListener::getOptionalModelOf);
                        if (modelOptional.isPresent()) {
                            event.context(identifier);
                            return;
                        }
                    }
                }
            } catch (Throwable e) {
            }
        }
    }

    public void onItemOverride(Event<ItemStack> event) {
        if (enableCmd.get()) {
            ItemStack stack = event.context();
            if (!stack.isEmpty()) {
                String id = ItemStackUtils.getSfId(stack);
                if (id != null && customModelDatas.containsKey(id)) {
                    CustomModelDataComponent val = customModelDatas.get(id);

                    ItemStack stackCopy = stack.copy();
                    ItemStackUtils.setOrRemoveChange(stackCopy, DataComponentTypes.CUSTOM_MODEL_DATA, val);
                    event.context(stackCopy);
                }
            }
        }
    }

    private final Map<String, CustomModelDataComponent> customModelDatas = new HashMap<>();
    private final Map<String, Identifier> customItemModels = new HashMap<>();
    private static final String OUR_NAMESPACE = "slimefunhelper";

    public void loadCustomModelDatas() {
        try {
            final File configFile = ModConfig.loadOrUseInternal("slimefun-item-model.yml");
            Yaml yaml = new Yaml();
            try (FileReader inputStream = new FileReader(configFile)) {
                // 将 YAML 文件内容加载到 Map 中
                Map<String, Object> data = yaml.load(inputStream);
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    try {
                        int cmd = (Integer) entry.getValue();
                        if (cmd != 0) {
                            customModelDatas.put(
                                    entry.getKey(), VItem.getInstance().createModelData(cmd));
                        }
                    } catch (ClassCastException e) {
                        Debug.info("Custom Model data could not be loaded :", entry.getKey());
                    }
                }
                // 获取具体数据
            } catch (Exception e) {
                Debug.info("AN INTERNAL ERROR WHILE READING CONFIG ITEM-MODEL");
                Debug.info(e);
            }
            Debug.info("Slimefun Custom Model Data load successfully");

        } catch (Throwable e) {
            Debug.info("error while loading CustomModelDatas");
            Debug.info(e);
        }
    }

    public Collection<Identifier> walkThroughResourcePacks(ResourceManager resourceManager, boolean allLoad) {
        Collection<Identifier> id = new LinkedHashSet<>();
        List<ResourcePack> packs = resourceManager.streamResourcePacks().toList();
        List<String> modelPathPattern = autoModelPattern.get();
        String pattern = modelPathPattern.stream().map(i -> "(" + i + ")").collect(Collectors.joining("|"));
        var predicate = Pattern.compile(pattern).asMatchPredicate();
        for (ResourcePack pack : packs) {

            String name = pack.getId();
            if (name.equals("minecraft")
                    || name.equals("realms")
                    || name.startsWith("fabric-")
                    || name.equals("fabric")
                    || name.equals("vanilla")) {
                continue;
            }
            if (name.equals(OUR_NAMESPACE)) {
                pack.findResources(ResourceType.CLIENT_RESOURCES, "slimefunhelper", "models/slimefunitem", (i, j) -> {
                    String realNamespace = i.getNamespace();
                    if (!i.getPath().endsWith(".json")) return;
                    String realPath = i.getPath().replaceFirst("^models/", "").replaceAll(".json$", "");
                    Identifier fullPathId = new Identifier(realNamespace, realPath);
                    Debug.info("load custom slimefun item model:", fullPathId);
                    String[] splits = realPath.split("/");
                    customItemModels.put(splits[splits.length - 1].toUpperCase(Locale.ROOT), fullPathId);
                    id.add(fullPathId);
                });
            } else {
                Set<String> namespacess = pack.getNamespaces(ResourceType.CLIENT_RESOURCES);

                for (String namespace : namespacess) {
                    // Debug.info("in namespace ",namespace);
                    pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "models", (i, j) -> {
                        /// Debug.info("finding resource ",i,j);
                        String realNamespace = i.getNamespace();
                        if (!i.getPath().endsWith(".json")) return;
                        String realPath =
                                i.getPath().replaceFirst("^models/", "").replaceAll(".json$", "");
                        String[] splits = realPath.split("/");
                        String trueId = splits[splits.length - 1];
                        Identifier shouldId = new Identifier(realNamespace, trueId);
                        // Debug.info(shouldId);
                        Identifier fullPathId = new Identifier(realNamespace, realPath);
                        Identifier shouldModelId = "item".equals(splits[0])
                                ? new Identifier(
                                        realNamespace, String.join("/", Arrays.copyOfRange(splits, 1, splits.length)))
                                : fullPathId;
                        boolean testResult = predicate.test(shouldModelId.toString());
                        if (OUR_NAMESPACE.equals(namespace) || testResult) {
                            // custom item
                            Debug.info("load custom slimefun item model:", shouldModelId);
                            if (testResult) {
                                customItemModels.put(splits[splits.length - 1].toUpperCase(Locale.ROOT), fullPathId);
                            }
                            id.add(fullPathId);
                        }
                    });
                }
            }
        }

        return id;
    }
}
