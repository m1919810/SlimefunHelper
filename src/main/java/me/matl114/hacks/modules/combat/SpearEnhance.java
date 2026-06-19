package me.matl114.hacks.modules.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.ResourceUtils;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
        registerListener(RenderListener.getAsyncItemModelSupply(), this::onModelSupply);
        registerListener(RenderListener.getAtlasSourceSupply(), this::onAtlas);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
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
        materialSwordToSpearMap.put(Items.WOODEN_SWORD, new Identifier("slimefunhelper", "spear/wooden_spear"));
        // 石制
        materialSwordToSpearMap.put(Items.STONE_SWORD, new Identifier("slimefunhelper", "spear/stone_spear"));
        // 铁制
        materialSwordToSpearMap.put(Items.IRON_SWORD, new Identifier("slimefunhelper", "spear/iron_spear"));
        // 金制
        materialSwordToSpearMap.put(Items.GOLDEN_SWORD, new Identifier("slimefunhelper", "spear/golden_spear"));
        // 钻石
        materialSwordToSpearMap.put(Items.DIAMOND_SWORD, new Identifier("slimefunhelper", "spear/diamond_spear"));
        // 下界合金
        materialSwordToSpearMap.put(Items.NETHERITE_SWORD, new Identifier("slimefunhelper", "spear/netherite_spear"));
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

    public void onModelSupply(Event<Set<Identifier>> event) {
        event.context()
                .addAll(ResourceUtils.lookupResources(
                        event.getArgs(0),
                        "slimefunhelper",
                        "slimefunhelper",
                        "models",
                        ".json",
                        s -> s.startsWith("spear")));
    }

    public void onReplaceSpearModel(Event<BakedModel> eventIdentifier) {
        if (eventIdentifier.isCancelled() || eventIdentifier.context != null) return;
        if (replaceSpearModel.get()) {
            ItemStack origin = eventIdentifier.getArgs(0);
            if (materialSwordToSpearMap.containsKey(origin.getItem())
                    && VItem.getInstance().isSpear(origin)) {
                Identifier item = materialSwordToSpearMap.get(origin.getItem());
                if (item != null) {
                    var model = RenderListener.getCustomModelOf(item);
                    if (model != null) {
                        eventIdentifier.context(model);
                    }
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
}
