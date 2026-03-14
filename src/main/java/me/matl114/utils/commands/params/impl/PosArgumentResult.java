package me.matl114.matlib.utils.command.params.impl;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.types.ExecutePos;
import org.joml.Vector3d;

import java.util.Optional;

public class PosArgumentResult extends AbstractArgumentResult<Optional<ExecutePos>> {
    String rawString;
    public PosArgumentResult(Optional<ExecutePos> vector3d, ArgumentType<Optional<ExecutePos>> type, ArgumentReader reader, int startIndex) {
        super(vector3d, type, reader, startIndex);
        if(vector3d == null) {
            rawString = null;
        }else{
            if(isDefault && vector3d.isPresent()){
                rawString = vector3d.get().asString();
            }else {
                rawString = String.join(" ", this.reader.getArgsInRange(startIndex, endIndex));
            }
        }
    }



    @Override
    public String resultAsString() {
        return rawString;
    }
}
