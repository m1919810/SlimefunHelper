package me.matl114.matlib.utils.command.params.impl;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;
import org.joml.Vector3d;

public abstract class AbstractArgumentResult<T> implements InputArgument<T> {
    final public ArgumentType<T> type;
    public final ArgumentReader reader;
    int startIndex;
    int endIndex;
    boolean isDefault;
    final T result;
    // the parsed range is from startIndex  to endIndex
    public AbstractArgumentResult(T result, ArgumentType<T> type, ArgumentReader reader, int startIndex) {
        this.type = type;
        this.reader = reader;
        this.startIndex = startIndex;
        this.endIndex = reader.cursor();
        this.isDefault = this.startIndex == this.endIndex;
        this.result = result;
    }

    @Override
    public ArgumentReader getEndReader() {
        return new ArgumentReader(reader).setCursor(startIndex);
    }

    @Override
    public ArgumentType<T> getType() {
        return type;
    }

    @Override
    public T result() {
        return result;
    }

    public String[] getParsedArgument(){
        return this.reader.getArgsInRange(this.startIndex, this.endIndex);
    }

    public final String tabbingString(){
        if(this.startIndex < this.endIndex){
            return this.reader.getArgsAt(this.endIndex - 1);
        }else{
            return null;
        }
    }

}
