package me.matl114.hacks.modules.combat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.events.Event;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AutoTotem extends BaseModule {
    //todo: move to combat
    private final Random inventorRandom = new Random();
    public static final String[] AUTO_TOTEM = {"totem", "auto-totem"};
    public static final String[] TOTEM_MODE = {"totem", "auto-totem-mode"};
    public AutoTotem() {

    }

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, AUTO_TOTEM)
        .build();

    public final EnumRef<AutoInvMode> mode = builder(Configs.COMBAT_CONFIG, AutoInvMode.class)
        .path(TOTEM_MODE)
        .defaultValue(AutoInvMode.LAZY)
        .build();


    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameTick(), this::onTick);
    }


    public void onTick(Event<ClientPlayerEntity> ev){
        var player = ev.context();
        if(enable.get()){
            ScreenHandler handled = ClientPlayerAccess.of(player).getServerScreenHandler();
            int offHandSlot = handled != player.playerScreenHandler ? -1: 40;
            if(mode.get() == AutoInvMode.LAZY){
                if(player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING){

                    List<Slot> slots = handled.slots;
                    for (var i = 0; i < slots.size(); ++ i){
                        if(slots.get(i).inventory instanceof PlayerInventory && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING){
                            InvTasks.clickSlotAsync(i, 40, SlotActionType.SWAP);
                            return;
                        }
                    }

                }
            }else if(mode.get() == AutoInvMode.TICK){
                IntList totemList = new IntArrayList();
                List<Slot> slots = handled.slots;
                for (var i = 0; i < slots.size(); ++ i){
                    if(slots.get(i).inventory instanceof PlayerInventory && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING && i != offHandSlot){
                        totemList.add(i);
                    }
                }
                if(!totemList.isEmpty()){
                    int random = totemList.getInt(inventorRandom.nextInt(totemList.size()));
                    InvTasks.clickSlotAsync(random, 40, SlotActionType.SWAP);
                }
            }
        }
    }

    public enum AutoInvMode implements ConfigEnum {
        LAZY,
        TICK
        ;
        public Text getDisplay(){
            return Text.translatable("configenum.auto-inv-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

}
