package me.matl114.hacks.modules.move;

import java.util.Deque;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.FireworkRocketEntityAccess;
import me.matl114.accessors.access.ItemStackAccess;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ElytraExtra extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public static final String[] ELYTRA_NO_KINETIC = {"elytra", "elytra-tweaks", "no-kinetic"};

    public static final String[] ELYTRA_NO_KINETIC_MODE = {"elytra", "elytra-tweaks", "no-kinetic-mode"};

    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"elytra", "unbreakable-elytra", "enable"};

    public static final String[] MOVE_ELYTRA_CHECK_PREIOD = {"elytra", "unbreakable-elytra", "period"};

    public static final String[] MOVE_ELYTRA_DELAY = {"elytra", "unbreakable-elytra", "delay"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY = {"elytra", "armor-fly", "enable"};
    public static final String[] MOVE_ELYTRA_ARMOR_FLY_HOTKEY = {"elytra", "armor-fly", "enable-hotkey"};
    public static final String[] MOVE_ELYTRA_ARMOR_ARMOR_MODE = {"elytra", "armor-fly", "armor-mode"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY_NO_KICK = {"elytra", "armor-fly", "antikick"};

    public static final String[] ELYTRA_FIREWORKS_TICKS = {
        "elytra", "custom-fireworks", "firework-delay-multiply-vanilla"
    };

    public static final String[] ELYTRA_FIREWORKS_TICKS_CUSTOM = {
        "elytra", "custom-fireworks", "firework-delay-cooldown-custom"
    };

    public static final String[] ELYTRA_CUSTOM_FIREWORKS = {"elytra", "custom-fireworks", "firework-item-id"};

    public static final String[] AUTO_USE_FIREWORKS = {"elytra", "custom-fireworks", "firework-auto-use-vanilla"};

    public static final String[] FIREWORKS_BUFFER = {"elytra", "custom-fireworks", "firework-effect-remain-ticks"};

    // public static final String[] ELYTRA_FLIGHT_CONTROL_FIREWORKS = {"elytra", "custom-fireworks",
    // "enable-fireworks"};

    public ElytraExtra() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef noKinetic =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_NO_KINETIC).build();

    public final EnumRef<Configs.BypassMode> noKineticMode = builder(
                    Configs.MOV_CONFIG, ELYTRA_NO_KINETIC_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

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

    public final KeyBindRef keyBind = toggleHotkey(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY_HOTKEY, new MultiKeyBind(), MOVE_ELYTRA_ARMOR_FLY)
            .build();

    public final EnumRef<Configs.AutoInvMode> armorMode = builder(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_ARMOR_MODE, Configs.AutoInvMode.class)
            .defaultValue(Configs.AutoInvMode.LAZY)
            .build();

    public final FlagRef antiKick = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY_NO_KICK, Boolean.class)
            .defaultValue(true)
            .build();

    public final StringRef customFireworks = builder(Configs.MOV_CONFIG, ELYTRA_CUSTOM_FIREWORKS, StringRef.TYPE)
            .defaultValue("^(.*?_MULTI_TOOL|STAFF_ELEMENTAL_WIND)$")
            .validator(Configs.REGEX_VALIDATOR)
            .build();

    public final IntRef fireworkTicks = builder(Configs.MOV_CONFIG, ELYTRA_FIREWORKS_TICKS, IntRef.TYPE)
            .defaultValue(10)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef customFireworkTicks = builder(Configs.MOV_CONFIG, ELYTRA_FIREWORKS_TICKS_CUSTOM, IntRef.TYPE)
            .defaultValue(100)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FireworkTimer timerVanilla = new FireworkTimer(fireworkTicks);

    public final FireworkTimer timerCustom = new FireworkTimer(customFireworkTicks);

    public final FlagRef autoRocket =
            flagBuilder(Configs.MOV_CONFIG, AUTO_USE_FIREWORKS).build();

    public final IntRef rocketBuffer = builder(Configs.MOV_CONFIG, FIREWORKS_BUFFER, IntRef.TYPE)
            .defaultValue(5)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    // public final FlagRef useFireworks =
    //        flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL_FIREWORKS).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(Listener.getEntityTrackDataUpdate(), this::handleEntityDataUpdate);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onStartFallFlying);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onUseFireworks);
        registerListener(Listener.getEntityClientVelocityUpdate(), this::onPlayerVelocity);
        registerListener(Listener.getEntityTrackDataUpdate(), this::onFireworkOwner);
        registerListener(Listener.getEntityRemoveListener(), this::onFireworkRemove);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    // elytra unbreakable?

    private int fakeGlideTime = 0;
    private int fakeGlidePoseTime = 0;

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
            } else if (armorFly.get() && this.thisFallFlyingIsArmorFly != -1) {
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
            // check hotbar first
            for (var i = 0; i < 9; ++i) {
                var item = mc.player.getInventory().getStack(i);
                if (VItem.getInstance().canGlide(item)
                        && mc.player.getPreferredEquipmentSlot(item) == EquipmentSlot.CHEST
                        && ElytraItem.isUsable(item)) {
                    return i + 36;
                }
            }
            var slots = mc.player.playerScreenHandler.slots;
            for (var i = 0; i < slots.size(); i++) {
                var slot = slots.get(i);
                if (slot.inventory instanceof PlayerInventory pinv
                        && VItem.getInstance().canGlide(slot.getStack())
                        && mc.player.getPreferredEquipmentSlot(slot.getStack()) == EquipmentSlot.CHEST
                        && ElytraItem.isUsable(slot.getStack())) {
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
                            packet.context().getHand() == Hand.MAIN_HAND ? mc.player.getInventory().selectedSlot : 40,
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

    public ItemStack findRocket() {
        ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (canBeUsedAsFireworks(stack)) {
            return stack;
        } else {
            stack = mc.player.getStackInHand(Hand.OFF_HAND);
            if (canBeUsedAsFireworks(stack)) {
                return stack;
            } else {
                // check hotbars
                for (var i = 0; i < 9; ++i) {
                    if (canBeUsedAsFireworks(mc.player.getInventory().getStack(i))) {
                        return mc.player.getInventory().getStack(i);
                    }
                }
                for (var i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
                    var slot = mc.player.currentScreenHandler.slots.get(i);
                    if (slot.inventory instanceof PlayerInventory && canBeUsedAsFireworks(slot.getStack())) {
                        return slot.getStack();
                    }
                }
            }
            return null;
        }
    }

    public int getRocketLevel(ItemStack stack) {
        if (stack.isOf(Items.FIREWORK_ROCKET)) {
            FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
            if (component != null) {
                return 1 + component.flightDuration();
            }
        }
        return 1;
    }

    private void sendUsePacket(float pitch, float yaw) {
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
                // check hotbars
                int idx = -1;

                for (var i = 0; i < 9; ++i) {
                    if (canBeUsedAsFireworks(mc.player.getInventory().getStack(i))) {
                        idx = i;
                        break;
                    }
                }
                if (idx != -1) {
                    int selected = mc.player.getInventory().selectedSlot;
                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(idx);
                    mc.interactionManager.sendSequencedPacket(
                            mc.world, s -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, s, yaw, pitch));
                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
                    return;
                }
                for (var i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
                    var slot = mc.player.currentScreenHandler.slots.get(i);
                    if (slot.inventory instanceof PlayerInventory && canBeUsedAsFireworks(slot.getStack())) {
                        idx = i;
                        break;
                    }
                }
                if (idx != -1) {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, idx, 40, SlotActionType.SWAP, mc.player);
                    // use it in offhand
                    mc.interactionManager.sendSequencedPacket(
                            mc.world, s -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, s, yaw, pitch));
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, idx, 40, SlotActionType.SWAP, mc.player);
                    return;
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
        int selected = mc.player.getInventory().selectedSlot;
        try {
            while (!delayQueue.isEmpty()) {
                var packetEntry = delayQueue.poll();
                if (packetEntry.index() != -1) {
                    if (packetEntry.index() != 40) {
                        PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(packetEntry.index());
                    }
                    Listener.sendPacketNoEvents(packetEntry.val());
                } else {
                    sendUsePacket(
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
    // tested in mc.loyisa.cn 1.21.1 20260311
    // could not pass GrimAC > 1.21.2 in loyisa due to inventory packets disorders and player input packet check
    int lastFlushRocketTick = 0;

    public void onPlayerVelocity(Event<Vec3d> fireworkEvent) {}

    public boolean shouldExcuteAntiKick() {
        if (armorFly.get()
                && mc.player != null
                && mc.player.isFallFlying()
                && thisFallFlyingIsArmorFly != -1
                && antiKick.get()) {
            switch (armorMode.get()) {
                case LAZY -> {
                    return !VItem.getInstance().canGlide(mc.player.getEquippedStack(EquipmentSlot.CHEST));
                }
                case TICK -> {
                    return false;
                }
            }
        }
        return false;
    }

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
                    // thisFallFlyingIsArmorFly = -1;
                    // thisTickSwitchingIndex = -1;
                    // trigger flush rockets
                    shouldFlushRocketsThisTick = true;
                }
                if (armorMode.get() == Configs.AutoInvMode.TICK && this.thisTickSwitchingIndex == -1) {
                    if (!VItem.getInstance().canGlide(player.getEquippedStack(EquipmentSlot.CHEST))) {
                        int idx = findElytra();
                        if (idx != -1) {
                            switchSlotToArmor(idx);
                            this.thisTickSwitchingIndex = idx;
                            this.thisFallFlyingIsArmorFly = this.thisTickSwitchingIndex;
                        }
                    } else {
                        // switch to origin armor
                        this.thisTickSwitchingIndex = thisFallFlyingIsArmorFly;
                    }
                }

            } else {
                // EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                // let the server sync our gliding state
                thisFallFlyingIsArmorFly = -1;
            }

        } else {
            thisFallFlyingIsArmorFly = -1;
        }

        // simple control

    }

    EntityDimensions pose = null;
    boolean restoreRotThisTick = false;
    int triggerKinetic = 0;
    boolean executeNoKineticAfterTravel = false;

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (movementManagerEvent.isCancelled()) {
            return;
        }

        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;

        if (thisFallFlyingIsArmorFly != -1) {
            // fix boundingbox error
            pose = player.dimensions;
            player.dimensions = player.getDimensions(EntityPose.STANDING);
            player.setBoundingBox(player.dimensions.getBoxAt(player.getPos()));
        }

        executeNoKineticAfterTravel = false;
        if (noKinetic.get() && thisFallFlyingIsArmorFly == -1 && player.isFallFlying()) {
            boolean canControl = lastFireworkRocket != null && lastFireworkRocket.isAlive();
            if (noKineticMode.get().hasAc()) {
                // control by rotation and velocity
                // simulation
                Vec3d vec3d = mc.player.getVelocity().multiply(4);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (vec3d.horizontalLength() > 0.3 && !MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    restoreRotThisTick = true;
                    triggerKinetic = 2;
                    // it can work, don't move it
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    } else {
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    }
                } else if (triggerKinetic > 0) {
                    triggerKinetic--;
                    restoreRotThisTick = true;
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    } else {
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    }
                }
            } else {
                executeNoKineticAfterTravel = true;
            }
        }
        // movementManagerEvent.cancel();
    }

    @Override
    public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (pose != null) {
            player.dimensions = pose;
        }
        pose = null;
        if (executeNoKineticAfterTravel) {

            //                Vec3d simu = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
            //                Vec3d predictedPos = mc.player.getPos().add(simu);
            boolean controlled = false;
            boolean canControl = lastFireworkRocket != null && lastFireworkRocket.isAlive();
            if (true) {
                // use firework to control server motion
                Vec3d vec3d = mc.player.getRotationVector().multiply(0.85 * 6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 1;
                    restoreRotThisTick = true;
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }
            if (!controlled && !canControl) {
                Vec3d vec3d = mc.player.getVelocity().multiply(6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 2;
                    restoreRotThisTick = true;
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }

            if (!controlled && triggerKinetic > 0) {
                triggerKinetic -= 1;
                restoreRotThisTick = true;
                // EntityUtils.setEntityPitchSafe(player,-player.getPitch());
                if (canControl) {
                    EntityUtils.setEntityPitchSafe(player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                } else {
                    EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                }
                controlled = true;
            }
        }
    }

    FireworkRocketEntity lastFireworkRocket;
    int lastFireworkRocketTick = 0;
    int lastFireworkThresholdTime = 0;
    boolean lastFireworkIsDeadSignal = false;

    public void onFireworkOwner(Event<DataTracker.SerializedEntry<?>> firework) {
        if (firework.context().id() == VDataFlag.ID_FIREWORK_SHOOTER_ID
                && firework.getArgs(0) instanceof FireworkRocketEntity fireworkEntity
                && mc.player != null
                && mc.player.isFallFlying()
                && firework.context().value() instanceof OptionalInt opint
                && opint.isPresent()
                && opint.getAsInt() == mc.player.getId()) {
            lastFireworkRocket = fireworkEntity;
            lastFireworkRocketTick = Tasks.getTick();
            lastFireworkIsDeadSignal = false;
            lastFireworkThresholdTime = 0;
        }
    }

    public void onFireworkRemove(Event<Entity> entityRemoveEvent) {
        if (entityRemoveEvent.context() instanceof FireworkRocketEntity fire && fire == lastFireworkRocket) {
            onRemoveFirework(lastFireworkRocket);
        }
    }

    public void onWorldSwitch(Event<World> event) {
        if (lastFireworkRocket != null && lastFireworkRocket.isAlive()) {
            onRemoveFirework(lastFireworkRocket);
        }
        lastFireworkRocket = null;
    }

    private void onRemoveFirework(FireworkRocketEntity rocket) {
        lastFireworkThresholdTime = FireworkRocketEntityAccess.of(rocket).getLiveTicks();
        lastFireworkIsDeadSignal = true;
        if (autoRocket.get()) {
            // mark next time must be auto, pass timer check
            timerVanilla.markOff();
        }
    }

    public boolean canFireworkControlMotion() {
        if (lastFireworkRocket != null) {
            if (lastFireworkRocket.isAlive()) {
                return true;
            } else if (Tasks.getTick() < lastFireworkRocketTick + lastFireworkThresholdTime + rocketBuffer.get()) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    int cnt = 0;

    public void launchFirework(float pitch, float yaw) {
        boolean autoFirework = autoRocket.get() && lastFireworkRocket != null;
        boolean emergency = autoFirework && lastFireworkIsDeadSignal;
        var rocket = findRocket();
        if (rocket != null) {
            boolean isVanilla = rocket.isOf(Items.FIREWORK_ROCKET);
            int level = getRocketLevel(rocket);
            FireworkTimer timer = isVanilla ? timerVanilla : timerCustom;
            boolean use = false;
            if (autoFirework) {
                if (lastFireworkRocket.isAlive()) {
                    return;
                } else if (emergency) {
                    // time limit, do not double
                    if (Tasks.getTick() < lastFireworkRocketTick + lastFireworkThresholdTime + rocketBuffer.get()) {
                        use = true;
                    }
                    lastFireworkIsDeadSignal = false;
                    // use = true;
                } else {
                    // already use, but server havn't sent our rocket
                    // check buffer time, if can not control, use timer to restart the control
                    if (canFireworkControlMotion()) {
                        return;
                    }
                }
            }
            // timer use
            if (!use && timer.tryFire(level)) {
                use = true;
            }
            if (use) {
                // reset the tick even if is from vanilla operation
                timer.fire(level);
                ACPostTasks.addPostTransactionAction((s) -> {
                    sendCustomUseFireworkPacket(pitch, yaw);
                });
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {

        this.lastTickGliding = mc.player.isFallFlying();

        if (this.thisTickSwitchingIndex != -1) {
            final int idx = this.thisTickSwitchingIndex;
            switchSlotToArmor(idx);
            // ACPostTasks.addPostTransactionAction((s)-> );
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
        if (restoreRotThisTick) {
            restoreRotThisTick = false;
            movementManagerEvent.context.playerStatus.restoreRotation();
        }
        return true;
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //        if(armorFly.get() && player.isFallFlying() && this.thisFallFlyingIsArmorFly != -1){
        //            player.input.playerInput =
        // PlayerInputUtils.of(player.input.playerInput).sprint(false).sneak(false).jump(false).forward(false).backward(false).right(false).left(false).toPlayerInput();
        //        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (true && player.isFallFlying()) {
            player.horizontalCollision = false;
        }
    }

    public static enum MotionMode implements ConfigEnum {
        VOID,
        FIRE_WORKS;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.motion-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public static class FireworkTimer {
        int lastTimeFire = 0;
        IntRef fireTicks;
        boolean lastTimeWasAuto = false;

        public FireworkTimer(IntRef fireTicks) {
            this.fireTicks = fireTicks;
        }

        public boolean tryFire(int level) {
            if (lastTimeWasAuto) {
                return true;
            }
            if (lastTimeFire + fireTicks.get() * level < Tasks.getTick()) {
                return true;
            } else {
                return false;
            }
        }

        public void markOff() {
            lastTimeWasAuto = true;
        }

        public void fire(int level) {
            lastTimeWasAuto = false;
            lastTimeFire = Tasks.getTick();
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        switch (presetEvent.context.getValue()) {
            case HACKING, VANILLA, AC_COMMON, AC_VULCAN, AC_GRIM -> {
                armorMode.set(Configs.AutoInvMode.LAZY);
            }
            case AC_MATRIX -> {
                armorMode.set(Configs.AutoInvMode.TICK);
            }
        }
    }
}
