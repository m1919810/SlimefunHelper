package me.matl114.utils;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegistryUtils {
    public static <T> Set<T> parseWhiteList(Registry<T> registry, String regex){
        Set<T> newBlocks = new LinkedHashSet<>();
        try{
            Predicate<String> predicate = Pattern.compile(regex).asMatchPredicate();
            for(var blockIds: registry.getIds()){
                if(predicate.test(blockIds.getPath())){
                    newBlocks.add(registry.get(blockIds));
                }
            }
        }catch (Throwable e){

        }
        return newBlocks;
    }

    public static <T> Set<RegistryEntry<T>> parseEntryWhiteList(Registry<T> registry, String regex){
        Set<RegistryEntry<T>> newBlocks = new LinkedHashSet<>();
        try{
            Predicate<String> predicate = Pattern.compile(regex).asMatchPredicate();
            for(var blockIds: registry.getIds()){
                if(predicate.test(blockIds.getPath())){
                    RegistryEntry<T> reg = registry.getEntry(blockIds).orElse(null);
                    if(reg != null){
                        newBlocks.add(reg);
                    }
                }
            }
        }catch (Throwable e){

        }
        return newBlocks;
    }
}
