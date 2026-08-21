package xaero.map;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import xaero.map.world.MapWorld;

public class MapProcessor {
    private MapWorld mapWorld;

    public MapWorld getMapWorld() {
        return this.mapWorld;
    }

    private String getMainId(int version, ClientPlayNetworkHandler connection) {
        return "";
    }
}
