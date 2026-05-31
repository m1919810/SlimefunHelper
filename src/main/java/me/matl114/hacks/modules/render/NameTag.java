package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        registerListener(RenderListener.getRenderGameHudTasks(), this::onRender);
        registerListener(Listener.getPostTick(), this::onUpdate);
    }

    public final FlagRef enable = flagBuilder(nameTag.addEnable()).build();

    public final DoubleRef height =
            doubleBuilder(nameTag.add("extra-height")).defaultValue(1.0D).build();

    public final DoubleRef size =
            doubleBuilder(nameTag.add("size")).defaultValue(1.0D).build();

    public final FlagRef hideName = flagBuilder(nameTag.add("hide-vanilla")).build();

    public final FlagRef showHealth = flagBuilder(nameTag.add("health")).build();

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

    public final NBTRef<WrapColor> distColor = builder(nameTag.add("distance-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.RED)))
            .build();

    public final NBTRef<WrapColor> popColor = builder(nameTag.add("pop-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Color.ORANGE)))
            .build();

    public final NBTRef<WrapColor> infoColor = builder(nameTag.add("other-info-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.YELLOW)))
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

    public void onUpdate(Event<Void> eventGameUpdate) {
        if (!checkNull() && enable.get()) {
            nameTagInfos = new ArrayList<>();
            for (var player : mc.world.getPlayers()) {
                if (player == mc.player) {
                    continue;
                }
                MutableText text = Text.empty();
                String name = player.getNameForScoreboard();
                if (name == null) continue;
                text.append(
                        player.getDisplayName().copy().withColor(nameColor.get().asRGB()));
                if (showHealth.get()) {
                    text.append(Text.literal(" %d♥".formatted((int) player.getHealth()))
                            .withColor(healthColor.get().asRGB()));
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
                nameTagInfos.add(new PlayerNameTagInfo(player, text, stack5, text2));
            }
        } else {
            nameTagInfos = null;
        }
    }

    public void onRender(Event<VDrawContext> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && nameTagInfos != null) {
            var stack = event.context;
            Matrix4f cam = RenderListener.getWorldModelViewMatrix();
            Matrix4f proj = RenderListener.getWorldBasicProjectionMatrix();
            Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(cam, proj);
            for (var entity : nameTagInfos) {
                onRenderPlayer(entity, stack, projector, (event.<Float>getArgs(0)));
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
                handleNameLine(vdraw, player);
                handleEquipment(vdraw, player);
                handleOtherInfoLine(vdraw, player);
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

    private static final float HEIGHT = 9.0F;

    public void handleSize(VDrawContext vdraw) {
        vdraw.getMatrices().translate(0, -(float) height.get());
        vdraw.getMatrices().scale((float) size.get(), (float) size.get());
    }

    public void handleNameLine(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.nameDisplay != null) {
            float length = player.nameLength;
            float lengthHalf = length / 2.0F;
            vdraw.getMatrices().translate(0, -HEIGHT);
            vdraw.drawText(mc.textRenderer, player.nameDisplay.asOrderedText(), (int) -lengthHalf, 0, -1, true);
        }
    }

    public void handleEquipment(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.equipments != null) {
            ItemStack[] stacks = player.equipments;
            int length = stacks.length * 18;
            int startX = -stacks.length * 9;
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().scale(0.75F, 0.75F);
            for (var i = 0; i < stacks.length; ++i) {
                vdraw.drawItem(stacks[i], startX + i * 18, -17, 999, 0);
                vdraw.drawItemInSlot(mc.textRenderer, stacks[i], startX + i * 18, -17, null);
            }
            vdraw.getMatrices().popMatrix();
            vdraw.getMatrices().translate(0, -HEIGHT * 1.5F);
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

    public static class PlayerNameTagInfo {
        PlayerEntity player;
        Text nameDisplay;
        float nameLength;
        ItemStack[] equipments;
        Text otherInfoDisplay;
        float otherInfoLength;

        public PlayerNameTagInfo(PlayerEntity player, Text display, ItemStack[] equipments, Text otherInfo) {
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
        }
    }
}
