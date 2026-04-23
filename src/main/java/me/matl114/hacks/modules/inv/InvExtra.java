package me.matl114.hacks.modules.inv;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;

public class InvExtra extends BaseModule {
    public static final String[] INV_CLICK_LIMIT = {"inventory", "packet-limit"};

    public static final String[] INV_GUI_CLICK_GRIM_FIX = {"inventory", "move-click-grim-fix"};

    public InvExtra() {}

    public final IntRef inventoryClickLimit = builder(Configs.INV_CONFIG, IntRef.TYPE)
            .path(INV_CLICK_LIMIT)
            .defaultValue(40)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef invGrimFix =
            flagBuilder(Configs.INV_CONFIG, INV_GUI_CLICK_GRIM_FIX).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(ClickSlotC2SPacket.class), this::onClickSlot);
    }

    public void onClickSlot(Event<ClickSlotC2SPacket> event) {
        if (invGrimFix.get()) {
            // do not support viafabric, I guess
            MovTasks.getMovExtra().sendPacketsForInventoryAction();
        }
    }
}
