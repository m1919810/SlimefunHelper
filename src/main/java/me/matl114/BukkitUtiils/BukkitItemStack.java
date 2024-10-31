package me.matl114.BukkitUtiils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Translatable;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class BukkitItemStack implements Cloneable, ConfigurationSerializable {
    private Material type;
    private int amount;
    private ItemMeta meta;

    protected BukkitItemStack() {
        this.type = Material.AIR;
        this.amount = 0;
    }

    public BukkitItemStack(@NotNull Material type) {
        this(type, 1);
    }

    public BukkitItemStack(@NotNull Material type, int amount) {
        this(type, amount, (short)0);
    }

    public BukkitItemStack(@NotNull Material type, int amount, short damage) {
        this(type, amount, damage, (Byte)null);
    }

    public BukkitItemStack(@NotNull Material type, int amount, short damage, @Nullable Byte data) {
        this.type = Material.AIR;
        this.amount = 0;
        Preconditions.checkArgument(type != null, "Material cannot be null");
        this.type = type;
        this.amount = amount;
        if (damage != 0) {
            this.setDurability(damage);
        }


    }

    public BukkitItemStack(@NotNull BukkitItemStack stack) throws IllegalArgumentException {
        this.type = Material.AIR;
        this.amount = 0;
        Preconditions.checkArgument(stack != null, "Cannot copy null stack");
        this.type = stack.getType();
        this.amount = stack.getAmount();

        if (stack.hasItemMeta()) {
            this.setItemMeta0(stack.getItemMeta(), this.type);
        }

    }

    @NotNull
    public Material getType() {
        return this.type;
    }

    public void setType(@NotNull Material type) {
        Preconditions.checkArgument(type != null, "Material cannot be null");
        this.type = type;
        if (this.meta != null) {
            this.meta = BukkitMock.getItemFactory().asMetaFor(this.meta, type);
        }



    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }





    /** @deprecated */
    @Deprecated
    public void setDurability(short durability) {
        ItemMeta meta = this.getItemMeta();
        if (meta != null) {
            ((Damageable)meta).setDamage(durability);
            this.setItemMeta(meta);
        }

    }

    /** @deprecated */
    @Deprecated
    public short getDurability() {
        ItemMeta meta = this.getItemMeta();
        return meta == null ? 0 : (short)((Damageable)meta).getDamage();
    }

    public int getMaxStackSize() {
        Material material = this.getType();
        return material != null ? material.getMaxStackSize() : -1;
    }



    public String toString() {
        StringBuilder toString = (new StringBuilder("ItemStack{")).append(this.getType().name()).append(" x ").append(this.getAmount());
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
            Material comparisonType =  this.type;
            return comparisonType == stack.getType() && this.getDurability() == stack.getDurability() && this.hasItemMeta() == stack.hasItemMeta() && (!this.hasItemMeta() || BukkitMock.getItemFactory().equals(this.getItemMeta(), stack.getItemMeta()));
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

    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + this.getType().hashCode();
        hash = hash * 31 + this.getAmount();
        hash = hash * 31 + (this.getDurability() & '\uffff');
        hash = hash * 31 + (this.hasItemMeta() ? (this.meta == null ? this.getItemMeta().hashCode() : this.meta.hashCode()) : 0);
        return hash;
    }

    public boolean containsEnchantment(@NotNull Enchantment ench) {
        return this.meta == null ? false : this.meta.hasEnchant(ench);
    }

    public int getEnchantmentLevel(@NotNull Enchantment ench) {
        return this.meta == null ? 0 : this.meta.getEnchantLevel(ench);
    }

    @NotNull
    public Map<Enchantment, Integer> getEnchantments() {
        return (Map)(this.meta == null ? ImmutableMap.of() : this.meta.getEnchants());
    }

    public void addEnchantments(@NotNull Map<Enchantment, Integer> enchantments) {
        Preconditions.checkArgument(enchantments != null, "Enchantments cannot be null");
        Iterator var3 = enchantments.entrySet().iterator();

        while(var3.hasNext()) {
            Map.Entry<Enchantment, Integer> entry = (Map.Entry)var3.next();
            this.addEnchantment((Enchantment)entry.getKey(), (Integer)entry.getValue());
        }

    }

    public void addEnchantment(@NotNull Enchantment ench, int level) {

    }





    public int removeEnchantment(@NotNull Enchantment ench) {
        int level = this.getEnchantmentLevel(ench);
        if (level != 0 && this.meta != null) {
            this.meta.removeEnchant(ench);
            return level;
        } else {
            return level;
        }
    }

//    public void removeEnchantments() {
//        if (this.meta != null) {
//            this.meta.removeEnchantments();
//        }
//    }

    @NotNull
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap();
        result.put("v", 0);
        result.put("type", this.getType().name());
        if (this.getAmount() != 1) {
            result.put("amount", this.getAmount());
        }

        ItemMeta meta = this.getItemMeta();
        if (meta!=null) {
            result.put("meta", meta);
        }

        return result;
    }
    public void addUnsafeEnchantment(@NotNull Enchantment ench, int level) {
        ItemMeta itemMeta = this.meta == null ? (this.meta = BukkitMock.getItemFactory().getItemMeta(this.type)) : this.meta;
        if (itemMeta != null) {
            itemMeta.addEnchant(ench, level, true);
        }

    }
    @NotNull
    public static BukkitItemStack deserialize(@NotNull Map<String, Object> args) {
        short damage = 0;
        int amount = 1;
        if (args.containsKey("damage")) {
            damage = ((Number)args.get("damage")).shortValue();
        }

        Material type=null;
        type = Material.getMaterial("LEGACY_" + (String)args.get("type"));
        if(type == null){
            type = Material.getMaterial((String)args.get("type"));
        }
        if(type == null){
            type=Material.BARRIER;
        }
        byte dataVal = type != null && type.getMaxDurability() == 0 ? (byte)damage : 0;
        if (dataVal != 0) {
            damage = 0;
        }

        if (args.containsKey("amount")) {
            amount = ((Number)args.get("amount")).intValue();
        }

        BukkitItemStack result = new BukkitItemStack(type, amount, damage);
        Object raw;
        if (args.containsKey("enchantments")) {
            raw = args.get("enchantments");
            if (raw instanceof Map) {
                Map<?, ?> map = (Map)raw;
                Iterator var9 = map.entrySet().iterator();

                while(var9.hasNext()) {
                    Map.Entry<?, ?> entry = (Map.Entry)var9.next();
                    Enchantment enchantment = Enchantment.getByName(entry.getKey().toString());
                    if (enchantment != null && entry.getValue() instanceof Integer) {
                        result.addUnsafeEnchantment(enchantment, (Integer)entry.getValue());
                    }
                }
            }
        } else if (args.containsKey("meta")) {
            raw = args.get("meta");
            if (raw instanceof ItemMeta) {
                result.setItemMeta((ItemMeta)raw);
            }
        }

        if ( args.containsKey("damage")) {
            result.setDurability(damage);
        }

        return result;
    }

    

    @Nullable
    public ItemMeta getItemMeta() {
        return this.meta == null ? BukkitMock.getItemFactory().getItemMeta(this.type) : this.meta.clone();
    }

    public boolean hasItemMeta() {
        return !BukkitMock.getItemFactory().equals(this.meta, (ItemMeta)null);
    }

    public boolean setItemMeta(@Nullable ItemMeta itemMeta) {
        return this.setItemMeta0(itemMeta, this.type);
    }

    private boolean setItemMeta0(@Nullable ItemMeta itemMeta, @NotNull Material material) {
        this.meta=itemMeta;
        return true;
    }

}
