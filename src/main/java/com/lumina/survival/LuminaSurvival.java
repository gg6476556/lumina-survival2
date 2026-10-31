package com.lumina.survival;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuminaSurvival implements ModInitializer {
    public static final String MOD_ID = "lumina_survival";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LuminaCommands.register();

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Thirst.onRespawn(newPlayer));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.getPlayer();
            Thirst.onLeave(p);
            BlackHoleGaze.clear(p.getUUID());
            SunGaze.clear(p.getUUID());
            Footsteps.clear(p.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                try {
                    BlackHoleGaze.tick(p);
                    SunGaze.tick(p);
                    WaterPhysics.tick(p);
                    Footsteps.tick(p);
                    Thirst.tick(p);
                } catch (Exception ex) {
                    if (server.getTickCount() % 200 == 0) LOGGER.error("Error en Lumina Survival", ex);
                }
            }
            try {
                DisasterManager.tick(server);
            } catch (Exception ex) {
                if (server.getTickCount() % 200 == 0) LOGGER.error("Error en desastres", ex);
            }
        });

        LOGGER.info("Lumina Survival cargado (26.3).");
    }
}
