package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import me.matl114.gui.Constants;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.SlotElement;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.InvTasks;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.versioned.api.VItem;
import me.matl114.versioned.api.VNbt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ItemStackTemplate implements NBTParsable<ItemStackTemplate> {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Codec<NbtCompound> NBT_COMPOUND_CODEC = VNbt.CODEC.comapFlatMap(
            element -> element instanceof NbtCompound compound
                    ? DataResult.success(compound)
                    : DataResult.error(() -> "ItemStackTemplate source must be an NBT compound"),
            compound -> compound);

    public static final NBTType<ItemStackTemplate> TYPE = new NBTType<>(
            "itemstacktemplate",
            NBT_COMPOUND_CODEC.xmap(ItemStackTemplate::new, ItemStackTemplate::nbt),
            ItemStackTemplate::createWidget,
            new ItemStackTemplate(new NbtCompound()));

    private NbtCompound nbt;

    @Nullable
    private DynamicRegistryManager cachedRegistry;

    @Nullable
    private ItemStack cachedItemStack;

    public ItemStackTemplate(NbtCompound nbt) {
        this.nbt = nbt.copy();
    }

    public ItemStackTemplate(Item item) {
        this((NbtCompound) ItemStack.CODEC
                .encodeStart(NbtOps.INSTANCE, new ItemStack(item))
                .getOrThrow());
    }

    private ItemStackTemplate(ItemStack itemStack) {
        this(VItem.getInstance().toNbt(itemStack, ItemStackUtils.registry()));
    }

    public NbtCompound nbt() {
        return nbt.copy();
    }

    public Identifier itemId() {
        String item = nbt.getString("id");
        Identifier id = item != null ? Identifier.tryParse(item) : null;
        return id != null && Registries.ITEM.containsId(id) ? id : Registries.ITEM.getId(Items.AIR);
    }

    public void setType(Identifier id) {
        NbtCompound updated = new NbtCompound();
        updated.putString("id", id.toString());
        updated.putInt("count", 1);
        nbt = updated;
        cachedRegistry = null;
        cachedItemStack = null;
    }

    @Nullable
    private static DynamicRegistryManager getDynamicRegistryManager() {
        return MC.getNetworkHandler() == null ? null : MC.getNetworkHandler().getRegistryManager();
    }

    private ItemStack previewStack() {
        return getDynamicRegistryManager() == null ? new ItemStack(Registries.ITEM.get(itemId())) : itemStack();
    }

    public ItemStack itemStack() {
        DynamicRegistryManager registry = ItemStackUtils.registry();
        if (cachedItemStack == null || cachedRegistry != registry) {
            try {
                ItemStack decoded = VItem.getInstance().fromNbt(nbt, registry);
                cachedItemStack = decoded == null || decoded.isEmpty() ? new ItemStack(Items.AIR) : decoded;
            } catch (Throwable ignored) {
                cachedItemStack = new ItemStack(Items.AIR);
            }
            cachedRegistry = registry;
        }
        return cachedItemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        DynamicRegistryManager registry = ItemStackUtils.registry();
        nbt = VItem.getInstance().toNbt(itemStack, registry);
        cachedRegistry = registry;
        cachedItemStack = itemStack.copy();
    }

    @Override
    public NBTType<ItemStackTemplate> type() {
        return TYPE;
    }

    private static DrawableWidget createWidget(
            ElementFactory factory, AttrKeyValue<ItemStackTemplate> value, int x, int y, int dx, int dy) {
        boolean hasPlayer = MC.player != null;
        int iconSize = Math.min(dy, dx / (hasPlayer ? 4 : 2));
        int inputWidth = Math.max(0, dx - (hasPlayer ? 4 : 2) * iconSize);
        BooleanSupplier canEdit = () -> getDynamicRegistryManager() != null;
        var input = McWidgetHelpers.createTextFieldEditBox(
                iconSize,
                0,
                inputWidth,
                dy,
                raw -> {
                    Identifier id = Identifier.tryParse(raw);
                    ItemStackTemplate template = value.get();
                    if (template != null
                            && id != null
                            && Registries.ITEM.containsId(id)
                            && !id.equals(template.itemId())) {
                        template.setType(id);
                        value.accept(template);
                    }
                },
                value.get() == null ? "minecraft:air" : value.get().itemId().toString());
        ButtonAction openEditor = ButtonAction.run(() -> {
            if (!canEdit.getAsBoolean()) {
                return;
            }
            ItemStackTemplate template = value.get();
            if (template == null) {
                return;
            }
            InvTasks.openEditScreen(template.itemStack(), edited -> {
                template.setItemStack(edited);
                value.accept(template);
                input.getDelegate().setText(template.itemId().toString());
            });
        });

        SubScreenWidget widget = new SubScreenWidget(x, y, dx, dy);
        if (iconSize > 0) {
            widget.addDrawableChild(factory.createElement(
                    new SlotElement(() -> {
                                ItemStackTemplate template = value.get();
                                return template == null ? ItemStack.EMPTY : template.previewStack();
                            })
                            .setSlotFrame(false),
                    null,
                    null,
                    0,
                    (dy - iconSize) / 2,
                    iconSize,
                    iconSize));
        }
        if (inputWidth > 0) {
            widget.addDrawableChild(input);
        }
        if (iconSize > 0) {
            widget.addDrawableChild(factory.createElement(
                    IconElement.fixedGui(Constants.EDITOR_SPRITE, openEditor)
                            .withActiveActionCondition(icon -> canEdit.getAsBoolean()),
                    null,
                    null,
                    iconSize + inputWidth,
                    (dy - iconSize) / 2,
                    iconSize,
                    iconSize));
        }
        if (hasPlayer && iconSize > 0) {
            widget.addDrawableChild(factory.createExecuteButton(
                    () -> Text.translatableWithFallback("widget.nbt-parsable.item-stack-template.import", "导入"),
                    List::of,
                    ButtonAction.run(() -> {
                        if (MC.player == null) {
                            return;
                        }
                        ItemStackTemplate imported = new ItemStackTemplate(
                                MC.player.getMainHandStack().copy());
                        value.accept(imported);
                        input.getDelegate().setText(imported.itemId().toString());
                    }),
                    2 * iconSize + inputWidth,
                    (dy - iconSize) / 2,
                    2 * iconSize,
                    iconSize));
        }
        return widget;
    }
}
