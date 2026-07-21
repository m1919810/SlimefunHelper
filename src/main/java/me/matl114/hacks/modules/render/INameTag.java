package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.SlimefunHelper;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.ItemStackDisplayUtils;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;

public abstract class INameTag extends BaseModule {
    public INameTag() {
        bindFlag(enable);
    }

    public final ModulePath nameTag = createRoot();

    protected abstract ModulePath createRoot();

    public final FlagRef enable = flagBuilder(nameTag.addEnable()).build();

    protected abstract void initializeModuleSettings();

    {
        initializeModuleSettings();
    }

    public final FlagRef showHealth = flagBuilder(nameTag.add("health")).build();

    public final FlagRef showPing = flagBuilder(nameTag.add("ping")).build();

    public final FlagRef dist = flagBuilder(nameTag.add("distance")).build();

    public final FlagRef equipment = flagBuilder(nameTag.add("equipment")).build();

    public final EnumRef<ItemStackDisplayUtils.DamageDisplay> equipmentPercentage = builder(
                    nameTag.add("equipment-damage"), ItemStackDisplayUtils.DamageDisplay.class)
            .defaultValue(ItemStackDisplayUtils.DamageDisplay.NONE)
            .build();

    public final FlagRef pop = flagBuilder(nameTag.add("pop")).build();

    public final FlagRef enchantmentSum =
            flagBuilder(nameTag.add("enchant-protection-sum")).build();

    public final FlagRef potion = flagBuilder(nameTag.add("potion")).build();

