package me.matl114.BukkitUtiils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.matl114.Access.StringNbtReaderAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.nbt.*;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BukkitConfigDeserializor {
    private static final Pattern ARRAY = Pattern.compile("^\\[.*]");
    private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)?i", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final StringNbtReader MOJANGSON_PARSER = new StringNbtReader(new StringReader(""));

    public static NbtElement deserializeObject(Object object) {
        if (object instanceof Map) {
            NbtCompound compound = new NbtCompound();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) object).entrySet()) {
                compound.put(entry.getKey(), deserializeObject(entry.getValue()));
            }

            return compound;
        } else if (object instanceof List) {
            List<Object> list = (List<Object>) object;
            if (list.isEmpty()) {
                return new NbtList(); // Default
            }

            NbtList tagList = new NbtList();
            for (Object tag : list) {
                tagList.add(deserializeObject(tag));
            }

            return tagList;
        } else if (object instanceof String) {
            String string = (String) object;

            if (ARRAY.matcher(string).matches()) {
                try {
                    return new StringNbtReader(new StringReader(string)).parseElement();
                } catch (CommandSyntaxException e) {
                    throw new RuntimeException("Could not deserialize found list ", e);
                }
            } else if (INTEGER.matcher(string).matches()) { //Read integers on our own
                return NbtInt.of(Integer.parseInt(string.substring(0, string.length() - 1)));
            } else if (DOUBLE.matcher(string).matches()) {
                return NbtDouble.of(Double.parseDouble(string.substring(0, string.length() - 1)));
            } else {
                NbtElement nbtBase = StringNbtReaderAccess.of(MOJANGSON_PARSER).type(string);

                if (nbtBase instanceof NbtInt) { // If this returns an integer, it did not use our method from above
                    return NbtString.of(nbtBase.asString()); // It then is a string that was falsely read as an int
                } else if (nbtBase instanceof NbtDouble) {
                    return NbtString.of(String.valueOf(((NbtDouble) nbtBase).doubleValue())); // Doubles add "d" at the end
                } else {
                    return nbtBase;
                }
            }
        }

        throw new RuntimeException("Could not deserialize NBTBase");
    }
    public static BukkitItemStack deserializeItemFromString(String string){
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(string);
        } catch (InvalidConfigurationException var3) {
            return new BukkitItemStack(Material.STONE,1);
        }
        BukkitItemStack item = config.getObject("item",BukkitItemStack.class);
        return (item != null ? item :new BukkitItemStack(Material.STONE,1));
    }
}
