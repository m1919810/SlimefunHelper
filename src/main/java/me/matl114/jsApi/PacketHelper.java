package me.matl114.jsApi;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.InvTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@ApiMethod
public class PacketHelper {
    static MinecraftClient mc = MinecraftClient.getInstance();
    public static Class<? extends Packet<?>> getPacketType(String packetType, boolean s2c){
        Identifier id = Identifier.tryParse(packetType);
        return Listener.getPacketClassById(id, s2c);
    }
    public static void sendPacket(Packet<?> packet){
        mc.getNetworkHandler().sendPacket(packet);
    }

    public static List<String> getAllPacketTypes(){
        return Listener.getRegisteredPacketTypes().keySet().stream().map(PacketType::id).map(Identifier::toString).toList();
    }

    public static int generateSequenceId(){
        var re = mc.world.getPendingUpdateManager().incrementSequence();
        int seq = re.getSequence();
        re.close();
        return seq;
    }


    public static void sendInventoryPacket(int slotId, int button, String actionTypeStr) {
        InvTasks.clickSlotAsync(slotId, button, SlotActionType.valueOf(actionTypeStr.toUpperCase(Locale.ROOT)));
    }
    //todo ; interactionManager methods
    public static void sendAttackBlock(int x, int y, int z, String direction, boolean offhand) {

    }
    public static void sendAttackBlock(Object pos, String direction, boolean offhand) {

    }
}
