package me.matl114.hacks.modules.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.matl114.api.Displayable;
import me.matl114.events.Event;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.BoundedPrimitiveFlagMap;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public class PlayerStatistic extends IRender2DColoredModule {
    private static final int ITEM_SIZE = 8;
    private static final int ITEM_TEXT_GAP = 2;
    private static final int ITEM_ROW_HEIGHT = 9;
    private static final int ITEM_TEXT_Y = 4;
    private static final ItemStack TOTEM_STACK = new ItemStack(Items.TOTEM_OF_UNDYING);
    private static final ItemStack TURTLE_STACK =
            PotionContentsComponent.createStack(Items.POTION, Potions.TURTLE_MASTER);
    private static final ItemStack FIREWORK_STACK = new ItemStack(Items.FIREWORK_ROCKET);
    private static final ItemStack EXP_STACK = new ItemStack(Items.EXPERIENCE_BOTTLE);

    public PlayerStatistic() {
        super("Statistic");
    }

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "in-game-hud").add("player-statistic");
    }

    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("player-statistic");

    public NBTRef<PlayerStatisticElementSelectSet> hudElementList = builder(
                    hud.add("elements"), PlayerStatisticElementSelectSet.class)
            .defaultValue(new PlayerStatisticElementSelectSet())
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void onUpdate(Event<Void> event) {}

    @Override
    public void render2D(VDrawContext vdraw, float partialTicks) {
        PlayerStatisticElementSelectSet set = hudElementList.get();
        if (set.getState(StatisticElement.TOTEM)) {
            handleTotem(vdraw);
        }
        if (set.getState(StatisticElement.TURTLE)) {
            handleTurtle(vdraw);
        }
        if (set.getState(StatisticElement.FIREWORK)) {
            handleFirework(vdraw);
        }
        if (set.getState(StatisticElement.EXP)) {
            handleExp(vdraw);
        }
        if (set.getState(StatisticElement.EFFECTS)) {
            handleEffects(vdraw);
        }
    }

    private void drawItemStatistic(VDrawContext vdraw, ItemStack stack, int count) {
        OrderedText text = Text.literal(String.valueOf(count)).asOrderedText();

        vdraw.pushMatrix();
        {
            if (right.get()) {
                vdraw.getMatrices().translate(-9, 0);
            }
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().scale(0.5F, 0.5F);
            vdraw.drawItem(stack, 0, 0, 0, 0);
            vdraw.getMatrices().popMatrix();
            if (!right.get()) {
                vdraw.getMatrices().translate(9, 0);
            } else {
                int textWidth = mc.textRenderer.getWidth(text);
                vdraw.getMatrices().translate(-textWidth, 0);
            }
        }
        vdraw.drawText(mc.textRenderer, text, 0, 0, color.get().withAlpha(255), true);
        vdraw.popMatrix();
        vdraw.getMatrices().translate(0, ITEM_ROW_HEIGHT);
    }

    public void handleTotem(VDrawContext vdraw) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> s.getKey().sample().getItem() == Items.TOTEM_OF_UNDYING)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(vdraw, TOTEM_STACK, cnt);
    }

    private boolean isTurtle(ItemStack stack) {
        var potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potion != null) {
            var po = potion.potion().orElse(null);
            return Objects.equals(po, Potions.TURTLE_MASTER)
                    || Objects.equals(po, Potions.LONG_TURTLE_MASTER)
                    || Objects.equals(po, Potions.STRONG_TURTLE_MASTER);
        }
        return false;
    }

    public void handleTurtle(VDrawContext vdraw) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> isTurtle(s.getKey().sample()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(vdraw, TURTLE_STACK, cnt);
    }

    public void handleFirework(VDrawContext vdraw) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> s.getKey().sample().getItem() == Items.FIREWORK_ROCKET)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(vdraw, FIREWORK_STACK, cnt);
    }

    public void handleExp(VDrawContext vdraw) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> s.getKey().sample().getItem() == Items.EXPERIENCE_BOTTLE)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(vdraw, EXP_STACK, cnt);
    }

    private static final RegistryDisplays.IIcon<StatusEffect> statusEffectRenderer =
            RegistryDisplays.getIcon(StatusEffect.class);

    public void handleEffects(VDrawContext vdraw) {
        for (var re : mc.player.getStatusEffects()) {
            OrderedText timeText = StatusEffectUtil.getDurationText(
                            re, 1.0F, mc.world.getTickManager().getTickRate())
                    .asOrderedText();
            float length = mc.textRenderer.getTextHandler().getWidth(timeText);
            vdraw.pushMatrix();
            if (right.get()) {
                vdraw.getMatrices().translate(-length - HEIGHT, 0);
            }
            {
                vdraw.pushMatrix();
                {
                    vdraw.getMatrices().scale(0.5F, 0.5F);
                    statusEffectRenderer.render(1, 1, vdraw, re.getEffectType().value());
                    // render level
                    int level = re.getAmplifier();
                    if (level > 0) {
                        String lv = String.valueOf(level + 1);
                        vdraw.drawText(mc.textRenderer, lv, 17 - mc.textRenderer.getWidth(lv), 9, -1, true);
                    }
                }

                vdraw.popMatrix();
                vdraw.drawText(
                        mc.textRenderer, timeText, (int) HEIGHT, 0, color.get().withAlpha(255), true);
            }
            vdraw.popMatrix();
            vdraw.getMatrices().translate(0, HEIGHT);
        }
    }

    public static class PlayerStatisticElementSelectSet extends BoundedPrimitiveFlagMap<StatisticElement>
            implements NBTParsable<PlayerStatisticElementSelectSet> {
        public static final NBTType<PlayerStatisticElementSelectSet> TYPE = createEnumMap(
                "PlayerStatisticElementSelectSet", StatisticElement.class, PlayerStatisticElementSelectSet::new);

        public PlayerStatisticElementSelectSet(
                List<StatisticElement> keys, Map<StatisticElement, Boolean> map, NBTType<Boolean> type) {
            super(keys, map, type);
        }

        public PlayerStatisticElementSelectSet() {
            super(StatisticElement.class);
        }

        @Override
        public NBTType<PlayerStatisticElementSelectSet> type() {
            return TYPE.cast();
        }
    }

    public static enum StatisticElement implements Displayable {
        TOTEM,
        TURTLE,
        FIREWORK,
        EXP,
        EFFECTS;

        @Override
        public Text getDisplay() {
            return Text.literal(name());
        }
    }
}
