package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.accessors.access.PlayerInteractItemC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;

public class InvDesyncFix extends BaseModule {
    public InvDesyncFix() {
        super("InvDesyncFix");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.INV_CONFIG, "inventory-desync-fix");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef useItemFix = flagBuilder(root.add("use-item")).build();

    public final FlagRef finishUseFix = flagBuilder(root.add("finish-use")).build();

    public final FlagRef totemFix = flagBuilder(root.add("totem-fix")).build();

    public final FlagRef fastSync = flagBuilder(root.add("fast-sync")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class),
                this::onUseItem,
                Integer.MAX_VALUE - 10);
        registerListener(
                Listener.getPacketPoint().getChannel(ScreenHandlerSlotUpdateS2CPacket.class),
                this::fastAsyncUpdateRevision);
        registerListener(
                Listener.getPacketPoint().getChannel(InventoryS2CPacket.class), this::fastAsyncUpdateRevision2);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onStatusConsumed);
    }

    private void nextRevision() {
        var serverHandler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        serverHandler.nextRevision();
    }

    public void onUseItem(Event<PlayerInteractItemC2SPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        if (enable.get()
                && useItemFix.get()
                && !LivingEntityAccess.of(mc.player).isTrackedUsingItem()) {
            Hand hand = event.context.getHand();
            ItemStack stack =
                    PlayerInteractItemC2SPacketAccess.of(event.context).getItemStack();
            ItemStack currentStack = mc.player.getStackInHand(hand);
            if (!ItemStack.areEqual(stack, currentStack)
                    || InteractUtils.isInteractItemAcceptable(mc.world, mc.player, stack)) {
                PacketManager.schedulePostScheduleCallback(event.context, this::nextRevision);
            }
        }
    }

    private void onStatusConsumed(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        if (enable.get() && event.context.getEntity(mc.world) == mc.player) {
            if (event.context.getStatus() == EntityStatuses.CONSUME_ITEM && finishUseFix.get()) {
                // abort revision, force desync
                // fix grim shit.
                nextRevision();
                nextRevision();
            }
            if (event.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING && totemFix.get()) {
                nextRevision();
                nextRevision();
            }
        }
    }

    public void fastAsyncUpdateRevision(Event<ScreenHandlerSlotUpdateS2CPacket> eventUpdate) {
        if (checkNull()) return;
        int syncId = eventUpdate.context.getSyncId();
        if (enable.get()
                && fastSync.get()
                && mc.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            if (syncId == 0) {
                syncPlayerInventoryRevision(eventUpdate.context.getRevision());
            } else {
                ScreenHandler handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
                if (handler.syncId == syncId) {
                    handler.revision = eventUpdate.context.getRevision();
                }
            }
        }
    }

    public void fastAsyncUpdateRevision2(Event<InventoryS2CPacket> eventUpdate) {
        if (checkNull()) return;
        int syncId = eventUpdate.context.getSyncId();
        if (enable.get()
                && fastSync.get()
                && mc.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            if (syncId == 0) {
                syncPlayerInventoryRevision(eventUpdate.context.getRevision());
            } else {
                ScreenHandler handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
                if (handler.syncId == syncId) {
                    handler.revision = eventUpdate.context.getRevision();
                }
            }
        }
    }

    public int playerInventoryRevisionManage = 0;

    public void syncPlayerInventoryRevision(int revision) {
        if (playerInventoryRevisionManage < 0) {
            playerInventoryRevisionManage = revision;
        } else {
            int abs = Math.abs(playerInventoryRevisionManage - revision);
            if (abs > 20) {
                playerInventoryRevisionManage = revision;
            } else {
                if (playerInventoryRevisionManage < revision) {
                    playerInventoryRevisionManage = revision;
                }
            }
        }
    }
}
