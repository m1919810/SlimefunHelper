package me.matl114.Utils;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import java.util.HashSet;
import java.util.regex.Pattern;

public class EntityUtils {
    public static void parseEntityWhiteList(String value, HashSet<EntityType<?>> collection){
        collection.clear();
        try{
            for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                if(Pattern.matches(value,Registries.ENTITY_TYPE.getId(entityType).getPath())){
                    collection.add(entityType);
                }
            }
            if(Pattern.matches(value,"animal")){
                for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                    if(entityType.getSpawnGroup()== SpawnGroup.CREATURE){
                        collection.add(entityType);
                    }
                }
            }
            if(Pattern.matches(value,"monster")){
                for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                    if(entityType.getSpawnGroup()== SpawnGroup.MONSTER){
                        if(!(entityType== net.minecraft.entity.EntityType.ZOGLIN)&&!(entityType== net.minecraft.entity.EntityType.ENDERMAN)){
                            collection.add(entityType);
                        }
                    }
                }
            }
            var iter=collection.iterator();
            while(iter.hasNext()){
                EntityType<?> entityType=iter.next();
                if(Pattern.matches(value,"!"+Registries.ENTITY_TYPE.getId(entityType).getPath())){
                    iter.remove();
                }
            }
        }catch(Throwable valuePatternError){
            collection.clear();
        }
        //Debug.info("Using whitelist",set);

    }
}
