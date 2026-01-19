package me.matl114.utils.UtilClass.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
public class CommandArgumentMap {
    @Getter
    Map<String,String> argsMap;
    public String getArg(String val){
        return argsMap.get(val);
    }

    public String getNonnull(String val){
        var re = argsMap.get(val);
        if(re == null){
            throw new ValueAbsentError(val);
        }
        return re;
    }
    public int getInt(String val){
        var re = getNonnull(val);
        return TabExecutor.gint(re, val);
    }
    public float getFloat(String val){
        var re = getNonnull(val);
        return TabExecutor.gfloat(re, val);
    }
    public double getDouble(String val){
        var re = getNonnull(val);
        return TabExecutor.gdouble(re, val);
    }
    public boolean getBoolean(String val){
        var re = getNonnull(val);
        return TabExecutor.gbool(re, val);
    }

}
