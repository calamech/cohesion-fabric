package dev.cohesion.network;

import dev.cohesion.CohesionConstants;
import dev.cohesion.config.ModEntry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import java.util.List;
import java.util.function.Consumer;

public class ModSyncConfigurationTask implements ConfigurationTask {

    public static final Type TYPE = new Type(CohesionConstants.MOD_ID + ":mod_sync");

    private final List<ModEntry> modEntries;

    public ModSyncConfigurationTask(List<ModEntry> modEntries) {
        this.modEntries = modEntries;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        ModManifestS2CPayload payload = new ModManifestS2CPayload(modEntries);
        sender.accept(ServerConfigurationNetworking.createS2CPacket(payload));
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
