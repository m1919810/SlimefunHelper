package me.matl114.gui.presets.single;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.managers.Tasks;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RegistryDisplays {

    public static ItemStack createEnchantmentIcon(Enchantment enchantment) {
        ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        var builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.add(
                ItemStackUtils.registry()
                        .getOptional(RegistryKeys.ENCHANTMENT)
                        .orElseThrow()
                        .getEntry(enchantment),
                1);
        ItemEnchantmentsComponent component = builder.build();
        itemStack.set(DataComponentTypes.STORED_ENCHANTMENTS, component);
        return itemStack;
    }

    public static <T> Text getDisplay(T val) {
        if (val instanceof StatusEffect effect) {
            return effect.getName();
        } else if (val instanceof EntityAttribute attribute) {
            return Text.translatable(attribute.getTranslationKey());
        } else if (val instanceof Enchantment enchantment) {
            return enchantment.description();
        } else if (val instanceof BlockEntityType<?> blockEntityType) {
            return Text.literal(
                    Registries.BLOCK_ENTITY_TYPE.getId(blockEntityType).getPath());
        } else if (val instanceof EntityType<?> entityType) {
            return Text.translatable(entityType.getTranslationKey());
        } else if (val instanceof Item itemConvertible) {
            return itemConvertible.getName();
        } else if (val instanceof Block itemStack) {
            return itemStack.getName();
        }
        return Text.literal(val.toString());
    }

    public static <T> RenderHandler of(Registry<T> registry, T value, Text name, Identifier identifier) {
        // Class<?> clazz = value.getClass();
        IIcon<T> icon = getIcon(registry); // (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(clazz, IIcon.EMPTY);
        return new IEntry<>(name, identifier, icon, value);
    }

    public static final ItemStack ANVIL_ITEM = new ItemStack(Items.ANVIL);
    public static final ItemStack AIR_ITEM = new ItemStack(Items.AIR);
    public static Map<Class<?>, IIcon<?>> TYPE_TO_ICON_MAP = ImmutableMap.<Class<?>, IIcon<?>>builder()
            .put(Item.class, IIcon.<ItemConvertible>renderItem(ItemStack::new))
            .put(Block.class, IIcon.<ItemConvertible>renderItem(ItemStack::new))
            .put(EntityAttribute.class, IIcon.renderItem((v) -> ANVIL_ITEM))
            .put(Enchantment.class, IIcon.renderItem(RegistryDisplays::createEnchantmentIcon))
            .put(BlockEntityType.class, IIcon.<BlockEntityType<?>>renderItem(s -> {
                if (s.blocks.isEmpty()) return AIR_ITEM;
                List<Block> blockList = s.blocks.stream().toList();
                int select = (Tasks.getSecond() % blockList.size());
                return new ItemStack(blockList.get(select));
            }))
            .put(EntityType.class, IIcon.<EntityType<?>>renderItem((v) -> {
                Item item = EntityUtils.entityToSpawnEgg(v);
                return new ItemStack(item == null ? Items.PIG_SPAWN_EGG : item);
            }))
            .put(StatusEffect.class, IIcon.<StatusEffect>renderSprite(effect -> {
                RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(effect);
                return getEffectTexture(entry);
            }))
            .build();

    public static <T> IIcon<T> getIcon(Class<T> registryClass) {
        return (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(registryClass, IIcon.EMPTY);
    }

    public static <T> IIcon<T> getIcon(Registry<T> registryClass) {
        return (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(RegistryUtils.getRegistryType(registryClass), IIcon.EMPTY);
    }

    public static interface IIcon<T> {
        public static IIcon<?> EMPTY = ((element, context, registerValue) -> {});
        public static ItemStack DEFAULT_NULL_ICON = new ItemStack(Items.BARRIER);

        default void render(DrawableWidget element, VDrawContext context, T registerValue) {
            // for default
            if (registerValue == null) {
                int startIndex = (element.getTextureHeight() - 16) / 2;
                context.drawItem(DEFAULT_NULL_ICON, startIndex, startIndex, 114514, 0);
            } else {
                renderNonnull(element, context, registerValue);
            }
        }

        public void renderNonnull(DrawableWidget element, VDrawContext context, T registerValue);

        public static <T> IIcon<T> renderItem(Function<T, ItemStack> function) {
            return ((element, context, registerValue) -> {
                int startIndex = (element.getTextureHeight() - 16) / 2;
                context.drawItem(function.apply(registerValue), startIndex, startIndex, 114514, 0);
            });
        }

        public static <T> IIcon<T> renderSprite(Function<T, Identifier> function) {
            return ((element, context, registerValue) -> {
                int startIndex = (element.getTextureHeight() - 16) / 2;
                context.drawGuiTexture(function.apply(registerValue), startIndex, startIndex, 16, 16);
            });
        }
    }

    @AllArgsConstructor
    public static class IEntry<T> implements RenderHandler {
        Text name;
        Identifier identifier;
        IIcon<T> icon;
        T value;
        // this render should be 20 high
        @Override
        public void renderAtCentered(
                DrawableWidget element,
                VDrawContext context,
                int mouseX,
                int mouseY,
                float delta,
                float alpha,
                boolean shouldHighlight) {
            icon.render(element, context, value);
            RenderHandler.drawScaledText0(context, mc.textRenderer, name, 20, 1, 200, 10, -16711936, -1);
            RenderHandler.drawScaledText0(
                    context, mc.textRenderer, Text.literal(identifier.toString()), 20, 10, 200, 19, -16711936, -1);
        }
    }
    public static Identifier getEffectTexture(RegistryEntry<StatusEffect> effect) {
        return (Identifier)effect.getKey().map(RegistryKey::getValue).map((id) -> {
            return id.withPrefixedPath("mob_effect/");
        }).orElseGet(MissingSprite::getMissingSpriteId);
    }
}