    public final NBTRef<WrapColor> nameColor = builder(nameTag.add("name-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.WHITE)))
            .build();

    public final NBTRef<WrapColor> healthColor = builder(nameTag.add("health-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(new Color(20, 170, 170))))
            .build();

    public final NBTRef<WrapColor> pingColor = builder(nameTag.add("ping-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.GREEN)))
            .build();

    public final NBTRef<WrapColor> distColor = builder(nameTag.add("distance-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.RED)))
            .build();

    public final NBTRef<WrapColor> popColor = builder(nameTag.add("pop-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Color.ORANGE)))
            .build();

    public final NBTRef<WrapColor> infoColor = builder(nameTag.add("other-info-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.YELLOW)))
            .build();

    public final NBTRef<WrapColor> potionColor = builder(nameTag.add("potion-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.WHITE)))
            .build();
    List<PlayerNameTagInfo> nameTagInfos;
    protected static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.OFFHAND
    };
    protected static final float HEIGHT = 9.0F;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender2DEvent(), this::onRender);
        registerListener(Listener.getPostTick(), this::onUpdate);
    }

    public static final Text DEV_PREFIX = ChatUtils.stringToText(
            "§x§e§b§3§3§e§b§l[§x§d§6§2§6§d§6§lD§x§c§1§1§a§c§1§le§x§a§c§0§d§a§c§lv§x§9§7§0§0§9§7§l]");

    private static Text getDurationText(int duration) {
        if (duration > Integer.MAX_VALUE - 1) {
            return Text.translatable("effect.duration.infinite");
        } else {
            int i = MathHelper.floor((float) duration);
            return Text.literal(
                    StringHelper.formatTicks(i, mc.world.getTickManager().getTickRate()));
        }
    }

    public void onUpdate(Event<Void> eventGameUpdate) {
        if (!checkNull() && enable.get()) {
            nameTagInfos = new ArrayList<>();
            for (var player : mc.world.getPlayers()) {
                if (!(player instanceof ClientPlayerEntity) && !(player instanceof OtherClientPlayerEntity)) {
                    continue;
                }
                MutableText text = Text.empty();
                String name = player.getNameForScoreboard();
                if (name == null) continue;
                if (SlimefunHelper.DEV_NAME.contains(name)) {
                    text.append(DEV_PREFIX);
                }
                if (TargetSelector.INSTANCE.isInFriendList(player)) {
                    text.append(ChatUtils.stringToText("&x&F&F&A&C&0&0["
                            + TargetSelector.INSTANCE.getPlayerList().getFriendAlias(player.getNameForScoreboard())
                            + "&x&F&F&A&C&0&0]"));
                }
                if (player.isCreative()) {
                    text.append(Text.literal("[C]").withColor(Color.RED.getRGB()));
                }

                text.append(
                        player.getDisplayName().copy().withColor(nameColor.get().asRGB()));
                if (showHealth.get()) {
                    text.append(Text.literal(" %d♥".formatted((int) player.getHealth()))
                            .withColor(healthColor.get().asRGB()));
                }
                if (showPing.get()) {
                    int latency = 0;
                    PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                    if (entry != null) {
                        latency = entry.getLatency();
                    }
                    text.append(Text.literal(" %dms".formatted(latency))
                            .withColor(pingColor.get().asRGB()));
                }
                if (dist.get()) {
                    double len = mc.player.getPos().distanceTo(player.getPos());
                    text.append(Text.literal(" d:%.1fm".formatted(len))
                            .withColor(distColor.get().asRGB()));
                }
                if (pop.get()) {
                    int popCnt = PlayerStateManager.INSTANCE.getPlayerPopCount(player);
                    if (popCnt > 0) {
                        text.append(Text.literal(" -%d".formatted(popCnt))
                                .withColor(popColor.get().asRGB()));
                    }
                }

                ItemStack[] stack5 = null;
                if (equipment.get()) {
                    stack5 = new ItemStack[6];
                    boolean hasNoEmpty = false;
                    for (int i = 0; i < 6; ++i) {
                        var re = player.getEquippedStack(SLOTS[i]);
                        stack5[i] = re;
                        if (!re.isEmpty()) {
                            hasNoEmpty = true;
                        }
                    }
                    if (!hasNoEmpty) {
                        stack5 = null;
                    }
                }

                List<Text> subTexts = new ArrayList<>();
                if (enchantmentSum.get()) {
                    PlayerStateManager.PlayerStatus status = PlayerStateManager.INSTANCE.getPlayerStatus(player);
                    if (status != null) {
                        if (status.protection > 0) {
                            subTexts.add(Text.literal("保护%d".formatted(status.protection))
                                    .withColor(infoColor.get().asRGB()));
                        }
                        if (status.blastProtection > 0) {
                            subTexts.add(Text.literal("爆炸%d".formatted(status.blastProtection))
                                    .withColor(infoColor.get().asRGB()));
                        }
                    }
                }
                MutableText text2;
                if (subTexts.isEmpty()) {
                    text2 = null;
                } else {
                    text2 = Text.empty();
                    boolean first = true;
                    for (var re : subTexts) {
                        if (first) {
                            first = false;
                        } else {
                            text2.append(Text.literal(" "));
                        }
                        text2.append(re);
                    }
                }
                Map<RegistryEntry<StatusEffect>, Text> visible = null;
                if (potion.get()) {
                    PlayerStateManager.PlayerStatus status = PlayerStateManager.INSTANCE.getPlayerStatus(player);
                    if (status != null) {
                        var map = status.visibleStatusEffects;
                        visible = new LinkedHashMap<>(map.size());
                        for (var entry : map.entrySet()) {
                            if (entry.getValue().visible) {
                                visible.put(
                                        entry.getKey(),
                                        getDurationText(entry.getValue().getRemainDurations()));
                            }
                        }
                    }
                }
                nameTagInfos.add(new PlayerNameTagInfo(player, text, stack5, text2, visible));
            }
        } else {
            nameTagInfos = null;
        }
    }

    public abstract void onRender(Event<VDrawContext> event);

    protected static final RegistryDisplays.IIcon<StatusEffect> statusEffectRenderer =
            RegistryDisplays.getIcon(StatusEffect.class);

    public static class PlayerNameTagInfo {
        PlayerEntity player;
        Text nameDisplay;
        float nameLength;
        ItemStack[] equipments;
        Text otherInfoDisplay;
        float otherInfoLength;
        Map<RegistryEntry<StatusEffect>, Text> visibleEffects;

        public PlayerNameTagInfo(
                PlayerEntity player,
                Text display,
                ItemStack[] equipments,
                Text otherInfo,
                Map<RegistryEntry<StatusEffect>, Text> effects) {
            this.player = player;
            this.nameDisplay = display;
            this.nameLength =
                    display == null ? 0.0F : mc.textRenderer.getTextHandler().getWidth(display);
            if (this.nameLength <= 0.0F) {
                this.nameDisplay = null;
            }
            this.equipments = equipments;
            this.otherInfoDisplay = otherInfo;
            this.otherInfoLength =
                    otherInfo == null ? 0.0F : mc.textRenderer.getTextHandler().getWidth(otherInfo);
            if (this.otherInfoLength <= 0.0F) {
                this.otherInfoDisplay = null;
            }
            visibleEffects = effects;
        }
    }
}
