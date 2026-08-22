package me.matl114.gui.presets.single;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleSpriteManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleType;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;

public class RegistryDisplays {

    public static ItemStack createEnchantmentIcon(Enchantment enchantment) {
        ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        RegistryEntry<Enchantment> entry =
                RegistryUtils.getRegistryEntry(ItemStackUtils.registry(), RegistryKeys.ENCHANTMENT, enchantment);

        if (entry == null) {
            return itemStack;
        }
        var builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.add(entry, 1);
        ItemEnchantmentsComponent component = builder.build();
        itemStack.set(DataComponentTypes.STORED_ENCHANTMENTS, component);
        return itemStack;
    }

    public static <T> Text getDisplay(Registry<T> registry, @Nonnull T val) {
        Text re = guessTranslation(val);
        if (re != null) return re;
        Identifier id = registry.getId(val);
        if (id != null) {
            return Text.translatable(registry.getKey().getValue().getPath() + ".minecraft." + id.getPath());
        }
        return Text.literal(val.toString());
    }

    public static <T> Text getDisplay(@Nonnull T val) {
        Text re = guessTranslation(val);
        if (re != null) return re;
        RegistryKey<? extends Registry<T>> registry = RegistryUtils.getRegistryTypeKey(val);
        if (registry != null) {
            Registry<T> re2 = Registries.REGISTRIES.get((RegistryKey) registry);
            if (re2 != null) {
                Identifier id = re2.getId(val);
                if (id != null) {
                    return Text.translatable(registry.getValue().getPath() + ".minecraft." + id.getPath());
                }
            }
        }
        return Text.literal(val.toString());
    }

    private static <T> Text guessTranslation(T val) {
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
        } else if (val instanceof SoundEvent soundEvent) {
            return Text.translatable("subtitles." + soundEvent.id().getPath());
        } else if (val instanceof Potion potionType) {
            return Text.translatable(Items.POTION.getTranslationKey() + ".effect." + potionType.getBaseName());
        }
        return null;
    }

    public static <T> RenderHandler of(Registry<T> registry, T value, Text name, Identifier identifier) {
        // Class<?> clazz = value.getClass();
        IIcon<T> icon = getIcon(registry); // (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(clazz, IIcon.EMPTY);
        return new IEntry<>(name, identifier, icon, value);
    }

    public static final ItemStack ANVIL_ITEM = new ItemStack(Items.ANVIL);
    public static final ItemStack AIR_ITEM = new ItemStack(Items.AIR);
    private static final Random RAND = new LocalRandom(999);
    public static Map<Class<?>, IIcon<?>> TYPE_TO_ICON_MAP = ImmutableMap.<Class<?>, IIcon<?>>builder()
            .put(Item.class, IIcon.<ItemConvertible>renderItem(ItemStack::new))
            .put(Block.class, IIcon.<ItemConvertible>renderItem(ItemStack::new))
            .put(EntityAttribute.class, IIcon.renderItem((v) -> ANVIL_ITEM))
            .put(Enchantment.class, IIcon.renderItem(RegistryDisplays::createEnchantmentIcon))
            .put(BlockEntityType.class, IIcon.<BlockEntityType<?>>renderItem(s -> {
                if (s.blocks.isEmpty()) return AIR_ITEM;
                List<Block> blockList = s.blocks.stream().toList();
                int select = ((int) (System.currentTimeMillis() / 1000) % blockList.size());
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
            .put(Potion.class, IIcon.<Potion>renderItem((v) -> {
                return PotionContentsComponent.createStack(Items.POTION, Registries.POTION.getEntry(v));
            }))
            .put(ParticleType.class, IIcon.<ParticleType<?>>renderSprite((v) -> {
                Identifier id = Registries.PARTICLE_TYPE.getId(v);
                ParticleSpriteManager manager = MinecraftClient.getInstance().particleSpriteManager;
                var re = manager.spriteAwareParticleFactories;
                var what = re.get(id);
                if (what != null) {
                    return what.getSprite(RAND);
                } else {
                    return null;
                }
            }))
            .build();

    public static <T> IIcon<T> getIcon(Class<T> registryClass) {
        return (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(registryClass, IIcon.EMPTY);
    }

    public static <T> IIcon<T> getIcon(Registry<T> registryClass) {
        return (IIcon<T>) TYPE_TO_ICON_MAP.getOrDefault(RegistryUtils.getRegistryType(registryClass), IIcon.EMPTY);
    }

    public static interface IIcon<T> {
        public static ItemStack DEFAULT_NULL_ICON = new ItemStack(Items.BARRIER);
        public static IIcon<?> EMPTY = ((x, y, context, registerValue) -> {
            context.drawItem(DEFAULT_NULL_ICON, x, y, 999, 0);
        });

        default void render(int startIndexX, int startIndexY, VDrawContext context, T registerValue) {
            if (registerValue == null) {
                context.drawItem(DEFAULT_NULL_ICON, startIndexX, startIndexY, 114514, 0);
            } else {
                renderNonnull(startIndexX, startIndexY, context, registerValue);
            }
        }

        public void renderNonnull(int width, int height, VDrawContext context, T registerValue);

        public static <T> IIcon<T> renderItem(Function<T, ItemStack> function) {
            return ((startIndexX, startIndexY, context, registerValue) -> {
                context.drawItem(function.apply(registerValue), startIndexX, startIndexY, 114514, 0);
            });
        }

        public static <T> IIcon<T> renderSprite(Function<T, ?> function) {
            return ((startIndexX, startIndexY, context, registerValue) -> {
                var re = function.apply(registerValue);
                if (re instanceof Identifier identifier) {
                    context.drawGuiTexture(identifier, startIndexX, startIndexY, 16, 16);
                } else if (re instanceof Sprite sprite) {
                    context.drawSprite(sprite, startIndexX, startIndexY, 0, 16, 16);
                }
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
            int startIndexX = (element.getTextureHeight() - 16) / 2;
            int startIndexY = startIndexX;
            icon.render(startIndexX, startIndexY, context, value);
            RenderHandler.drawScaledText0(context, mc.textRenderer, name, 20, 1, 200, 10, -16711936, -1);
            RenderHandler.drawScaledText0(
                    context, mc.textRenderer, Text.literal(identifier.toString()), 20, 10, 200, 19, -16711936, -1);
        }
    }

    public static Sprite getEffectTexture(RegistryEntry<StatusEffect> effect) {
        return MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(effect);
    }
}
