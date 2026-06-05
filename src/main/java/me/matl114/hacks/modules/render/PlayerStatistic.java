package me.matl114.hacks.modules.render;

import me.matl114.api.Displayable;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.BoundedPrimitiveMap;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerStatistic extends BaseModule {
    public PlayerStatistic(){
        super("Statistic");
    }
    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("player-statistic");
    public FlagRef enable = flagBuilder(hud.add("enable")).build();

    public KeyBindRef keyBind = toggleHotkey(hud.add("hotkey"), new MultiKeyBind(), hud.add("enable"))
        .build();

    public FlagRef right = flagBuilder(hud.add("right")).build();

    public NBTRef<PlayerStatisticElementSelectSet> hudElementList = builder(hud.add("elements"), PlayerStatisticElementSelectSet.class)
        .defaultValue(new PlayerStatisticElementSelectSet())
        .build();

    public NBTRef<Vec2> pos = builder(hud.add("pos"), Vec2.class)
        .defaultValue(new Vec2(0.5D, 0.5D))
        .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
        .build();

    public NBTRef<WrapColor> color = builder(hud.add("color"), WrapColor.class)
        .defaultValue(new WrapColor(TextColor.parse("#F05BDA").getOrThrow()))
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderGameHudTasks(), this::onRender);
    }

    public void onRender(Event<VDrawContext> event) {
        if (checkNull()) return;
        if (enable.get() && !event.<Boolean>getArgs(1)) {
            PlayerStatisticElementSelectSet set = hudElementList.get();
            VDrawContext vdraw = event.context;
            vdraw.pushMatrix();
            try {
                handleRenderPosition(vdraw);
                //                vdraw.drawText(mc.textRenderer, "HelloWorld", 0,0,-1, false);
                //                vdraw.getMatrices().translate(0, 9);
                //                vdraw.drawText(mc.textRenderer, "HelloWorld2", 0,0,-1, true);
                //                vdraw.getMatrices().translate(0, 9);
                //
                // vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"),
                // 0,30, 0, 20, 0, 0,1,0 , 1);
                if (set.getState(StatisticElement.TOTEM)) {
                    handleTotem(vdraw);
                }
                if (set.getState(StatisticElement.POP)) {
                    handlePop(vdraw);
                }
                if (set.getState(StatisticElement.TURTLE)) {
                    handleTurtle(vdraw);
                }
                if( set.getState(StatisticElement.FIREWORK)){
                    handleFirework(vdraw);
                }
                if (set.getState(StatisticElement.EFFECTS)) {
                    handleEffects(vdraw);
                }
            } finally {
                vdraw.popMatrix();
            }
        }
    }
    public static final float HEIGHT =9.0F;
    public void drawText(VDrawContext vdraw, String text) {
        drawText(vdraw, Text.literal(text).asOrderedText());
    }
    public void drawText(VDrawContext vdraw, OrderedText text) {
        int rgb = color.get().withAlpha(255);

        if (right.get()) {
            int width = mc.textRenderer.getWidth(text);
            vdraw.drawText(mc.textRenderer, text, -width, 0, rgb, true);
        } else {
            vdraw.drawText(mc.textRenderer, text, 0, 0, rgb, true);
        }

        vdraw.getMatrices().translate(0, HEIGHT);
    }


    public void handleTotem(VDrawContext vdraw){
        String serverName = "Totem: %d";
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if(map != null){
            cnt = map.entrySet().stream().filter(s -> s.getKey().sample().getItem() == Items.TOTEM_OF_UNDYING)
                .mapToInt(Map.Entry::getValue)
                .sum();
        }else {
            cnt = 0;
        }
        drawText(vdraw, serverName.formatted(cnt));
    }

    public void handlePop(VDrawContext vdraw){
        String serverName = "Pop: %d";
        int cnt = PlayerStateManager.INSTANCE.getPlayerPopCount(mc.player);
        drawText(vdraw, serverName.formatted(cnt));
    }
    private boolean isTurtle(ItemStack stack){
        var potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if(potion != null){
            var po = potion.potion().orElse(null);
            return Objects.equals(po, Potions.TURTLE_MASTER) || Objects.equals(po, Potions.LONG_TURTLE_MASTER) || Objects.equals(po, Potions.STRONG_TURTLE_MASTER);
        }return false;
    }
    public void handleTurtle(VDrawContext vdraw){
        String serverName = "Turtle: %d";
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if(map != null){
            cnt = map.entrySet().stream().filter(s -> isTurtle(s.getKey().sample()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }else {
            cnt = 0;
        }
        drawText(vdraw, serverName.formatted(cnt));
    }

    public void handleFirework(VDrawContext vdraw){
        String serverName = "Fireworks: %d";
        var map = PlayerStateManager.INSTANCE.inventorySummary;
        int cnt;
        if(map != null){
            cnt = map.entrySet().stream().filter(s -> s.getKey().sample().getItem() == Items.FIREWORK_ROCKET)
                .mapToInt(Map.Entry::getValue)
                .sum();
        }else {
            cnt = 0;
        }
        drawText(vdraw, serverName.formatted(cnt));
    }
    private final static RegistryDisplays.IIcon<StatusEffect> statusEffectRenderer = RegistryDisplays.getIcon(StatusEffect.class);
    public void handleEffects(VDrawContext vdraw){
        for (var re : mc.player.getStatusEffects()){
            OrderedText timeText = StatusEffectUtil.getDurationText(re, 1.0F, mc.world.getTickManager().getTickRate()).asOrderedText();
            float length = mc.textRenderer.getTextHandler().getWidth(timeText);
            vdraw.pushMatrix();
            if(right.get()){
                vdraw.getMatrices().translate(-length - HEIGHT, 0);
            }
            {
                vdraw.pushMatrix();
                {
                    vdraw.getMatrices().scale(0.5F, 0.5F);
                    statusEffectRenderer.render(1, 1, vdraw, re.getEffectType().value());
                    //render level
                    int level = re.getAmplifier();
                    if(level > 0){
                        String lv = String.valueOf(level + 1);
                        vdraw.drawText(mc.textRenderer, lv, 17 - mc.textRenderer.getWidth(lv), 9, -1, true);
                    }
                }

                vdraw.popMatrix();
                vdraw.drawText(mc.textRenderer, timeText,(int) HEIGHT, 0, color.get().withAlpha(255), true);
            }
            vdraw.popMatrix();
            vdraw.getMatrices().translate(0, HEIGHT);
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

    public static class PlayerStatisticElementSelectSet extends BoundedPrimitiveMap<StatisticElement, Boolean>
        implements NBTParsable<PlayerStatisticElementSelectSet> {
        public static final NBTType<PlayerStatisticElementSelectSet> TYPE = create(
            PlayerStatisticElementSelectSet.class,
            PlayerStatisticElementSelectSet::new,
            Arrays.asList(StatisticElement.values()),
            CodecUtils.enumCodec(StatisticElement.class),
            StatisticElement::createKeyNameWidget,
            NBTTypes.BOOLEAN_TYPE,
            250,
            320,
            20);

        public PlayerStatisticElementSelectSet(List<StatisticElement> keys, Map<StatisticElement, Boolean> map, NBTType<Boolean> type) {
            super(keys, map, type);
        }

        public PlayerStatisticElementSelectSet() {
            this(Arrays.asList(StatisticElement.values()), Map.of(), NBTTypes.BOOLEAN_TYPE);
        }

        public boolean getState(StatisticElement element) {
            return map.get(element);
        }

        @Override
        public NBTType<PlayerStatisticElementSelectSet> type() {
            return TYPE.cast();
        }
    }

    public static enum StatisticElement implements Displayable {
        TOTEM,
        POP,
        TURTLE,
        FIREWORK,
        EFFECTS
        ;
        public DrawableWidget createKeyNameWidget(int x, int y, int width, int height) {
            int estimateWidth = 180;
            int startX = (width - estimateWidth) / 2;
            return ExecutableWidget.instance(x + startX, y, estimateWidth, height)
                .setElementHandler(new ButtonElement(TextProvider.of(getDisplay()), ButtonAction.empty()));
        }

        @Override
        public Text getDisplay() {
            return Text.literal(name());
        }
    }
}
