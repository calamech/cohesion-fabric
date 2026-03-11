package dev.cohesion.client.network;

import dev.cohesion.client.ModSyncManager;
import dev.cohesion.client.ui.SyncCompleteScreen;
import dev.cohesion.client.ui.SyncScreen;
import dev.cohesion.config.ModEntry;
import dev.cohesion.network.ManifestAckC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;

public class ClientPacketHandler {

    public static void handleManifest(List<ModEntry> entries, ClientConfigurationNetworking.Context context) {
        Minecraft client = Minecraft.getInstance();
        Path gameDir = client.gameDirectory.toPath();

        // If a previous sync session downloaded mods but the client hasn't restarted yet,
        // the mods are on disk but not loaded by Fabric. Don't auto-ack.
        if (ModSyncManager.isPendingRestart(gameDir)) {
            client.execute(() -> client.setScreen(new SyncCompleteScreen()));
            return;
        }

        ModSyncManager syncManager = new ModSyncManager(entries, gameDir);

        if (syncManager.isUpToDate()) {
            context.responseSender().sendPacket(new ManifestAckC2SPayload(true));
        } else {
            client.execute(() -> {
                client.setScreen(new SyncScreen(syncManager, context));
            });
        }
    }
}
