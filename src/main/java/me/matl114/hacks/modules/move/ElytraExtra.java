package me.matl114.hacks.modules.move;

import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.FireworkRocketEntityAccess;
import me.matl114.accessors.access.ItemStackAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.ACTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ElytraExtra extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public static final String[] ELYTRA_NO_KINETIC = {"elytra", "elytra-tweaks", "no-kinetic"};

    public static final String[] ELYTRA_NO_KINETIC_MODE = {"elytra", "elytra-tweaks", "no-kinetic-mode"};

    public static final String[] ELYTRA_MACE_FIX = {"elytra", "elytra-tweaks", "mace-hit-fix"};

    public static final String[] ELYTRA_MACE_FIX_MODE = {"elytra", "elytra-tweaks", "mace-hit-fix-mode"};

    public static final String[] ELYTRA_LOCK_ROTATION = {"elytra", "elytra-tweaks", "lock-rotation-hotkey"};

    public static final String[] ELYTRA_AUTO_SWITCH = {"elytra", "elytra-tweaks", "auto-switch"};

    public static final String[] ELYTRA_LIQUID_FLY_FIX = {"elytra", "elytra-tweaks", "liquid-fallflying-fix"};

    public static final String[] ELYTRA_NO_FALL_WHEN_CONTROL = {"elytra", "elytra-tweaks", "no-fall-when-landing"};

    // public static final String[] ELYTRA_ANTI_KB = {"elytra", "elytra-tweaks", "elytra-anti-kb"};

    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"elytra", "unbreakable-elytra", "enable"};

    public static final String[] MOVE_ELYTRA_CHECK_PREIOD = {"elytra", "unbreakable-elytra", "period"};

    public static final String[] MOVE_ELYTRA_DELAY = {"elytra", "unbreakable-elytra", "delay"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY = {"elytra", "armor-fly", "enable"};
    public static final String[] MOVE_ELYTRA_ARMOR_FLY_HOTKEY = {"elytra", "armor-fly", "enable-hotkey"};
    public static final String[] MOVE_ELYTRA_ARMOR_ARMOR_MODE = {"elytra", "armor-fly", "armor-mode"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY_NO_KICK = {"elytra", "armor-fly", "antikick"};

    public static final String[] MOVE_ELYTRA_ARMOR_FLY_POSE_FIX = {"elytra", "armor-fly", "pose-fix"};

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

    public final FlagRef fuckGrimAC = MovTasks.getMovExtra().fuckGrimAC;

    public final FlagRef noKinetic =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_NO_KINETIC).build();

    public final EnumRef<Configs.BypassMode> noKineticMode = builder(
                    Configs.MOV_CONFIG, ELYTRA_NO_KINETIC_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final FlagRef maceFix =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_MACE_FIX).build();

    public final EnumRef<Configs.BypassMode> maceFixMode = builder(
                    Configs.MOV_CONFIG, ELYTRA_MACE_FIX_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final KeyBindRef lockRot = hotkey(Configs.MOV_CONFIG, ELYTRA_LOCK_ROTATION, new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleLockRot))
            .build();

    public final FlagRef liquidFix =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_LIQUID_FLY_FIX).build();

    public final FlagRef noFallLanding =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_NO_FALL_WHEN_CONTROL).build();

    public final FlagRef autoSwitch =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_AUTO_SWITCH).build();

    // public final FlagRef elytraAntiKB = flagBuilder(Configs.MOV_CONFIG, ELYTRA_ANTI_KB).build();

    public final FlagRef enableUnbreakableElytra =
            flagBuilder(Configs.MOV_CONFIG, MOVE_UNBREAKABLE_ELYTRA).build();

    public final IntRef period = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_CHECK_PREIOD, IntRef.TYPE)
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef armorFly =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY).build();

    public final KeyBindRef keyBind = toggleHotkey(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY_HOTKEY, new MultiKeyBind(), MOVE_ELYTRA_ARMOR_FLY)
            .build();

    public final EnumRef<ArmorFlyMode> armorMode = builder(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_ARMOR_MODE, ArmorFlyMode.class)
            .defaultValue(ArmorFlyMode.LAZY)
            .build();

    public final FlagRef antiKick = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY_NO_KICK, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef poseFix =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_ARMOR_FLY_POSE_FIX).build();

    public final NBTRef<Regex> customFireworks = builder(Configs.MOV_CONFIG, ELYTRA_CUSTOM_FIREWORKS, Regex.class)
            .defaultValue(new Regex("^(.*?_MULTI_TOOL|STAFF_ELEMENTAL_WIND)$"))
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
            .defaultValue(7)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef rocketBoost = flagBuilder(
                    Configs.MOV_CONFIG, makePath("elytra.custom-fireworks.firework-boost-enable"))
            .build();

    public final DoubleRef rocketBoostSpeed = builder(
                    Configs.MOV_CONFIG, makePath("elytra.custom-fireworks.firework-boost-speed"), DoubleRef.TYPE)
            .defaultValue(1.7D)
            .validator(Configs.doubleRange(0.0d, 10000.0D))
            .build();

    // public final FlagRef useFireworks =
    //        flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL_FIREWORKS).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityClientVelocityUpdate().getChannel(EntityType.PLAYER), this::onElytraKB);
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.PLAYER), this::handleEntityDataUpdate);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onStartFallFlying);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onUseFireworks);
        registerListener(
                Listener.getEntityClientVelocityUpdate().getChannel(EntityType.PLAYER), this::onPlayerVelocity);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkOwner);
        registerListener(Listener.getEntityRemoveListener(), this::onFireworkRemove);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::handleMaceAttack);
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::attackPost);
    }

    //    public void onHit(Event<WorldEventS2CPacket> event){
    //        if(event.context().getData())
    //    }

    public void onElytraKB(Event<Vec3d> velocity) {
        if (false && velocity.getArgs(0) == mc.player && mc.player != null && mc.player.isFallFlying()) {
            Vec3d vec3d = velocity.context.normalize();
            // hit with wrong kb
            if (vec3d.y < -0.9) {
                velocity.context(new Vec3d(velocity.context.x, 0, velocity.context.z));
                //                Debug.chat(vec3d);
                //                var re = Math.abs( vec3d.dotProduct(mc.player.getVelocity().normalize()));
                //                Debug.chat(re);
                //                if(re < 0.8){
                //                    velocity.cancel();
                //                }
            }
        }
    }

    public Vec3d lockRotation = null;

    public void toggleLockRot() {
        if (mc.player == null) return;
        if (mc.player.isFallFlying()) {
            if (lockRotation != null) {
                lockRotation = null;
                Debug.chat("[Elytra] 已取消视角锁定");
            } else {
                lockRotation = mc.player.getRotationVector();
                Debug.chat("[Elytra] 已锁定当前视角");
                ClientPlayerAccess.of(mc.player)
                        .getLegalMovementManager()
                        .addMovementModifier(new LegalMovementManager.MovementModifier() {
                            @Override
                            public int priority() {
                                return PRIORITY_LOW;
                            }

                            @Override
                            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                                if (!movementManagerEvent.context.hasImportantRotation() && lockRotation != null) {
                                    EntityUtils.setEntityRotationSafe(player, lockRotation);
                                }
                            }

                            @Override
                            public boolean postModify(
                                    Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                                movementManagerEvent.context.playerStatus.restoreRotation();
                                return lockRotation != null
                                        && movementManagerEvent.context.playerStatus.entity.isFallFlying();
                            }
                        });
            }
        } else {
            Debug.chat("[Elytra] 你不在滑翔");
        }
    }
    // elytra unbreakable?

    private boolean nextTimeLaunchElytraUnbreakable = false;
    public int elytraUnbreakableSwitchSlot = -1;

    public boolean shouldElytraUnbreakable() {
        return !(armorFly.get() && thisFallFlyingIsArmorFly != -1)
                && enableUnbreakableElytra.get()
                && mc.player != null
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).get(DataComponentTypes.UNBREAKABLE) == null;
    }

    public int findElytraUnbreakableSwitchSlot() {
        if (this.thisFallFlyingIsAutoSwitch != -1) {
            return this.thisFallFlyingIsAutoSwitch;
        } else {
            return findEmptyPlaceForElytra();
        }
    }

    public void runElytraUnbreakable(Event<Integer> tickEvent) {
        if (shouldElytraUnbreakable()
                && tickEvent.context() >= period.get()
                && canContinueGliding()
                && mc.player != null
                && mc.player.isFallFlying()) {
            elytraUnbreakableSwitchSlot = findElytraUnbreakableSwitchSlot();
            if (elytraUnbreakableSwitchSlot == -1) {

                mc.getNetworkHandler()
                        .sendPacket(
                                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                //                Debug.info("send stop glide");
                nextTimeLaunchElytraUnbreakable = true;
                tickEvent.context(0);
            } else {
                switchSlotToArmor(elytraUnbreakableSwitchSlot);
                nextTimeLaunchElytraUnbreakable = true;
                tickEvent.context(0);
            }
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
                && (serializedEntryMutableObject.context.id() == VDataFlag.ID_FLAGS
                        || serializedEntryMutableObject.context.id() == VDataFlag.ID_POSE)
                && player.isFallFlying()) {
            if (canContinueGliding()) {
                if (nextPacketResetFallFlying) {
                    var val = serializedEntryMutableObject.context();
                    if (val.id() == VDataFlag.ID_FLAGS) {
                        nextPacketResetFallFlying = false;
                        byte data = (byte) val.value();
                        if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                            // try start
                            if (canContinueGliding() && hasGlidingEquipments()) {
                                serializedEntryMutableObject.context(
                                        new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                            }
                        }
                    }
                }
                // handle elytra unbreakable actions
                if (shouldElytraUnbreakable()) {
                    var val = serializedEntryMutableObject.context();

                    if (val.id() == VDataFlag.ID_FLAGS) {
                        byte data = (byte) val.value();
                        if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                            //                        Debug.info("[data]stop gliding");
                            if (nextTimeLaunchElytraUnbreakable) {
                                nextTimeLaunchElytraUnbreakable = false;
                                if (elytraUnbreakableSwitchSlot != -1) {
                                    switchSlotToArmor(elytraUnbreakableSwitchSlot);
                                }
                                // cancel stop fallflying only when can continue
                                if (canContinueGliding()) {
                                    serializedEntryMutableObject.context(
                                            new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                    (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                                    mc.getNetworkHandler()
                                            .sendPacket(new ClientCommandC2SPacket(
                                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                    thisTickHasStartFallFly = true;
                                    if (elytraUnbreakableSwitchSlot != -1) {
                                        flushRockets();
                                    }
                                    MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                                } else {
                                    clearRockets();
                                }

                                elytraUnbreakableSwitchSlot = -1;
                            }
                        }
                    } else if (val.id() == VDataFlag.ID_POSE) {
                        // standing pose
                        if (val.value() instanceof EntityPose pos && pos != EntityPose.FALL_FLYING) {
                            serializedEntryMutableObject.context(
                                    new DataTracker.SerializedEntry(val.id(), val.handler(), EntityPose.FALL_FLYING));
                        }
                    }
                    // if not unbreakable run, armorFly runs
                } else if (armorFly.get() && this.thisFallFlyingIsArmorFly != -1) {
                    var val = serializedEntryMutableObject.context();
                    if (val.id() == VDataFlag.ID_FLAGS) {
                        // This is a vanilla operation
                        byte data = (byte) val.value();
                        if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                            // try start
                            if (disableNextArmorFlyLazyElytraTransaction > 0) {
                                --disableNextArmorFlyLazyElytraTransaction;
                                return;
                            }
                            if (armorMode.get() == ArmorFlyMode.LAZY) {
                                if (onSwitchItemArmorFallFlying()) {
                                    serializedEntryMutableObject.context(
                                            new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                    (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                                    mc.getNetworkHandler()
                                            .sendPacket(new ClientCommandC2SPacket(
                                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                    thisTickHasStartFallFly = true;
                                    // we delayed the packets here to ensure that rockets are usable
                                    // these rockets may not work,
                                    flushRockets();
                                } else {
                                    clearRockets();
                                }

                                if (
                                // armorMode.get() == Configs.AutoInvMode.LAZY &&
                                this.thisTickSwitchingIndex != -1) {
                                    switchSlotToArmor(this.thisTickSwitchingIndex);
                                    this.thisTickSwitchingIndex = -1;
                                }
                                MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();

                            } else if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
                                if (onSwitchItemArmorFallFlying()) {
                                    serializedEntryMutableObject.context(
                                            new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                    (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                                    mc.getNetworkHandler()
                                            .sendPacket(new ClientCommandC2SPacket(
                                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                    thisTickHasStartFallFly = true;
                                    flushRockets();
                                } else {
                                    clearRockets();
                                }
                                // we delayed the packets here to ensure that rockets are usable

                                MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                            } else {
                                thisTickTickStartFallFly = true;
                                serializedEntryMutableObject.context(
                                        new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)
                                                (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                            }
                        }
                    } else if (val.id() == VDataFlag.ID_POSE) {
                        // this is a vanilla operation, we handle this to make fluent flying
                        if (thisFallFlyingIsArmorFly != -1
                                && val.value() instanceof EntityPose pos
                                && pos != EntityPose.FALL_FLYING
                                && !poseFix.get()) {
                            serializedEntryMutableObject.context(
                                    new DataTracker.SerializedEntry(val.id(), val.handler(), EntityPose.FALL_FLYING));
                        }
                    }
                }
            }
            {
                // read only tasks
                // handle these tasks after the auto handle above
                var val = serializedEntryMutableObject.context();
                if (val.id() == VDataFlag.ID_FLAGS) {
                    // handle switch armor when end fallflying
                    if (thisFallFlyingIsAutoSwitch != -1) {
                        // update
                        byte data = (byte) val.value();
                        if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                            switchSlotToArmor(thisFallFlyingIsAutoSwitch);
                            thisFallFlyingIsAutoSwitch = -1;
                        }
                    }
                }
            }
        }
    }

    PlayerInteractEntityC2SPacket lastHandledPacket;
    public int disableNextArmorFlyLazyElytraTransaction = 0;

    public boolean shouldUseDelayMovementAttackMaceFix() {
        if (maceFixMode.get() == Configs.BypassMode.BYPASS_GRIM) {
            if (thisFallFlyingIsArmorFly != -1) {
                return armorMode.get() == ArmorFlyMode.LAZY;
            } else return true;
        }
        return false;
    }

    public void handleMaceAttack(Event<PlayerInteractEntityC2SPacket> interactPacket) {
        if (interactPacket.isCancelled()) return;
        PlayerInteractEntityC2SPacket packet = interactPacket.context();
        if (maceFix.get()
                && ((Enum) packet.type.getType()).name().equals("ATTACK")
                && mc.player.getMainHandStack().getItem() instanceof MaceItem mace
                && mc.player.isFallFlying()
                && !shouldUseDelayMovementAttackMaceFix()) {
            // try stop
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            // current not armor flying
            if (thisFallFlyingIsArmorFly == -1) {
                // not armor fly, do reset fly
                lastHandledPacket = packet;
            }
        }
    }

    boolean nextPacketResetFallFlying = false;

    public void attackPost(Event<PlayerInteractEntityC2SPacket> packet) {
        if (packet.context() == lastHandledPacket) {
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            nextPacketResetFallFlying = true;
        }
        lastHandledPacket = null;
    }

    @Override
    public int priority() {
        return PRIORITY_COMMON;
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
                        && mc.player.canEquip(item, EquipmentSlot.CHEST)
                        && !item.willBreakNextUse()) {
                    return i + 36;
                }
            }
            var slots = mc.player.playerScreenHandler.slots;
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

    public int findEmptyPlaceForElytra() {
        if (ClientPlayerAccess.of(mc.player).getServerScreenHandler() == mc.player.playerScreenHandler) {
            // check hotbar first
            for (var i = 0; i < 9; ++i) {
                var item = mc.player.getInventory().getStack(i);
                if (item.isEmpty()
                        || (!VItem.getInstance().canGlide(item) && mc.player.canEquip(item, EquipmentSlot.CHEST))) {
                    return i + 36;
                }
            }
            var slots = mc.player.playerScreenHandler.slots;
            for (var i = 0; i < slots.size(); i++) {
                var slot = slots.get(i);
                if (slot.inventory instanceof PlayerInventory pinv
                        && (slot.getStack().isEmpty()
                                || (!VItem.getInstance().canGlide(slot.getStack())
                                        && mc.player.canEquip(slot.getStack(), EquipmentSlot.CHEST)))) {
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
            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            if (customFireworks.get().test(id)) {
                return true;
            }
            String sfid = ItemStackUtils.getSfId(stack);

            return sfid != null && customFireworks.get().test(sfid);
        }
    }
    // do not catch flushing packets
    boolean flushing = false;
    // todo rewrite this shit
    public void onUseFireworks(Event<PlayerInteractItemC2SPacket> packet) {
        if (armorFly.get() && !flushing && (thisFallFlyingIsArmorFly != -1 || elytraUnbreakableSwitchSlot != -1)) {
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
        if (thisFallFlyingIsArmorFly != -1 || elytraUnbreakableSwitchSlot != -1) {
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
                    int selected = mc.player.getInventory().getSelectedSlot();
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
        if (mc.player.playerScreenHandler == ClientPlayerAccess.of(mc.player).getServerScreenHandler()) {
            MovTasks.getMovExtra().sendPacketsForInventoryAction();
            int armorSlot = 6;
            int targetSlot = idx; // InvTasks.getScreenSlotByInventoryIndex(idx);
            ScreenHandler handler = mc.player.playerScreenHandler;
            if (targetSlot >= 36 && targetSlot <= 45) {
                // use number operation
                int target = (targetSlot < 45) ? targetSlot - 36 : 40;
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
            } else {
                // fuck, do not kick me.

                // swap target to hotbar, hotbar to target
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, 40, SlotActionType.SWAP, mc.player);
                // swap hotbar to armor, armor to hotbar
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, 40, SlotActionType.SWAP, mc.player);
                // swap the rest
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, 40, SlotActionType.SWAP, mc.player);
            }
        }
    }

    public boolean checkLiquid() {
        if (liquidFix.get()) {
            return !mc.player.isInLava() && !mc.player.isTouchingWater();
        } else {
            return !mc.player.isTouchingWater();
        }
    }

    public boolean canContinueGliding() {
        return checkLiquid()
                && !mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public boolean hasGlidingEquipments() {
        Iterator var1 = EquipmentSlot.VALUES.iterator();

        EquipmentSlot equipmentSlot;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            equipmentSlot = (EquipmentSlot) var1.next();
        } while (!mc.player.canGlideWith(mc.player.getEquippedStack(equipmentSlot), equipmentSlot));
        return true;
    }

    public void onStartFallFlying(Event<Boolean> booleanEvent) {
        // not fallFlying, and not suitable for gliding
        // check armor fly
        // reset fly transaction
        // this is a check during the flying

        if ((Boolean) booleanEvent.getArgs(0) || booleanEvent.isCancelled()) {
            return;
        }
        this.thisFallFlyingIsArmorFly = -1;

        // reset armor fly status
        if (!booleanEvent.context()) {
            if (armorFly.get()) {
                if (canContinueGliding()) {
                    // check equipments
                    if (onSwitchItemArmorFallFlying()) {
                        booleanEvent.context(Boolean.TRUE);
                        shouldFlushRocketsThisTick = true;
                    }
                }
            } else if (autoSwitch.get()) {
                // auto switch if not armorFly;
                if (canContinueGliding()) {
                    if (onAutoSwitchItemFallFlying(true)) {
                        booleanEvent.context(Boolean.TRUE);
                    }
                }
            }
        }
    }

    public boolean onSwitchItemArmorFallFlying() {
        if (!canContinueGliding()) return false;
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
                thisTickHasStartFallFly = true;
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

    public boolean onAutoSwitchItemFallFlying(boolean stopSprint) {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!VItem.getInstance().canGlide(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                thisFallFlyingIsAutoSwitch = elytraIndex;
                switchSlotToArmor(elytraIndex);
                thisTickHasStartFallFly = true;
                return true;
            }
            return false;
        } else {
            thisFallFlyingIsAutoSwitch = -1;
            return true;
        }
    }

    public int thisTickSwitchingIndex = -1;
    public int thisFallFlyingIsArmorFly = -1;
    public int thisFallFlyingIsAutoSwitch = -1;
    public boolean thisTickTickStartFallFly = false;

    boolean thisTickHasStartFallFly = false;

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
                    sendUsePacket(
                            packetEntry.val().getPitch(), packetEntry.val().getYaw());
                }
            }
        } finally {
            flushing = false;
            PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
        }
    }

    public void clearRockets() {
        delayQueue.clear();
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
                default -> {
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
                    // thisTickSwitchingIndex = -1;
                    // trigger flush rockets
                    shouldFlushRocketsThisTick = true;
                }
                if (armorMode.get() != ArmorFlyMode.LAZY && this.thisTickSwitchingIndex == -1) {
                    if (thisTickTickStartFallFly) {
                        thisTickTickStartFallFly = false;
                        if (onSwitchItemArmorFallFlying()) {

                            mc.getNetworkHandler()
                                    .sendPacket(new ClientCommandC2SPacket(
                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            thisTickHasStartFallFly = true;
                            flushRockets();
                        } else {
                            clearRockets();
                        }
                        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                    } else if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
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
                }

            } else {
                // EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                // let the server sync our gliding state
                thisFallFlyingIsArmorFly = -1;
            }

        } else {
            thisFallFlyingIsArmorFly = -1;
        }
        if (thisFallFlyingIsAutoSwitch != -1) {
            if (!player.isFallFlying()) {
                switchSlotToArmor(thisFallFlyingIsAutoSwitch);
                thisFallFlyingIsAutoSwitch = -1;
            }
        }
        if (player.isFallFlying() && rocketBoost.get() && canFireworkControlMotion()) {
            // Vec3d vec3d = player.getVelocity();
            player.setVelocity(player.getRotationVector().multiply(rocketBoostSpeed.get()));
        }
        // slow falling with no crash
        if (false && player.isFallFlying()) {
            if (!movementManagerEvent.context().hasImportantRotation()) {
                restoreRotThisTick = true;
                player.setPitch(0.0F);
                if (Tasks.getTick() % 2 == 0) {
                    EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                }
                movementManagerEvent.context.pushImportantRotation(true, true);
            }
        }
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
            if (poseFix.get()) {
                player.setPose(EntityPose.STANDING);
            } else {
                pose = player.dimensions;
                player.dimensions = player.getDimensions(EntityPose.STANDING);
                player.setBoundingBox(player.dimensions.getBoxAt(player.getPos()));
            }
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
    // boolean lastFireworkIsDeadSignal = false;

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
            // lastFireworkIsDeadSignal = false;
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
        // lastFireworkIsDeadSignal = true;
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

    public boolean shouldLaunchNextFirework() {
        if (lastFireworkRocket != null) {
            if (lastFireworkRocket.isAlive()) {
                return false;
            } else if (Tasks.getTick()
                    < lastFireworkRocketTick + lastFireworkThresholdTime + Math.max(0, rocketBuffer.get() - 2)) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    int cnt = 0;
    final int FIREWORK_DELTA = 10;

    public void launchFirework(float pitch, float yaw) {
        boolean autoFirework = autoRocket.get() && lastFireworkRocket != null;
        var rocket = findRocket();
        if (rocket != null) {
            boolean isVanilla = rocket.isOf(Items.FIREWORK_ROCKET);
            int level = getRocketLevel(rocket);
            FireworkTimer timer = isVanilla ? timerVanilla : timerCustom;
            if (timer.canFire()) {
                boolean use = false;
                if (autoFirework) {
                    if (lastFireworkRocket.isAlive()) {
                        return;
                    } else if (Tasks.getTick()
                            >= lastFireworkRocketTick
                                    + lastFireworkThresholdTime
                                    + (rocketBuffer.get() - FIREWORK_DELTA / 2)) {
                        // time limit, do not double
                        use = true;
                        // use = true;
                    }
                }
                // timer use
                if (!use && shouldLaunchNextFirework() && timer.tryFire(level)) {
                    use = true;
                }
                if (use) {
                    // reset the tick even if is from vanilla operation
                    timer.fire(level);
                    ACTasks.addPostTransactionAction((s) -> {
                        sendCustomUseFireworkPacket(pitch, yaw);
                    });
                }
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
            //            if (canContinueGliding()) {
            //                // mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
            //                // ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            //            } else {
            //                EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
            //            }
        }
        if (this.thisFallFlyingIsArmorFly != -1 && poseFix.get()) {
            mc.player.setPose(EntityPose.STANDING);
        }
        if (shouldFlushRocketsThisTick) {
            ACTasks.addPostTransactionAction((s) -> {
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
        if (nextTickIsOnGroundTick && storedPos != null) {
            mc.player.setPosition(mc.player.getPos().withAxis(Direction.Axis.Y, storedPos.y));
        }
        storedPos = null;
        return true;
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (thisFallFlyingIsArmorFly != -1) {
            // fix grimac multiaction c
            PlayerInputUtils.of(player.input).sprint(false).applyInput(player.input);
            player.setSprinting(false);
        }
        //        if(armorFly.get() && player.isFallFlying() && this.thisFallFlyingIsArmorFly != -1){
        //            player.input.playerInput =
        // PlayerInputUtils.of(player.input.playerInput).sprint(false).sneak(false).jump(false).forward(false).backward(false).right(false).left(false).toPlayerInput();
        //        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean nextTickIsOnGroundTick = false;
    Vec3d storedPos = null;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (true && player.isFallFlying()) {
            player.horizontalCollision = false;
            if (fuckGrimAC.get()) {
                var input = PlayerInputUtils.of(player.input);
                input.forward(false).backward(false).left(false).right(false);
                if (thisTickHasStartFallFly) {
                    input.jump(true);
                } else {
                    input.jump(true);
                }
                input.applyInput(player.input);
            }
        }
        thisTickHasStartFallFly = false;
        NoFall noFallModule = MovTasks.getNoFall();
        // handle nofall
        boolean handleNoFall = false;
        noFall:
        if (noFallModule.entityStage == 1
                && player.getY() <= noFallModule.lastOnGroundHeight - noFallModule.safeDistance) {
            if (player.isFallFlying() && noFallLanding.get() && !nextTickIsOnGroundTick) {
                boolean shouldHandle = player.isOnGround() && !movementManagerEvent.context.playerStatus.onGround;
                if (shouldHandle) {
                    if (canFireworkControlMotion()) {
                        // controlling tick
                        player.setPosition(
                                player.getX(), movementManagerEvent.context.playerStatus.pos.y + 9E-8, player.getZ());
                        ClientPlayerAccess.of(player).resyncPos();
                        player.setOnGround(false);
                        MovTasks.getNoFall()
                                .setLastOnGroundHeight(movementManagerEvent.context.playerStatus.pos.y + 9E-8);
                        handleNoFall = true;
                        storedPos = player.getPos();
                        break noFall;
                    }
                    if (armorFly.get() && thisFallFlyingIsArmorFly != -1) {
                        player.setOnGround(false);
                        movementManagerEvent.context.playerStatus.restorePos();
                        MovTasks.getFloatingUtils().setGrimFloatingTick(true);
                        handleNoFall = true;
                        break noFall;
                    }
                    // what can I say.
                    player.setPosition(
                            player.getX(), movementManagerEvent.context.playerStatus.pos.y + 9E-8, player.getZ());
                    ClientPlayerAccess.of(player).resyncPos();
                    player.setOnGround(false);
                    storedPos = player.getPos();
                    handleNoFall = true;
                }
            }
        }

        if (!handleNoFall) {
            nextTickIsOnGroundTick = false;
        } else {
            nextTickIsOnGroundTick = true;
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

        public boolean canFire() {
            // do not fire too close,
            return Tasks.getTick() > lastTimeFire + 10;
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
            case HACKING, VANILLA, AC_COMMON, AC_GRIM -> {
                armorMode.set(ArmorFlyMode.LAZY);
            }
            case AC_MATRIX, AC_VULCAN -> {
                armorMode.set(ArmorFlyMode.TICK);
            }
        }
        switch (presetEvent.context.getValue()) {
            case AC_GRIM -> noKineticMode.set(Configs.BypassMode.BYPASS_GRIM);
            default -> noKineticMode.set(Configs.BypassMode.NO_BYPASS);
        }
        switch (presetEvent.context.getValue()) {
            case AC_GRIM -> {
                maceFixMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
            default -> maceFixMode.set(Configs.BypassMode.NO_BYPASS);
        }
    }

    public static enum ArmorFlyMode implements ConfigEnum {
        LAZY,
        TICK_LEGACY,
        TICK;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.armor-fly-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
