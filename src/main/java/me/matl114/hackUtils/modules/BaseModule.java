package me.matl114.hackUtils.modules;

import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.utils.UtilClass.ListenerPoint;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.*;
import java.util.function.Consumer;

public abstract class BaseModule {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    public BaseModule(){

    }
    protected boolean lastActiveFlag = false;
    protected boolean removed = false;
    public boolean isActive(){
        return lastActiveFlag;
    }
    public boolean isRemoved(){
        return removed;
    }
    protected Config.FlagRef bindedFlag = null;
    protected static final String REASON_BIND = "flag binding";
    protected static final String REASON_LISTENER = "listener";
    // bind the Module's status to the Flag
    public final void bindFlag(Config.FlagRef flagRef){
        if(bindedFlag != null){
            removeBind();
        }
        bindedFlag = flagRef;
        if(flagRef != null){
            flagRef.addUpdateListenerWithUpdate(new NamedConsumer<>(this, this::updateActiveStatus, REASON_BIND));
        }

    }

    private void removeBind(){
        if(bindedFlag != null){
            bindedFlag.removeUpdateListener(s -> this.isOwner(s, REASON_BIND) );
            bindedFlag = null;
        }
    }
    // this is called via the bindedFlag
    protected final void updateActiveStatus(boolean active){
        if(lastActiveFlag != active){
            lastActiveFlag = active;
            if(active){
                onEnableModule();
            }else{
                onDisableModule();
            }
        }
    }
    //module enable and disable
    //note that it might be called outside the game, so you have check basic vars
    public  void onEnableModule(){

    }

    public  void onDisableModule(){

    }
    //this is managed by ModuleManager
    @MustBeInvokedByOverriders
    public void onCreate(){
        registerAll();
    }
    @MustBeInvokedByOverriders
    public void onRemove(){
        if(removed){
            throw new IllegalStateException("Removed twice");
        }
        removeBind();
        unregisterAll();
        removed = true;
    }

    //this is for convenience
    @MustBeInvokedByOverriders
    public final <T extends BaseModule> T register(ModuleManager manager){
        manager.registerModule(this);
        return (T)this;
    }
    @MustBeInvokedByOverriders
    public final void unregister(ModuleManager manager){
        manager.unregisterModule(this);
    }
    //this is also for convenience
    private final Set<ListenerPoint<?>> registeredPoints = new LinkedHashSet<>();
    public <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler){
        registerListener(listener, handler, 0);
    }

    public <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler, int p){
        listener.registerHandler(new NamedConsumer<>(this, handler, REASON_LISTENER), p);
        registeredPoints.add(listener);
    }
    // you should put listeners here
    @MustBeInvokedByOverriders
    public void registerAll(){

    }
    // listeners will be automatically unregistered in onRemove
    public <W> void unregisterAll(){
        registeredPoints.forEach(s -> s.unregisterHandler(this::isOwner));
        registeredPoints.clear();
    }
    // for removal convenience
    protected <W> boolean isOwner(Object c){
        return (c instanceof NamedConsumer<?> named && named.name == this);
    }

    protected <W> boolean isOwner(Object c, String name){
        return (c instanceof NamedConsumer<?> named && named.name == this && Objects.equals(name, named.reason));
    }
    // named consumer to mark who's owner
    public static class NamedConsumer<W> implements Consumer<W> {
        private final BaseModule name;
        private final Consumer<W> delegate;
        private final String reason;
        public NamedConsumer(BaseModule name, Consumer<W> delegate, String reason){
            this.name = name;
            this.delegate = delegate;
            this.reason = reason;
        }

        @Override
        public void accept(W w) {
            this.delegate.accept(w);
        }
    }
    public <T> T cast(){
        return (T)this;
    }
}
