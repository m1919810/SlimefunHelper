package me.matl114.hacks.modules.extra;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.EntryPrimitiveMap;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.hacks.utils.entity.FakePlayerEntity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.utils.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class FakePlayer extends BaseModule {
    public FakePlayer() {
        super("FakePlayer");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "other.fake-player");

    public final StringRef name = builder(root.add("name"), StringRef.TYPE)
            .defaultValue("juhaoshabi666")
            .build();

    public final FlagRef hasPhysics = flagBuilder(root.add("has-physics")).build();

    public final FlagRef copyEquipment = flagBuilder(root.add("copy-equipment")).build();

    public final DoubleRef maxHealth =
            doubleBuilder(root.add("max-health")).defaultValue(20.0D).build();

    public final FlagRef autoTotem = flagBuilder(root.add("auto-totem")).build();

    public final FlagRef regeneration = flagBuilder(root.add("regeneration")).build();

    public final FlagRef overrideEffect =
            flagBuilder(root.add("override-effects")).build();

    public final NBTRef<EntryPrimitiveMap<StatusEffect, Integer>> constantEffects = builder(
                    root.add("constant-effects"), EntryPrimitiveMap.<StatusEffect, Integer>parameter())
            .defaultValue(new EntryPrimitiveMap<>(Registries.STATUS_EFFECT, NBTTypes.INT_TYPE, Map.of()))
            .build();

    public final FlagRef logHit = flagBuilder(root.add("log-hit")).build();

    public final NBTRef<StringFormat> hitLog = builder(root.add("hit-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("name", "damage_type", "damage", "final_damage"),
                    "&f{name} was hit, hp-{final_damage}",
                    true))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onHit,
                Integer.MAX_VALUE);
        registerListener(Listener.getPacketPreHandlePoint().getChannel(ExplosionS2CPacket.class), this::onExplode);
        registerListener(Listener.getPreGameTick(), this::onTickKinetic);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        if (mc.world == null) {
            acceptor.accept(createTitleLabel("widget.fake-player.fake-player-list.enter-world", 0, dblank, dx, dy));
            return;
        }
        acceptor.accept(createTitleLabel("widget.fake-player.fake-player-list.title", 0, dblank, dx, dy));
        DynamicListWidget list = new DynamicListWidget(0, dblank, dx);
        for (var re : mc.world.getEntities()) {
            if (re instanceof FakePlayerEntity fakePlayer) {
                createWidgetForFakePlayer(fakePlayer, list, dx, dy, dblank);
            }
        }
        int dxx = (dx - dy) / 2;
        acceptor.accept(list);
        acceptor.accept(ExecutableWidget.instance(dxx, dblank, dy, dy)
                .setElementHandler(IconElement.fixedGui(Constants.ADD_SPRITE, ButtonAction.run(() -> {
                            FakePlayerEntity fakePlayer = createNewFakePlayer();
                            createWidgetForFakePlayer(fakePlayer, list, dx, dy, dblank);
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.fake-player.fake-player-list.add.tooltips", "")))));
    }

    private DrawableWidget createWidgetForFakePlayer(
            FakePlayerEntity fakePlayer, @Nullable DynamicListWidget list, int dx, int dy, int dblank) {
        DynamicContentWidget<DrawableWidget> widget = new DynamicContentWidget<>(() -> null, 0, 0);
        SubScreenWidget subScreenWidget = new SubScreenWidget(0, 0, dx, dy + dblank);
        subScreenWidget.addDrawableChild(DisplayWidget.instance(0, dblank, dx - dy, dy)
                .setRenderHandler(new ButtonElement(
                        TextProvider.of(Text.translatable(
                                "widget.fake-player.fake-player-list.info",
                                fakePlayer.getDisplayName(),
                                "%.2f".formatted(fakePlayer.getX()),
                                "%.2f".formatted(fakePlayer.getY()),
                                "%.2f".formatted(fakePlayer.getZ()),
                                fakePlayer.getHealth())),
                        ButtonAction.empty())));
        subScreenWidget.addDrawableChild(ExecutableWidget.instance(dx - dy, dblank, dy, dy)
                .setElementHandler(IconElement.fixedGui(Constants.REMOVE_SPRITE, ButtonAction.run(() -> {
                            if (list != null) list.remove(widget);
                            removeFakePlayer(fakePlayer);
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.fake-player.fake-player-list.remove.tooltips", "")))));
        widget.setContentSupplier(() -> !fakePlayer.isRemoved() ? subScreenWidget : null);
        if (list != null) {
            list.addDrawableChild(widget);
        }
        return widget;
    }

    private void removeFakePlayer(FakePlayerEntity fakePlayer) {
        fakePlayer.setHealth(0.0F);
        mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.KILLED);
        fakePlayer.setRemoved(Entity.RemovalReason.KILLED);
    }

    private FakePlayerEntity createNewFakePlayer() {
        FakePlayerEntity fakePlayer = new FakePlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name.get()));
        fakePlayer.setFreeze(!hasPhysics.get());
        fakePlayer
                .getAttributes()
                .getCustomInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(maxHealth.get());
        fakePlayer.setTickTask(this::onFakePlayerTick);
        if (copyEquipment.get()) {
            fakePlayer.copyEquipmentFrom(mc.player);
        }
        fakePlayer.copyDataFrom(mc.player);
        // enable absorption
        fakePlayer
                .getAttributes()
                .getCustomInstance(EntityAttributes.GENERIC_MAX_ABSORPTION)
                .setBaseValue(1024);
        mc.world.addEntity(fakePlayer);
        return fakePlayer;
    }

    private void onFakePlayerTick(FakePlayerEntity fakePlayer) {
        if (fakePlayer.isDead()) {
            Tasks.scheduleDelayed(() -> removeFakePlayer(fakePlayer), 0);
            return;
        }
        if (fakePlayer.hasStatusEffect(StatusEffects.REGENERATION)) {
            StatusEffectInstance instance = fakePlayer.getStatusEffect(StatusEffects.REGENERATION);
            if (instance != null) {
                int duration = instance.getDuration();
                int amplifier = instance.getAmplifier();
                int i = 50 >> amplifier;
                if (i == 0 || duration % i == 0) {
                    if (fakePlayer.getHealth() < fakePlayer.getMaxHealth()) {
                        fakePlayer.heal(1.0F);
                    }
                }
            }
        }
        if (autoTotem.get()) {
            fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        if (regeneration.get()) {
            fakePlayer.setHealth(fakePlayer.getMaxHealth());
        }
        if (overrideEffect.get()) {
            var effectMap = constantEffects.get();
            Registries.STATUS_EFFECT.streamEntries().forEach(s -> {
                Integer val = effectMap.getEntryValue(s.value());
                if (val != null && val > 0) {
                    if (!fakePlayer.hasStatusEffect(s)) {
                        fakePlayer.setStatusEffect(new StatusEffectInstance(s, 114514, val - 1), null);
                    }
                } else {
                    if (fakePlayer.hasStatusEffect(s)) {
                        fakePlayer.removeStatusEffect(s);
                    }
                }
            });
        }
    }

    public void onDamage(FakePlayerEntity fakePlayer, DamageSource source, float rawDamage) {
        float realDamage = fakePlayer.damage(rawDamage, source);
        if (realDamage > 0 && logHit.get()) {
            log(hitLog.get()
                    .formatText(
                            fakePlayer.getDisplayName(),
                            source.getTypeRegistryEntry()
                                    .getKey()
                                    .get()
                                    .getValue()
                                    .getPath(),
                            rawDamage,
                            realDamage));
        }
    }

    private void onHit(Event<PlayerInteractEntityC2SPacket> interactEntity) {
        if (checkNull()) return;
        if (mc.world.getEntityById(interactEntity.context.entityId) instanceof FakePlayerEntity fake) {
            interactEntity.cancel();
            if (PlayerInteractEntityC2SPacketAccess.of(interactEntity.context).isAttack()) {
                onAttack(fake);
            }
        }
    }

    private void onAttack(FakePlayerEntity fake) {
        ItemStack weapon = mc.player.getStackInHand(Hand.MAIN_HAND);
        // todo: add other types
        float damage =
                DamageUtils.getRealAttackDamage(mc.player, fake, weapon, PlayerStateManager.INSTANCE.fallDistance);
        onDamage(fake, DamageUtils.createDirectDamageSource(DamageTypes.PLAYER_ATTACK, mc.player), damage);
    }

    private void onExplode(Event<ExplosionS2CPacket> event) {
        if (checkNull()) return;
        List<FakePlayerEntity> fakes = new ArrayList<>();
        for (var re : mc.world.getEntities()) {
            if (re instanceof FakePlayerEntity) {
                fakes.add((FakePlayerEntity) re);
            }
        }
        if (fakes.isEmpty()) {
            return;
        }
        Map<BlockPos, BlockState> stateMap = new LinkedHashMap<>();
        Vec3d center = new Vec3d(
            event.context.getX(),
            event.context.getY(),
            event.context.getZ()
        );
        BlockPos explodeCenter = BlockPos.ofFloored(center);
        float radius = 0;
        if (Objects.equals(center, explodeCenter.toCenterPos())
                && mc.world.getBlockState(explodeCenter).getBlock() instanceof RespawnAnchorBlock) {
            stateMap.put(explodeCenter, Blocks.AIR.getDefaultState());
            if (radius <= 0) {
                radius = 6;
            }
        } else {
            if (radius <= 0) {
                radius = 5;
            }
        }
        for (var re : fakes) {
            float damage = ExplosionUtils.calculateExplosionRawDamage(
                    radius,
                    center,
                    re.getBoundingBox(),
                    ExplosionUtils.fromWorldWithOverrides(mc.world, stateMap),
                    ExplosionUtils.ALL_TERRAIN);
            if (damage > 0) {
                onDamage(re, DamageUtils.createDamageSource(DamageTypes.PLAYER_ATTACK, null, mc.player), damage);
            }
        }
    }

    protected Object2LongMap<Entity> piercingCooldowns = new Object2LongOpenHashMap<>();

    private void onTickKinetic(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
        if (SpearEnhance.isUsingSpear(mc.player) && SpearEnhance.canSpearKineticAttack(mc.player)) {
            Vec3d startEye = new Vec3d(
                            PlayerStateManager.INSTANCE.lastX,
                            PlayerStateManager.INSTANCE.lastY,
                            PlayerStateManager.INSTANCE.lastZ)
                    .add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
            Vec3d direction = PlayerStateManager.INSTANCE.getLastRotationVector();
            double minRange = 2.0;
            double maxRange = 4.5;
            Vec3d movement = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed;
            double speedBonus = Math.max(0, movement.dotProduct(direction));
            double finalMaxRange = maxRange + speedBonus;
            Vec3d startPoint = startEye.add(direction.multiply(minRange));
            Vec3d endPoint = startEye.add(direction.multiply(finalMaxRange));
            float hitboxMargin = 0.125F;
            Box box = Box.of(startPoint, (double) hitboxMargin, (double) hitboxMargin, (double) hitboxMargin)
                    .stretch(endPoint.subtract(startPoint))
                    .expand(1.0);
            float max = Math.max(0, hitboxMargin);
            for (var e : mc.world.getOtherEntities(mc.player, box)) {
                if (e instanceof FakePlayerEntity livingEntity) {
                    if (livingEntity
                            .getBoundingBox()
                            .raycast(startPoint, endPoint)
                            .isPresent()) {
                        onSpearKinetic(livingEntity, direction);
                    } else if (max > 0) {
                        var box2 = livingEntity.getBoundingBox().expand(hitboxMargin);
                        var re = box2.raycast(startPoint, endPoint);
                        if (re.isPresent()) {
                            Vec3d vec3d = re.get();
                            Vec3d vec3d2 = box2.getCenter();
                            Optional<Vec3d> optional3 =
                                    livingEntity.getBoundingBox().raycast(vec3d, vec3d2);
                            if (optional3.isPresent()) {
                                onSpearKinetic(livingEntity, direction);
                            }
                        }
                    }
                }
            }
        } else {
            piercingCooldowns.clear();
        }
    }

    public boolean isInPiercingCooldown(Entity target, int cooldownTicks) {
        if (this.piercingCooldowns.isEmpty()) {
            return false;
        } else if (this.piercingCooldowns.containsKey(target)) {
            return mc.world.getTime() - this.piercingCooldowns.getLong(target) < (long) cooldownTicks;
        } else {
            return false;
        }
    }

    public void startPiercingCooldown(Entity target) {
        this.piercingCooldowns.put(target, mc.world.getTime());
    }

    private void onSpearKinetic(FakePlayerEntity fakePlayer, Vec3d rotation) {
        if (isInPiercingCooldown(fakePlayer, 10)) {
            return;
        }
        startPiercingCooldown(fakePlayer);
        double d = rotation.dotProduct(PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.multiply(20.0));
        Vec3d predictorMovement =
                EntityInternalAccess.of(fakePlayer).getPositionPredictor().getKnownDeltaMovement();
        double g = rotation.dotProduct(predictorMovement.multiply(20.0D));
        double h = Math.max(0.0, d - g);
        boolean canDamage = h > 4.6;
        if (!canDamage) {
            return;
        }
        double e = mc.player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        // use netherite spear data
        float damage = (float) (e + MathHelper.floor(h * 1.2F));
        onDamage(
                fakePlayer,
                DamageUtils.createDirectDamageSource(
                        RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.ofVanilla("spear")), mc.player),
                damage);
    }
}
