package me.matl114.bukkit;

import static me.matl114.utils.ItemStackUtils.*;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import javax.annotation.Nonnull;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VRecord;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class BukkitItemStackUtils {
    public static ConfigurationSerializableDataType<BukkitItemStack> DATATYPE_MOCKITEMSTACK =
            new ConfigurationSerializableDataType(BukkitItemStack.class);
    public static ItemStack STACK_FORBIDDEN = new ItemStack(Items.BARRIER, 1);

    public static void init() {}

    static {
        Debug.info("Bukkit ItemStack Utils enabled");
    }

    public static ItemStack getAsDisplayItem(BukkitItemStack itemStack) {
        try {
            if (itemStack instanceof CraftItemStack cis) {
                return cis.buildDisplay();
            }
            ItemStack stack = new ItemStack(itemStack.getType());
            stack.setCount(itemStack.getAmount());
            if (itemStack.hasItemMeta()) {
                BukkitMetaItem meta = itemStack.getItemMeta();
                if (meta.hasDisplayName()) {
                    ItemStackUtils.setCustomName(stack, ItemStackUtils.jsonRawToText(meta.getDisplayName()));
                }
                if (meta.hasLore()) {
                    ItemStackUtils.setLore(
                            stack,
                            meta.getLore().stream()
                                    .map(ItemStackUtils::jsonRawToText)
                                    .toList());
                }
                if (meta.hasEnchants()) {

                    ItemStackUtils.setEnchantmentGlow(stack);
                }
                if (BukkitMetaType.ENCHANT_BOOK.isType(meta)) {
                    ItemStackUtils.setEnchantmentGlow(stack);
                }
                if (BukkitMetaType.SKULL.isType(meta)) {
                    if (BukkitMetaType.SKULL.getAttr(meta, "skull-owner") instanceof BukkitPlayerProfile bp) {
                        bp.addGameProfile(stack);
                    }
                }
                BukkitPersistentDataContainer container = meta.getPersistentDataContainer();
                if (container != null) {
                    updateCustomData(
                            stack, nbtCompound -> nbtCompound.put("PublicBukkitValues", container.toCompound()));
                }
                if (meta.hasCustomModelData()) {
                    setCustomModelData(stack, meta.getCustomModelData());
                }
            }
            //            Debug.info("get stack as display");
            //            Debug.info(stack);
            //            Debug.info(stack.hasNbt()?stack.getNbt():"null");
            return stack;

        } catch (Throwable e) {
            Debug.info("error in ItemConvertion");
            return STACK_FORBIDDEN;
        }
    }

    public static String getHashFromProfile(ProfileComponent profileComponent) {
        var pps = VRecord.getProperties(profileComponent.gameProfile()).get("textures");
        if (pps == null || pps.isEmpty()) return null;
        Property ppt = Iterables.getFirst(pps, null);
        if (ppt == null) return null;
        JsonObject jsonObject = BukkitPlayerProfile.PlayerSkin.decodePropertyValue(ppt.value());
        if (jsonObject != null) {
            if (jsonObject.get("textures") instanceof JsonObject object1) {
                if (object1.get("SKIN") instanceof JsonObject object2) {
                    if (object2.get("url") instanceof JsonPrimitive primitive) {
                        String url = primitive.getAsString();
                        String[] parts = url.split("/");
                        return parts[parts.length - 1];
                    }
                }
            }
        }
        return null;
    }

    @Nonnull
    public static PropertyMap buildPropertyMap(PropertyMap oldMap, String hash) {
        try {
            Property property = BukkitPlayerProfile.encodeUrlToProperty(
                    BukkitPlayerProfile.fromHashToUrl(hash),
                    BukkitPlayerProfile.PlayerTextures.SkinModel.CLASSIC,
                    null);
            PropertyMap map1 = new PropertyMap();
            map1.putAll(oldMap);
            map1.removeAll("textures");
            map1.put("textures", property);
            return map1;
        } catch (Throwable e) {
            return oldMap;
        }
    }

    public static ProfileComponent buildPlayerHeadProfileCSCoreLib(String hash) {
        try {
            BukkitPlayerProfile.PlayerSkin skin = BukkitPlayerProfile.fromHashCode(hash);
            BukkitPlayerProfile profile = skin.getProfile();
            profile.name = "CS-CoreLib";
            // GameProfile profile = new GameProfile(uid, "CS-CoreLib");
            return profile.createGameProfile();
        } catch (Throwable e) {
            return null;
        }
    }
}
