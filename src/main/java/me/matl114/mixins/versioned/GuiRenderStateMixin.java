package me.matl114.mixins.versioned;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import me.matl114.accessors.gui.GuiRenderStateLayerAccess;
import me.matl114.accessors.gui.GuiRendererStateAccess;
import me.matl114.versioned.impl.DrawContext_v1_21_11;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements GuiRendererStateAccess {
    @Shadow
    @Final
    private List<GuiRenderState.Layer> rootLayers;

    @Shadow
    public GuiRenderState.Layer currentLayer;

    @Shadow
    public abstract void createNewRootLayer();

    @Unique
    boolean hasDepth = false;

    @WrapWithCondition(
            method = "createNewRootLayer",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private <E> boolean createNewRootLayer(List instance, E e) {
        if (!hasDepth && e instanceof GuiRenderStateLayerAccess layerAccess && layerAccess.getDepth() != 0) {
            hasDepth = true;
        }
        if (!hasDepth) {
            // use vanilla
            return true;
        }
        int size = instance.size();
        int currentDepth = ((GuiRenderStateLayerAccess) e).getDepth();
        for (int i = 0; i < size; i++) {
            GuiRenderStateLayerAccess layerAccess = (GuiRenderStateLayerAccess) instance.get(i);
            if (currentDepth < layerAccess.getDepth()) {
                instance.add(i, e);
                return false;
            }
        }
        // add at the end
        return true;
    }

    @Unique
    public void setLayerToDepth() {
        int depth = DrawContext_v1_21_11.getCurrentDepthLevel();
        int size = this.rootLayers.size();
        int idx = -1;
        for (int i = 0; i < size; i++) {
            GuiRenderStateLayerAccess layer = (GuiRenderStateLayerAccess) this.rootLayers.get(i);
            if (layer.getDepth() < depth) {
                continue;
            } else if (layer.getDepth() == depth) {
                idx = i;
            } else if (layer.getDepth() > depth) {
                break;
            }
        }
        if (idx != -1) {
            this.currentLayer = this.rootLayers.get(idx);
        } else {
            createNewRootLayer();
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void clear(CallbackInfo ci) {
        hasDepth = false;
    }

    @Inject(method = "findAndGoToLayerIntersecting", at = @At("HEAD"))
    private void findAndGoToLayerIntersecting(CallbackInfo ci) {
        if (hasDepth) {
            int depth = DrawContext_v1_21_11.getCurrentDepthLevel();
            if (depth != GuiRenderStateLayerAccess.of(this.currentLayer).getDepth()) {
                setLayerToDepth();
            }
        }
    }

    @WrapOperation(
            method = "findAndGoToLayerIntersecting",
            at = @At(value = "INVOKE", target = "Ljava/util/List;getLast()Ljava/lang/Object;"))
    private <E> E findAndGoToLayerIntersecting(List<E> instance, Operation<E> original) {
        if (!hasDepth) {
            return original.call(instance);
        } else {
            // put the fucking correct depth
            int depth = DrawContext_v1_21_11.getCurrentDepthLevel();
            int size = instance.size();
            for (int i = size - 1; i >= 0; --i) {
                GuiRenderStateLayerAccess layer = (GuiRenderStateLayerAccess) this.rootLayers.get(i);
                if (layer.getDepth() == depth) {
                    return (E) layer;
                } else if (layer.getDepth() > depth) {
                    continue;
                } else {
                    // create layer at this depth
                    break;
                }
            }
            createNewRootLayer();
            return (E) this.currentLayer;
        }
    }
}
