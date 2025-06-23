package me.matl114.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import me.matl114.bukkitUtiils.ItemStackHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Triplet;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Stream;

public class ItemStackUtils {
    public static NbtCompound getDisplay(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("display")){
                return stack.getNbt().getCompound("display");
            }
        }
        return null;
    }
    public static NbtCompound getOrCreateDisplay(ItemStack stack){
        return stack.getOrCreateSubNbt("display");
    }
    public static void setDisplay(ItemStack stack,NbtCompound display){
        stack.getOrCreateNbt().put("display", display);
    }
    public static Text jsonRawToText(String jsonRaw){
        try{
            if(jsonRaw == null)return null;
            return Text.Serialization.fromJson(jsonRaw);
        }catch (Throwable e){
            return null;
        }
    }
    public static String textToJsonRaw(Text text){
        if(text == null)return null;
        try{
            return Text.Serialization.toJsonString(text);
        }catch (Throwable e){
            return null;
        }
    }
    public static String getCustomNameRaw(ItemStack stack){
        var nbt = getDisplay(stack);
        return nbt == null?"":nbt.getString("Name");
    }
    @Nullable
    public static Text getCustomName(ItemStack stack){
        var nbt = getDisplay(stack);
        return nbt == null?Text.empty():jsonRawToText(nbt.getString("Name"));
    }


    public static void setCustomName(ItemStack stack,  Text text){
        String jsonRaw = textToJsonRaw(text);
        NbtCompound nbtCompound;
        if(jsonRaw == null){
            nbtCompound = getDisplay(stack);
            if(nbtCompound != null){
                nbtCompound.remove("Name");
            }
        }else {
            nbtCompound = getOrCreateDisplay(stack);
            nbtCompound.putString("Name", jsonRaw);
        }
    }
