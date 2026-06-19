package me.matl114.hacks.modules.move;

import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import me.matl114.utils.containers.MetaData;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ClearAllEffectsConsumeEffect;
import net.minecraft.item.consume.RemoveEffectsConsumeEffect;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PlayerStateManager extends BaseModule {
    public static PlayerStateManager INSTANCE;
    double startFallingY;
    public double fallDistance;
    public double lastX;
    public double lastZ;
    public double lastY;
    public float lastPitch;
    public float lastYaw;
    public boolean lastOnGround;
    public boolean lastSprint;
    public Vec3d lastKnownMovementSpeed = Vec3d.ZERO;
    public Vec3d lastAverageMovementSpeed = Vec3d.ZERO;
    public Vec3d lastSetBackPosition = Vec3d.ZERO;
    boolean lastTickHasMovement = false;
    public boolean lastClimbing;
    public boolean lastInLava;
    public boolean lastInWater;
    public boolean lastInWeb;
    private boolean inWeb;
    public boolean lastInWall;
    public boolean lastUnderBlock;
    public PlayerInputUtils.Input lastInput = PlayerInputUtils.EMPTY.clone();
    public boolean serverSideCanFly;
    public Deque<Vec3d> last40Positions = new ArrayDeque<>();
    public BlockPos lastVelocityAffectingPos = BlockPos.ORIGIN;
    public Map<ItemStackSample, Integer> inventorySummary;
    public Map<ItemStackSample, Integer> inventoryTotalSummary;
    private static final int MAX_SIZE = 20;

    {
        for (int i = 0; i < MAX_SIZE; ++i) {
            last40Positions.add(Vec3d.ZERO);
        }
    }

    public PlayerStateManager() {
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onMove, Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInputC2SPacket.class),
                this::onPlayerInput,
                Integer.MAX_VALUE);
        registerListener(Listener.getPlayerWebSlowPoint(), this::handleInWeb);
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityAttackEvent);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class),
                this::onPlayerCommand,
                Integer.MAX_VALUE);
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInitialize);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientTickEndC2SPacket.class), this::onTickEnd, Integer.MAX_VALUE);
        registerListener(Listener.getPreGameTick(), this::updateOtherPlayers);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onTotemPop);
        registerListener(Listener.getServerLeavePoint(), this::onLeave);
        registerListener(Listener.getPostClickSlot(), this::onClickSlot);
        registerListener(Listener.getPacketPoint().getChannel(InventoryS2CPacket.class), this::onInventoryUpdate);
        registerListener(
                Listener.getPacketPoint().getChannel(ScreenHandlerSlotUpdateS2CPacket.class),
                this::onInventorySlotUpdate);
        registerListener(
                Listener.getPacketPoint().getChannel(CloseHandledScreenC2SPacket.class), this::onInventoryClose);
        registerListener(Listener.getPacketPoint().getChannel(PlayerRespawnS2CPacket.class), this::onRespawn);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.PLAYER), this::onEntityTrackedDataUpdate);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityConsume);
        registerListener(
                Listener.getEntityRemoveListener().getChannel(EntityType.SPLASH_POTION), this::onSplashedPotionHit);
        registerListener(
                Listener.getEntityRemoveListener().getChannel(EntityType.LINGERING_POTION), this::onLingerPotionHit);
        registerListener(
                Listener.getEntityPreTickListener().getChannel(EntityType.AREA_EFFECT_CLOUD),
                this::onAreaEffectCloudTick);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityStatusEffectS2CPacket.class),
                this::onEntityEffect);
    }

    public void onMove(Event<PlayerMoveC2SPacket> event) {
        if (event.isCancelled()) return;
        PlayerMoveC2SPacket packet = event.context;
        if (PlayerMoveC2SPacketAccess.of(packet).getCause() != PlayerMoveC2SPacketAccess.Cause.TRIGGER_SIMULATION) {
            // will not be intercepted by antiCheat
            Vec3d oldMove = new Vec3d(lastX, lastY, lastZ);

            if (!packet.changesPosition()) {
                if (packet.isOnGround()) {
                    handleOnGroundFlag();
                }
            } else {
                Vec3d vec3d = new Vec3d(packet.getX(lastX), packet.getY(lastY), packet.getZ(lastZ));
                if (!containsInvalidValues(vec3d.x, vec3d.y, vec3d.z)) {
                    handleMove(vec3d, packet.isOnGround());
                }
            }
            lastOnGround = packet.isOnGround();
            if (packet.changesLook()) {
                lastPitch = packet.getPitch(lastPitch);
                lastYaw = packet.getYaw(lastYaw);
            }
            lastKnownMovementSpeed = new Vec3d(lastX - oldMove.x, lastY - oldMove.y, lastZ - oldMove.z);
            lastTickHasMovement = true;
        }
        // update input here , low version
        if (!ViaFabricPlusHooks.isSupportEndTick()) {
            lastInput = PlayerInputUtils.of(mc.player);
        }
    }

    public void onPlayerInput(Event<PlayerInputC2SPacket> eventInput) {
        if (eventInput.isCancelled()) return;
        if (ViaFabricPlusHooks.isSupportEndTick()) {
            lastInput = PlayerInputUtils.of(eventInput.context);
        }
    }

    public void onPlayerInitialize(Event<ClientPlayerEntity> event) {
        onPlayerReset();
    }

    private static boolean containsInvalidValues(double x, double y, double z) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);
    }

    public void handleY(double y, boolean onGround) {
        // handle water
        if (!mc.player.isTouchingWater()) {
            if (mc.player.updateMovementInFluid(FluidTags.WATER, 0.014)) {
                fallDistance = 0.0;
            }
        } else {
            fallDistance = 0.0;
        }
        if (lastY > y) {
            if (!mc.player.isTouchingWater()) {
                fallDistance += lastY - y;
            }
        }
        if (onGround) {
            handleOnGroundFlag();
        }
        // handle reset
        if (lastY < y) {
            startFallingY = y;
            fallDistance = 0;
        }
        handleFallDistanceEnvironmentCheck();
    }

    public void onLand() {}

    public void handleFallDistanceEnvironmentCheck() {
        if (fallDistance < 0) {
            fallDistance = 0;
        }
        if (fallDistance > 0) {
            // check water
        }
    }

    public void handleOnGroundFlag() {
        // fall on
        if (!lastOnGround) {
            onLand();
            lastOnGround = true;
        }
        fallDistance = 0.0;
    }

    public void handleMove(Vec3d pos, boolean onGround) {
        handleY(pos.getY(), onGround);
        lastY = pos.getY();
        lastX = pos.getX();
        lastZ = pos.getZ();
        lastOnGround = onGround;
    }

    public void handleInWeb(Event<Vec3d> vec3dEvent) {
        fallDistance = 0.0;
        lastInWeb = true;
        inWeb = true;
    }

    public void onPreGameTick(Event<ClientPlayerEntity> event) {
        handleTick();
    }

    private BlockPos calculateVelocityAffectingPos() {
        BlockPos pos = mc.player.getVelocityAffectingPos();
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir() && !state.isLiquid()) {
            return pos;
        }
        Box box = mc.player.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX - 1e-7); // 避免边界溢出，实际遍历时用 <= 处理
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ - 1e-7);
        int y = pos.getY();
        boolean hasBlock = false;
        search:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos candidate = new BlockPos(x, y, z);
                BlockState candidateState = mc.world.getBlockState(candidate);
                if (!candidateState.isAir() && !candidateState.isLiquid()) {
                    hasBlock = true;
                    break search;
                }
            }
        }
        if (!hasBlock) {
            return pos;
        }
        Box velocityTest = box.offset(0, 0.500001F, 0);
        List<BlockPos> blockPoses = CollisionUtil.getIntersectingBlockPositions(mc.world, velocityTest, false);
        for (var re : blockPoses) {
            if (pos.getY() == re.getY()) {
                return re;
            }
        }
        return pos;
    }

    private Stream<ItemStack> streamInvContent(ItemStack stack) {
        var cp = stack.get(DataComponentTypes.CONTAINER);
        return cp == null ? Stream.empty() : cp.stream();
    }

    private Stream<ItemStack> streamItems(ItemStack stack) {
        return Streams.concat(Stream.of(stack), streamInvContent(stack).flatMap(this::streamItems));
    }

    private int cooldownInvSummary = 0;

    public void handleTick() {
        // base flag ticks;
        lastInLava = mc.player.isInLava();
        lastInWater = mc.player.isTouchingWater();
        lastClimbing = mc.player.isClimbing();
        lastInWeb = inWeb;
        inWeb = false;
        lastInWall = MovTasks.isCollidingWithEnvironment(mc.player);
        Box box = mc.player.getBoundingBox();
        lastUnderBlock = MovTasks.isCollidingWithEnvironment(
                mc.player, box.withMinY(box.maxY).withMaxY(box.maxY + 0.42));
        lastVelocityAffectingPos = calculateVelocityAffectingPos();
        if (++cooldownInvSummary > 10 || inventorySummary == null || inventoryTotalSummary == null) {
            cooldownInvSummary = 0;
            LinkedHashMap<ItemStackSample, Integer> map0 = new LinkedHashMap<>();
            mc.player.getInventory().getMainStacks().stream()
                    .filter(v -> !v.isEmpty())
                    .forEach(s -> map0.merge(ItemStackSample.of(s), s.getCount(), Integer::sum));
            inventorySummary = map0;
            LinkedHashMap<ItemStackSample, Integer> map1 = new LinkedHashMap<>(map0.size());
            for (var re : map0.entrySet()) {
                int count = re.getValue();
                streamItems(re.getKey().sample())
                        .filter(v -> !v.isEmpty())
                        .forEach(s -> map1.merge(ItemStackSample.of(s), s.getCount() * count, Integer::sum));
            }

            inventoryTotalSummary = map1;
        }
        // push vec3d
        Vec3d nowPos = new Vec3d(lastX, lastY, lastZ);
        last40Positions.addLast(nowPos);
        Vec3d last1MinPos = null;
        while (last40Positions.size() > MAX_SIZE) {
            last1MinPos = last40Positions.removeFirst();
        }
        if (last1MinPos != null) {
            lastAverageMovementSpeed = nowPos.subtract(last1MinPos).multiply(1D / MAX_SIZE);
        }

        // falldistance tick
        if (lastInLava) {
            fallDistance *= 0.5;
        }
        if (lastInWater) {
            fallDistance = 0.0;
        }
        if (mc.player.hasVehicle()) {
            fallDistance = 0.0;
        }
        if (mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            fallDistance = 0.0;
        }
        if (lastClimbing) {
            fallDistance = 0.0;
        }
    }

    public void onEntityAttackEvent(Event<EntityDamageS2CPacket> eventS2C) {
        if (mc.player != null && eventS2C.context.sourceCauseId() == mc.player.getId()) {
            // me attack them
            var source = eventS2C.context.sourceType().getKey().orElse(null);
            if (DamageUtils.isType(source, "mace_smash")) {
                // we trigger a mace smash
                handleMaceSmash();
            }
        }
        if (mc.player != null && eventS2C.context.entityId() == mc.player.getId()) {
            var source = eventS2C.context.sourceType().getKey().orElse(null);
            if (DamageUtils.isType(source, "ender_pearl")) {
                handlePearlTeleport();
            }
        }
    }

    public void onPlayerCommand(Event<ClientCommandC2SPacket> event) {
        if (event.isCancelled()) return;
        switch (event.context.getMode()) {
            case START_SPRINTING -> {
                lastSprint = true;
            }
            case STOP_SPRINTING -> {
                lastSprint = false;
            }
        }
    }

    public void handleMaceSmash() {
        if (fallDistance > 1.5) {
            fallDistance = 0;
        }
    }

    public void onClickSlot(Event<SlotActionType> eventClick) {
        cooldownInvSummary = 100;
    }

    public void onInventoryUpdate(Event<InventoryS2CPacket> event) {
        cooldownInvSummary = 100;
    }

    public void onInventorySlotUpdate(Event<ScreenHandlerSlotUpdateS2CPacket> event) {
        cooldownInvSummary = 100;
    }

    public void onInventoryClose(Event<CloseHandledScreenC2SPacket> event) {
        cooldownInvSummary = 100;
    }

    public void handlePearlTeleport() {
        fallDistance = 0;
    }

    public void onPlayerReset() {
        startFallingY = Double.MIN_VALUE;
        fallDistance = 0;
        lastKnownMovementSpeed = new Vec3d(0, 0, 0);
        lastAverageMovementSpeed = new Vec3d(0, 0, 0);
        lastSetBackPosition = new Vec3d(0, 0, 0);
        last40Positions.clear();
        for (int i = 0; i < MAX_SIZE; ++i) {
            last40Positions.add(Vec3d.ZERO);
        }
        lastX = 0.0D;
        lastY = 0.0D;
        lastZ = 0.0D;
        lastOnGround = false;
        lastPitch = 0.0F;
        lastYaw = 0.0F;
        lastSprint = false;
        lastInput = PlayerInputUtils.EMPTY.clone();
        lastVelocityAffectingPos = BlockPos.ORIGIN;
        inventoryTotalSummary = null;
        inventorySummary = null;
    }

    public void onTickEnd(Event<ClientTickEndC2SPacket> tickEndPacket) {
        if (tickEndPacket.isCancelled()) return;
        if (!lastTickHasMovement) {
            lastKnownMovementSpeed = Vec3d.ZERO;
        }
        lastTickHasMovement = false;
    }

    // api methods

    public boolean isRotationDifferent() {
        return EntityUtils.isRotationDifferent(lastPitch, mc.player.getPitch(), lastYaw, mc.player.getYaw());
    }

    public boolean isRotationDifferent(float pitch, float yaw) {
        return EntityUtils.isRotationDifferent(lastPitch, pitch, lastYaw, yaw);
    }

    public void sendSprintStatus(boolean sprint) {
        if (sprint != lastSprint) {
            if (sprint) {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            } else {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }

    // other players;
    public static final String KEY_RENDER_CONTROL = "slimefunhelper:player_manager/player_status";
    public static final EquipmentSlot[] ARMOR =
            new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private PlayerStatus getOrCreateStatus(PlayerEntity pl) {
        MetaData data = ((MetadataHolder) pl).getMetadata();
        return data.getOrPut(this, KEY_RENDER_CONTROL, PlayerStatus::new);
    }

    private PlayerStatus getPlayerStatus0(PlayerEntity entity) {
        if (entity instanceof MetadataHolder holder && !holder.isMetaEmpty()) {
            return holder.getMetadata().get(this, KEY_RENDER_CONTROL);
        }
        return null;
    }

    public PlayerStatus getPlayerStatus(PlayerEntity entity) {
        var re = getPlayerStatus0(entity);
        if (re != null && re.lastUpdate < Tasks.getTick() - 10) {
            re = null;
        }
        return re;
    }

    private Map<Integer, Integer> popMap = new ConcurrentHashMap<>();

    public int getPlayerPopCount(PlayerEntity entity) {
        var re = popMap.get(entity.getId());
        return re == null ? 0 : re;
    }

    public void updateOtherPlayers(Event<ClientPlayerEntity> eventUpdate) {
        for (var player : mc.world.getPlayers()) {
            if (player instanceof MetadataHolder metadataHolder) {
                PlayerStatus status = getOrCreateStatus(player);
                status.tickUpdate(player);
            }
        }
    }

    public void onTotemPop(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        EntityStatusS2CPacket packet = event.context;
        if (packet.getEntity(mc.world) instanceof PlayerEntity player) {
            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
                int uid = player.getId();
                popMap.merge(uid, 1, Integer::sum);
            }
            if (packet.getStatus() == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
                onDeath(player);
            }
        }
    }

    private void onDeath(Entity entity) {
        popMap.remove(entity.getId());
    }

    public void onRespawn(Event<PlayerRespawnS2CPacket> eventRespawn) {
        if (checkNull()) return;
        onDeath(mc.player);
    }

    public void onLeave(Event<Void> event) {
        popMap.clear();
    }

    private static final Int2ObjectOpenHashMap<RegistryEntry<StatusEffect>> colorToRegistry =
            new Int2ObjectOpenHashMap<>();

    static {
        for (var re : Registries.STATUS_EFFECT) {
            var entry = Registries.STATUS_EFFECT.getEntry(re);
            var color = re.getColor();
            colorToRegistry.put(color, entry);
        }
    }

    public void onEntityTrackedDataUpdate(Event<DataTracker.SerializedEntry<?>> eventDataUpdate) {
        if (eventDataUpdate.getArgs(0) instanceof PlayerEntity pl) {
            if (eventDataUpdate.context.id() == VDataFlag.ID_POTION_SWIRLS
                    && eventDataUpdate.context.value() instanceof List<?> lst) {
                // update visible effect list
                List<ParticleEffect> particles = (List<ParticleEffect>) lst;
                PlayerStatus status = getOrCreateStatus(pl);
                Set<RegistryEntry<StatusEffect>> keys = new HashSet<>(status.visibleStatusEffects.keySet());
                for (var ptc : particles) {
                    if (ptc instanceof TintedParticleEffect tinted) {
                        int colorValue = ColorUtils.withAlphaInt(tinted.color, 0);
                        var effect = colorToRegistry.get(colorValue);
                        if (effect != null) {
                            keys.remove(effect);
                            var effectTracker = status.visibleStatusEffects.computeIfAbsent(effect, EffectTracker::new);
                            if (!effectTracker.hasInitialized()) {
                                effectTracker.startTick = Tasks.getTick();
                            }
                            effectTracker.visible = true;
                        }
                    }
                }
                for (var re : keys) {
                    status.visibleStatusEffects.remove(re);
                }
            } else if (eventDataUpdate.context.id() == VDataFlag.ID_LIVING_FLAGS
                    && eventDataUpdate.context.value() instanceof Number lst) {
                byte byteValue = lst.byteValue();
                PlayerStatus status = getOrCreateStatus(pl);
                boolean useItem = (byteValue & VDataFlag.USING_ITEM_FLAG_INDEX) > 0;
                if (!useItem && pl.isUsingItem()) {
                    // cancel use metadata
                    ItemStack lastUsing = status.lastUsing;
                    Hand lastHand = status.lastUsingHand;
                    if (lastUsing != null && lastHand != null && !lastUsing.isEmpty()) {
                        Tasks.scheduleDelayed(
                                () -> {
                                    ItemStack handItem = pl.getStackInHand(lastHand);
                                    if ((lastUsing.getCount() > 1
                                                    && ItemStack.areItemsAndComponentsEqual(lastUsing, handItem))
                                            || (lastUsing.getCount() <= 1
                                                    && handItem.getItem() != lastUsing.getItem())) {
                                        // mark as consuming
                                        ItemStack consumedUsing = lastUsing;
                                        ConsumableComponent componentEat =
                                                consumedUsing.get(DataComponentTypes.CONSUMABLE);
                                        if (componentEat != null) {
                                            consumedUsing
                                                    .streamAll(Consumable.class)
                                                    .forEach(s -> {
                                                        if (s instanceof PotionContentsComponent foodComponent) {
                                                            float scale = (Float) consumedUsing.getOrDefault(
                                                                    DataComponentTypes.POTION_DURATION_SCALE, 1.0F);
                                                            foodComponent.forEachEffect(
                                                                    (instance) -> {
                                                                        if (!(instance.getEffectType()
                                                                                        .value())
                                                                                .isInstant()) {
                                                                            status.visibleStatusEffects
                                                                                    .computeIfAbsent(
                                                                                            instance.getEffectType(),
                                                                                            EffectTracker::new)
                                                                                    .refresh(instance);
                                                                        }
                                                                    },
                                                                    scale);
                                                        }
                                                        ;
                                                    });
                                            if (!componentEat.onConsumeEffects().isEmpty()) {
                                                for (var effect : componentEat.onConsumeEffects()) {
                                                    if (effect instanceof ApplyEffectsConsumeEffect apply) {
                                                        apply.effects().forEach(s -> status.visibleStatusEffects
                                                                .computeIfAbsent(s.getEffectType(), EffectTracker::new)
                                                                .refresh(s));
                                                    } else if (effect instanceof ClearAllEffectsConsumeEffect clear) {
                                                        // it will be cleared by tracked data update
                                                        // status.visibleStatusEffects.clear();
                                                    } else if (effect instanceof RemoveEffectsConsumeEffect remove) {
                                                        // it will be cleared by tracked data update
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                1);
                    }
                }
            } else if (eventDataUpdate.context.id() == VDataFlag.ID_HEALTH
                    && eventDataUpdate.context.value() instanceof Number lst) {
                double doubleValue = lst.doubleValue();
                if (doubleValue <= 0.0D) {
                    // death
                    onDeath(pl);
                }
            }
        }
    }

    public void onEntityEffect(Event<EntityStatusEffectS2CPacket> event) {
        if (checkNull()) return;
        if (mc.world.getEntityById(event.context.getEntityId()) instanceof PlayerEntity pl) {
            PlayerStatus status = getOrCreateStatus(pl);
            for (var re : pl.getStatusEffects()) {
                status.visibleStatusEffects
                        .computeIfAbsent(re.getEffectType(), EffectTracker::new)
                        .refresh(re);
            }
        }
    }

    private static final List<StatusEffectInstance> TOTEM_EFFECTS = List.of(
            new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1),
            new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1),
            new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));

    public void onEntityConsume(Event<EntityStatusS2CPacket> eventEntityStatusS2CPacket) {
        if (checkNull()) return;
        EntityStatusS2CPacket packet = eventEntityStatusS2CPacket.context;
        if (packet.getEntity(mc.world) instanceof PlayerEntity player) {
            // shimt
            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
                // experience
                PlayerStatus status = getOrCreateStatus(player);
                for (var effect : TOTEM_EFFECTS) {
                    status.visibleStatusEffects
                            .computeIfAbsent(effect.getEffectType(), EffectTracker::new)
                            .refresh(effect);
                }
            }
        }
    }

    public static float getToleranceMargin(Entity entity) {
        return Math.max(0.0F, Math.min(0.3F, (float) (entity.age - 2) / 20.0F));
    }

    public void onSplashedPotionHit(Event<PotionEntity> eventPotionEntity) {
        if (checkNull()) return;
        Entity.RemovalReason reason = eventPotionEntity.getArgs(0);
        if (reason.shouldDestroy()) {
            PotionEntity potionEntity = eventPotionEntity.context;
            ItemStack stack = potionEntity.getStack();
            if (stack.isEmpty()) return;
            PotionContentsComponent potionContentsComponent = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potionContentsComponent == null
                    || Objects.equals(potionContentsComponent, PotionContentsComponent.DEFAULT)) {
                return;
            }
            float durationScale = stack.getOrDefault(DataComponentTypes.POTION_DURATION_SCALE, 1.0f);
            Box boundingBox = potionEntity.getBoundingBox();
            boundingBox = boundingBox.expand(4, 2, 4);
            List<PlayerEntity> players = mc.world.getNonSpectatingEntities(PlayerEntity.class, boundingBox);
            if (!players.isEmpty()) {
                float g = getToleranceMargin(potionEntity);
                for (PlayerEntity player : players) {
                    if (player.isDead()) continue;
                    double distanceSq = MathUtils.squaredMagnitude(
                            boundingBox, player.getBoundingBox().expand(g));
                    if (distanceSq >= 16.0) continue;
                    double actualDistance = Math.sqrt(distanceSq);
                    double attenuation = 1.0 - actualDistance / 4.0;
                    for (StatusEffectInstance effectInstance : potionContentsComponent.getEffects()) {
                        RegistryEntry<StatusEffect> effectType = effectInstance.getEffectType();
                        StatusEffect effect = effectType.value();

                        if (!effect.isInstant()) {
                            // 持续效果：持续时间随衰减因子和 durationScale 缩放
                            int originalDuration = effectInstance.getDuration(); // 假设有此方法，原代码通过 mapDuration 获取
                            int newDuration = (int) (attenuation * originalDuration * durationScale + 0.5);
                            // 避免施加过短的效果（小于 1 秒）
                            if (newDuration < 20) continue;

                            StatusEffectInstance newInstance = new StatusEffectInstance(
                                    effectType,
                                    newDuration,
                                    effectInstance.getAmplifier(),
                                    effectInstance.isAmbient(),
                                    effectInstance.shouldShowParticles());
                            getOrCreateStatus(player)
                                    .visibleStatusEffects
                                    .computeIfAbsent(newInstance.getEffectType(), EffectTracker::new)
                                    .refresh(newInstance);
                        }
                    }
                }
            }
        }
    }

    private static final String AREA_EFFECT_CLOUD_POTION_CONTENT =
            "slimefunhelper:player_manager/tracking_linger_potion_type";

    public void onLingerPotionHit(Event<PotionEntity> eventLinger) {
        if (checkNull()) return;
        Entity.RemovalReason reason = eventLinger.getArgs(0);
        if (reason.shouldDestroy()) {
            PotionEntity potionEntity = eventLinger.context;
            ItemStack stack = potionEntity.getStack();
            if (stack.isEmpty()) return;
            PotionContentsComponent potionContentsComponent = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potionContentsComponent == null
                    || Objects.equals(potionContentsComponent, PotionContentsComponent.DEFAULT)) {
                return;
            }
            float durationScale = stack.getOrDefault(DataComponentTypes.POTION_DURATION_SCALE, 1.0f);
            Vec3d pos = potionEntity.getPos();
            int startTick = Tasks.getTick();
            Box detectBox = new Box(pos.add(-0.2, -0.2, -0.2), pos.add(0.2, 0.2, 0.2));
            Tasks.scheduleRepeated(
                    () -> {
                        if (checkNull()) return true;
                        if (Tasks.getTick() > startTick + 20) return true;
                        List<AreaEffectCloudEntity> near =
                                mc.world.getNonSpectatingEntities(AreaEffectCloudEntity.class, detectBox);
                        if (near.isEmpty()) return false;
                        for (var en : near) {
                            if (en instanceof MetadataHolder holder) {
                                holder.getMetadata()
                                        .put(
                                                this,
                                                AREA_EFFECT_CLOUD_POTION_CONTENT,
                                                Pair.of(potionContentsComponent, durationScale));
                            }
                        }
                        return true;
                    },
                    1,
                    1);
        }
    }

    public void onAreaEffectCloudTick(Event<AreaEffectCloudEntity> eventCloud) {
        if (checkNull()) return;
        if (Tasks.getTick() % 5 != 0) return;
        var cloud = eventCloud.context;
        float radius = cloud.getRadius();
        performCloudUpdate(cloud, radius);
    }

    /**
     * 核心更新逻辑（每 5 刻执行一次）
     */
    private void performCloudUpdate(AreaEffectCloudEntity cloud, float currentRadius) {
        // 1. 清理过期记录（reapplicationDelay 默认 20 刻）
        // 无药水效果则跳过
        if (cloud instanceof MetadataHolder holder && !holder.isMetaEmpty()) {
            MetaData data = holder.getMetadata();
            Pair<PotionContentsComponent, Float> pairData = data.get(this, AREA_EFFECT_CLOUD_POTION_CONTENT);
            if (pairData != null) {
                List<PlayerEntity> targets =
                        mc.world.getNonSpectatingEntities(PlayerEntity.class, cloud.getBoundingBox());
                if (targets.isEmpty()) return;
                List<StatusEffectInstance> effectList = new ArrayList<>();
                pairData.getFirst().forEachEffect(effectList::add, pairData.getSecond());
                for (PlayerEntity target : targets) {
                    // 冷却检查
                    if (target.isDead()) continue;
                    // 水平距离检查
                    double dx = target.getX() - cloud.getX();
                    double dz = target.getZ() - cloud.getZ();
                    if (dx * dx + dz * dz > currentRadius * currentRadius) continue;

                    // 施加每个效果
                    for (StatusEffectInstance effect : effectList) {
                        StatusEffect statusEffect = effect.getEffectType().value();
                        if (!statusEffect.isInstant()) {
                            getOrCreateStatus(target)
                                    .visibleStatusEffects
                                    .computeIfAbsent(effect.getEffectType(), EffectTracker::new)
                                    .refresh(effect);
                        }
                    }
                }
            }
        }
    }

    public static class PlayerStatus {

        public int lastUpdate;
        public AttributeContainer attributeSnapShot = null;
        // public int popCount;
        public int protection;
        public int blastProtection;
        public ItemStack lastUsing;
        public Hand lastUsingHand;
        public boolean lastInBlock;
        public boolean lastUnderBlock;
        public Map<RegistryEntry<StatusEffect>, EffectTracker> visibleStatusEffects = new ConcurrentHashMap<>();
        // todo: add more shit
        public void tickUpdate(PlayerEntity player) {
            AttributeContainer container = new AttributeContainer(
                    DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) player.getType()));
            container.setFrom(player.getAttributes());
            this.attributeSnapShot = container;
            int protection = 0;
            int blastProtection = 0;
            for (var re : ARMOR) {
                ItemStack stack = player.getEquippedStack(re);
                if (stack.isEmpty()) continue;
                ;
                ItemEnchantmentsComponent itemEnchant = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (itemEnchant.isEmpty()) continue;
                ;
                int level = ItemStackUtils.getEnchantmentLevel(itemEnchant, Enchantments.PROTECTION);
                protection += level;
                level = ItemStackUtils.getEnchantmentLevel(itemEnchant, Enchantments.BLAST_PROTECTION);
                blastProtection += level;
            }
            this.protection = protection;
            this.blastProtection = blastProtection;
            if (player.isUsingItem()) {
                lastUsing = player.getActiveItem().copy();
                lastUsingHand = player.getActiveHand();
            } else {
                lastUsing = null;
                lastUsingHand = null;
            }
            lastInBlock = MovTasks.isCollidingWithEnvironment(player);
            Box box = mc.player.getBoundingBox();
            lastUnderBlock = MovTasks.isCollidingWithEnvironment(
                    player, box.withMinY(box.maxY).withMaxY(box.maxY + 0.42));
            this.lastUpdate = Tasks.getTick();
        }
    }

    public static class EffectTracker {
        int startTick = 0;
        int duration = 0;
        public boolean visible;
        final StatusEffect effectInstance;

        public EffectTracker(RegistryEntry<StatusEffect> effectRegistryEntry) {
            effectInstance = effectRegistryEntry.value();
        }

        public EffectTracker(StatusEffectInstance effectInstance) {
            this.effectInstance = effectInstance.getEffectType().value();
            this.startTick = Tasks.getTick();
            this.duration = effectInstance.getDuration();
        }

        public void refresh(StatusEffectInstance effectInstance) {
            int startTick = Tasks.getTick();
            int duration = effectInstance.getDuration();
            if (startTick + duration > this.startTick + this.duration) {
                this.startTick = startTick;
                this.duration = duration;
            }
        }

        public boolean hasInitialized() {
            return startTick != 0;
        }

        public int getRemainDurations() {
            int tick = startTick + duration;
            return Math.max(tick - Tasks.getTick(), 0);
        }
    }
}
