package me.matl114.SlimefunUtils;

import me.matl114.Utils.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class StorageItemRenderer {
    static final ThreadLocal<Boolean> isRendering = ThreadLocal.withInitial(() -> false);
    public static void render(
            DrawContext itemRenderer,
            //#endif
           LivingEntity livingEntity,
           World world,

            ItemStack itemStack, int x, int y,int z,int seed
    ){
        ItemStack stack=null;
        try{
            stack= SlimefunUtils.getContainedItemInfo(itemStack);
        }catch (Throwable e){
            Debug.info("An Error occurred while deserialization");
            return;
        }
        if(stack!=null){
            RenderUtils.drawItem(itemRenderer,livingEntity,world,stack,0.6f,x+4,y+4,seed,z,10);
        }
    }


}
