package me.matl114.mixins.events;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.utils.containers.MetaData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenEvents extends AbstractParentElement implements MetadataHolder, ScreenAccess {
    @Unique
    List<Consumer<Screen>> initializeTasks;

    public void addInitTask(Consumer<Screen> runnable) {
        if (initializeTasks == null) {
            initializeTasks = new ArrayList<>();
        }
        initializeTasks.add(runnable);
    }

    @Unique
    List<Runnable> screenCloseFuture;

    public void addCloseFuture(Runnable runnable) {
        if (screenCloseFuture == null) {
            screenCloseFuture = new ArrayList<>();
        }
        screenCloseFuture.add(runnable);
    }

    @Inject(method = "close", at = @At(value = "RETURN"))
    private void onScreenClsoe(CallbackInfo ci) {
        Listener.getPostCloseScreen().broadcast((Screen) (AbstractParentElement) this);
        if (screenCloseFuture != null) {
            for (Runnable runnable : screenCloseFuture) {
                runnable.run();
            }
        }
    }

    @Inject(
            method = "init(Lnet/minecraft/client/MinecraftClient;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screen/Screen;setInitialFocus()V",
                            shift = At.Shift.BEFORE))
    public void onPostInitialization(MinecraftClient client, int width, int height, CallbackInfo ci) {
        // first initialize
        Listener.getPostInitializeScreen().broadcast((Screen) (AbstractParentElement) this);
        if (initializeTasks != null) {
            for (Consumer<Screen> runnable : initializeTasks) {
                runnable.accept((Screen) (AbstractParentElement) this);
            }
        }
    }

    @Inject(
            method = "init(Lnet/minecraft/client/MinecraftClient;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screen/Screen;initTabNavigation()V",
                            shift = At.Shift.AFTER))
    public void onClearAndInit(CallbackInfo ci) {
        Listener.getPostInitializeScreen().broadcast((Screen) (AbstractParentElement) this);
        if (initializeTasks != null) {
            for (Consumer<Screen> runnable : initializeTasks) {
                runnable.accept((Screen) (AbstractParentElement) this);
            }
        }
    }

    @Inject(
            method = "resize",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screen/Screen;initTabNavigation()V",
                            shift = At.Shift.AFTER))
    public void onResize(MinecraftClient client, int width, int height, CallbackInfo ci) {
        Listener.getPostInitializeScreen().broadcast((Screen) (AbstractParentElement) this);
        if (initializeTasks != null) {
            for (Consumer<Screen> runnable : initializeTasks) {
                runnable.accept((Screen) (AbstractParentElement) this);
            }
        }
    }

    @Unique
    public MetaData metaData;

    @Unique
    public MetaData getMetadata() {
        if (metaData == null) {
            metaData = new MetaData();
        }
        return metaData;
    }

    @Unique
    @Override
    public boolean isMetaEmpty() {
        return metaData == null;
    }

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    @Shadow
    protected void remove(Element child) {}

    @Shadow
    protected abstract <T extends Drawable> T addDrawable(T drawable);

    @Unique
    public <T extends Element & Drawable & Selectable> T addDrawableChildTo(T drawable) {
        if (drawable instanceof DisplayWidget display) {
            addDrawable(display);
            return drawable;
        } else {
            return addDrawableChild(drawable);
        }
    }

    @Unique
    public void removeChildFrom(Element val) {
        remove(val);
    }

    @Getter
    @Setter
    @Unique
    Screen parent = null;

    @Unique
    public void open() {
        MinecraftClient.getInstance().setScreen((Screen) (Object) this);
    }

    @Unique
    public void openFromCurrent() {
        parent = MinecraftClient.getInstance().currentScreen;
        open();
    }

    @Unique
    public void openFrom(Screen parent) {
        this.parent = parent;
        open();
    }

    @Unique
    public void switchToScreen(Screen anotherScreen) {
        Screen p = this.parent;
        this.parent = null;
        ScreenAccess.of(anotherScreen).setParent(p);
        MinecraftClient.getInstance().setScreen(anotherScreen);
    }

    public void switchFromCurrent() {
        Screen current = MinecraftClient.getInstance().currentScreen;
        if (current == null) {
            this.parent = null;
        } else {
            this.parent = ((ScreenEvents) (Object) current).parent;
            ((ScreenEvents) (Object) current).parent = null;
        }
        MinecraftClient.getInstance().setScreen((Screen) (Object) this);
    }

    @ModifyArgs(
            method = "close",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    public void onRedirectReturnScreen(Args args) {
        if (parent != null) {
            args.set(0, parent);
            parent = null;
        }
    }
}
