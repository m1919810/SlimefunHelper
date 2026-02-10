package me.matl114.bukkit;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VNbt;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;

public class BukkitConfigDeserializor {
    private static final Pattern ARRAY = Pattern.compile("^\\[.*]");
    private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)?i", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE =
            Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);

    public static NbtElement deserializeObject(final Object object) {
        // The new logic expects the top level object to be a single string, holding the entire nbt tag as SNBT.
        if (object instanceof final String snbtString) {
            try {
                return StringNbtReader.fromOps(ItemStackUtils.registry().getOps(NbtOps.INSTANCE))
                        .read(snbtString);
            } catch (final CommandSyntaxException e) {
                throw new RuntimeException("Failed to deserialise nbt", e);
            }
        } else { // Legacy logic is passed to the internal legacy deserialization that attempts to read the old format
            // that *unsuccessfully* attempted to read/write nbt to a full yml tree.
            return deserializeObjectLegacy(object);
        }
    }

    public static NbtElement deserializeObjectLegacy(Object object) {
        if (object instanceof Map) {
            NbtCompound compound = new NbtCompound();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) object).entrySet()) {
                compound.put(entry.getKey(), deserializeObjectLegacy(entry.getValue()));
            }

            return compound;
        } else if (object instanceof List) {
            List<Object> list = (List<Object>) object;
            if (list.isEmpty()) {
                return new NbtList(); // Default
            }

            NbtList tagList = new NbtList();
            for (Object tag : list) {
                tagList.add(deserializeObjectLegacy(tag));
            }

            return tagList;
        } else if (object instanceof String) {
            String string = (String) object;

            if (ARRAY.matcher(string).matches()) {

                return VNbt.getInstance().readNbt(string);
            } else if (INTEGER.matcher(string).matches()) { // Read integers on our own
                return NbtInt.of(Integer.parseInt(string.substring(0, string.length() - 1)));
            } else if (DOUBLE.matcher(string).matches()) {
                return NbtDouble.of(Double.parseDouble(string.substring(0, string.length() - 1)));
            } else {
                NbtElement nbtBase;
                try {
                    nbtBase = StringNbtReader.fromOps(ItemStackUtils.registry().getOps(NbtOps.INSTANCE))
                            .read(string);
                } catch (CommandSyntaxException e) {
                    throw new RuntimeException("Could not deserialize found element ", e);
                }
                if (nbtBase instanceof NbtInt nit) { // If this returns an integer, it did not use our method from above
                    return NbtString.of(
                            String.valueOf(nit.value())); // It then is a string that was falsely read as an int
                } else if (nbtBase instanceof NbtDouble) {
                    return NbtString.of(
                            String.valueOf(((NbtDouble) nbtBase).doubleValue())); // Doubles add "d" at the end
                } else {
                    return nbtBase;
                }
            }
        }

        throw new RuntimeException("Could not deserialize NBTBase");
    }

    public static final String TEST_CASE = "item:\n" + "  ==: org.bukkit.inventory.ItemStack\n"
            + "  v: 3465\n"
            + "  type: DIRT\n"
            + "  meta:\n"
            + "    ==: ItemMeta\n"
            + "    meta-type: UNSPECIFIC\n"
            + "    PublicBukkitValues:\n"
            + "      infinityexpansion:display: 351372703i\n";

    public static BukkitItemStack deserializeItemFromString(String string) {
        return deserializeItemFromStringTest(string);
    }

    public static BukkitItemStack deserializeItemFromStringTest(String string) {
        BukkitYaml config = new BukkitYaml();
        try {
            return config.getItemStackFromString(string); //  config.loadFromString(string);
        } catch (BukkitYaml.InvalidConfigException var3) {
            Debug.info(var3);
            return new BukkitItemStack(Items.STONE, 1);
        }
        //        BukkitItemStack item = config.getObject("item",BukkitItemStack.class);
        //        return (item != null ? item :new BukkitItemStack(Items.STONE,1));
    }
}
