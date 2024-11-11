package me.matl114.BukkitUtiils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BukkitPersistentDataContainer implements PersistentDataContainer {
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
    public <T, Z> void set(@NotNull NamespacedKey var1, @NotNull PersistentDataType<T, Z> var2, @NotNull Z var3){

    }

    public <T, Z> boolean has(@NotNull NamespacedKey var1, @NotNull PersistentDataType<T, Z> var2){
        return false;
    }

    @Nullable
    public <T, Z> Z get(@NotNull NamespacedKey var1, @NotNull PersistentDataType<T, Z> var2){
        return null;
    }

    @NotNull
    public <T, Z> Z getOrDefault(@NotNull NamespacedKey var1, @NotNull PersistentDataType<T, Z> var2, @NotNull Z var3){
        return null;
    }

    @NotNull
    public Set<NamespacedKey> getKeys(){
        return this.container.keySet().stream().map(NamespacedKey::fromString).collect(Collectors.toUnmodifiableSet());
    }

    public void remove(@NotNull NamespacedKey var1){

    }


    public boolean isEmpty(){
        return false;
    }

    @NotNull
    public PersistentDataAdapterContext getAdapterContext(){
        return null;
    }
}
