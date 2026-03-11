package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

// todo: move in the future, or not
public class KeepInv extends BaseModule {

    public static final String[] KEEP_INV = {"button-toggle", "keep-inv"};
    public static final String CLEAR_KEEP = "clear-keep";

    public KeepInv() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.TOGGLE_CONFIG, KEEP_INV).build();

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + CLEAR_KEEP, this::clearKeep);
    }

    public void clearKeep() {

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            Debug.chat(Text.literal("已清除界面历史记录"));
        }
    }
}
