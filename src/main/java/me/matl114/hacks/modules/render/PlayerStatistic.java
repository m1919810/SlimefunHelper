package me.matl114.hacks.modules.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public class PlayerStatistic extends IRender2DColoredModule {
    private static final int ITEM_ROW_HEIGHT = 9;

    public PlayerStatistic() {
        super("Statistic");
    }

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "in-game-hud").add("player-statistic");
    }

    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("player-statistic");

    public final FlagRef enableItems = flagBuilder(hud.add("enable-items")).build();

    public final NBTRef<EntrySet<Item>> itemTypes = builder(hud.add("item-types"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.TOTEM_OF_UNDYING, Items.FIREWORK_ROCKET)))
            .build();

    public final FlagRef enablePotions = flagBuilder(hud.add("enable-potions")).build();

    public final NBTRef<EntrySet<Potion>> potionTypes = builder(hud.add("potion-types"), EntrySet.<Potion>parameter())
            .defaultValue(new EntrySet<>(Registries.POTION, List.of(Potions.TURTLE_MASTER.value())))
            .build();

    public final FlagRef enableEffect = flagBuilder(hud.add("enable-effect")).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void onUpdate(Event<Void> event) {}

    @Override
    public void render2D(VDrawContext vdraw, float partialTicks) {
        if (enableItems.get()) {
            for (var re : itemTypes.get().set()) {
                handleItem(vdraw, re);
            }
        }
        if (enablePotions.get()) {
            for (var re : potionTypes.get().set()) {
                handleTurtle(vdraw, re);
            }
        }

        if (enableEffect.get()) {
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

    public void handleItem(VDrawContext vdraw, Item itemType) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> s.getKey().sample().isOf(itemType))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(vdraw, new ItemStack(itemType), cnt);
    }

    private boolean isTurtle(ItemStack stack, Potion potionType) {
        var potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potion != null) {
            var po = potion.potion().orElse(null);
            if (po == null) return false;
            if (Objects.equals(po, potionType)) {
                return true;
            }
            var re = potionType.getEffects().stream().map(StatusEffectInstance::getEffectType);
            var re2 = po.value().getEffects().stream().map(StatusEffectInstance::getEffectType);
            return Objects.equals(re, re2);
        }
        return false;
    }

    public void handleTurtle(VDrawContext vdraw, Potion potionType) {
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if (map != null) {
            cnt = map.entrySet().stream()
                    .filter(s -> isTurtle(s.getKey().sample(), potionType))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        } else {
            cnt = 0;
        }
        drawItemStatistic(
                vdraw, PotionContentsComponent.createStack(Items.POTION, Registries.POTION.getEntry(potionType)), cnt);
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
}
