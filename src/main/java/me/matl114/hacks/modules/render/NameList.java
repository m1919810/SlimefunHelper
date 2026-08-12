package me.matl114.hacks.modules.render;

import java.util.List;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.gui.Constants;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.render.ItemStackDisplayUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public class NameList extends INameTag {
    public NameList() {
        super("NameList");
    }

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "player-info.name-list");
    }

    @Override
    protected void initializeModuleSettings() {
        playerListMaxLength = builder(nameTag.add("player-list-max-length"), IntRef.TYPE)
                .defaultValue(20)
                .build();
        right = flagBuilder(nameTag.add("list-right")).build();
        pos = builder(nameTag.add("list-pos"), Vec2.class)
                .defaultValue(new Vec2(0.02D, 0.02D))
                .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
                .build();
    }

    public IntRef playerListMaxLength;

    public FlagRef right;
    public NBTRef<Vec2> pos;

    @Override
    public void onRender(Event<VDrawContext> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && nameTagInfos != null) {
            var stack = event.context;
            stack.getMatrices().pushMatrix();
            handleRenderPosition(stack);
            int count = 0;
            for (var entry : nameTagInfos) {
                if (count >= playerListMaxLength.get()) {
                    handleTooManyPlayerList(stack);
                    break;
                }
                onRenderList(entry, stack, (event.<Float>getArgs(0)));
                stack.getMatrices().translate(0, HEIGHT);
                count += 1;
            }
            stack.getMatrices().popMatrix();
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
            handleOtherInfo(vdraw, player);
            handleEffectDisplayList(vdraw, player);
        } finally {
            vdraw.popMatrix();
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
                    String countOverride = null;
                    ItemStack stackOverride = stacks[i];
                    if (equipmentPercentage.get().isNotIn(ItemStackDisplayUtils.DamageDisplay.NONE)
                            && stackOverride.getCount() == 1
                            && stackOverride.isDamageable()) {

                        countOverride = ItemStackDisplayUtils.getDamageShowText(
                                        stackOverride, equipmentPercentage.get())
                                .getString();
                        stackOverride = stackOverride.copy();
                        stackOverride.setDamage(0);
                    }
                    vdraw.drawItemInSlot(mc.textRenderer, stackOverride, i * 16, 0, countOverride);
                }
            }
            vdraw.getMatrices().popMatrix();
            if (!right.get()) {
                vdraw.getMatrices().translate((stacks.length * 9), 0);
            }
        }
    }

    public void handleOtherInfo(VDrawContext vdraw, PlayerNameTagInfo player) {
        if (player.otherInfoDisplay != null) {
            float length = player.otherInfoLength;
            if (right.get()) {
                vdraw.getMatrices().translate(-length, 0);
            }
            vdraw.drawText(mc.textRenderer, player.otherInfoDisplay.asOrderedText(), (int) 0, 0, -1, true);
            if (!right.get()) {
                vdraw.getMatrices().translate(length, 0);
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

    public void handleTooManyPlayerList(VDrawContext vdraw) {
        vdraw.pushMatrix();
        OrderedText display = Text.literal("......(" + (nameTagInfos.size() - playerListMaxLength.get()) + " more)")
                .asOrderedText();
        float length = mc.textRenderer.getTextHandler().getWidth(display);
        if (right.get()) {
            vdraw.getMatrices().translate(-length, 0);
        }
        vdraw.drawText(mc.textRenderer, display, (int) 0, 0, -1, true);
        vdraw.popMatrix();
    }
}
