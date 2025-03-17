package me.matl114.renders.implement;

import me.matl114.renders.RenderMain;
import me.matl114.utils.EntityUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

public class SpawnerRender {
    public static void init(){

    }

    private static final Map<EntityType<?>, ItemStack> ENTITYTYPE_TO_NEWSPAWNEREGGS = new HashMap<>(){{
        for (EntityType<?> types : Registries.ENTITY_TYPE){
            Item optionalEgg = EntityUtils.entityToSpawnEgg(types);
            if (optionalEgg != null && optionalEgg != Items.AIR){
                put(types, NewVersionModelRender.ofNewVersion(new ItemStack(optionalEgg)));
            }
        }
    }};
    public static ItemStack getRenderingEntityContent(EntityType<?> typed) {
        return ENTITYTYPE_TO_NEWSPAWNEREGGS.containsKey(typed) ? ENTITYTYPE_TO_NEWSPAWNEREGGS.get(typed).copy() : null;
    }
    static{
        RenderMain.registerContainerInfoPredicate(1005, (stack)->{
            EntityType<?> typed = EntityUtils.getStoredEntityType(stack);
            if(typed != null){
                return getRenderingEntityContent(typed);
            }
            return null;
        });
    }
}
