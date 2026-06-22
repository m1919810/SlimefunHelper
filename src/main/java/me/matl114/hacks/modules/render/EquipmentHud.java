package me.matl114.hacks.modules.render;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.gui.Constants;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Direction2d;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

public class EquipmentHud extends BaseModule {
    public final ModulePath invHud = makePath(Configs.RENDER_CONFIG, "in-game-hud.equipment-hud");

    public EquipmentHud() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(invHud.addEnable()).build();

    public KeyBindRef keyBind = toggleHotkey(invHud.add("hotkey"), new MultiKeyBind(), invHud.add("enable"))
            .build();
    public EnumRef<DamageDisplay> damageDisplay = builder(invHud.add("damage-display"), DamageDisplay.class)
            .defaultValue(DamageDisplay.NONE)
            .build();

    public EnumRef<Direction2d> displayDirection = builder(invHud.add("damage-display-position"), Direction2d.class)
            .defaultValue(Direction2d.DOWN)
            .build();

    public NBTRef<Vec2> pos = builder(invHud.add("pos"), Vec2.class)
            .defaultValue(new Vec2(0.5D, 0.8D))
            .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
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
        registerListener(RenderListener.getRenderGameHudTasks(), this::onRender);
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
        int startX = (int) (xPer * sizeX);
        int startY = (int) (yPer * sizeY);
        vdraw.getMatrices().translate(startX, startY);
    }

    private void onRender(Event<VDrawContext> event) {
        if (checkNull()) return;
        if (enable.get() && !event.<Boolean>getArgs(1)) {
            VDrawContext vdraw = event.context;

            //        vdraw.pushMatrix();
            //        vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"),
            // sizeX
            // - 30,sizeX, sizeY - 20, sizeY, 0, 0,1,0 , 1);
            //        vdraw.popMatrix();

            vdraw.pushMatrix();
            try {
                handleRenderPosition(vdraw);
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
                            vdraw.drawGuiTexture(
                                    Constants.EMPTY_SLOT_TO_SPRITE.get(re.getKey()), startX, startY, 16, 16);
                        }
                    }
                }
            } finally {
                vdraw.popMatrix();
            }
        }
    }

    private void drawDamageIfAbsent(VDrawContext vdraw, ItemStack stack, int startX, int startY) {
        DamageDisplay display = damageDisplay.get();
        if (display == DamageDisplay.NONE) return;
        var damage = stack.getMaxDamage();
        if (damage > 0) {
            int damage2 = stack.getDamage();
            int damageLeft = damage - damage2;
            Text text =
                    switch (display) {
                        case DAMAGE -> {
                            yield Text.literal("-%d".formatted(damage2));
                        }
                        case DAMAGE_LEFT -> {
                            yield Text.literal("%d".formatted(damageLeft));
                        }
                        case PERCENTAGE -> {
                            yield Text.literal("%d%%".formatted((damageLeft * 100) / damage));
                        }
                        default -> {
                            yield null;
                        }
                    };
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
                        getDamageDisplayColor(damage2, damage),
                        true);
            }
        }
    }

    public int getDamageDisplayColor(int damage, int damageMax) {
        damage = damageMax - damage;
        if (damage < damageMax * 0.33) {
            return Colors.RED;
        } else if (damage < damageMax * 0.66) {
            return Colors.YELLOW;
        } else {
            return Colors.GREEN;
        }
    }

    public static enum DamageDisplay implements ConfigEnum {
        NONE,
        DAMAGE_LEFT,
        DAMAGE,
        PERCENTAGE;

        @Override
        public String getConfigEnumType() {
            return "equipment_hud_damage_display_type";
        }
    }
}
