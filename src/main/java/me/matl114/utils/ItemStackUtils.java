package me.matl114.utils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import me.matl114.bukkit.BukkitItemStackUtils;
import me.matl114.versioned.api.VHideFlag;
import me.matl114.versioned.impl.TooltipHideFlag_v1_21_1;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientDynamicRegistryType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.component.DataComponentTypes.*;

@ApiMethod
public class ItemStackUtils {


    public static VHideFlag[] getHideFlags(){
        return TooltipHideFlag_v1_21_1.values();
    }


    public static Predicate<ItemStack> componentPredicate(ComponentType<?> type){
        return (stack)-> hasInPatch(stack, type);
    }
    public static <T>  Predicate<ItemStack> componentPredicate(ComponentType<T> type,  Predicate<T> test, boolean nullDefault){
        return (stack)->{
            var val = stack.get(type);
            if(val != null){
                return test.test(val);
            }else {
                return nullDefault;
            }
        };
    }


    public interface TooltipsToggle{
        public void apply(ItemStack stack, boolean showInTooltip);
        public static  TooltipsToggle byComponent(ComponentType<Unit> type){
            return ((stack, showInTooltip) -> {
                if(showInTooltip){
                    stack.remove(type);
                }else {
                    stack.set(type, Unit.INSTANCE);
                }
            });
        }
        public static <T> TooltipsToggle onComponent(ComponentType<T> type,  ComponentTooltipsToggle<T> toggle){
            return ((stack, showInTooltips)->{
                T val = stack.get(type);
                if(val != null){
                    stack.set(type, toggle.toggle(val, showInTooltips));
                }
            });
        }
    }
    public interface ComponentTooltipsToggle<T>{
        T toggle(T val, boolean showInToolTips);
    }

    @SuppressWarnings("all")
    public static <T> T getInPatch(ItemStack stack, ComponentType<T> type){
        if(stack != null && !stack.isEmpty()){
            var map = stack.components.changedComponents;
            if(map == null)return null;
            var optional = map.get(type);
            return (T)(optional == null? null: optional.orElse(null));
        }
        return null;
    }
    public static boolean hasInPatch(ItemStack stack){
        if(stack != null && !stack.isEmpty()){
            var map = stack.components.changedComponents;
            // add compat to via item 1.20.4
            if(map == null)return false;
            if(map.isEmpty())return false;
            if(map.size() >=2 )return true;
            if(map.containsKey(CUSTOM_DATA)){
                //check protocol item
                NbtComponent customData = (NbtComponent) map.get(CUSTOM_DATA).orElse(null);
                if(customData == null || customData.isEmpty())return false;
                var nbt = customData.getNbt();
                Set<String> keys = nbt.getKeys();
                if(keys.size() > 2)return true;
                // we only support Damage , because most of these are from damage
                int val = nbt.get("Damage") instanceof NbtInt nbtInt ? nbtInt.intValue() : 0;
                if(val > 0)return true;
                for(var key : keys){
                    //viaversion items
                    if(Objects.equals("Damage", key) || key.contains( "VV|Protocol")){
                        continue;
                    }
                    return true;
                }
                return false;
            }else return true;

        }
        return false;
    }
    public static boolean hasInPatch(ItemStack stack,  ComponentType<?> type){
        if(stack != null && !stack.isEmpty()){
            var map = stack.components.changedComponents;
            if(map == null)return false;
            return map.containsKey(type) && !Objects.equals(Optional.empty(), map.get(type));
        }
        return false;
    }


