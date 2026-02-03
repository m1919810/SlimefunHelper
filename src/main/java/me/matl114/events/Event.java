package me.matl114.events;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true, fluent = true)
@Getter
@Setter
public class Event<T> {
    public Event(T context, boolean canCancel){
        this(context, canCancel, false, new Object[0]);
    }
    public Event(T context, boolean canCancel, boolean canModifyContext, Object... extraArgs){
        this.context = context;
        this.extraArgs = extraArgs;
        this.canCancel = canCancel;
        this.canModifyContext = canModifyContext;
    }
    public Event<T> cancel(){
        Preconditions.checkArgument(this.canCancel, "Can not cancel!");
        this.cancel = true;
        return this;
    }
    public Event<T> context(T val){
        Preconditions.checkArgument(this.canModifyContext, "Can not modify");
        this.context = val;
        return this;
    }

    public <W> W getArgs(int idx){
        return (W)this.extraArgs[idx];
    }
    public boolean isCancelled(){
        return this.cancel;
    }
    public T context;
    public Object[] extraArgs;
    public boolean cancel = false;
    public final boolean canCancel;
    public final boolean canModifyContext;
}
