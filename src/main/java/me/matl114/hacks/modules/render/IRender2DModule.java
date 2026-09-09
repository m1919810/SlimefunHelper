package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WidgetPos;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.versioned.api.VDrawContext;

public abstract class IRender2DModule extends BaseModule {
    public final ModulePath hud = createRoot();

    protected abstract ModulePath createRoot();

    protected void initializeSettings() {}

    public IRender2DModule() {
        super("IRender2DModule");
        bindFlag(enable);
    }

    public IRender2DModule(String name) {
        super(name);
        bindFlag(enable);
    }

    public FlagRef enable = flagBuilder(hud.add("enable")).build();

    public KeyBindRef keyBind = toggleHotkey(hud.add("hotkey"), new MultiKeyBind(), hud.add("enable"))
            .build();

    {
        initializeSettings();
    }

    public FlagRef right = flagBuilder(hud.add("right")).build();

    public NBTRef<WidgetPos> pos = builder(hud.add("pos"), WidgetPos.class)
            .defaultValue(new WidgetPos(0, 0.0D, 0.0D, 0, 0))
            .build();

    public DoubleRef size =
            builder(hud.add("scaling"), DoubleRef.TYPE).defaultValue(1.0D).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getRender2DEvent(), this::onRender);
        registerListener(Listener.getPostTick(), this::onUpdate);
    }

    public abstract void onUpdate(Event<Void> event);

    public void onRender(Event<VDrawContext> event) {
        if (checkNull()) return;
        if (enable.get() && !event.<Boolean>getArgs(1)) {
            VDrawContext vdraw = event.context;
            vdraw.pushMatrix();
            try {
                handleRenderPosition(vdraw);
                render2D(vdraw, event.getArgs(0));
            } finally {
                vdraw.popMatrix();
            }
        }
    }

    public void handleRenderPosition(VDrawContext vdraw) {
        int sizeX = mc.getWindow().getScaledWidth();
        //        vdraw.pushMatrix();
        //        vdraw.drawTexturedQuad(Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"), sizeX
        // - 30,sizeX, sizeY - 20, sizeY, 0, 0,1,0 , 1);
        //        vdraw.popMatrix();
        var pp = pos.get();
        int posX = pp.getWindowX(mc.getWindow());
        int posY = pp.getWindowY(mc.getWindow());
        int startX = posX;
        int startY = (posY);
        vdraw.getMatrices().translate(startX, startY);
        vdraw.getMatrices().scale((float) size.get(), (float) size.get());
    }

    public abstract void render2D(VDrawContext vdraw, float partialTicks);

    public static final float HEIGHT = 9;
}
