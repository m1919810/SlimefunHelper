package me.matl114.hacks.modules.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.events.model.GuiModel;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.ResourceUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public class EnchantmentDisplay extends BaseModule {
    public static final String NAMESPACE = "slimefunhelper";
    public static final String MODEL_PATH = "enchantment_icon/";
    public final ModulePath modelConfig = makePath(Configs.RENDER_CONFIG, "itemstack-display.enchantment-display");
    public static final Map<RegistryKey<Enchantment>, Identifier> ENCHANTMENT_ICON_MODELS = Map.ofEntries(
            Map.entry(Enchantments.BANE_OF_ARTHROPODS, enchantmentIcon("icon_bane_of_arthropods")),
            Map.entry(Enchantments.BLAST_PROTECTION, enchantmentIcon("icon_blast_protection")),
            Map.entry(Enchantments.BREACH, enchantmentIcon("icon_breach")),
            Map.entry(Enchantments.DENSITY, enchantmentIcon("icon_density")),
            Map.entry(Enchantments.FIRE_PROTECTION, enchantmentIcon("icon_fire_protection")),
            Map.entry(Enchantments.FORTUNE, enchantmentIcon("icon_fortune")),
            Map.entry(Enchantments.POWER, enchantmentIcon("icon_power")),
            Map.entry(Enchantments.PROTECTION, enchantmentIcon("icon_protection")),
            Map.entry(Enchantments.SHARPNESS, enchantmentIcon("icon_sharpness")),
            Map.entry(Enchantments.SILK_TOUCH, enchantmentIcon("icon_silk_touch")),
            Map.entry(Enchantments.SMITE, enchantmentIcon("icon_smite")));

    public EnchantmentDisplay() {
        super("EnchantDisplay");
        bindFlag(enable);
    }

    public final FlagRef enable =
            builder(modelConfig.addEnable(), Boolean.class).defaultValue(true).build();

    public final FlagRef enableMace =
            builder(modelConfig.add("mace"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableWeapon =
            builder(modelConfig.add("weapon"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableEquippable = builder(modelConfig.add("equippable"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableTool =
            builder(modelConfig.add("tool"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableBow =
            builder(modelConfig.add("bow"), Boolean.class).defaultValue(false).build();

    private static Identifier enchantmentIcon(String path) {
        return new Identifier(NAMESPACE, MODEL_PATH + path);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getAsyncItemModelSupply(), this::onItemModelSupply);
        registerListener(RenderListener.getAtlasSourceSupply(), this::onTextureSupply);
        registerListener(RenderListener.getDetachedItemStackInformation(), this::onInfoAttached);
    }

    public void onItemModelSupply(Event<Set<Identifier>> event) {
        event.context().addAll(ResourceUtils.lookupOurModelResources(event.getArgs(0), "enchantment_icon"));
    }

    public void onTextureSupply(Event<Set<Identifier>> event) {
        if (event.getArgs(1).equals(new Identifier("minecraft", "blocks"))) {
            event.context().addAll(ResourceUtils.lookupOurTextureResources(event.getArgs(0), "enchantment_icon"));
        }
    }

    public final Map<Item, List<RegistryKey<Enchantment>>> checkList = new HashMap<>();
    public final Map<Item, FlagRef> checkFlags = new HashMap<>();

    {
        for (var item : Registries.ITEM) {
            if (item instanceof MaceItem mace) {
                checkList.put(item, List.of(Enchantments.DENSITY, Enchantments.BREACH));
                checkFlags.put(item, enableMace);
            } else if (VItem.getInstance().isWeapon(new ItemStack(item))) {
                checkList.put(
                        item, List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS));
                checkFlags.put(item, enableWeapon);
            } else if (item instanceof Equipment) {
                checkList.put(
                        item,
                        List.of(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION));
                checkFlags.put(item, enableEquippable);
            } else if (item.getComponents().contains(DataComponentTypes.TOOL)) {
                checkList.put(item, List.of(Enchantments.FORTUNE, Enchantments.SILK_TOUCH));
                checkFlags.put(item, enableTool);
            } else if (item instanceof BowItem bow) {
                checkList.put(item, List.of(Enchantments.POWER));
                checkFlags.put(item, enableBow);
            }
        }
    }

    public void onInfoAttached(Event<List<GuiModel>> event) {
        if (enable.get()) {
            ItemStack stack = event.getArgs(0);
            if (stack.isEmpty()) {
                return;
            }
            if (checkList.containsKey(stack.getItem())) {
                FlagRef checkFlag = checkFlags.get(stack.getItem());
                if (checkFlag == null || !checkFlag.get()) {
                    return;
                }
                var lst = checkList.get(stack.getItem());
                ItemEnchantmentsComponent enchantmentsComponent = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (enchantmentsComponent != null && !enchantmentsComponent.isEmpty()) {
                    for (var ls : lst) {
                        if (ItemStackUtils.getEnchantmentLevel(enchantmentsComponent, ls) > 0) {
                            event.context().add(GuiModel.of(ENCHANTMENT_ICON_MODELS.get(ls)));
                            return;
                        }
                    }
                }
            }
        }
    }
}
