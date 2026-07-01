package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.matl114.SlimefunHelper;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.Constants;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.*;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2d;

public class NameTag extends BaseModule {
    public NameTag() {
        bindFlag(enable);
    }

    public final ModulePath nameTag = makePath(Configs.RENDER_CONFIG, "player-info.name-tag");

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender2DEvent(), this::onRender);
        registerListener(Listener.getPostTick(), this::onUpdate);
    }

    public final FlagRef enable = flagBuilder(nameTag.addEnable()).build();

    public final FlagRef enablePlayer =
            flagBuilder(nameTag.add("show-player-head")).build();

    public final FlagRef enableList =
            flagBuilder(nameTag.add("show-player-list")).build();

    public final DoubleRef height =
            doubleBuilder(nameTag.add("player-extra-height")).defaultValue(1.0D).build();

    public final DoubleRef size =
            doubleBuilder(nameTag.add("player-size")).defaultValue(1.0D).build();

    public FlagRef right = flagBuilder(nameTag.add("list-right")).build();
    public NBTRef<Vec2> pos = builder(nameTag.add("list-pos"), Vec2.class)
            .defaultValue(new Vec2(0.02D, 0.02D))
            .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
            .build();

    public final FlagRef hideName = flagBuilder(nameTag.add("hide-vanilla")).build();

    public final FlagRef showHealth = flagBuilder(nameTag.add("health")).build();

    public final FlagRef showPing = flagBuilder(nameTag.add("ping")).build();

    public final FlagRef dist = flagBuilder(nameTag.add("distance")).build();

    public final FlagRef equipment = flagBuilder(nameTag.add("equipment")).build();

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
    private final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.OFFHAND
    };
    public static final Text DEV_PREFIX = ChatUtils.stringToText(
            "§x§e§b§3§3§e§b§l[§x§d§6§2§6§d§6§lD§x§c§1§1§a§c§1§le§x§a§c§0§d§a§c§lv§x§9§7§0§0§9§7§l]");

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
                    text.append(Text.literal("[F]").withColor(Color.ORANGE.getRGB()));
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

    private static Text getDurationText(int duration) {
        if (duration > Integer.MAX_VALUE - 1) {
            return Text.translatable("effect.duration.infinite");
        } else {
            int i = MathHelper.floor((float) duration);
            return Text.literal(
                    StringHelper.formatTicks(i, mc.world.getTickManager().getTickRate()));
        }
    }

    public void onRender(Event<VDrawContext> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && nameTagInfos != null) {
            var stack = event.context;
            if (enablePlayer.get()) {
                Matrix4f cam = RenderListener.getWorldModelViewMatrix();
                Matrix4f proj = RenderListener.getWorldBasicProjectionMatrix();
                Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(cam, proj);
                for (var entity : nameTagInfos) {
                    if (entity.player != mc.getCameraEntity()) {
                        onRenderPlayer(entity, stack, projector, (event.<Float>getArgs(0)));
                    }
                }
            }
            if (enableList.get()) {
                stack.getMatrices().pushMatrix();
                handleRenderPosition(stack);
                for (var entry : nameTagInfos) {
                    onRenderList(entry, stack, (event.<Float>getArgs(0)));
                    stack.getMatrices().translate(0, HEIGHT);
                }
                stack.getMatrices().popMatrix();
            }
        }
    }

    public void onRenderPlayer(
            PlayerNameTagInfo player, VDrawContext vdraw, Function<Vec3d, Vector2d> projector, float tick) {
        Vec3d pos = player.player.getLerpedPos(tick).add(0, player.player.getHeight() + 0.5, 0);
        Vector2d screenPos = projector.apply(pos);
        if (screenPos != null) {
            vdraw.pushMatrix();
            try {
                vdraw.getMatrices().translate((float) screenPos.x, (float) screenPos.y);
                handleSize(vdraw);
                handleNameLinePlayer(vdraw, player);
                handleEquipmentPlayer(vdraw, player);
                handleOtherInfoLine(vdraw, player);
                handleEffectDisplayPlayer(vdraw, player);
                //               if(screenPos.x == 0F && screenPos.y == 0F){
                //                    vdraw.getMatrices().translate((float) screenPos.x + 1.0F, (float) screenPos.y);
                //                }else{
                //                  // Debug.chat("Print", screenPos);
                //                    vdraw.getMatrices().translate((float) screenPos.x + 1.0F, (float) screenPos.y);
                //               }
                // vdraw.drawText(mc.textRenderer, player.getNameForScoreboard(), 0,0, -1, true);
            } finally {
                vdraw.popMatrix();
            }
        }
    }

    public void handleRenderPosition(VDrawContext vdraw) {
        int sizeX = mc.getWindow().getScaledWidth();
        int sizeY = mc.getWindow().getScaledHeight();
        //        vdraw.pushMatrix();
        //        vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"), sizeX
        // - 30,sizeX, sizeY - 20, sizeY, 0, 0,1,0 , 1);
        //        vdraw.popMatrix();
        var pp = pos.get();
        double xPer = pp.x();
        double yPer = pp.y();
        int startX = (int) (right.get() ? (sizeX - xPer * sizeX) : xPer * sizeX);
        int startY = (int) (yPer * sizeY);
        vdraw.getMatrices().translate(startX, startY);
    }

    public void onRenderList(PlayerNameTagInfo player, VDrawContext vdraw, float tick) {
        vdraw.pushMatrix();
        try {
            handleNameLineList(vdraw, player);
            handleEquipmentList(vdraw, player);
            handleEffectDisplayList(vdraw, player);
        } finally {
            vdraw.popMatrix();
        }
    }

    private static final float HEIGHT = 9.0F;

    public void handleSize(VDrawContext vdraw) {
        vdraw.getMatrices().translate(0, -(float) height.get());
        vdraw.getMatrices().scale((float) size.get(), (float) size.get());
    }

    public void handleNameLinePlayer(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.nameDisplay != null) {
            float length = player.nameLength;
            float lengthHalf = length / 2.0F;
            vdraw.getMatrices().translate(0, -HEIGHT);
            vdraw.drawText(mc.textRenderer, player.nameDisplay.asOrderedText(), (int) -lengthHalf, 0, -1, true);
        }
    }

    public void handleNameLineList(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.nameDisplay != null) {
            float length = player.nameLength;
            if (right.get()) {
                vdraw.getMatrices().translate(-length, 0);
            }
            vdraw.drawText(mc.textRenderer, player.nameDisplay.asOrderedText(), (int) 0, 0, -1, true);
            if (!right.get()) {
                vdraw.getMatrices().translate(length, 0);
            }
        }
    }

    public void handleEquipmentPlayer(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.equipments != null) {
            ItemStack[] stacks = player.equipments;
            int length = stacks.length * 18;
            int startX = -stacks.length * 9;
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().scale(0.75F, 0.75F);
            for (var i = 0; i < stacks.length; ++i) {
                if (stacks[i].isEmpty()) {
                    EquipmentSlot slot = SLOTS[i];
                    vdraw.drawGuiTexture(Constants.EMPTY_SLOT_TO_SPRITE.get(slot), startX + i * 18, -17, 16, 16);
                } else {
                    vdraw.drawItem(stacks[i], startX + i * 18, -17, 999, 0);
                    vdraw.drawItemInSlot(mc.textRenderer, stacks[i], startX + i * 18, -17, null);
                }
            }
            vdraw.getMatrices().popMatrix();
            vdraw.getMatrices().translate(0, -HEIGHT * 1.5F);
        }
    }

    public void handleEquipmentList(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.equipments != null) {
            ItemStack[] stacks = player.equipments;
            if (right.get()) {
                vdraw.getMatrices().translate(-(stacks.length * 9), 0);
            }
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().scale(9 / 16.0F, 9 / 16.0F);
            for (var i = 0; i < stacks.length; ++i) {
                if (stacks[i].isEmpty()) {
                    EquipmentSlot slot = SLOTS[i];
                    vdraw.drawGuiTexture(Constants.EMPTY_SLOT_TO_SPRITE.get(slot), i * 16, 0, 16, 16);
                } else {
                    vdraw.drawItem(stacks[i], i * 16, 0, 999, 0);
                    vdraw.drawItemInSlot(mc.textRenderer, stacks[i], i * 16, 0, null);
                }
            }
            vdraw.getMatrices().popMatrix();
            if (!right.get()) {
                vdraw.getMatrices().translate((stacks.length * 9), 0);
            }
        }
    }

    public void handleOtherInfoLine(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.otherInfoDisplay != null) {
            float length = player.otherInfoLength;
            float lengthHalf = length / 2.0F;
            vdraw.getMatrices().translate(0, -HEIGHT);
            vdraw.drawText(mc.textRenderer, player.otherInfoDisplay.asOrderedText(), (int) -lengthHalf, 0, -1, true);
        }
    }

    private static final RegistryDisplays.IIcon<StatusEffect> statusEffectRenderer =
            RegistryDisplays.getIcon(StatusEffect.class);

    public void handleEffectDisplayPlayer(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.visibleEffects != null) {
            List<Map.Entry<RegistryEntry<StatusEffect>, Text>> line =
                    player.visibleEffects.entrySet().stream().toList();
            int size = line.size();
            for (var i = 0; i < size; i += 3) {
                int endI = Math.min(i + 3, size);
                if (endI == i) continue;
                float width = (endI - i - 1);
                for (var j = i; j < endI; j++) {
                    width += 9;
                    width += mc.textRenderer
                            .getTextHandler()
                            .getWidth(line.get(j).getValue());
                }
                vdraw.getMatrices().translate(0, -HEIGHT);
                vdraw.getMatrices().pushMatrix();
                {
                    vdraw.getMatrices().translate(-width / 2, 0);
                    for (var j = i; j < endI; j++) {
                        var entry = line.get(j);
                        vdraw.getMatrices().pushMatrix();
                        {
                            vdraw.getMatrices().scale(0.5F, 0.5F);
                            statusEffectRenderer.render(
                                    1, 1, vdraw, entry.getKey().value());
                        }
                        vdraw.getMatrices().popMatrix();
                        vdraw.getMatrices().translate(9, 0);
                        vdraw.drawText(
                                mc.textRenderer,
                                entry.getValue().asOrderedText(),
                                0,
                                0,
                                potionColor.get().withAlpha(255),
                                true);
                        vdraw.getMatrices()
                                .translate(
                                        mc.textRenderer
                                                        .getTextHandler()
                                                        .getWidth(line.get(j).getValue())
                                                + 1,
                                        0);
                    }
                }
                vdraw.getMatrices().popMatrix();
            }
        }
    }

    public void handleEffectDisplayList(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.visibleEffects != null) {
            List<Map.Entry<RegistryEntry<StatusEffect>, Text>> line =
                    player.visibleEffects.entrySet().stream().toList();
            int size = line.size();
            for (var i = 0; i < size; i++) {
                var entry = line.get(i);
                float len = mc.textRenderer.getTextHandler().getWidth(entry.getValue());
                if (right.get()) {
                    vdraw.getMatrices().translate(-9 - len, 0);
                }
                vdraw.getMatrices().pushMatrix();
                vdraw.getMatrices().scale(9 / 16.0F, 9 / 16.0F);
                statusEffectRenderer.render(0, 0, vdraw, entry.getKey().value());
                vdraw.getMatrices().popMatrix();
                vdraw.drawText(
                        mc.textRenderer,
                        entry.getValue().asOrderedText(),
                        9,
                        0,
                        potionColor.get().withAlpha(255),
                        true);

                if (!right.get()) {
                    vdraw.getMatrices().translate(9 + len, 0);
                }
            }
        }
    }

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