//    @Nonnull
//    public static List<Text> getLore(ItemStack stack){
//        var nbt = getDisplay(stack);
//        List<Text> lore = new ArrayList<>();
//        if(nbt == null)return lore;
//        NbtList list = nbt.getList("Lore", NbtElement.STRING_TYPE);
//        for (var re: list){
//            String loreI = re.asString();
//            lore.add(jsonRawToText(loreI));
//        }
//        return lore;
//    }

    public static NbtCompound getEnchantment(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("Enchantments")){
                return stack.getNbt().getCompound("Enchantments");
            }
        }
        return null;
    }

    public static void applyItemEnchant(ItemStack stack,  Stream<Pair<String, Integer>> ench){
        NbtList list = new NbtList();
        ench.sorted(Comparator.comparing(Pair::getFirst))
            .forEach(var->list.add(EnchantmentHelper.createNbt(Identifier.tryParse(var.getFirst()), var.getSecond())));
        if(list.isEmpty()){
            if(stack.hasNbt()){
                stack.getNbt().remove("Enchantments");
            }
        }else {
            stack.getOrCreateNbt().put("Enchantments", list);
        }
    }

    public static Stream<Pair<String, Integer>> getItemEnchant(ItemStack stack){
        if(stack.hasNbt()){
            return stack.getEnchantments().stream().map(NbtCompound.class::cast).map(nbtComp->new Pair<>(nbtComp.getString("id"), nbtComp.getInt("lvl")));
        }
        return Stream.empty();
    }
    private static final Map<String, EquipmentSlot> NAME_TO_SLOT =new HashMap<>();
    static {
        for (var re: EquipmentSlot.values()){
            NAME_TO_SLOT.put(re.getName(), re);
        }
    }
    public static Stream<Triplet< String, EntityAttributeModifier, EquipmentSlot>> getEntityModifier(ItemStack stack){

        if (stack.hasNbt() && stack.getNbt().contains("AttributeModifiers", 9)) {
            NbtList nbtList = stack.getNbt().getList("AttributeModifiers", 10);
            return nbtList.stream().map(NbtCompound.class::cast)
                .map(nbtCompound->{
                    String optionalSlot = nbtCompound.getString("Slot");
                    EquipmentSlot optional = null;
                    if(optionalSlot != null && NAME_TO_SLOT.containsKey(optionalSlot)){
                        optional = NAME_TO_SLOT.get(optionalSlot);
                    }
                    String id = nbtCompound.getString("AttributeName");
                    EntityAttributeModifier entityAttributeModifier = EntityAttributeModifier.fromNbt(nbtCompound);
                    return new Triplet<>(id, entityAttributeModifier, optional);
                })
                .filter(trp->{
                    return trp.getB() != null;
                });
        }
        return Stream.empty();
    }
    public static void applyEntityModifier(ItemStack stack,  Stream<Triplet< String, EntityAttributeModifier, EquipmentSlot>> data){
        NbtList list = new NbtList();
        data.forEach(var->{
            NbtCompound compound = var.getB().toNbt();
            compound.putString("AttributeName", var.getA());
            if(var.getC() != null){
                compound.putString("Slot", var.getC().getName());
            }
            list.add(compound);
        });
        if(list.isEmpty()){
            if(stack.hasNbt()){
                stack.getNbt().remove("AttributeModifiers");
            }
        }else {
            stack.getOrCreateNbt().put("AttributeModifiers", list);
        }

    }


    public static boolean getIsUnbreakable(ItemStack stack){
        if(stack.hasNbt() && stack.getNbt().getBoolean("Unbreakable")){
            return true;
        }
        return false;
    }
    public static void setUnbreakable(ItemStack stack, boolean ub){
        if(ub){
            stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        }else {
            if(stack.hasNbt()){
                stack.getNbt().remove("Unbreakable");
            }
        }
    }

    private static final Style LORE_STYLE = Style.EMPTY.withColor(Formatting.DARK_PURPLE).withItalic(true);
    public static List<Text> getLore(ItemStack stack){
        NbtCompound nbt = stack.getNbt();
        List<Text> list = new ArrayList<>();
        if (nbt != null && nbt.contains("display", 10)) {
            NbtCompound nbtCompound = nbt.getCompound("display");
            if (nbtCompound.getType("Lore") == 9) {
                NbtList nbtList = nbtCompound.getList("Lore", 8);

                for(int j = 0; j < nbtList.size(); ++j) {
                    String string = nbtList.getString(j);

                    try {
                        MutableText mutableText2 = Text.Serialization.fromJson(string);
                        if (mutableText2 != null) {
                            list.add(mutableText2);
                        }
                    } catch (Exception var19) {
                        nbtCompound.remove("Lore");
                    }
                }
            }
        }
        return list;
    }

    public static void setLore(ItemStack itemStack, List<Text> lore){
        if(lore == null || lore.isEmpty()){
            NbtCompound nbtCompound = getDisplay(itemStack);
            if(nbtCompound != null){
                nbtCompound.remove("Display");
            }
        }else {
            NbtList list =  new NbtList();
            for (var j = 0; j< lore.size(); ++j){
                Text text = lore.get(j);
                String json  =textToJsonRaw(text);
                list.add(NbtString.of(json));
            }
            getOrCreateDisplay(itemStack).put("Lore", list);
        }
    }

    public static List<String> getLoreString(ItemStack stack){
        NbtCompound nbt = stack.getNbt();
        List<String> list = new ArrayList<>();
        if (nbt != null && nbt.contains("display", 10)) {
            NbtCompound nbtCompound = nbt.getCompound("display");
            if (nbtCompound.getType("Lore") == 9) {
                NbtList nbtList = nbtCompound.getList("Lore", 8);

                for(int j = 0; j < nbtList.size(); ++j) {
                    String string = nbtList.getString(j);

                    try {
                        MutableText mutableText2 = Text.Serialization.fromJson(string);
                        if (mutableText2 != null) {
                            list.add(mutableText2.getString().replaceAll("§.", ""));
                        }
                    } catch (Exception var19) {
                        nbtCompound.remove("Lore");
                    }
                }
            }
        }
        return list;
    }
    public static NbtList getStoredEnchantment(ItemStack stack){
        return EnchantedBookItem.getEnchantmentNbt(stack);
    }
    public static void setEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("Enchantments", enchantments);
    }
    public static void setStoredEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("StoredEnchantments", enchantments);
    }
    public static ItemStack getCleanedItem(ItemStack stack){
        return getCleanedItem(stack, true);
    }
    public static ItemStack getCleanedItem(ItemStack stack, boolean keepDur){
        return getCleanedItem(stack,keepDur,true);
    }
    public static ItemStack getCleanedItem(ItemStack stack, boolean keepDur, boolean keepEnchant){
        return getCleanedItem(stack,true, keepDur, keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack, boolean keepNBT, boolean keepDur, boolean keepEnchant){
        return getCleanedItem(stack, -999, keepNBT, keepDur, keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack, int setAmount, boolean keepNBT, boolean keepDur, boolean keepEnchant){
        ItemStack cleaned=stack.getItem().getDefaultStack();

        if(!keepNBT){
            if(setAmount!=-999){
                cleaned.setCount(setAmount);
            }
            return cleaned;
        }
        ItemStack stackCopy=stack.copy();
        if(setAmount!=-999){
            stack.setCount(setAmount);
        }
        if(!keepDur){
            stackCopy.setDamage(cleaned.getDamage());
        }
        if(!keepEnchant){
            stackCopy.removeSubNbt("Enchantments");
            stackCopy.removeSubNbt("StoredEnchantments");
        }

        return stackCopy;
    }
    public static boolean matchItemWithoutLore(ItemStack stack1, ItemStack stack2){
        if(! stack1.isOf(stack2.getItem()) ){
            return false;
        }
        if(stack1.isEmpty()){
            return stack2.isEmpty();
        }else if(stack2.isEmpty()){
            return false;
        }else {
            NbtCompound compound1 = stack1.getNbt();
            NbtCompound compound2 = stack2.getNbt();
            if(compound1 == null || compound2 == null){
                return compound1 == compound2;
            }
            Map<String, NbtElement> map1 = new HashMap<>(compound1.entries);
            Map<String, NbtElement> map2 = new HashMap<>(compound2.entries);
            var n1 = map1.remove("display");
            var n2 = map2.remove("display");
            return map1.equals(map2) && ((n1 instanceof NbtCompound c1 && n2 instanceof NbtCompound c2)? Objects.equals(c1.get("Name"),c2.get("Name")): n1 == n2);
        }
    }
    public static boolean isSimilarItemStack(ItemStack stack1,ItemStack stack2){
        return ItemStack.canCombine(stack1,stack2);
    }
    public static NbtCompound getStoredBlockEntity(ItemStack stack){
        if(stack.hasNbt()){
            var nbt = stack.getNbt();
            if(nbt != null && nbt.contains("BlockEntityTag")){
                return nbt.getCompound("BlockEntityTag");
            }
        }
        return null;
    }
    protected static String BUKKIT_NAMESPACE="PublicBukkitValues";
    protected static String SLIMEFUN_ID_PATH="slimefun:slimefun_item";
    protected static boolean isGrassOrShortGrass = Registries.ITEM.get(new Identifier("minecraft","grass")) != Items.AIR;
    public static ItemStack newItem(String type,String id){
        String[] typedString = type.split("[$]");
        String typedStr = typedString[0].toLowerCase(Locale.ROOT);
        if( "grass".equals(typedStr) || "short_grass".equals(typedStr) ){
            typedStr = isGrassOrShortGrass?"grass":"short_grass";
        }
        Item typed = Registries.ITEM.get(new Identifier("minecraft", typedStr));

        ItemStack stacked = new ItemStack(typed);
        if(typedString.length == 2 ){
            if(typed == Items.PLAYER_HEAD){
                stacked.getOrCreateNbt().put("SkullOwner", ItemStackHelper.buildPlayerHead(typedString[1]));
            }
        }
        if(id!=null && !"null".equals(id)){
            getOrCreateBukkitValues(stacked).putString(SLIMEFUN_ID_PATH,id);
        }
        return stacked.isEmpty() ? null: stacked;
    }
    public static String getSfId(NbtCompound nbt){
        NbtCompound bukkitValues=getBukkitValues(nbt);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static void setSfId(ItemStack stack , String id){
        if(id == null || id.isEmpty()){
            var re = getBukkitValues(stack);
            if(re != null){
                re.remove(SLIMEFUN_ID_PATH);
                if(re.isEmpty()){
                    stack.removeSubNbt(BUKKIT_NAMESPACE);
                }
            }
        }else {
            var re = getOrCreateBukkitValues(stack);
            re.putString(SLIMEFUN_ID_PATH, id);
        }
    }
    public static String getSfId(ItemStack stack) {
        NbtCompound bukkitValues=getBukkitValues(stack);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static NbtCompound getBukkitValues(ItemStack stack) {
        if(stack.hasNbt()){
            return getBukkitValues(stack.getNbt());
        }else return null;
    }
    public static NbtCompound getOrCreateBukkitValues(ItemStack stack) {
        return stack.getOrCreateSubNbt(BUKKIT_NAMESPACE);
    }
    public static NbtCompound getBukkitValues(NbtCompound tag) {
        if(tag!=null&&!tag.isEmpty()&&tag.contains(BUKKIT_NAMESPACE)){
            return tag.getCompound(BUKKIT_NAMESPACE);
        }else return null;
    }
    public static String getSfIdFromBukkitValues(NbtCompound bukkitValues) {
        if(bukkitValues!=null&&!bukkitValues.isEmpty()&&bukkitValues.contains(SLIMEFUN_ID_PATH)){
            return bukkitValues.getString(SLIMEFUN_ID_PATH);
        }else return null;
    }

    public static void setCustomModelData(ItemStack stack,int customModelData){
        stack.getOrCreateNbt().putInt("CustomModelData",customModelData);
    }


}
