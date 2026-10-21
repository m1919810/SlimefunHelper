package me.matl114.hacks.modules.task;

import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;

public class MovementManager extends BaseModule {
    public static MovementManager INSTANCE;

    public MovementManager() {
        super("MovementManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerInitConfiguration(), this::configurePipelinesForPlayer);
    }

    public static final LegalMovementManager.ModifierPipeline PLAYER_PIPELINE_0 =
            new LegalMovementManager.ModifierPipeline(0) {
                @Override
                public int priority() {
                    return 0;
                }
            };
    private static final Map<String, LegalMovementManager.DelegateMovementModifier> instanceMap = new LinkedHashMap<>();

    public static <T extends BaseModule> void registerMovementPipeline(T clazz) {
        registerMovementPipeline(clazz.getClass().getName(), clazz);
    }

    public static <T extends BaseModule> void registerMovementPipeline(String owner, T clazz) {
        if (LegalMovementManager.MovementModifier.class.isAssignableFrom(clazz.getClass())) {
            LegalMovementManager.MovementModifier movementModifier = (LegalMovementManager.MovementModifier) clazz;
            var delegate = instanceMap.computeIfAbsent(owner, (v) -> {
                var re = new LegalMovementManager.DelegateMovementModifier(() -> movementModifier);
                PLAYER_PIPELINE_0.addMovementModifierFactory(() -> re);
                return re;
            });
            delegate.setDelegate(() -> movementModifier);
        }
    }

    public void configurePipelinesForPlayer(Event<ClientPlayerEntity> playerEvent) {
        var player = playerEvent.context();
        var legalMovement = ClientPlayerAccess.of(player).getLegalMovementManager();
        PLAYER_PIPELINE_0.resetForNewPlayer(player);
        legalMovement.addMovementModifier(PLAYER_PIPELINE_0);
    }
}
