package me.matl114.hacks.modules.combat;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import lombok.Setter;
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.UseItem;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.hooks.ViaProtocols;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.NetworkUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.ResourceUtils;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableInt;

public class SpearEnhance extends BaseModule {
    public static SpearEnhance INSTANCE;
    public final ModulePath spearModule = makePath(Configs.COMBAT_CONFIG, "spear-module");

    public SpearEnhance() {
        super("SpearEnhance");
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

    public final FlagRef fixOldVersionSpearSound = builder(
                    spearModule.add("fix-old-version-spear-sound"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<WrapColor> renderColor = builder(
                    spearModule.add("render-kinetic-players-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.YELLOW)))
            .build();

    public final FlagRef spearSpeedReset =
            flagBuilder(spearModule.add("reset-spear-speed-rot-enable")).build();

    public final KeyBindRef spearSpeedRotReset = moduleEntry(
                    spearModule.add("reset-spear-speed-rot-hotkey"),
                    new MultiKeyBind(),
                    spearModule.add("reset-spear-speed-rot-enable"))
            .build();

    public final FlagRef spearSpeedResetAuto =
            flagBuilder(spearModule.add("reset-spear-speed-auto")).build();

    public final FlagRef spearSpeedResetTargetJudge =
            flagBuilder(spearModule.add("reset-spear-speed-only-combat")).build();

    public final FlagRef autoFocusTarget =
            flagBuilder(spearModule.add("reset-spear-auto-focus-target")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(RenderListener.getCustomModelOverride(), this::onReplaceSpearModel);
        registerListener(Listener.getClientPlayerPostSendMovementPoint(), this::onPostTick);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onUsePiercing);
        registerListener(Listener.getAttackAction(), this::onUsingStab);
        // registerListener(RenderListener.getAsyncItemModelSupply(), this::onModelSupply);
        registerListener(RenderListener.getAtlasSourceSupply(), this::onAtlas);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onSpearEntity);
        registerListener(Listener.getPostPlayerUseItem(), this::onSpearUse);
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
                var render = RenderCollectors.createBoxCollector(false, true, false);
                for (var re : mc.world.getPlayers()) {
                    if (re != mc.getCameraEntity()) {
                        if (canSpearKineticAttack(re)) {
                            render.submit(re.getBoundingBox(), color);
                        }
                    }
                }
                render.render3D(event.context);
                render.clear();
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public static boolean canSpearKineticAttack(PlayerEntity player) {
        if (isUsingSpear(player)) {
            if (mc.player.getItemUseTime() < 8) {
                return false;
            }
            ItemStack stack = getSpear();
            int maxKineticTime = getMaxKineticTime(stack);
            return player.getItemUseTime() < maxKineticTime;
        }
        return false;
    }

    public static boolean canSpearKineticAttack() {
        return canSpearKineticAttack(mc.player);
    }

    public static int getMaxKineticTime(ItemStack stack) {
        if (!VItem.getInstance().isSpear(stack)) {
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

    // map 声明改为
    private final Map<Item, Identifier> materialSwordToSpearMap = new HashMap<>();

    // 初始化
    {
        // 木制
        materialSwordToSpearMap.put(Items.WOODEN_SWORD, new Identifier("slimefunhelper", "wooden_spear"));
        // 石制
        materialSwordToSpearMap.put(Items.STONE_SWORD, new Identifier("slimefunhelper", "stone_spear"));
        // 铁制
        materialSwordToSpearMap.put(Items.IRON_SWORD, new Identifier("slimefunhelper", "iron_spear"));
        // 金制
        materialSwordToSpearMap.put(Items.GOLDEN_SWORD, new Identifier("slimefunhelper", "golden_spear"));
        // 钻石
        materialSwordToSpearMap.put(Items.DIAMOND_SWORD, new Identifier("slimefunhelper", "diamond_spear"));
        // 下界合金
        materialSwordToSpearMap.put(Items.NETHERITE_SWORD, new Identifier("slimefunhelper", "netherite_spear"));
    }

    public void onAtlas(Event<Set<Identifier>> event) {
        if (event.getArgs(1).equals(new Identifier("minecraft", "blocks"))) {
            event.context()
                    .addAll(ResourceUtils.lookupResources(
                            event.getArgs(0),
                            "slimefunhelper",
                            "slimefunhelper",
                            "textures",
                            ".png",
                            s -> s.startsWith("spear")));
        }
    }

    //    public void onModelSupply(Event<Set<Identifier>> event) {
    //        event.context()
    //                .addAll(ResourceUtils.lookupResources(
    //                        event.getArgs(0),
    //                        "slimefunhelper",
    //                        "slimefunhelper",
    //                        "models",
    //                        ".json",
    //                        s -> s.startsWith("spear")));
    //    }

    public void onReplaceSpearModel(Event<Identifier> eventIdentifier) {
        if (eventIdentifier.isCancelled() || eventIdentifier.context != null) return;
        if (replaceSpearModel.get()) {
            ItemStack origin = eventIdentifier.getArgs(0);
            if (materialSwordToSpearMap.containsKey(origin.getItem())
                    && VItem.getInstance().isSpear(origin)) {
                Identifier item = materialSwordToSpearMap.get(origin.getItem());
                if (item != null) {
                    eventIdentifier.context(item);
                }
            }
        }
    }

    private static final String META_DATA_SPEAR_LAST_KINETIC_TIME = "slimefunhelper:spear_module/last_kinetic_time";

    public void onEntityStatus(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        if (event.context.getStatus() == VDataFlag.ENTITY_STATUS_KINETIC_ATTACK
                && event.context.getEntity(mc.world) instanceof PlayerEntity pl
                && pl instanceof MetadataHolder md) {
            md.getMetadata().put(this, META_DATA_SPEAR_LAST_KINETIC_TIME, mc.world.getTime());
        }
    }

    public long getLastKineticTime(Entity player) {
        if (player instanceof MetadataHolder md
                && !md.isMetaEmpty()
                && md.getMetadata().get(this, META_DATA_SPEAR_LAST_KINETIC_TIME) instanceof Number nb) {
            return nb.longValue();
        }
        return -2147483648L;
    }

    public float getTimeSinceLastKineticAttack(Entity pl, float tickProgress) {
        var lastKineticTime = getLastKineticTime(pl);
        return lastKineticTime < 0L ? 0.0F : (float) (mc.world.getTime() - lastKineticTime) + tickProgress;
    }

    public boolean hasRealComponent(ItemStack stack) {
        Item item = stack.getItem();
        Identifier trans = materialSwordToSpearMap.get(item);
        if (trans != null && VItem.getInstance().isSpear(stack)) {
            return true;
        }
        return false;
    }

    @Setter
    boolean forceSpearReset = false;

    public void onPostTick(Event<ClientPlayerEntity> eventPostTick) {
        if (checkNull()) return;
        if (eventPostTick.context == mc.player
                && (spearSpeedReset.get() || forceSpearReset)
                && ViaFabricPlusHooks.isSupportDupRot()) {
            forceSpearReset = false;
            boolean autoCondition = true;

            if (spearSpeedResetAuto.get()) {
                // 长矛使用时自动关闭啥比玩意免得我忘了
                if (isUsingSpear(mc.player)) {
                    autoCondition = false;
                }
            }
            if (spearSpeedResetTargetJudge.get()) {
                boolean anyMatch = TargetSelector.INSTANCE.getAttackableEntities(25).stream()
                        .anyMatch(s -> s instanceof PlayerEntity pl && isUsingSpear(pl));
                if (!anyMatch) {
                    autoCondition = false;
                }
            }
            if (autoCondition) {
                Vec3d look = PlayerStateManager.INSTANCE.getLastRotationVector();
                if (autoFocusTarget.get() && isUsingSpear(mc.player)) {
                    Entity targetEntity =
                            TargetSelector.INSTANCE.searchAttackEntity(30, true, pl -> pl instanceof PlayerEntity);
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
                // reset speed and rotation
                LegacySnapRotManager.INSTANCE.snapAt(look, true);
            }
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

    private static RegistryEntry<SoundEvent> tryRegisterSound(String sytr) {
        try {
            Identifier sound = Identifier.ofVanilla(sytr);
            return Registry.registerReference(Registries.SOUND_EVENT, sound, SoundEvent.of(sound));
        } catch (Throwable e) {
            return null;
        }
    }

    private static final Optional<RegistryEntry<SoundEvent>> currentSpearHitSoundEvent =
            Optional.ofNullable(tryRegisterSound("item.spear.hit"));
    private static final Optional<RegistryEntry<SoundEvent>> currentSpearUseSoundEvent =
            Optional.ofNullable(tryRegisterSound("item.spear.use"));

    public void onSpearEntity(Event<EntityStatusS2CPacket> eventPost) {
        if (checkNull()) return;
        if (fixOldVersionSpearSound.get() && eventPost.context.getStatus() == VDataFlag.ENTITY_STATUS_KINETIC_ATTACK) {
            Entity entity = eventPost.context.getEntity(mc.world);
            if (entity instanceof LivingEntity lv && lv.isUsingItem()) {
                ItemStack stack = lv.getActiveItem();
                if (materialSwordToSpearMap.containsKey(stack.getItem())
                        && VItem.getInstance().isSpear(stack)) {
                    currentSpearHitSoundEvent.ifPresent((hitSound) -> {
                        mc.world.playSoundFromEntity(lv, hitSound.value(), entity.getSoundCategory(), 1.0F, 1.0F);
                    });
                }
            }
        }
    }

    public void onSpearUse(Event<UseItem> eventAction) {
        if (checkNull()) return;
        if (fixOldVersionSpearSound.get()) {
            Hand hand = eventAction.context.hand();
            ItemStack stack = mc.player.getStackInHand(hand);
            if (materialSwordToSpearMap.containsKey(stack.getItem())
                    && VItem.getInstance().isSpear(stack)) {
                MutableInt mutableInt = new MutableInt(0);
                Tasks.scheduleRepeatedPre(
                        () -> {
                            if (mutableInt.getAndIncrement() > 20) {
                                return true;
                            }
                            if (checkNull()) return true;
                            if (mc.player.isUsingItem()) {
                                if (mc.player.getActiveHand() == hand
                                        && ItemStack.areItemsAndComponentsEqual(stack, mc.player.getActiveItem())) {
                                    currentSpearUseSoundEvent.ifPresent((sound) -> {
                                        mc.player
                                                .getEntityWorld()
                                                .playSound(
                                                        mc.player,
                                                        mc.player.getX(),
                                                        mc.player.getY(),
                                                        mc.player.getZ(),
                                                        sound,
                                                        mc.player.getSoundCategory(),
                                                        1.0F,
                                                        1.0F);
                                    });
                                }
                                return true;
                            } else {
                                return false;
                            }
                        },
                        1,
                        1);
            }
        }
    }
}
