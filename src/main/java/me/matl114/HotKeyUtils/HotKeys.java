package me.matl114.HotKeyUtils;

import me.matl114.ModConfig;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunUtils;
import net.minecraft.client.network.ClientPlayerEntity;

public class HotKeys {
    public static void init(){
        KeyCode.init();
        Debug.info("HotKeys enabled!");
    }
    public static final IHotKey COPY_SFID_KEY=new SimpleHotKey("copy-sfId", ModConfig.getSlimefunIdCopyHotkey(),(manager -> {
        ClientPlayerEntity player= manager.getClient().player;
        if(player!=null){
            return SlimefunUtils.copySfIdInHand(player,manager.getClient());
        }
        return false;
    })).register(SimpleInputManager.getInstance());
}
