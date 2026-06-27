package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.versioned.api.VDrawContext;

public abstract class IRender2DModule extends BaseModule {
    public final ModulePath hud = createRoot();

    protected abstract ModulePath createRoot();

    public IRender2DModule() {
        bindFlag(enable);
    }

    public IRender2DModule(String name) {
        super(name);
        bindFlag(enable);
    }

    public FlagRef enable = flagBuilder(hud.add("enable")).build();

    public KeyBindRef keyBind = toggleHotkey(hud.add("hotkey"), new MultiKeyBind(), hud.add("enable"))
            .build();

    public FlagRef right = flagBuilder(hud.add("right")).build();

    public NBTRef<Vec2> pos = builder(hud.add("pos"), Vec2.class)
            .defaultValue(new Vec2(0.0D, 0.0D))
            .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
            .build();

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

    public abstract void render2D(VDrawContext vdraw, float partialTicks);

    public static final float HEIGHT = 9;
}
