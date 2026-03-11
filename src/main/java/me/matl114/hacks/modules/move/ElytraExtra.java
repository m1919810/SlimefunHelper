package me.matl114.hacks.modules.move;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.ItemStackAccess;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElytraExtra extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"elytra", "unbreakable-elytra", "enable"};

    public static final String[] MOVE_ELYTRA_CHECK_PREIOD = {"elytra", "unbreakable-elytra", "period"};

    public static final String[] MOVE_ELYTRA_DELAY = {"elytra", "unbreakable-elytra", "delay"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY = {"elytra", "armor-fly", "enable"};

    public static final String[] MOVE_ELYTRA_ARMOR_ARMOR_MODE = {"elytra", "armor-fly", "armor-mode"};

    public static final String[] MOVE_ELYTRA_MOTION_CONTROL = {"elytra", "simple-flight-control", "enable-motion"};

    public static final String[] MOVE_ELYTRA_HEIGHT_CONTROL = {"elytra", "simple-flight-control", "enable-height"};

    public static final String[] ELYTRA_CUSTOM_FIREWORKS = {"elytra", "custom-fireworks", "firework-item-id"};

    public static final String[] ELYTRA_FLIGHT_CONTROL = {"elytra", "flight-control", "enable"};

    public static final String[] ELYTRA_FLIGHT_CONTROL_FIREWORKS = {"elytra", "custom-fireworks", "enable-fireworks"};

    public ElytraExtra() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef enableUnbreakableElytra =
            flagBuilder(Configs.MOV_CONFIG, MOVE_UNBREAKABLE_ELYTRA).build();

    public final IntRef period = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_CHECK_PREIOD, IntRef.TYPE)
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef delay = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_DELAY, IntRef.TYPE)
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef armorFly =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY).build();

    public final EnumRef<Configs.AutoInvMode> armorMode = builder(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_ARMOR_MODE, Configs.AutoInvMode.class)
            .defaultValue(Configs.AutoInvMode.LAZY)
            .build();

    public final StringRef customFireworks = builder(Configs.MOV_CONFIG, ELYTRA_CUSTOM_FIREWORKS, StringRef.TYPE)
            .defaultValue("^(.*?_MULTI_TOOL|STAFF_ELEMENTAL_WIND)$")
            .validator(Configs.REGEX_VALIDATOR)
            .build();

    public final FlagRef useFireworks =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL_FIREWORKS).build();

    public final FlagRef simpleControlM =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_MOTION_CONTROL).build();

    public final FlagRef simpleControlH =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_HEIGHT_CONTROL).build();

    public final FlagRef controlE =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(Listener.getEntityTrackDataUpdate(), this::handleEntityDataUpdate);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onStartFallFlying);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onUseFireworks);
    }

    // TODO: fake elytra flight figure it out: NO USE, server player pose will not change
    // elytra unbreakable?

    // TODO: Elytra Mode: velocity control, fake creative flight, rewrite this elytra unbreakable
    // TODO: armor flight,
    private int fakeGlideTime = 0;
    private int fakeGlidePoseTime = 0;
    // todo: check unbreakable flag
    // todo: 鞘翅甲飞
    // todo: 动量控制
    public boolean shouldElytraUnbreakable() {
        return enableUnbreakableElytra.get()
                && mc.player != null
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).get(DataComponentTypes.UNBREAKABLE) == null;
    }

    public void runElytraUnbreakable(Event<Integer> tickEvent) {
        if (shouldElytraUnbreakable() && tickEvent.context() >= period.get()) {
            fakeGlideTime += 1;
            fakeGlidePoseTime += 1;
            if (mc.player != null && mc.player.isFallFlying()) {
                mc.getNetworkHandler()
                        .sendPacket(
                                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                //                Debug.info("send stop glide");
            }
            Tasks.scheduleDelayed(
                    () -> {
                        if (mc.player != null && mc.player.isFallFlying() && !mc.player.isOnGround()) {
                            mc.getNetworkHandler()
                                    .sendPacket(new ClientCommandC2SPacket(
                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            //                    Debug.info("send restart glide");
                        } else {
                            //                    Debug.info("not glide anymore");
                        }
                    },
                    delay.get());

            tickEvent.context(0);
        }
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    // may cause fake gliding !!! must be careful
    public void handleEntityDataUpdate(Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject) {
        if (serializedEntryMutableObject.isCancelled()) return;
        // only when elytra unbreakable do
        if (serializedEntryMutableObject.extraArgs().length > 0
                && serializedEntryMutableObject.extraArgs()[0] instanceof ClientPlayerEntity player
                && player == mc.player
                && player.isFallFlying()
                && canContinueGliding()) {
            if (shouldElytraUnbreakable()) {
                ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
                // do all the checks to avoid ghost gliding
                if (VItem.getInstance().canGlide(itemStack) && isUsable(itemStack)) {
                    var val = serializedEntryMutableObject.context();

                    if (val.id() == VDataFlag.ID_FLAGS) {
                        byte data = (byte) val.value();
                        if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                            //                        Debug.info("[data]stop gliding");
                            if (fakeGlideTime > 0) {
                                fakeGlideTime -= 1;
                                // cancel stop fallflying
                                // ;
                                serializedEntryMutableObject.context(
                                        new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                            }

                        } else {
                            //                        Debug.info("[data]start gliding");
                        }
                    } else if (val.id() == VDataFlag.ID_POSE) {
                        // standing pose
                        EntityPose pose = (EntityPose) val.value();
                        if (fakeGlidePoseTime > 0
                                && pose != EntityPose.FALL_FLYING
                                && player.getPose() == EntityPose.FALL_FLYING) {
                            // cancel pose sync
                            fakeGlidePoseTime -= 1;
                        }
                    }
                }
            } else if (armorFly.get()) {
                var val = serializedEntryMutableObject.context();
                if (val.id() == VDataFlag.ID_FLAGS) {
                    // This is a vanilla operation
                    byte data = (byte) val.value();
                    if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                        // try start
                        if (onSwitchItemFallFlying()) {
                            serializedEntryMutableObject.context(new DataTracker.SerializedEntry(
                                    val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                            mc.getNetworkHandler()
                                    .sendPacket(new ClientCommandC2SPacket(
                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            // todo: add configuration

                        }
                        // we delayed the packets here to ensure that rockets are usable

                        flushRockets();
                        // flush rocket before switch elytra
                        if (armorMode.get() == Configs.AutoInvMode.LAZY && this.thisTickSwitchingIndex != -1) {
                            switchSlotToArmor(this.thisTickSwitchingIndex);
                            this.thisTickSwitchingIndex = -1;
                        }
                    }
                } else if (val.id() == VDataFlag.ID_POSE) {
                    // this is a vanilla operation, we handle this to make fluent flying
                    if (thisFallFlyingIsArmorFly != -1
                            && val.value() instanceof EntityPose pos
                            && pos != EntityPose.FALL_FLYING) {
                        serializedEntryMutableObject.context(
                                new DataTracker.SerializedEntry(val.id(), val.handler(), EntityPose.FALL_FLYING));
                    }
                }
            }
        }
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    public int findElytra() {
        // only backpack can operate
        if (ClientPlayerAccess.of(mc.player).getServerScreenHandler() == mc.player.playerScreenHandler) {
            var slots = mc.player.currentScreenHandler.slots;
            for (var i = 0; i < slots.size(); i++) {
                var slot = slots.get(i);
                if (slot.inventory instanceof PlayerInventory pinv
                        && VItem.getInstance().canGlide(slot.getStack())
                        && mc.player.canEquip(slot.getStack(), EquipmentSlot.CHEST)
                        && !slot.getStack().willBreakNextUse()) {
                    return i;
                }
            }
        }
        return -1;
    }

    public Deque<IndexEntry<PlayerInteractItemC2SPacket>> delayQueue = new ConcurrentLinkedDeque<>();

    public boolean canBeUsedAsFireworks(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (stack.isOf(Items.FIREWORK_ROCKET)) {
            return true;
        } else {
            String regex = customFireworks.get();
            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            if (Pattern.matches(regex, id)) {
                return true;
            }
            String sfid = ItemStackUtils.getSfId(stack);

            return sfid != null && Pattern.matches(regex, sfid);
        }
    }
    // do not catch flushing packets
    boolean flushing = false;

    public void onUseFireworks(Event<PlayerInteractItemC2SPacket> packet) {
        if (armorFly.get() && !flushing && thisFallFlyingIsArmorFly != -1) {
            ItemStack stack = mc.player.getStackInHand(packet.context().getHand());
            Item item = ItemStackAccess.of(stack).getRealItem();
            if (item != null && item != Items.AIR) {

                ItemStack stackOrigin = stack;
                // make a stackCopy of origin item with 1 count
                stack = new ItemStack(item);
                stack.applyChanges(stackOrigin.components.getChanges());
                if (canBeUsedAsFireworks(stack)) {
                    // 40-> offhand
                    delayQueue.add(new IndexEntry<>(
                            packet.context().getHand() == Hand.MAIN_HAND
                                    ? mc.player.getInventory().getSelectedSlot()
                                    : 40,
                            packet.context()));
                    packet.cancel();
                }
            }
        }
    }

    public void sendCustomUseFireworkPacket(float pitch, float yaw) {
        if (armorFly.get() && thisFallFlyingIsArmorFly != -1) {
            delayQueue.add(new IndexEntry<>(-1, new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, -1, yaw, pitch)));
        } else {
            sendUsePacket(pitch, yaw);
        }
    }

    public void sendUsePacket(float pitch, float yaw) {
        ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (canBeUsedAsFireworks(stack)) {
            mc.interactionManager.sendSequencedPacket(
                    mc.world, s -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, s, yaw, pitch));
        } else {
            stack = mc.player.getStackInHand(Hand.OFF_HAND);
            if (canBeUsedAsFireworks(stack)) {
                mc.interactionManager.sendSequencedPacket(
                        mc.world, s -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, s, yaw, pitch));
            } else {
                int idx = -1;
                for (var i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
                    var slot = mc.player.currentScreenHandler.slots.get(i);
                    if (slot.inventory instanceof PlayerInventory && canBeUsedAsFireworks(slot.getStack())) {
                        idx = i;
                    }
                }
                if (idx == -1) {

                } else {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, idx, 40, SlotActionType.SWAP, mc.player);
                    // use it in offhand
                    mc.interactionManager.sendSequencedPacket(
                            mc.world, s -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, s, yaw, pitch));
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, idx, 40, SlotActionType.SWAP, mc.player);
                }
            }
        }
    }

    public void switchSlotToArmor(int idx) {
        int armorSlot = 6;
        int targetSlot = idx; // InvTasks.getScreenSlotByInventoryIndex(idx);
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (targetSlot >= 36 && targetSlot <= 45) {
            // use number operation
            int target = (targetSlot < 45) ? targetSlot - 36 : 40;
            mc.interactionManager.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    public boolean canContinueGliding() {
        return !mc.player.isTouchingWater()
                && !mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public void onStartFallFlying(Event<Boolean> booleanEvent) {
        // not fallFlying, and not suitable for gliding
        // check armor fly
        // reset fly transaction
        this.thisFallFlyingIsArmorFly = -1;
        if (!(Boolean) booleanEvent.getArgs(0)
                && !booleanEvent.isCancelled()
                && !booleanEvent.context()
                && armorFly.get()) {
            if (canContinueGliding()) {
                // check equipments
                if (onSwitchItemFallFlying()) {
                    booleanEvent.context(Boolean.TRUE);
                    shouldFlushRocketsThisTick = true;
                }
            }
        }
    }

    private boolean onSwitchItemFallFlying() {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!VItem.getInstance().canGlide(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                switchSlotToArmor(elytraIndex);
                thisTickSwitchingIndex = elytraIndex;
                thisFallFlyingIsArmorFly = thisTickSwitchingIndex;
                lastFlushRocketTick = 0;
                // mc.player.input.playerInput =
                // PlayerInputUtils.of(mc.player.input.playerInput).sprint(false).sneak(false).jump(true).forward(false).backward(false).right(false).left(false).toPlayerInput();
                return true;
            }
            return false;
        } else {
            thisTickSwitchingIndex = -1;
            return true;
        }
    }

    int thisTickSwitchingIndex = -1;
    int thisFallFlyingIsArmorFly = -1;

    @Override
    public void preTick(Event<LegalMovementManager> movementManagerEvent) {}

    boolean shouldFlushRocketsThisTick = false;
    boolean lastTickGliding = false;

    boolean setback = false;

    public void onSetBack(Event<PlayerPositionLookS2CPacket> setbackPacket) {
        setback = true;
    }

    public void flushRockets() {
        flushing = true;
        int selected = mc.player.getInventory().getSelectedSlot();
        try {
            while (!delayQueue.isEmpty()) {
                var packetEntry = delayQueue.poll();
                if (packetEntry.index() != -1) {
                    if (packetEntry.index() != 40) {
                        PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(packetEntry.index());
                    }
                    Listener.sendPacketNoEvents(packetEntry.val());
                } else {
                    sendCustomUseFireworkPacket(
                            packetEntry.val().getPitch(), packetEntry.val().getYaw());
                }
            }
        } finally {
            flushing = false;
            PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
        }
    }

    // ArmorFly works
    // tested in 3c3u.uno, 20260311
    // could not pass GrimAC in loyisa due to inventory packets disorders
    int lastFlushRocketTick = 0;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (armorFly.get() && player.isFallFlying()) {
            if (canContinueGliding()) {
                ++lastFlushRocketTick;
                // EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
                // no reset and find Elytra at equipmentSlot, maybe a desync in inventory
                if (lastFlushRocketTick > 10
                        && VItem.getInstance().canGlide(player.getEquippedStack(EquipmentSlot.CHEST))) {
                    thisFallFlyingIsArmorFly = -1;
                    // trigger flush rockets
                    shouldFlushRocketsThisTick = true;
                }
                if (armorMode.get() == Configs.AutoInvMode.TICK && this.thisTickSwitchingIndex == -1) {
                    if (!VItem.getInstance().canGlide(player.getEquippedStack(EquipmentSlot.CHEST))) {
                        int idx = findElytra();
                        if (idx != -1) {
                            switchSlotToArmor(idx);
                            thisTickSwitchingIndex = idx;
                        }
                    }
                }

            } else {
                EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                thisFallFlyingIsArmorFly = -1;
            }

        } else {
            thisFallFlyingIsArmorFly = -1;
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {

        this.lastTickGliding = mc.player.isFallFlying();

        if (this.thisTickSwitchingIndex != -1) {
            final int idx = this.thisTickSwitchingIndex;
            switchSlotToArmor(idx);
            // ACPostTasks.addPostTransactionAction((s)-> );
            Debug.info("Post", mc.player.getEquippedStack(EquipmentSlot.CHEST));
            if (canContinueGliding()) {
                // mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                // ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            } else {
                EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
            }
        }
        if (shouldFlushRocketsThisTick) {
            ACPostTasks.addPostTransactionAction((s) -> {
                flushRockets();
            });
            shouldFlushRocketsThisTick = false;
        }
        this.thisTickSwitchingIndex = -1;
        //        if(canContinueArmorGliding()){
        //            flushRockets();
        //        }
        return true;
    }
    // todo: Elytra Control
    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //        if(armorFly.get() && player.isFallFlying() && this.thisFallFlyingIsArmorFly != -1){
        //            player.input.playerInput =
        // PlayerInputUtils.of(player.input.playerInput).sprint(false).sneak(false).jump(false).forward(false).backward(false).right(false).left(false).toPlayerInput();
        //        }
    }

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent) {
        if (movementManagerEvent.isCancelled()) {
            return;
        }
        // movementManagerEvent.cancel();
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}
}
