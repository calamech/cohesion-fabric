package dev.cohesion;

import dev.cohesion.config.ServerConfig;
import dev.cohesion.network.ManifestAckC2SPayload;
import dev.cohesion.network.ModManifestS2CPayload;
import dev.cohesion.network.ModSyncConfigurationTask;
import dev.cohesion.server.ServerModResolver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CohesionMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");

    @Override
    public void onInitialize() {
        LOGGER.info("Cohesion initializing...");

        // Register payload types
        PayloadTypeRegistry.configurationS2C().register(ModManifestS2CPayload.TYPE, ModManifestS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.configurationC2S().register(ManifestAckC2SPayload.TYPE, ManifestAckC2SPayload.STREAM_CODEC);

        // Resolve mods from Modrinth on server start
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                // Pass the MC version so dependencies can be resolved for the right game version
                String mcVersion = net.minecraft.SharedConstants.getCurrentVersion().name();
                ServerModResolver.setServerMcVersion(mcVersion);
                LOGGER.info("Server MC version: {}", mcVersion);

                ServerConfig config = ServerConfig.load(server.getServerDirectory());
                ServerModResolver.resolve(config).join();
            } catch (Exception e) {
                LOGGER.error("Failed to resolve mods from Modrinth, server will start without mod sync.", e);
            }
        });

        // Register in BEFORE_CONFIGURE with an early phase so our task is added
        // BEFORE the Fabric registry sync task (which registers in BEFORE_CONFIGURE default phase).
        // This is critical: if the client is missing mods, our task blocks the config
        // phase before registry sync sends entries the client doesn't know about.
        ResourceLocation earlyPhase = ResourceLocation.fromNamespaceAndPath(CohesionConstants.MOD_ID, "early");
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.addPhaseOrdering(earlyPhase, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(earlyPhase, (handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, ModManifestS2CPayload.TYPE)) {
                handler.addTask(new ModSyncConfigurationTask(ServerModResolver.getResolvedMods()));
            } else {
                LOGGER.debug("Client does not have Cohesion installed, skipping mod sync.");
            }
        });

        // Handle the client's acknowledgment
        ServerConfigurationNetworking.registerGlobalReceiver(
                ManifestAckC2SPayload.TYPE,
                (payload, context) -> {
                    if (payload.accepted()) {
                        LOGGER.info("Client accepted mod manifest.");
                        context.networkHandler().completeTask(ModSyncConfigurationTask.TYPE);
                    } else {
                        LOGGER.info("Client declined mod manifest, disconnecting.");
                        context.networkHandler().disconnect(
                                Component.literal("You declined required mods from Cohesion.")
                        );
                    }
                }
        );

        LOGGER.info("Cohesion initialized.");
    }
}
