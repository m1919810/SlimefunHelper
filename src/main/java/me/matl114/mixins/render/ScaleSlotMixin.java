package me.matl114.mixins.render;

import me.matl114.accessors.gui.ScaleSlotAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(Slot.class)
public abstract class ScaleSlotMixin implements ScaleSlotAccess {
    @Unique
    float scale = 1.0f;
    @Unique
    int extraDepth = 0;
    @Unique
    public ScaleSlotAccess setXYScale(float scale){
        this.scale = scale;
        return this;
    }
    @Unique
    public float getXYScale(){
        return this.scale;
    }
    @Unique
    public ScaleSlotAccess setExtraDepth(int depth){
        this.extraDepth = depth;
        return this;
    }
    @Unique
    public int getExtraDepth(){
        return this.extraDepth;
    }
    public boolean isDefault(){
        return this.scale == 1.0f && this.extraDepth == 0;
    }

    @Unique
    public void apply(MatrixStack stack){
        stack.scale(scale,scale,1);
        stack.translate(0,0, extraDepth);
    }
}
