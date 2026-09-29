package me.matl114.hacks.modules.extra;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.ChatRecv;
import me.matl114.events.impl.MetadataUpdate;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Holder;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.impl.baritone.BaritoneFuture;
import me.matl114.hooks.impl.baritone.BaritoneLanding;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.DamageUtils;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundNotify extends BaseModule {
    private static final String MOD_ID = "slimefunhelper";
    private static final int SOUND_COOLDOWN_TICKS = 10;
    public static SoundNotify INSTANCE;

    public final ModulePath path = makePath(Configs.EXTRA_CONFIG, "other.sound-notify");

    public SoundNotify() {
        super("SoundNotify");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    private final ModulePath entityLogPath = path.add("entity-log");
    public final FlagRef playerEnter =
            flagBuilder(entityLogPath.add("player-enter-range")).build();
    public final FlagRef playerLeave =
            flagBuilder(entityLogPath.add("player-leave-range")).build();
    public final NBTRef<Holder<SoundEvent>> entityLogSound = soundBuilder(entityLogPath, ENTITY_LOG_SOUND);

    private final ModulePath totemPath = path.add("totem");
    public final FlagRef selfTotem = flagBuilder(totemPath.add("self-pop")).build();
    public final FlagRef otherTotem = flagBuilder(totemPath.add("other-pop")).build();
    public final NBTRef<Holder<SoundEvent>> totemSound = soundBuilder(totemPath, TOTEM_SOUND);

    private final ModulePath effectWarnPath = path.add("effect-warn");
    public final FlagRef effectWarn = flagBuilder(effectWarnPath.addEnable()).build();
    public final NBTRef<EntrySet<StatusEffect>> warnedEffects = builder(
                    effectWarnPath.add("effects"), EntrySet.<StatusEffect>parameter())
            .defaultValue(new EntrySet<>(Registries.STATUS_EFFECT, List.of()))
            .build();
    public final IntRef effectWarnDuration =
            intBuilder(effectWarnPath.add("duration-ticks")).defaultValue(200).build();
    public final NBTRef<Holder<SoundEvent>> effectWarnSound = soundBuilder(effectWarnPath, EFFECT_WARN_SOUND);

    private final ModulePath messagePath = path.add("message-detection");
    public final FlagRef messageDetection = flagBuilder(messagePath.addEnable()).build();
    public final ListRef messageKeywords = builder(messagePath.add("keywords"), ListRef.TYPE)
            .defaultValue(List.of())
            .build();

    // Private-message detection stays inactive until a server-specific format is defined.
    public final FlagRef privateMessageSound =
            flagBuilder(messagePath.add("private-message-sound")).build();
    public final NBTRef<Holder<SoundEvent>> messageDetectionSound = soundBuilder(messagePath, MESSAGE_DETECTION_SOUND);

    private final ModulePath itemSearchPath = path.add("item-search");
    public final FlagRef itemSearch = flagBuilder(itemSearchPath.addEnable()).build();
    public final NBTRef<EntrySet<Item>> importantItems = builder(
                    itemSearchPath.add("important-items"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(
                    new Regex(
                            "^(.*ton_skull|netherite.*|.*_star|.*_apple|.*potion|tot.*|end_c.*l|obsi.*|.*anchor|expe.*|mace|ely.*|.*shulker.*|trident)$"),
                    Registries.ITEM))
            .build();
    public final NBTRef<Holder<SoundEvent>> itemSearchSound = soundBuilder(itemSearchPath, ITEM_SEARCH_SOUND);

    private final ModulePath durabilityPath = path.add("durability");
    public final FlagRef durabilityWarn =
            flagBuilder(durabilityPath.addEnable()).build();
    public final IntRef durabilityThresholdValue =
            intBuilder(durabilityPath.add("threshold")).defaultValue(10).build();

    public final DoubleRef durabilityThresholdPercentage = doubleBuilder(durabilityPath.add("threshold-percentage"))
            .defaultValue(0.1)
            .build();
    public final NBTRef<Holder<SoundEvent>> durabilitySound = soundBuilder(durabilityPath, DURABILITY_SOUND);

    private final ModulePath baritonePath = path.add("baritone");
    public final FlagRef baritoneLanding =
            flagBuilder(baritonePath.add("landing")).build();
    public final NBTRef<Holder<SoundEvent>> baritoneSound = soundBuilder(baritonePath, BARITONE_SOUND);

    private final ModulePath attackPath = path.add("attack");
    public final FlagRef clientMaceAttack =
            flagBuilder(attackPath.add("client-mace")).build();
    public final FlagRef serverMaceAttack =
            flagBuilder(attackPath.add("server-mace")).build();
    public final NBTRef<Holder<SoundEvent>> attackSound = soundBuilder(attackPath, ATTACK_SOUND);

    public static final Optional<RegistryEntry<SoundEvent>> ENTITY_LOG_SOUND = registerSound("event.entity-log.notify");
    public static final Optional<RegistryEntry<SoundEvent>> TOTEM_SOUND = registerSound("event.totem.notify");
    public static final Optional<RegistryEntry<SoundEvent>> EFFECT_WARN_SOUND =
            registerSound("event.effect-warn.notify");
    public static final Optional<RegistryEntry<SoundEvent>> MESSAGE_DETECTION_SOUND =
            registerSound("event.message-detection.notify");
    public static final Optional<RegistryEntry<SoundEvent>> ITEM_SEARCH_SOUND =
            registerSound("event.item-search.notify");
    public static final Optional<RegistryEntry<SoundEvent>> DURABILITY_SOUND = registerSound("event.durability.notify");
    public static final Optional<RegistryEntry<SoundEvent>> BARITONE_SOUND = registerSound("event.baritone.notify");
    public static final Optional<RegistryEntry<SoundEvent>> ATTACK_SOUND = registerSound("event.attack.notify");
    public static final Optional<RegistryEntry<SoundEvent>> SEARCH_LABEL_SOUND =
            registerSound("event.search-label.notify");
    public static final Optional<RegistryEntry<SoundEvent>> TEST_SOUND = registerSound("event.test");

    private static Optional<RegistryEntry<SoundEvent>> registerSound(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        try {
            return Optional.of(Registry.registerReference(Registries.SOUND_EVENT, id, SoundEvent.of(id)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private NBTRef<Holder<SoundEvent>> soundBuilder(
            ModulePath soundPath, Optional<RegistryEntry<SoundEvent>> defaultSound) {
        return builder(soundPath.add("sound"), Holder.<SoundEvent>parameter())
                .defaultValue(Holder.of(
                        Registries.SOUND_EVENT,
                        defaultSound.map(RegistryEntry::value).orElse(null)))
                .build();
    }

    private enum Cue {
        ENTITY_LOG,
        TOTEM,
        EFFECT_WARN,
        MESSAGE_DETECTION,
        ITEM_SEARCH,
        DURABILITY,
        BARITONE,
        ATTACK
    }

    private final EnumMap<Cue, TimerExecutor> soundTimers;

    {
        EnumMap<Cue, TimerExecutor> timers = new EnumMap<>(Cue.class);
        for (Cue cue : Cue.values()) {
            timers.put(cue, new TimerExecutor());
        }
        soundTimers = timers;
    }

    private final Set<RegistryEntry<StatusEffect>> alertedEffects = new HashSet<>();
    private final Set<EquipmentSlot> lowDurabilitySlots = new HashSet<>();
    private boolean durabilityInitialized;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntitySpawnS2CPacket.class), this::onEntitySpawn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntitiesDestroyS2CPacket.class), this::onEntityDestroy);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onClientAttack);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onServerAttack);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM), this::handleItemEntityItemData);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM_FRAME), this::handleItemFrameItemData);
        registerListener(Listener.getPostTick(), this::onPostTick);
        registerListener(Listener.getServerDisconnectPoint(), this::onDisconnect);
        registerListener(Listener.getChatMessageReceive(), this::onChatReceive);
        registerListener(BaritoneHooks.getLandingEvent(), this::onBaritoneLanding);
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> event) {
        if (!enable.get() || !playerEnter.get() || checkNull() || loginServerCheck()) return;
        EntitySpawnS2CPacket packet = event.context();
        if (packet.getEntityType() == EntityType.PLAYER && !packet.getUuid().equals(mc.player.getUuid())) {
            play(Cue.ENTITY_LOG);
        }
    }

    public void onEntityDestroy(Event<EntitiesDestroyS2CPacket> event) {
        if (!enable.get() || !playerLeave.get() || checkNull() || loginServerCheck()) return;
        for (int entityId : event.context().getEntityIds()) {
            if (mc.world.getEntityById(entityId) instanceof PlayerEntity player && player != mc.player) {
                play(Cue.ENTITY_LOG);
                return;
            }
        }
    }

    public void onEntityStatus(Event<EntityStatusS2CPacket> event) {
        if (!enable.get() || checkNull() || event.context().getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) {
            return;
        }
        Entity entity = event.context().getEntity(mc.world);
        if (entity == mc.player && selfTotem.get()) {
            play(Cue.TOTEM);
        } else if (entity instanceof PlayerEntity && entity != mc.player && otherTotem.get()) {
            play(Cue.TOTEM);
        }
    }

    public void onClientAttack(Event<PlayerInteractEntityC2SPacket> event) {
        if (!enable.get()
                || !clientMaceAttack.get()
                || checkNull()
                || event.isCancelled()
                || !PlayerInteractEntityC2SPacketAccess.of(event.context()).isAttack()) {
            return;
        }
        if (mc.player.getMainHandStack().isOf(Items.MACE) && PlayerStateManager.INSTANCE.fallDistance > 1.5D) {
            play(Cue.ATTACK);
        }
    }

    public void onServerAttack(Event<EntityDamageS2CPacket> event) {
        if (!enable.get() || !serverMaceAttack.get() || checkNull()) return;
        EntityDamageS2CPacket packet = event.context();
        if (packet.sourceCauseId() == mc.player.getId()
                && mc.world.getEntityById(packet.entityId()) instanceof PlayerEntity
                && DamageUtils.isType(packet.sourceType().getKey().orElse(null), "mace_smash")) {
            play(Cue.ATTACK);
        }
    }

    public void onPostTick(Event<Void> event) {
        if (checkNull()) return;
        if (!enable.get()) {
            alertedEffects.clear();
            resetDurabilityTracking();
            return;
        }
        updateEffectWarnings();
        updateDurabilityWarnings();
    }

    private void updateEffectWarnings() {
        if (!effectWarn.get()) {
            alertedEffects.clear();
            return;
        }
        Set<RegistryEntry<StatusEffect>> activeConfiguredEffects = new HashSet<>();
        int threshold = Math.max(0, effectWarnDuration.get());
        for (StatusEffectInstance instance : mc.player.getStatusEffects()) {
            RegistryEntry<StatusEffect> effect = instance.getEffectType();
            if (!warnedEffects.get().test(effect.value())) continue;
            activeConfiguredEffects.add(effect);
            int duration = instance.getDuration();
            if (duration > 0 && duration < threshold && alertedEffects.add(effect)) {
                play(Cue.EFFECT_WARN);
            } else if (duration <= 0 || duration >= threshold) {
                alertedEffects.remove(effect);
            }
        }
        alertedEffects.retainAll(activeConfiguredEffects);
    }

    public void handleItemEntityItemData(Event<MetadataUpdate> entryUpdateEvent) {
        if (itemSearch.get()) {
            var entry = entryUpdateEvent.context().metadata();
            if (entry.id() == VDataFlag.ID_ITEM_ITEMSTACK
                    && (entry.value()) instanceof ItemStack stack
                    && entryUpdateEvent.context.entity() instanceof ItemEntity item) {
                onItemEntity(item, item.getStack(), stack);
            }
        }
    }

    public void handleItemFrameItemData(Event<MetadataUpdate> entryUpdateEvent) {
        if (itemSearch.get()) {
            var entry = entryUpdateEvent.context().metadata();
            if (entry.id() == VDataFlag.ID_ITEM_FRAME_ITEMSTACK
                    && entry.value() instanceof ItemStack stack
                    && entryUpdateEvent.context.entity() instanceof ItemFrameEntity item) {
                onItemEntity(item, item.getHeldItemStack(), stack);
            }
        }
    }

    public void onItemEntity(Entity itemEntity, ItemStack oldStack, ItemStack stack) {
        if (!stack.isEmpty()
                && importantItems.get().test(stack.getItem())
                && !importantItems.get().test(oldStack.getItem())) {
            play(Cue.ITEM_SEARCH);
        }
    }

    private void updateDurabilityWarnings() {
        if (!durabilityWarn.get()) {
            resetDurabilityTracking();
            return;
        }
        double per = Math.clamp(durabilityThresholdPercentage.get(), 0, 1);
        int thresholdValue = Math.max(0, durabilityThresholdValue.get());
        for (EquipmentSlot slot :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.BODY, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) {
                lowDurabilitySlots.remove(slot);
                continue;
            }
            ItemStack identity = stack.copy();
            identity.setDamage(0);
            int left = stack.getMaxDamage() - stack.getDamage();
            double remainingPercent = (double) (left) / stack.getMaxDamage();
            if (remainingPercent >= per && left >= thresholdValue) {
                lowDurabilitySlots.remove(slot);
            } else if (!durabilityInitialized) {
                lowDurabilitySlots.add(slot);
            } else if (lowDurabilitySlots.add(slot)) {
                play(Cue.DURABILITY);
            }
        }
        durabilityInitialized = true;
    }

    private void resetDurabilityTracking() {
        lowDurabilitySlots.clear();
        durabilityInitialized = false;
    }

    public void onChatReceive(Event<ChatRecv> event) {
        if (!enable.get() || !messageDetection.get() || event.isCancelled() || checkNull()) {
            return;
        }
        if (privateMessageSound.get()) {
            // todo: detect private message?
        }
        String message = ChatUtils.textToPlainString(event.context().text()).toLowerCase(Locale.ROOT);
        for (String keyword : messageKeywords.get()) {
            if (keyword != null && !keyword.isBlank() && message.contains(keyword.toLowerCase(Locale.ROOT))) {
                play(Cue.MESSAGE_DETECTION);
                return;
            }
        }
    }

    public void onBaritoneLanding(Event<BaritoneFuture> event) {
        if (enable.get() && baritoneLanding.get()) {
            BaritoneLanding landing = event.getArgs(0);
            if (landing != null) {
                play(Cue.BARITONE);
            }
        }
    }

    public void onDisconnect(Event<Void> event) {
        alertedEffects.clear();
        resetDurabilityTracking();
        for (TimerExecutor timer : soundTimers.values()) {
            timer.mark(-SOUND_COOLDOWN_TICKS);
        }
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createExecuteButton(
                "widget.sound-notify.test-usage", ButtonAction.run(this::playTestSound), 0, dblank, dx, dy));
    }

    private void playTestSound() {
        playSound(TEST_SOUND);
    }

    private void play(Cue cue) {
        if (!enable.get() || checkNull()) return;
        soundTimers.get(cue).run(SOUND_COOLDOWN_TICKS, () -> playSound(soundFor(cue)));
    }

    public void playSound(Optional<RegistryEntry<SoundEvent>> sound) {
        if (checkNull()) return;
        sound.ifPresent(soundEvent -> mc.world.playSound(
                mc.player,
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                soundEvent,
                mc.player.getSoundCategory(),
                1.0F,
                1.0F));
    }

    private boolean loginServerCheck() {
        return mc.world.getWorldBorder().getSize() < 100;
    }

    private Optional<RegistryEntry<SoundEvent>> soundFor(Cue cue) {
        return switch (cue) {
            case ENTITY_LOG -> configuredSound(entityLogSound);
            case TOTEM -> configuredSound(totemSound);
            case EFFECT_WARN -> configuredSound(effectWarnSound);
            case MESSAGE_DETECTION -> configuredSound(messageDetectionSound);
            case ITEM_SEARCH -> configuredSound(itemSearchSound);
            case DURABILITY -> configuredSound(durabilitySound);
            case BARITONE -> configuredSound(baritoneSound);
            case ATTACK -> configuredSound(attackSound);
        };
    }

    private Optional<RegistryEntry<SoundEvent>> configuredSound(NBTRef<Holder<SoundEvent>> soundRef) {
        Holder<SoundEvent> holder = soundRef.get();
        if (holder == null || holder.entry() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(holder.registry().getEntry(holder.entry()));
    }
}
