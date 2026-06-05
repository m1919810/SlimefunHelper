package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.*;

public class InvHud extends BaseModule {
    public InvHud(){

    }

    public final ModulePath invHud = makePath(Configs.RENDER_CONFIG, "in-game-hud.inv-hud");

    public final FlagRef enable  = flagBuilder(invHud.addEnable())
        .build();

    public KeyBindRef keyBind = toggleHotkey(invHud.add("hotkey"), new MultiKeyBind(), invHud.add("enable"))
        .build();

    public FlagRef right = flagBuilder(invHud.add("right")).build();

    public FlagRef down = flagBuilder(invHud.add("down")).build();

    public NBTRef<Vec2> pos = builder(invHud.add("pos"), Vec2.class)
        .defaultValue(new Vec2(0.02D, 0.3D))
        .validator((v) -> v.x() >= 0.0D && v.y() >= 0.0D && v.x() <= 1.0D && v.y() <= 1.0D)
        .build();

    public NBTRef<RegistryRegex<Item>> whiteList = builder(invHud.add("show-items"), NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
        .defaultValue(new RegistryRegex<>(new Regex("^(.*)$"), Registries.ITEM))
        .build();

    public IntRef line = intBuilder(invHud.add("count-per-line"))
        .defaultValue(9)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onPostTick);
        registerListener(RenderListener.getRenderGameHudTasks(), this::onRender2D);
    }

    List<ItemStack> toShow;


    public void onPostTick(Event<ClientPlayerEntity> event){
        if(enable.get()){
            toShow = new ArrayList<>();
            Map<ItemStackSample, Integer> map;
            if((map =PlayerStateManager.INSTANCE.inventoryTotalSummary) != null){
                for(var re : map.entrySet()){
                    if(whiteList.get().test(re.getKey().sample().getItem())){
                        ItemStack stack = re.getKey().sample().copyWithCount(re.getValue());
                        toShow.add(stack);
                    }
                }
            }
            toShow.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
        }else {
            toShow = null;
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
        int startY = (int) (down.get() ? (sizeY - yPer * sizeY) : yPer * sizeY);
        vdraw.getMatrices().translate(startX, startY);
    }
    private void drawItem(VDrawContext vdraw, int totalLine, int x, int y, ItemStack stack){
        int startX = right.get() ? (- 18 * x - 18) : (18 * x);
        int startY = down.get()? (-18  * totalLine + 18 * y) : (18 * y);
        vdraw.drawItem(stack, startX + 1, startY + 1, 999,0);
        vdraw.drawItemInSlot(mc.textRenderer, stack, startX + 1, startY +1, null);
    }
    public void onRender2D(Event<VDrawContext> event){
        if (checkNull()) return;
        if (enable.get() && !event.<Boolean>getArgs(1) && toShow != null) {
            VDrawContext vdraw = event.context;
            vdraw.pushMatrix();
            try {
                handleRenderPosition(vdraw);
                int line = 0;
                int cpl = this.line.get();
                int totalLine = ((toShow.size() - 1) / cpl ) + 1;
                int idx = 0;
                for (var it : toShow){
                    drawItem(vdraw, totalLine, idx, line,  it);
                    if(++ idx >= cpl){
                        idx = 0;
                        line += 1;
                    }
                }
            }finally {
                vdraw.popMatrix();
            }
        }
    }


}
