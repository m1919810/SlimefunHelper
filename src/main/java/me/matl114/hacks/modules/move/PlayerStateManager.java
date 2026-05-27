package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.containers.MetaData;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.tag.FluidTags;
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
    public PlayerInputUtils.Input lastInput = PlayerInputUtils.EMPTY.clone();
    public boolean serverSideCanFly;
    public Deque<Vec3d> last40Positions = new ArrayDeque<>();
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
        registerListener(Listener.getPacketPostSendPoint().getChannel(PlayerMoveC2SPacket.class), this::onMove);
        registerListener(Listener.getPlayerWebSlowPoint(), this::handleInWeb);
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityAttackEvent);
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(ClientCommandC2SPacket.class), this::onPlayerCommand);
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInitialize);
        registerListener(Listener.getPacketPostSendPoint().getChannel(ClientTickEndC2SPacket.class), this::onTickEnd);
        registerListener(Listener.getPreGameTick(), this::updateOtherPlayers);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onTotemPop);
    }

    public void onMove(Event<PlayerMoveC2SPacket> event) {
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
        // update input here
        lastInput = PlayerInputUtils.of(mc.player.input);
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

    public void handleTick() {
        // base flag ticks;
        lastInLava = mc.player.isInLava();
        lastInWater = mc.player.isTouchingWater();
        lastClimbing = mc.player.isClimbing();
        lastInWeb = inWeb;
        inWeb = false;
        lastInWall = MovTasks.isCollidingWithEnvironment(mc.player);
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
        if (mc.player.isRiding()) {
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
            if (Objects.equals(source, DamageTypes.MACE_SMASH)) {
                // we trigger a mace smash
                handleMaceSmash();
            }
        }
        if (mc.player != null && eventS2C.context.entityId() == mc.player.getId()) {
            var source = eventS2C.context.sourceType().getKey().orElse(null);
            if (Objects.equals(source, DamageTypes.ENDER_PEARL)) {
                handlePearlTeleport();
            }
        }
    }

    public void onPlayerCommand(Event<ClientCommandC2SPacket> event) {
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
    }

    public void onTickEnd(Event<ClientTickEndC2SPacket> tickEndPacket) {
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
    public final EquipmentSlot[] ARMOR =
            new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

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

    public int getPlayerPopCount(PlayerEntity entity) {
        var re = getPlayerStatus(entity);
        return re == null ? 0 : re.popCount;
    }

    public void updateOtherPlayers(Event<ClientPlayerEntity> eventUpdate) {
        for (var player : mc.world.getPlayers()) {
            if (player instanceof MetadataHolder metadataHolder) {
                MetaData data = metadataHolder.getMetadata();
                PlayerStatus status = data.getOrPut(this, KEY_RENDER_CONTROL, PlayerStatus::new);
                AttributeContainer container = new AttributeContainer(
                        DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) player.getType()));
                container.setFrom(player.getAttributes());
                status.attributeSnapShot = container;
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
                status.protection = protection;
                status.blastProtection = blastProtection;
                status.lastUpdate = Tasks.getTick();
            }
        }
    }

    public void onTotemPop(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        EntityStatusS2CPacket packet = event.context;
        if (packet.getEntity(mc.world) instanceof PlayerEntity player
                && packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && player instanceof MetadataHolder holder) {
            MetaData data = holder.getMetadata();
            PlayerStatus status = data.getOrPut(this, KEY_RENDER_CONTROL, PlayerStatus::new);
            status.popCount++;
        }
    }

    public static class PlayerStatus {

        public int lastUpdate;
        public AttributeContainer attributeSnapShot = null;
        public int popCount;
        public int protection;
        public int blastProtection;

        // todo: add more shit
    }
}
