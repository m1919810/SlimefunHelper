package me.matl114.BukkitUtiils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitOfflineplayer implements ConfigurationSerializable {
    protected String name;
    protected String uuid;

    public static  BukkitOfflineplayer deserialize(Map<String, Object> args) {
        // Backwards comparability
        BukkitOfflineplayer offline= new BukkitOfflineplayer();
        offline.name = (String) args.get("name");
        offline.uuid = (String) args.get("uuid");
        return offline;
    }
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("UUID", uuid);
        result.put("name", name);
        return result;
    }

}
