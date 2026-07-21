package me.matl114.hacks.modules.render;

import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.gui.Constants;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Direction2d;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.render.ItemStackDisplayUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class EquipmentHud extends IRender2DModule {
    public final ModulePath invHud = createRoot();

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "in-game-hud.equipment-hud");
    }

    public EquipmentHud() {}

    public EnumRef<ItemStackDisplayUtils.DamageDisplay> damageDisplay = builder(
                    invHud.add("damage-display"), ItemStackDisplayUtils.DamageDisplay.class)
            .defaultValue(ItemStackDisplayUtils.DamageDisplay.NONE)
            .build();

    public EnumRef<Direction2d> displayDirection = builder(invHud.add("damage-display-position"), Direction2d.class)
            .defaultValue(Direction2d.DOWN)
            .build();

    public NBTRef<Vec2> handPos = builder(invHud.add("hand-pos"), Vec2.class)
            .defaultValue(new Vec2(-60.0D, 0.0D))
            .build();
    public NBTRef<Vec2> handPos2 = builder(invHud.add("offhand-pos"), Vec2.class)
            .defaultValue(new Vec2(40.0D, 0.0D))
            .build();
    public NBTRef<Vec2> handPos3 = builder(invHud.add("head-pos"), Vec2.class)
            .defaultValue(new Vec2(-40.0D, 0.0D))
            .build();
    public NBTRef<Vec2> handPos4 = builder(invHud.add("chest-pos"), Vec2.class)
            .defaultValue(new Vec2(-20.0D, 0.0D))
            .build();
    public NBTRef<Vec2> handPos5 = builder(invHud.add("leg-pos"), Vec2.class)
            .defaultValue(new Vec2(0.0D, 0.0D))
            .build();
    public NBTRef<Vec2> handPos6 = builder(invHud.add("feet-pos"), Vec2.class)
            .defaultValue(new Vec2(20.0D, 0.0D))
            .build();

    Map<EquipmentSlot, NBTRef<Vec2>> map = new LinkedHashMap<>();

    {
        map.put(EquipmentSlot.MAINHAND, handPos);
        map.put(EquipmentSlot.OFFHAND, handPos2);
        map.put(EquipmentSlot.HEAD, handPos3);
        map.put(EquipmentSlot.CHEST, handPos4);
        map.put(EquipmentSlot.LEGS, handPos5);
        map.put(EquipmentSlot.FEET, handPos6);
    }

    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void onUpdate(Event<Void> event) {}

    @Override
    public void render2D(VDrawContext vdraw, float partialTicks) {
        for (var re : map.entrySet()) {
            ItemStack stack = mc.player.getEquippedStack(re.getKey());
            {
                var pp = re.getValue().get();
                int startX = (int) pp.x();
                int startY = (int) pp.y();
                if (!stack.isEmpty()) {
                    vdraw.drawItem(stack, startX, startY, 999, 0);
                    vdraw.drawItemInSlot(mc.textRenderer, stack, startX, startY, null);
                    drawDamageIfAbsent(vdraw, stack, startX, startY);
                } else {
                    vdraw.drawGuiTexture(Constants.EMPTY_SLOT_TO_SPRITE.get(re.getKey()), startX, startY, 16, 16);
                }
            }
        }
    }

    private void drawDamageIfAbsent(VDrawContext vdraw, ItemStack stack, int startX, int startY) {
        ItemStackDisplayUtils.DamageDisplay display = damageDisplay.get();
        if (display == ItemStackDisplayUtils.DamageDisplay.NONE) return;
        var damage = stack.getMaxDamage();
        if (damage > 0) {
            Text text = ItemStackDisplayUtils.getDamageShowText(stack, display);
            if (text != null) {
                float len = mc.textRenderer.getTextHandler().getWidth(text);
                int startXX, startYY;
                switch (displayDirection.get()) {
                    case UP -> {
                        startXX = (int) (startX + 8 - ((len - 1) / 2.0F));
                        startYY = startY - 8;
                    }
                    case LEFT -> {
                        startXX = (int) (startX - len);
                        startYY = startY + 4;
                    }
                    case RIGHT -> {
                        startXX = (int) (startX + 16);
                        startYY = startY + 4;
                    }
                    default -> {
                        // down
                        startXX = (int) (startX + 8 - ((len - 1) / 2.0F));
                        startYY = startY + 15;
                    }
                }

                vdraw.drawText(
                        mc.textRenderer,
                        text.asOrderedText(),
                        startXX,
                        startYY,
                        ItemStackDisplayUtils.getDamageDisplayColor(stack),
                        true);
            }
        }
    }
}
