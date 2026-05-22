package me.matl114.hacks.modules.render;

import me.matl114.api.Displayable;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.basic.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleEntry;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.BoundedPrimitiveMap;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.Debug;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class Hud extends BaseModule {
    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("hud");

    public Hud() {

    }

    public FlagRef enable = flagBuilder(hud.add("enable"))
        .build();

    public KeyBindRef keyBind = toggleHotkey(Configs.RENDER_CONFIG, hud.add("hotkey").toPath(), new MultiKeyBind(), hud.add("enable").toPath())
        .build();

    public FlagRef right = flagBuilder(hud.add("right"))
        .build();

    public NBTRef<HudElementSelectSet> hudElementList = builder(hud.add("elements"), HudElementSelectSet.class)
        .defaultValue(new HudElementSelectSet())
        .build();

    public DoubleRef xpos = builder(hud.add("x-pos"), DoubleRef.TYPE)
        .defaultValue(0.0D)
        .validator(Configs.doubleRange(0.0D, 1.0D))
        .build();

    public DoubleRef ypos = builder(hud.add("y-pos"), DoubleRef.TYPE)
        .defaultValue(0.0D)
        .validator(Configs.doubleRange(0.0D, 1.0D))
        .build();

    public NBTRef<WrapColor> color = builder(hud.add("color"), WrapColor.class)
        .defaultValue(new WrapColor(ColorUtils.color(Formatting.WHITE)))
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRenderGameHudTasks(), this::onRender);
        registerListener(Listener.getPostTick(), this::onUpdate);
    }
    List<HudModuleEntry> moduleEntries = null;
    List<OrderedText> moduleListRender;

    public void initializeModuleEntryList(){
        moduleEntries = new ArrayList<>();
        for (var re : HackModules.getModuleGroups()){
            for (var module : re.registered){
                module.getModuleEntries().map(HudModuleEntry::new).forEach(moduleEntries::add);
            }
        }
        for (var module : moduleEntries){
            if(!ChatUtils.hasTranslation(module.moduleEntry.getTranslationKey())){
                Debug.info("Missing translation key for", module.moduleEntry.getTranslationKey());
            }
        }
        sortModuleEntries();
    }
    private void sortModuleEntries(){
        moduleEntries.sort(Comparator.comparingDouble(s -> -mc.textRenderer.getTextHandler().getWidth(s.getDisplay())));
    }
    public void onUpdate(Event<Void> event){

        if(!checkNull() && enable.get() && hudElementList.get().getState(HudElement.MODULE_LIST) ){
            if(moduleEntries == null){
                initializeModuleEntryList();
            }
            boolean val = false;
            for (var re : moduleEntries){
                if(re.tickUpdate()){
                    val = true;
                }
            }
            if(val){
                sortModuleEntries();
            }
        }else {
            moduleEntries = null;
            moduleListRender = null;
        }
    }


    public void onRender(Event<VDrawContext> event){
        if(checkNull())return;
        if(enable.get() && !event.<Boolean>getArgs(1) && !mc.debugHudEntryList.isF3Enabled()){
            HudElementSelectSet set = hudElementList.get();
            VDrawContext vdraw = event.context;
            vdraw.pushMatrix();
            try{
                handleRenderPosition(vdraw);
//                vdraw.drawText(mc.textRenderer, "HelloWorld", 0,0,-1, false);
//                vdraw.getMatrices().translate(0, 9);
//                vdraw.drawText(mc.textRenderer, "HelloWorld2", 0,0,-1, true);
//                vdraw.getMatrices().translate(0, 9);
//                vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"), 0,30, 0, 20, 0, 0,1,0 , 1);
                if(set.getState(HudElement.ICON)){
                    handleIcon(vdraw);
                }
                if(set.getState(HudElement.COMMON_INFO)){
                    handleCommonInfo(vdraw);
                }
                if(set.getState(HudElement.POSITION)){
                    handlePosition(vdraw);
                }
                if(set.getState(HudElement.ROTATION)){
                    handleRotation(vdraw);
                }
                if(set.getState(HudElement.FALL_DISTANCE)){
                    handleFallDistance(vdraw);
                }
                if (set.getState(HudElement.SPEED)) {
                    handleSpeed(vdraw);
                }
                if(set.getState(HudElement.MODULE_LIST)){
                    handleModuleList(vdraw);
                }
            }finally {
                vdraw.popMatrix();
            }
        }
    }
    public static final float HEIGHT = 9;
    public void handleRenderPosition(VDrawContext vdraw){
        int sizeX = mc.getWindow().getScaledWidth();
        int sizeY = mc.getWindow().getScaledHeight();
        vdraw.pushMatrix();
        vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"), sizeX - 30,sizeX, sizeY - 20, sizeY, 0, 0,1,0 , 1);
        vdraw.popMatrix();
        double xPer = xpos.get();
        double yPer = ypos.get();
        int startX = (int) (right.get() ? (sizeX - xPer * sizeX) : xPer * sizeX);
        int startY = (int) (yPer * sizeY);
        vdraw.getMatrices().translate(startX, startY);
    }

    public void drawText(VDrawContext vdraw, String text){
        drawText(vdraw, Text.literal(text).formatted(Formatting.BOLD).asOrderedText());
    }
    public void drawText(VDrawContext vdraw, OrderedText text){
        int rgb = color.get().withAlpha(255);
        if(right.get()){
            int width = mc.textRenderer.getWidth(text);
            vdraw.drawText(mc.textRenderer, text, -width, 0, rgb, true);
        }else {
            vdraw.drawText(mc.textRenderer, text, 0,0, rgb, true);
        }
        vdraw.getMatrices().translate(0, HEIGHT);
    }

    public void handleIcon(VDrawContext vdraw){

    }

    public void handleCommonInfo(VDrawContext vdraw){
        // tps, fps, version
        SupportVersion currentVersion = ViaFabricPlusHooks.getInstance().getCurrentVersion();
        Text text = ChatUtils.stringToText("&a&lMCv" + currentVersion + (Objects.equals(currentVersion, SupportVersion.CURRENT) ? "" : "(Via)" + " Fps:"+ mc.getCurrentFps()));
        drawText(vdraw, text.asOrderedText());
    }

    public void handlePosition(VDrawContext vdraw){
        String template = "%.2f, %.2f, %.2f";
        String chunk = "Chunk: [%d %d]";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        int chunkX = ((int)manager.lastX) >> 4;
        int chunkZ = ((int)manager.lastZ) >> 4;
        drawText(vdraw, chunk.formatted(chunkX, chunkZ));
        drawText(vdraw, template.formatted(manager.lastX, manager.lastY, manager.lastZ));
    }

    public void handleRotation(VDrawContext vdraw){
        String rotation = "P:%.2f, Y: %.2f";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, rotation.formatted(manager.lastPitch, MathHelper.wrapDegrees(manager.lastYaw)));
    }


    public void handleFallDistance(VDrawContext vdraw){
        String fallDistance = "Fall dist: %.2f";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, fallDistance.formatted(manager.fallDistance));
    }

    public void handleSpeed(VDrawContext vdraw){
        String speed = "Avg:%.2fm/s, Kwn:%.2fm/s";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, speed.formatted(manager.lastAverageMovementSpeed.length() * 20, manager.lastKnownMovementSpeed.length() * 20));
    }

    public void handleModuleList(VDrawContext vdraw){
        if(moduleEntries != null){
            int cnt = 0;
            for (var text: moduleEntries){
                if(cnt >= 20){
                    drawText(vdraw,"...%d more".formatted(moduleEntries.size() - cnt));
                    break;
                }
                double height = text.getAnimationHeight();
                if(height < 0){
                    if(text.lastState){
                        drawText(vdraw, text.getDisplay().asOrderedText());
                        cnt += 1;
                    }
                }else {
                    vdraw.getMatrices().translate(0.0F, (float) height);
                    cnt += 1;
                }
            }
        }
    }



    public static enum HudElement implements Displayable {
        ICON,
        COMMON_INFO,
        POSITION,
        ROTATION,
        FALL_DISTANCE,
        SPEED,
        MODULE_LIST;

        @Override
        public Text getDisplay() {
            return Text.literal(name());
        }

        public DrawableWidget createKeyNameWidget(int x, int y, int width, int height) {
            int estimateWidth = 180;
            int startX = (width - estimateWidth) / 2;
            return ExecutableWidget.instance(x + startX, y, estimateWidth, height)
                .setElementHandler(
                    new ButtonElement(TextProvider.of(getDisplay()), ButtonAction.empty())
                );
        }
    }

    public static class HudElementSelectSet extends BoundedPrimitiveMap<HudElement, Boolean> implements NBTParsable<HudElementSelectSet> {
        public static final NBTType<HudElementSelectSet> TYPE = create(
            HudElementSelectSet.class,
            HudElementSelectSet::new,
            Arrays.asList(HudElement.values()),
            CodecUtils.enumCodec(HudElement.class),
            HudElement::createKeyNameWidget,
            NBTTypes.BOOLEAN_TYPE,
            250,
            320,
            20
        );

        public HudElementSelectSet(List<HudElement> keys, Map<HudElement, Boolean> map, NBTType<Boolean> type) {
            super(keys, map, type);
        }

        public HudElementSelectSet() {
            this(Arrays.asList(HudElement.values()), Map.of(), NBTTypes.BOOLEAN_TYPE);
        }

        public boolean getState(HudElement element){
            return map.get(element);
        }


        @Override
        public NBTType<HudElementSelectSet> type() {
            return TYPE.cast();
        }
    }

    public static class HudModuleEntry implements Displayable {
        ModuleEntry moduleEntry;
        boolean lastState;
        double switchCountDown;
        public HudModuleEntry(ModuleEntry moduleEntry) {
            this.moduleEntry = moduleEntry;
            this.lastState = moduleEntry.getActiveState();
            this.switchCountDown = -1;
            this.lastDisplay = moduleEntry.getDisplay().formatted(Formatting.BOLD);
        }
        Text lastDisplay;
        Text lastMeta;
        public boolean tickUpdate(){
            if(lastState != moduleEntry.getActiveState()){
                lastState = moduleEntry.getActiveState();
                switchCountDown = HEIGHT + 1.0D;
            }
            if(switchCountDown >= 0.0D){
                switchCountDown -= 1.5D;
            }

            if(!Objects.equals(lastMeta, moduleEntry.getMetaData())){
                lastMeta = moduleEntry.getMetaData();
                lastDisplay = (lastMeta != null ? (moduleEntry.getDisplay().append(Text.literal("[")).append(lastMeta).append(Text.literal("]"))) : moduleEntry.getDisplay()).formatted(Formatting.BOLD);
                return true;
            }
            return false;
        }

        public double getAnimationHeight(){
            return switchCountDown < 0.0D ? switchCountDown : (lastState ? (HEIGHT -  switchCountDown) : switchCountDown);
        }

        @Override
        public Text getDisplay() {
            return lastDisplay;
        }
    }
}

