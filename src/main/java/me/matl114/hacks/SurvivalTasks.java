package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.survival.*;
import me.matl114.hacks.modules.survival.ElytraFinder;
import org.jetbrains.annotations.ApiStatus;

public class SurvivalTasks {
    public static void init() {}

    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Survival");

    @Getter
    private static SchedularSettings schedularSettings;

    @Getter
    private static VillagerEsp villagerEsp;

    @Getter
    private static WorldManager worldManager;

    @Getter
    private static BlockFarm blockFarm;

    @Getter
    private static AntiAXray antiAXray;

    @Getter
    private static SeedOre seedOre;

    @Getter
    private static AutoLibrarian autoLibrarian;

    @Getter
    private static AutoBreed autoBreed;

    @Getter
    private static SearchControl searchControl;

    @Getter
    private static PathManager pathManager;

    @ApiStatus.Experimental
    @Getter
    private static ElytraFinder elytraFinder;

    @Getter
    private static BaritoneFix baritoneFix;

    @Getter
    public static XaeroHelper xaeroHelper;

    private static void initModules(ModuleManager m) {
        schedularSettings = new SchedularSettings().register(m);
        villagerEsp = new VillagerEsp().register(m);
        worldManager = new WorldManager().register(m);
        blockFarm = new BlockFarm().register(m);
        antiAXray = new AntiAXray().register(m);
        seedOre = new SeedOre().register(m);
        autoLibrarian = new AutoLibrarian().register(m);
        autoBreed = new AutoBreed().register(m);
        searchControl = new SearchControl().register(m);
        pathManager = new PathManager().register(m);
        baritoneFix = new BaritoneFix().register(m);
        xaeroHelper = new XaeroHelper().register(m);
    }

    static {
        moduleManager.registerFactories(SurvivalTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
