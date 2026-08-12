package me.matl114.hooks.impl.xaeroworldmap;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public class RightClickPosOption extends RightClickOption {
    MapClickContext context;
    BlockPos pos;
    RegistryKey<World> world;

    public RightClickPosOption(
            MapClickContext context,
            RegistryKey<World> currentWorld,
            BlockPos pos,
            int index,
            IRightClickableElement target) {
        super(
                context.formatter.format(
                        "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()),
                        String.valueOf(pos.getX()),
                        String.valueOf(pos.getY()),
                        String.valueOf(pos.getZ())),
                index,
                target);
        this.pos = pos;
        this.world = currentWorld;
        ;
        this.context = context;
    }

    @Override
    public void onAction(Screen var1) {
        context.function.accept(world, pos);
    }
}
