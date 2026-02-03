package me.matl114.utils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApiMethod
public class ClientUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean isPlayerOnline(){
        return mc.player != null && !mc.disconnecting;
    }
    public static boolean isNetworkConnecting(){
        return mc.getServer() != null;
    }



    public static CompletableFuture<List<String>> getServerPluginResources(){
        String command = "/version ";
        StringReader ojReader = new StringReader(command);
        ojReader.skip();
        var dispatcher= mc.getNetworkHandler().getCommandDispatcher();
        var parseResult = dispatcher.parse(ojReader, mc.getNetworkHandler().getCommandSource());
        return mc.getNetworkHandler().getCommandDispatcher().getCompletionSuggestions(parseResult)
            .thenApply((suggestions -> {
                return suggestions.getList().stream().map(Suggestion::getText).sorted().toList();
            }));
    }

    public static List<String> getServerCommands(){
        return mc.getNetworkHandler().getCommandDispatcher().getRoot().getChildren()
            .stream()
            .map(CommandNode::getName)
            .toList();
    }





}
