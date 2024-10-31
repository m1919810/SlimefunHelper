package me.matl114.BukkitUtiils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.Hash;
import me.matl114.Utils.ItemStackUtils;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.tags.CustomItemTagContainer;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class BukkitMetaItem implements ItemMeta ,Cloneable {
    protected Material item;
    protected HashMap<String,Object> attributes=new HashMap<>();
    public BukkitMetaItem(Material material) {
        item=material;
        attributes=new HashMap<>();
    }
    public boolean hasDisplayName(){
        return attributes.containsKey("display-name");
    }

    @NotNull
     public String getDisplayName(){
        return attributes.get("display-name").toString();
    }

    public void setDisplayName(@Nullable String var1){
        return;
    }

    public boolean hasLocalizedName(){
        return false;
    }

    @NotNull
    public String getLocalizedName(){
        return null;
    }

    public void setLocalizedName(@Nullable String var1){

    }

    public boolean hasLore(){
        return attributes.containsKey("lore");
    }

    @Nullable
    public List<String> getLore(){
        return (List<String>) attributes.get("lore");
    }

    public void setLore(@Nullable List<String> var1){

    }

    public boolean hasCustomModelData(){
        return this.attributes.containsKey("custom-model-data");
    }

    public int getCustomModelData(){
        return (Integer) this.attributes.getOrDefault("custom-model-data",0);
    }

    public void setCustomModelData(@Nullable Integer var1){

    }

    public boolean hasEnchants(){
        return !getEnchants().isEmpty();
    }

    public boolean hasEnchant(@NotNull Enchantment var1){
        if(hasEnchants()){
            return getEnchants().containsKey(var1);
        }else return false;
    }

    public int getEnchantLevel(@NotNull Enchantment var1){
        return hasEnchant(var1)? getEnchants().get(var1):0;
    }

    @NotNull
    public Map<Enchantment, Integer> getEnchants(){
        return (Map<Enchantment, Integer>) attributes.getOrDefault("enchants",new HashMap<>());
    }

    public boolean addEnchant(@NotNull Enchantment var1, int var2, boolean var3){
        getEnchants().put(var1,var2);
        return false;
    }

    public boolean removeEnchant(@NotNull Enchantment var1){
        return getEnchants().remove(var1)!=null;
    }

    public void removeEnchantments(){
        getEnchants().clear();
    }

    public boolean hasConflictingEnchant(@NotNull Enchantment var1){
        return false;
    }

    public void addItemFlags(@NotNull ItemFlag... var1){

    }

    public void removeItemFlags(@NotNull ItemFlag... var1){

    }

    @NotNull
    public Set<ItemFlag> getItemFlags(){
        return null;
    }

    public boolean hasItemFlag(@NotNull ItemFlag var1){
        return false;
    }

    public boolean isUnbreakable(){
        return false;
    }

    public void setUnbreakable(boolean var1){

    }

    public boolean hasAttributeModifiers(){
        return false;
    }

    @Nullable
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(){
        return null;
    }

    @NotNull
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(@NotNull EquipmentSlot var1){
        return null;
    }

    @Nullable
    public Collection<AttributeModifier> getAttributeModifiers(@NotNull Attribute var1){
        return null;
    }

    public boolean addAttributeModifier(@NotNull Attribute var1, @NotNull AttributeModifier var2){
        return false;
    }

    public void setAttributeModifiers(@Nullable Multimap<Attribute, AttributeModifier> var1){

    }

    public boolean removeAttributeModifier(@NotNull Attribute var1){
        return false;
    }

    public boolean removeAttributeModifier(@NotNull EquipmentSlot var1){
        return false;
    }

    public boolean removeAttributeModifier(@NotNull Attribute var1, @NotNull AttributeModifier var2){
        return false;
    }

    @NotNull
    public String getAsString(){
        return null;
    }

    /** @deprecated */
    @Deprecated
    @NotNull
    public CustomItemTagContainer getCustomTagContainer(){
        return null;
    }

    @ApiStatus.Internal
    public void setVersion(int var1){

    }

    @NotNull
    public ItemMeta clone(){
        BukkitMetaItem var1=null;
        try{
            var1=(BukkitMetaItem) super.clone();
        }catch (Throwable e){
            e.printStackTrace();
        }
        var1.item=item;
        var1.attributes= (HashMap)attributes.clone();
        return var1;
    }
    @Override
    public Map<String, Object> serialize() {
        throw new AssertionError();
    }
    public static ItemMeta deserialize(@NotNull Map<String, Object> map) throws Throwable{
        Preconditions.checkArgument(map != null, "Cannot deserialize null map");
        BukkitMetaItem bmi= new BukkitMetaItem(Material.AIR);
        try {
            bmi.attributes.putAll(map);
            return bmi;
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
    public String toString(){
        return new StringBuilder("Bukkit Meta:{").append(attributes.toString()).append("}").toString();
    }
    public PersistentDataContainer getPersistentDataContainer(){
        if(this.attributes.containsKey("PublicBukkitValues")){
            Map nbtMap = (Map) this.attributes.get("PublicBukkitValues");
            if (nbtMap != null) {
                BukkitPersistentDataContainer container= new BukkitPersistentDataContainer();
                container.putData((NbtCompound)BukkitConfigDeserializor.deserializeObject(nbtMap));
                return container;
            }
        }
        return null;
    }
}
