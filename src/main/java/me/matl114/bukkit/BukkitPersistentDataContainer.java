package me.matl114.bukkit;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.HashMap;
import java.util.Map;

public class BukkitPersistentDataContainer  {
    public Map<String, NbtElement> container=new HashMap<>();
    public BukkitPersistentDataContainer() {

    }
    public void putData(Map<String ,NbtElement> container) {
        this.container.putAll(container);
    }
    public void putData(NbtCompound compound){
        for (String key : compound.getKeys()) {
            this.container.put(key, compound.get(key));
        }
    }
    public NbtCompound toCompound(){
        NbtCompound compound = new NbtCompound();
        for (String key : container.keySet()) {
            compound.put(key, container.get(key));
        }
        return compound;
    }


}
