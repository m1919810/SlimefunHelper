package me.matl114.hackUtils;

import me.matl114.hackUtils.modules.ModuleManager;
import me.matl114.hackUtils.modules.extra.ClientExtra;

public class ExtraTasks {
    public static void init(){

    }

    public static ClientExtra clientExtra;
    private static void initModules(ModuleManager m){
        clientExtra = new ClientExtra()
            .register(m);
        ;
    }
    static{
        HackModules.getManager().registerFactories(ExtraTasks::initModules);
    }
}
