package me.matl114.hacks.modules.render;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render2D;
import me.matl114.gui.Constants;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.render.ItemStackDisplayUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2d;

public class NameTag extends INameTag {
    public NameTag() {
        super("NameTag");
    }

    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "player-info.name-tag");
    }

    protected void initializeModuleSettings() {
        height = doubleBuilder(nameTag.add("player-extra-height"))
                .defaultValue(1.0D)
                .build();
        size = doubleBuilder(nameTag.add("player-size")).defaultValue(1.0D).build();
        hideName = flagBuilder(nameTag.add("hide-vanilla")).build();
    }

    //
    //    public final FlagRef enablePlayer =
    //            flagBuilder(nameTag.add("show-player-head")).build();
    //
    //    public final FlagRef enableList =
    //            flagBuilder(nameTag.add("show-player-list")).build();

    public DoubleRef height;

    public DoubleRef size;

    public FlagRef hideName;

    public void onRender(Event<Render2D> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && nameTagInfos != null) {
            var stack = event.context.drawContext();
            Matrix4f cam = RenderListener.getWorldModelViewMatrix();
            Matrix4f proj = RenderListener.getWorldBasicProjectionMatrix();
            Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(cam, proj);
            for (var entity : nameTagInfos) {
                if (entity.player != mc.getCameraEntity()) {
                    onRenderPlayer(entity, stack, projector, (event.context.partialTicks()));
                }
            }
        }
    }

    public void onRenderPlayer(
            PlayerNameTagInfo player, VDrawContext vdraw, Function<Vec3d, Vector2d> projector, float tick) {
        Vec3d pos = player.player.getLerpedPos(tick).add(0, player.player.getHeight() + 0.5, 0);
        Vector2d screenPos = projector.apply(pos);
        if (screenPos != null) {
            vdraw.getMatrices().pushMatrix();
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
                vdraw.getMatrices().popMatrix();
            }
        }
    }

    public void handleSize(VDrawContext vdraw) {
        vdraw.getMatrices().translate(0, -(float) height.get());
        vdraw.getMatrices().scale((float) size.get(), (float) size.get());
    }

    public void handleNameLinePlayer(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.nameDisplay != null) {
            float textHeight = vdraw.getTextDrawer().getTextHeight() + 1;
            float length = player.nameLength;
            float lengthHalf = length / 2.0F;
            vdraw.getMatrices().translate(0, -textHeight);
            vdraw.drawText(vdraw.getTextDrawer(), player.nameDisplay.asOrderedText(), (int) -lengthHalf, 0, -1, false);
        }
    }

    public void handleEquipmentPlayer(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.equipments != null) {
            float textHeight = vdraw.getTextDrawer().getTextHeight() + 1;
            ItemStack[] stacks = player.equipments;
            int startX = -stacks.length * 9;
            boolean hasDurability = false;
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().scale(0.75F, 0.75F);
            for (var i = 0; i < stacks.length; ++i) {
                if (stacks[i].isEmpty()) {
                    EquipmentSlot slot = SLOTS[i];
                    vdraw.drawGuiTexture(Constants.EMPTY_SLOT_TO_SPRITE.get(slot), startX + i * 18, -17, 16, 16);
                } else {
                    vdraw.drawItem(stacks[i], startX + i * 18, -17, 999, 0);
                    ItemStack stackOverride = stacks[i];
                    vdraw.drawItemInSlot(vdraw.getTextDrawer(), stackOverride, startX + i * 18, -17, null);
                    if (equipmentPercentage.get().isNotIn(ItemStackDisplayUtils.DamageDisplay.NONE)
                            && stackOverride.getCount() == 1
                            && stackOverride.isDamageable()) {
                        hasDurability = true;
                        OrderedText dur = ItemStackDisplayUtils.getDamageShowText(
                                        stackOverride, equipmentPercentage.get())
                                .asOrderedText();
                        float width = vdraw.getTextDrawer().getWidth(dur);
                        int color = ItemStackDisplayUtils.getDamageDisplayColor(stackOverride);
                        vdraw.drawText(
                                vdraw.getTextDrawer(),
                                dur,
                                startX + i * 18 + 9 + (int) ((-width - 1) / 2),
                                -16 - (int) textHeight,
                                color,
                                true);
                    }
                }
            }
            vdraw.getMatrices().popMatrix();
            vdraw.getMatrices().translate(0, hasDurability ? -textHeight * 2.0F : -textHeight * 1.5F);
        }
    }

    public void handleOtherInfoLine(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.otherInfoDisplay != null) {
            float textHeight = vdraw.getTextDrawer().getTextHeight() + 1;
            float length = player.otherInfoLength;
            float lengthHalf = length / 2.0F;
            vdraw.getMatrices().translate(0, -textHeight);
            vdraw.getMatrices().pushMatrix();
            vdraw.drawText(
                    vdraw.getTextDrawer(), player.otherInfoDisplay.asOrderedText(), (int) -lengthHalf, 0, -1, false);
            vdraw.getMatrices().popMatrix();
        }
    }

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
                    width += vdraw.getTextDrawer().getWidth(line.get(j).getValue());
                }
                vdraw.getMatrices().translate(0, -9);
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
                                vdraw.getTextDrawer(),
                                entry.getValue().asOrderedText(),
                                0,
                                (int) ((9 - vdraw.getTextDrawer().getTextHeight()) / 2.0F),
                                potionColor.get().withAlpha(255),
                                true);
                        vdraw.getMatrices()
                                .translate(
                                        vdraw.getTextDrawer()
                                                        .getWidth(line.get(j).getValue())
                                                + 1,
                                        0);
                    }
                }
                vdraw.getMatrices().popMatrix();
            }
        }
    }
}
