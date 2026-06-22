package me.matl114.hacks.modules.combat;

import io.netty.buffer.ByteBuf;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.hooks.ViaProtocols;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.NetworkUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SpearEnhance extends BaseModule {
    public static SpearEnhance INSTANCE;
    public final ModulePath spearModule = makePath(Configs.COMBAT_CONFIG, "spear-module");

    public SpearEnhance() {
        INSTANCE = this;
    }

    public final FlagRef spearAutoRestart =
            flagBuilder(spearModule.add("spear-auto-restart")).build();

    public final FlagRef renderKineticPlayers =
            flagBuilder(spearModule.add("render-kinetic-players")).build();

    public final FlagRef replaceSpearModel = builder(spearModule.add("replace-via-spear-model"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef fixOldVersionSpear = builder(spearModule.add("fix-old-version-spear"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef fixOldVersionPiercing = builder(
                    spearModule.add("fix-old-version-spear-piercing"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<WrapColor> renderColor = builder(
                    spearModule.add("render-kinetic-players-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.YELLOW)))
            .build();

    public final FlagRef spearSpeedReset =
            flagBuilder(spearModule.add("reset-spear-speed-rot-enable")).build();

    public final KeyBindRef spearSpeedRotReset = moduleEntry(
                    spearModule.add("reset-spear-speed-rot-hotkey"),
                    new MultiKeyBind(),
                    spearModule.add("reset-spear-speed-rot-enable"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(RenderListener.getCustomModelOverride(), this::onReplaceSpearModel);
        registerListener(Listener.getClientPlayerPostSendMovementPoint(), this::onPostTick);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onUsePiercing);
        registerListener(Listener.getAttackAction(), this::onUsingStab);
    }

    public static boolean isUsingSpear(PlayerEntity player) {
        // todo consider viaversion
        return player != null && player.isUsingItem() && VItem.getInstance().isSpear(player.getActiveItem());
    }

    public static ItemStack getSpear() {
        return mc.player.getActiveItem();
    }

    public void onPreTick(Event<ClientPlayerEntity> tickEvent) {
        if (isUsingSpear(mc.player)) {
            ItemStack stack = getSpear();
            int maxKineticTime = getMaxKineticTime(stack);
            Hand hand = mc.player.getActiveHand();
            if (spearAutoRestart.get() && mc.player.getItemUseTime() > maxKineticTime) {
                mc.interactionManager.stopUsingItem(mc.player);
                mc.interactionManager.interactItem(mc.player, hand);
            }
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (renderKineticPlayers.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                int color = renderColor.get().withAlpha(64);
                var render = RenderUtils.createBoxCollector(false, true, false);
                for (var re : mc.world.getPlayers()) {
                    if (re != mc.getCameraEntity()) {
                        if (canSpearKineticAttack(re)) {
                            render.submit(re.getBoundingBox(), color);
                        }
                    }
                }
                render.render(event.context);
                render.clear();
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public static boolean canSpearKineticAttack(PlayerEntity player) {
        if (isUsingSpear(player)) {
            ItemStack stack = getSpear();
            KineticWeaponComponent kineticWeaponComponent = stack.get(DataComponentTypes.KINETIC_WEAPON);
            if (kineticWeaponComponent != null) {
                if (player.getItemUseTime() < kineticWeaponComponent.delayTicks()) {
                    return false;
                }
            } else {
                if (player.getItemUseTime() < 8) {
                    return false;
                }
            }
            int maxKineticTime = getMaxKineticTime(stack);
            return player.getItemUseTime() < maxKineticTime;
        }
        return false;
    }

    public static boolean canSpearKineticAttack() {
        return canSpearKineticAttack(mc.player);
    }

    public static int getMaxKineticTime(ItemStack stack) {
        KineticWeaponComponent kineticWeaponComponent = stack.get(DataComponentTypes.KINETIC_WEAPON);
        if (kineticWeaponComponent != null) {
            if (kineticWeaponComponent.damageConditions().isPresent()) {
                return kineticWeaponComponent.damageConditions().get().maxDurationTicks();
            }
            return 0;
        } else {
            Item item = stack.getItem();
            float lastingSec;
            if (item == Items.DIAMOND_SWORD) {
                lastingSec = 10;
            } else if (item == Items.NETHERITE_SWORD) {
                lastingSec = 8.75f;
            } else if (item == Items.IRON_SWORD) {
                lastingSec = 11.25f;
            } else if (item == Items.COPPER_SWORD) {
                lastingSec = 12.5f;
            } else if (item == Items.STONE_SWORD || item == Items.GOLDEN_SWORD) {
                lastingSec = 13.75f;
            } else if (item == Items.WOODEN_SWORD) {
                lastingSec = 15f;
            } else {
                return 0;
            }
            return (int) lastingSec * 20;
        }
    }

    private final Map<Item, Item> materialSwordToSpearMap = new HashMap<>();

    {
        // 木制
        materialSwordToSpearMap.put(Items.WOODEN_SWORD, Items.WOODEN_SPEAR);
        // 石制
        materialSwordToSpearMap.put(Items.STONE_SWORD, Items.STONE_SPEAR);
        // 铁制
        materialSwordToSpearMap.put(Items.IRON_SWORD, Items.IRON_SPEAR);
        // 金制
        materialSwordToSpearMap.put(Items.GOLDEN_SWORD, Items.GOLDEN_SPEAR);
        // 钻石
        materialSwordToSpearMap.put(Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR);
        // 下界合金
        materialSwordToSpearMap.put(Items.NETHERITE_SWORD, Items.NETHERITE_SPEAR);

        // 铜制（根据模组实际物品名调整）
        materialSwordToSpearMap.put(Items.COPPER_SWORD, Items.COPPER_SPEAR);
        // 如果使用原版铜锭但剑来自其他模组，例如：
        // put(Registry.ITEM.get(new Identifier("some_mod", "copper_sword")),
        //     Registry.ITEM.get(new Identifier("some_mod", "copper_spear")));
    }

    public void onReplaceSpearModel(Event<Identifier> eventIdentifier) {
        if (eventIdentifier.isCancelled() || eventIdentifier.context != null) return;
        if (replaceSpearModel.get()) {
            ItemStack origin = eventIdentifier.getArgs(0);
            if (materialSwordToSpearMap.containsKey(origin.getItem())
                    && VItem.getInstance().isSpear(origin)) {
                Item item = materialSwordToSpearMap.get(origin.getItem());
                if (item != null) {
                    eventIdentifier.context(item.getComponents().get(DataComponentTypes.ITEM_MODEL));
                }
            }
        }
    }

    public ItemStack getOriginalStack(ItemStack stack) {
        Item item = stack.getItem();
        Item trans = materialSwordToSpearMap.get(item);
        return trans == null ? ItemStack.EMPTY : new ItemStack(trans);
    }

    public KineticWeaponComponent getRealComponent(ItemStack stack) {
        Item item = stack.getItem();
        Item trans = materialSwordToSpearMap.get(item);
        if (trans != null && VItem.getInstance().isSpear(stack)) {
            return trans.getComponents().get(DataComponentTypes.KINETIC_WEAPON);
        }
        return stack.get(DataComponentTypes.KINETIC_WEAPON);
    }

    public void onPostTick(Event<ClientPlayerEntity> eventPostTick) {
        if (checkNull()) return;
        if (eventPostTick.context == mc.player && spearSpeedReset.get() && ViaFabricPlusHooks.isSupportDupRot()) {

            Vec3d look = null;
            if (isUsingSpear(mc.player)) {
                Entity targetEntity =
                        TargetSelector.INSTANCE.searchAttackEntity(10, true, pl -> pl instanceof PlayerEntity);
                if (targetEntity != null) {
                    look = targetEntity
                            .dimensions
                            .getBoxAt(PositionPredict.INSTANCE
                                    .spearPredictArgument
                                    .get()
                                    .predict(targetEntity))
                            .getCenter()
                            .subtract(mc.player.getEyePos());
                }
            }
            if (look == null) {
                look = mc.player.getRotationVector();
            }

            // reset speed and rotation
            LegacySnapRotManager.INSTANCE.snapAt(look, true);
        }
    }

    public void onUsePiercing(Event<PlayerActionC2SPacket> eventPiercing) {
        if (fixOldVersionPiercing.get()
                && ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 9)
                && eventPiercing.context.getAction().ordinal() == 7) {
            if (onPiercing(() -> eventPiercing.context.getSequence())) {
                eventPiercing.cancel();
            }
        }
    }

    public void onUsingStab(Event<HitResult> eventStab) {
        if (fixOldVersionPiercing.get()
                && ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 9)
                && VItem.getInstance().isSpear(mc.player.getStackInHand(Hand.MAIN_HAND))
                && !mc.interactionManager.isFlyingLocked()) {
            if (onPiercing(() -> 0)) {
                mc.player.swingHand(Hand.MAIN_HAND);
                eventStab.cancel();
            }
        }
    }

    public boolean onPiercing(IntSupplier seq) {
        if (VItem.getInstance().isSpear(mc.player.getStackInHand(Hand.MAIN_HAND))
                && ViaFabricPlusHooks.getInstance().isViaEnabled()) {
            if (SupportVersion.CURRENT.isHigherOrEqualTo(21, 6)) {
                var wrapper = ViaFabricPlusHooks.getInstance().createViaPacket();
                wrapper.writePacketType(ViaProtocols.V1_21_5_TO_1_21_6, PlayPackets.PLAYER_ACTION);
                wrapper.write("VAR_INT", 7);
                wrapper.write("LONG", 0L);
                wrapper.write("BYTE", (byte) 0);
                wrapper.write("VAR_INT", seq.getAsInt());
                wrapper.scheduleSendToServer(ViaProtocols.V1_21_6_TO_1_21_7, true);
            } else {
                // todo: need test
                PlayerActionC2SPacket actionPacket = new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN);
                ByteBuf buf = NetworkUtils.createBytebuf();
                Listener.getConnectionAccess().getOutboundState().codec().encode(buf, (Packet) actionPacket);
                int id = VarInts.read(buf);
                VarInts.read(buf);
                long pos = buf.readLong();
                short sh = buf.readUnsignedByte();
                int sequence = VarInts.read(buf);
                buf.release();
                buf = NetworkUtils.createBytebuf();
                try {
                    VarInts.write(buf, id);
                    VarInts.write(buf, 7);
                    buf.writeLong(pos);
                    buf.writeByte(sh);
                    VarInts.write(buf, sequence);
                    Listener.getConnectionAccess().sendByteBuf(buf.retain());
                } finally {
                    buf.release();
                }
            }
            return true;
        }
        return false;
    }
}