    public static <T> void setOrRemoveChange(ItemStack stack,  ComponentType<T> type,@Nullable T val){
        if(stack != null && !stack.isEmpty()){
            var cpmap = stack.components;
            var map = cpmap.changedComponents;
            if(map == null)return;
            boolean shouldChange;
            if (val == null ) {
                shouldChange = map.containsKey(type);
            }else {
                var op = map.get(type);
                if(op != null && Objects.equals(val, op.orElse(null))){
                    shouldChange = false;
                }else shouldChange = true;
            }
            if(shouldChange){
                //copy before write
                cpmap.onWrite();
                //update the map after copy
                map = cpmap.changedComponents;
                if(val == null)map.remove(type);
                else map.put(type, Optional.of(val));
            }
        }
    }
    public static <T> void markRemoveAsChange(ItemStack stack,  ComponentType<T> type){
        if(stack != null && !stack.isEmpty()){
            var cpmap = stack.components;
            var map = cpmap.changedComponents;
            if(map != null){
                //no need to modify
                if(map.containsKey(type) && map.get(type) == Optional.empty())return;
                cpmap.onWrite();
                cpmap.changedComponents.put(type, Optional.empty());
            }
        }
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static DynamicRegistryManager staticRegistry;


    public static <T> Identifier solveDynamic(RegistryEntry<T> entry){
        return entry.getKey().get().getValue();
    }

    public static <T> RegistryEntry<T> findEntry(Registry<T> registry, Identifier id){
        return registry.getOrEmpty(id).map(registry::getEntry).orElse(null);
    }

    public static class DelegateRegistryWrapperLookup implements RegistryWrapper.WrapperLookup{
        protected static final DelegateRegistryWrapperLookup INSTANCE = new DelegateRegistryWrapperLookup();
        @Override
        public Stream<RegistryKey<? extends Registry<?>>> streamAllRegistryKeys() {
            return registry().streamAllRegistryKeys();
        }

        @Override
        public <T> Optional<RegistryWrapper.Impl<T>> getOptionalWrapper(RegistryKey<? extends Registry<? extends T>> registryRef) {
            return registry().getOptionalWrapper(registryRef);
        }

        public  <T> RegistryWrapper.Impl<T> getWrapperOrThrow(RegistryKey<? extends Registry<? extends T>> registryRef){
            return registry().getWrapperOrThrow(registryRef);
        }
        public  <V> RegistryOps<V> getOps(DynamicOps<V> delegate) {
            return registry().getOps(delegate);
        }

        public RegistryEntryLookup.RegistryLookup createRegistryLookup() {
            return registry().createRegistryLookup();
        }
    }

    public static RegistryWrapper.WrapperLookup delegate(){
        return DelegateRegistryWrapperLookup.INSTANCE;
    }

    @Nonnull
    public static DynamicRegistryManager registry(){
        if(mc.getNetworkHandler() != null){
            return mc.getNetworkHandler().getRegistryManager();
        }else {
            if(staticRegistry == null){
                staticRegistry = ClientDynamicRegistryType.createCombinedDynamicRegistries().getCombinedRegistryManager();
            }
            return staticRegistry;
        }
    }
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static Text jsonRawToText(String jsonRaw){
        try{
            if(jsonRaw == null)return null;
            JsonElement jsonElement = JsonParser.parseString(jsonRaw);
            return jsonElement == null ? null : TextCodecs.CODEC.parse(registry().getOps(JsonOps.INSTANCE), jsonElement).getOrThrow(JsonParseException::new);
        }catch (Throwable e){
            return null;
        }
    }
    public static String textToJsonRaw(Text text){
        if(text == null)return null;
        try{
            var re = TextCodecs.CODEC.encodeStart(registry().getOps(JsonOps.INSTANCE), text).getOrThrow(JsonParseException::new);
            return GSON.toJson(re);
        }catch (Throwable e){
            return null;
        }
    }
    @Nullable
    public static Text getCustomName(ItemStack stack){
        var text = getInPatch(stack , CUSTOM_NAME);
        return text == null ? Text.empty() : text;
    }


    public static void setCustomName(ItemStack stack,  Text text){
        setOrRemoveChange(stack, CUSTOM_NAME , Objects.equals(text, Text.empty()) ? null : text);
    }


    public static void applyItemEnchant(ItemStack stack,  ItemEnchantmentsComponent ench){
        setOrRemoveChange(stack, ENCHANTMENTS, Objects.equals(ench, ItemEnchantmentsComponent.DEFAULT)? null: ench);
    }

    public static ItemEnchantmentsComponent getItemEnchant(ItemStack stack){
        var itemEnchant = getInPatch(stack, ENCHANTMENTS);
        return itemEnchant == null? ItemEnchantmentsComponent.DEFAULT : itemEnchant;
    }
    private static final Map<String, EquipmentSlot> NAME_TO_SLOT =new HashMap<>();
    static {
        for (var re: EquipmentSlot.values()){
            NAME_TO_SLOT.put(re.getName(), re);
        }
    }
    public static AttributeModifiersComponent getEntityModifier(ItemStack stack){
        var attr = getInPatch(stack, ATTRIBUTE_MODIFIERS);
        return attr == null? AttributeModifiersComponent.DEFAULT: attr;
    }
    public static void applyEntityModifier(ItemStack stack,  AttributeModifiersComponent data){
        setOrRemoveChange(stack, ATTRIBUTE_MODIFIERS, Objects.equals(data, AttributeModifiersComponent.DEFAULT)? null: data);
    }


    public static boolean getIsUnbreakable(ItemStack stack){
        return hasInPatch(stack, UNBREAKABLE);
    }
    public static void setUnbreakable(ItemStack stack, boolean ub){
        UnbreakableComponent component = getInPatch(stack, UNBREAKABLE);
        if(component == null){
            setOrRemoveChange(stack, UNBREAKABLE, ub? new UnbreakableComponent(true): null);
        }else {
            if(!ub){
                setOrRemoveChange(stack, UNBREAKABLE, null);
            }
        }

    }

    public static void setDamage(ItemStack stack,  int damage){
        if(stack == ItemStack.EMPTY)return;
        if(damage > 0){
            stack.setDamage(damage);
        }else {
            setOrRemoveChange(stack, DAMAGE, null);
        }
    }

    private static final Style LORE_STYLE = Style.EMPTY.withColor(Formatting.DARK_PURPLE).withItalic(true);
    public static List<Text> getLore(ItemStack stack){
        var itemLore = getInPatch(stack, LORE);
        return itemLore == null? new ArrayList<>(): new ArrayList<>(itemLore.lines());
    }
    public static List<Text> getLoreReadOnly(ItemStack stack){
        var itemLore = getInPatch(stack, LORE);
        return itemLore == null ? List.of(): itemLore.lines();
    }

    public static void setLore(ItemStack itemStack, List<Text> lore){
        if(lore != null && !lore.isEmpty()){
            setOrRemoveChange(itemStack, LORE, new LoreComponent(lore));
        }else {
            setOrRemoveChange(itemStack, LORE, null);
        }
    }

    public static List<String> getLoreString(ItemStack stack){
        return getLoreReadOnly(stack).stream().map(txt->txt.getString().replace("§.","")).collect(Collectors.toCollection(ArrayList::new));
    }
    public static ItemEnchantmentsComponent getStoredEnchantment(ItemStack stack){
        var ench = getInPatch(stack, STORED_ENCHANTMENTS);
        return ench == null? ItemEnchantmentsComponent.DEFAULT: ench;
    }
    public static void setEnchantmentGlow(ItemStack stack){
        setOrRemoveChange(stack, ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
    }
    public static void setEnchantment(ItemStack stack, ItemEnchantmentsComponent enchantments){
        setOrRemoveChange(stack, ENCHANTMENTS, Objects.equals(enchantments, ItemEnchantmentsComponent.DEFAULT)? null: enchantments);
    }
    public static void setStoredEnchantment(ItemStack stack, ItemEnchantmentsComponent enchantments){
        setOrRemoveChange(stack,STORED_ENCHANTMENTS, Objects.equals(enchantments, ItemEnchantmentsComponent.DEFAULT)? null: enchantments);
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
            setOrRemoveChange(stackCopy, ENCHANTMENTS, null);
            setOrRemoveChange(stackCopy, STORED_ENCHANTMENTS, null);
        }

        return stackCopy;
    }

    public static boolean matchItemWithout(ItemStack stack1, ItemStack stack2, boolean matchDur, boolean matchEnch, boolean matchLore){

        if(stack1.isEmpty()){
            return stack2.isEmpty();
        }else if(stack2.isEmpty()){
            return false;
        }else if(! stack1.isOf(stack2.getItem()) ){
            return false;
        }{
            //both not empty and with same item
//            if(matchDur && matchEnch){
//                return matchLore ? ItemStack.areItemsAndComponentsEqual(stack1, stack2) : matchItemWithoutLore(stack1, stack2);
//            }else{
//                // optimize
//                ItemStack clean1 = getCleanedItem(stack1, matchDur, matchEnch);
//                ItemStack clean2 = getCleanedItem(stack2, matchDur, matchEnch);
//                return matchLore ? ItemStack.areItemsAndComponentsEqual(clean1, clean2) : matchItemWithoutLore(clean1, clean2);
//            }
            if(matchDur && matchEnch && matchLore){
                return ItemStack.areItemsAndComponentsEqual(stack1, stack2);
            }
            var compound1 = stack1.components.changedComponents;
            var compound2 = stack2.components.changedComponents;
            if(compound1 == null || compound2 == null){
                return compound1 == compound2;
            }

            Map<ComponentType, Optional> map1 = new HashMap<>(compound1);
            Map<ComponentType, Optional> map2 = new HashMap<>(compound2);
            Optional n1;
            Optional n2;
            if(!matchLore){
                n1 = map1.remove(LORE);
                n2 = map2.remove(LORE);
                //both having or not having lore
                if( !((n1 == null)? (n2 == null || n2 == Optional.empty()) : (n2 != null && n2.isPresent()))  ){
                    return false;
                }
            }
            if(!matchDur){
                n1 = map1.remove(DAMAGE);
                n2 = map2.remove(DAMAGE);
                //both having or not having lore
                if( !((n1 == null)? (n2 == null || n2 == Optional.empty()) : (n2 != null && n2.isPresent()))  ){
                    return false;
                }
            }
            if(!matchEnch){
                n1 = map1.remove(ENCHANTMENTS);
                n2 = map2.remove(ENCHANTMENTS);
                //both having or not having lore
                if( !((n1 == null)? (n2 == null || n2 == Optional.empty()) : (n2 != null && n2.isPresent()))  ){
                    return false;
                }
            }
            return map1.equals(map2);
        }
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
            var compound1 = stack1.components.changedComponents;
            var compound2 = stack2.components.changedComponents;
            if(compound1 == null || compound2 == null){
                return compound1 == compound2;
            }
            Map<ComponentType, Optional> map1 = new HashMap<>(compound1);
            Map<ComponentType, Optional> map2 = new HashMap<>(compound2);
            var n1 = map1.remove(LORE);
            var n2 = map2.remove(LORE);
            //both having or not having lore
            return ((n1 == null)? (n2 == null || n2 == Optional.empty()) : (n2 != null && n2.isPresent())) && map1.equals(map2) ;
        }
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
                setOrRemoveChange(stacked, PROFILE, BukkitItemStackUtils.buildPlayerHeadProfileCSCoreLib(typedString[1]));
            }
        }
        if(id!=null && !"null".equals(id)){
            setSfId(stacked, id);
        }
        return stacked.isEmpty() ? null: stacked;
    }
    public static boolean hasCustomData(ItemStack itemStack){
        NbtComponent customData = getInPatch(itemStack, CUSTOM_DATA);
        return customData != null && !customData.isEmpty();
    }
    private static final NbtCompound EMPTY = new NbtCompound(ImmutableMap.of());
    public static NbtCompound getCustomDataReadOnly(ItemStack itemStack){
        NbtComponent customData = getInPatch(itemStack, CUSTOM_DATA);
        return customData == null? EMPTY: customData.getNbt();
    }
    public static void mapCustomData(ItemStack itemStack, UnaryOperator<NbtCompound> updater){
        NbtComponent customData = getInPatch(itemStack, CUSTOM_DATA);
        NbtCompound nbtCompound ;
        if(customData == null){
            nbtCompound = new NbtCompound();
        }else {
            nbtCompound = customData.copyNbt();
        }
        nbtCompound = updater.apply(nbtCompound);
        if(nbtCompound == null || nbtCompound.isEmpty()){
            setOrRemoveChange(itemStack, CUSTOM_DATA, null);
        }else {
            setOrRemoveChange(itemStack, CUSTOM_DATA, new NbtComponent(nbtCompound));
        }

    }
    public static void updateCustomData(ItemStack itemStack, Consumer<NbtCompound> updater){
        NbtComponent customData = getInPatch(itemStack, CUSTOM_DATA);
        NbtCompound nbtCompound ;
        if(customData == null){
            nbtCompound = new NbtCompound();
        }else {
            nbtCompound = customData.copyNbt();
        }
         updater.accept(nbtCompound);
        if(nbtCompound == null || nbtCompound.isEmpty()){
            setOrRemoveChange(itemStack, CUSTOM_DATA, null);
        }else {
            setOrRemoveChange(itemStack, CUSTOM_DATA, new NbtComponent(nbtCompound));
        }

    }
    public static NbtCompound getBukkitValueReadOnly(ItemStack stack){
        NbtCompound compound = getCustomDataReadOnly(stack);
        return getBukkitValue(compound);
    }
    public static NbtCompound getBukkitValue(@Nonnull NbtCompound nbt){
        return nbt.contains(BUKKIT_NAMESPACE, NbtElement.COMPOUND_TYPE) ? (nbt.get(BUKKIT_NAMESPACE) instanceof NbtCompound cpd? cpd : null): null;
    }
    private static NbtCompound createBukkitValue(NbtCompound nbt){
        NbtCompound nbt0 ;
        if(nbt.get(BUKKIT_NAMESPACE) instanceof NbtCompound nbt2){
            return nbt2;
        }
        nbt0 = new NbtCompound();

        nbt.put(BUKKIT_NAMESPACE, nbt0);
        return nbt0;
    }
    public static String getSfIdFromBukkitValues(NbtCompound ntb){
        return ntb == null? null: (ntb.get(SLIMEFUN_ID_PATH) instanceof NbtString nbtString ? nbtString.asString() : null);
    }
    public static String getSfId(NbtCompound nbt){
        NbtCompound bukkitValues=getBukkitValue(nbt);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static void setSfId(ItemStack stack , String id){
        if(id == null || id.isEmpty()){
            mapCustomData(stack, (nbt)->{
                var nbt0 = getBukkitValue(nbt);
                if(nbt0 != null){
                    nbt0.remove(SLIMEFUN_ID_PATH);
                    if(nbt0.isEmpty()){
                        nbt.remove(BUKKIT_NAMESPACE);
                    }
                }
                return nbt;
            });
        }else {
            mapCustomData(stack, (nbt)->{
                NbtCompound compound = createBukkitValue(nbt);
                compound.putString(SLIMEFUN_ID_PATH, id);
                return nbt;
            });
        }
    }
    public static String getSfId(ItemStack stack) {
        NbtCompound bukkitValues=getBukkitValueReadOnly(stack);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }

    public static ItemStack withTypeChange(ItemStack itemStack, Item typeChange){
        return itemStack.copyComponentsToNewStackIgnoreEmpty((ItemConvertible) typeChange, itemStack.getCount());
    }


    public static void setCustomModelData(ItemStack stack,int customModelData){
        setOrRemoveChange(stack, CUSTOM_MODEL_DATA, new CustomModelDataComponent(customModelData));
    }


    public static int getEnchantmentLevel(ItemEnchantmentsComponent component, RegistryKey<Enchantment> key){
        Registry<Enchantment> enchantmentRegistry = ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT);
        return component.getLevel (enchantmentRegistry.getEntry(Enchantments.SHARPNESS).orElse(null));
    }


}
