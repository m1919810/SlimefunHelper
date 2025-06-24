package me.matl114.bukkitUtiils;

import com.google.common.base.Preconditions;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class BukkitItemStack implements Cloneable, ConfigurationSerializable {
    private Item type;
    private int amount;
    private BukkitMetaItem meta;

    protected BukkitItemStack() {
        this.type = Items.AIR;
        this.amount = 0;
    }

    public BukkitItemStack(@NotNull Item type) {
        this(type, 1);
    }

    public BukkitItemStack(@NotNull Item type, int amount) {
        this(type, amount, (short)0);
    }

    public BukkitItemStack(@NotNull Item type, int amount, short damage) {
        this(type, amount, damage, (Byte)null);
    }

    public BukkitItemStack(@NotNull Item type, int amount, short damage, @Nullable Byte data) {
        this.type = Items.AIR;
        this.amount = 0;
        Preconditions.checkArgument(type != null, "Material cannot be null");
        this.type = type;
        this.amount = amount;
    }

    public BukkitItemStack(@NotNull BukkitItemStack stack) throws IllegalArgumentException {
        this.type = Items.AIR;
        this.amount = 0;
        Preconditions.checkArgument(stack != null, "Cannot copy null stack");
        this.type = stack.getType();
        this.amount = stack.getAmount();

        if (stack.hasItemMeta()) {
            this.setItemMeta0(stack.getItemMeta(), this.type);
        }

    }

    @NotNull
    public Item getType() {
        return this.type;
    }

    public void setType(@NotNull Item type) {
        Preconditions.checkArgument(type != null, "Material cannot be null");
        this.type = type;
        if (this.meta != null) {
            this.meta = BukkitSerializationMock.getItemFactory().asMetaFor(this.meta, type);
        }



    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }





    public String toString() {
        StringBuilder toString = (new StringBuilder("ItemStack{")).append(Registries.ITEM.getId(this.getType()).getPath().toUpperCase(Locale.ROOT)).append(" x ").append(this.getAmount());
        if (this.hasItemMeta()) {
            toString.append(", ").append(this.getItemMeta());
        }

        return toString.append('}').toString();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof BukkitItemStack)) {
            return false;
        } else {
            BukkitItemStack stack = (BukkitItemStack)obj;
            return this.getAmount() == stack.getAmount() && this.isSimilar(stack);
        }
    }

    public boolean isSimilar(@Nullable BukkitItemStack stack) {
        if (stack == null) {
            return false;
        } else if (stack == this) {
            return true;
        } else {
            Item comparisonType =  this.type;
            return comparisonType == stack.getType() &&  this.hasItemMeta() == stack.hasItemMeta() && (!this.hasItemMeta() || BukkitSerializationMock.getItemFactory().equals(this.getItemMeta(), stack.getItemMeta()));
        }
    }

    @NotNull
    public BukkitItemStack clone() {
        try {
            BukkitItemStack BukkitItemStack = (BukkitItemStack) super.clone();
            if (this.meta != null) {
                BukkitItemStack.meta = this.meta.clone();
            }
            return BukkitItemStack;
        } catch (CloneNotSupportedException var2) {
            CloneNotSupportedException e = var2;
            throw new Error(e);
        }
    }












//    public void removeEnchantments() {


    @NotNull
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap();
        result.put("v", 0);
        result.put("type", Registries.ITEM.getId( this.getType()).getPath().toUpperCase(Locale.ROOT));
        if (this.getAmount() != 1) {
            result.put("amount", this.getAmount());
        }

        BukkitMetaItem meta = this.getItemMeta();
        if (meta!=null) {
            result.put("meta", meta);
        }

        return result;
    }


    @NotNull
    public static BukkitItemStack deserialize(@NotNull Map<String, Object> args) {
        short damage = 0;
        int amount = 1;
        if (args.containsKey("damage")) {
            damage = ((Number)args.get("damage")).shortValue();
        }
        Item type ;
        try{
            type = Registries.ITEM.get(new Identifier("minecraft",((String) args.get("type")).toLowerCase(Locale.ROOT)));

        }catch (Throwable e){
            type = Items.BARRIER;
        }
        if (args.containsKey("amount")) {
            amount = ((Number)args.get("amount")).intValue();
        }

        BukkitItemStack result = new BukkitItemStack(type, amount, damage);
        Object raw;
        if (args.containsKey("meta")) {
            raw = args.get("meta");
            if (raw instanceof BukkitMetaItem) {
                result.setItemMeta((BukkitMetaItem) raw);
            }
        }


        return result;
    }

    

    @Nullable
    public BukkitMetaItem getItemMeta() {
        return this.meta == null ? BukkitSerializationMock.getItemFactory().getItemMeta(this.type) : this.meta.clone();
    }

    public boolean hasItemMeta() {
        return !BukkitSerializationMock.getItemFactory().equals(this.meta, null);
    }

    public boolean setItemMeta(@Nullable BukkitMetaItem itemMeta) {
        return this.setItemMeta0(itemMeta, this.type);
    }

    private boolean setItemMeta0(@Nullable BukkitMetaItem itemMeta, @NotNull Item material) {
        this.meta=itemMeta;
        return true;
    }

}
