package me.matl114.utils;

import me.matl114.SlimefunHelper;
import me.matl114.utils.UtilClass.ChunkIterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;

import java.io.File;
import java.util.List;


@ApiMethod
public class CommonUtils {
    public static int parseIntOrDefault(String value,int defaultValue){
        try{
            return Integer.parseInt(value);
        }catch (Throwable e){
            return defaultValue;
        }
    }

    public static Identifier getNamespaceKey(String id) {
        return new Identifier(SlimefunHelper.MOD_ID, id);
    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static String getServerName(){
        if(mc.isInSingleplayer()){
            if (mc.world == null) return "";

            File folder = (mc.getServer()).session.getWorldDirectory(mc.world.getRegistryKey()).toFile();
            if (folder.toPath().relativize(mc.runDirectory.toPath()).getNameCount() != 2) {
                folder = folder.getParentFile();
            }
            return folder.getName();
        }
        if (mc.getCurrentServerEntry() != null) {
            return (mc.getCurrentServerEntry().isRealm() ? "realms" : mc.getCurrentServerEntry().address);
        }
        return "";
    }

    public static String getWorldName() {
        // Singleplayer
        if (mc.isInSingleplayer()) {
            if (mc.world == null) return "";

            File folder = (mc.getServer()).session.getWorldDirectory(mc.world.getRegistryKey()).toFile();
            if (folder.toPath().relativize(mc.runDirectory.toPath()).getNameCount() != 2) {
                folder = folder.getParentFile();
            }
            return folder.getName() + "|"+ mc.world.getRegistryKey().getValue();
        }

        // Multiplayer
        if (mc.getCurrentServerEntry() != null) {
            return (mc.getCurrentServerEntry().isRealm() ? "realms" : mc.getCurrentServerEntry().address) + (mc.world == null ? "":  "|" + mc.world.getRegistryKey().getValue());
        }

        return mc.world == null ? "":  mc.world.getRegistryKey().getValue().toString();
    }

    public static RegistryKey<DimensionOptions> getCurrentDimensionOption(){
        if(mc.world == null)return DimensionOptions.OVERWORLD;
        switch (mc.world.getRegistryKey().getValue().getPath()){
            case "the_nether" -> {
                return DimensionOptions.NETHER;
            }
            case "the_end" -> {
                return DimensionOptions.END;
            }
            case "overworld" -> {
                return DimensionOptions.OVERWORLD;
            }
            default -> {
                // need fix
                DimensionType type = mc.world.getDimension();
                if(type.ultrawarm() || type.hasCeiling()){
                    return DimensionOptions.NETHER;
                }
                if(type.hasSkyLight()){
                    return DimensionOptions.OVERWORLD;
                }
                if(type.bedWorks())return DimensionOptions.END;
                return DimensionOptions.OVERWORLD;
            }
        }
    }

    public static Iterable<Chunk> chunks(boolean onlyWithLoadedNeighbours) {
        return () -> new ChunkIterator(onlyWithLoadedNeighbours);
    }


    public static List<String> filterString(List<String> str, String str2){
        return str.stream().filter(s -> s.contains(str2)).toList();
    }

}
