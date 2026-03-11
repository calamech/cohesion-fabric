package dev.cohesion.client.ui;

import dev.cohesion.client.ModSyncManager;
import dev.cohesion.client.ModSyncManager.SyncAction;
import dev.cohesion.network.ManifestAckC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SyncScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Cohesion");

    private final ModSyncManager syncManager;
    private final ClientConfigurationNetworking.Context networkContext;
    private final List<SyncAction> actions;

    private Button syncButton;
    private Button disconnectButton;

    private boolean syncing = false;
    private String statusMessage = "";
    private boolean syncComplete = false;
    private boolean syncFailed = false;

    public SyncScreen(ModSyncManager syncManager, ClientConfigurationNetworking.Context networkContext) {
        super(Component.translatable("cohesion.sync.title"));
        this.syncManager = syncManager;
        this.networkContext = networkContext;
        this.actions = syncManager.getSyncActions();
        LOGGER.info("SyncScreen created with {} action(s):", actions.size());
        for (SyncAction action : actions) {
            LOGGER.info("  {} - {} ({})", action.action(), action.entry().slug(), action.entry().filename());
        }
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 150;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int bottomY = this.height - 40;

        syncButton = Button.builder(
                Component.translatable("cohesion.sync.button.sync_restart"),
                button -> onSyncAndRestart()
        ).bounds(centerX - buttonWidth - 5, bottomY, buttonWidth, buttonHeight).build();

        disconnectButton = Button.builder(
                Component.translatable("cohesion.sync.button.disconnect"),
                button -> onDisconnect()
        ).bounds(centerX + 5, bottomY, buttonWidth, buttonHeight).build();

        addRenderableWidget(syncButton);
        addRenderableWidget(disconnectButton);
    }

    private void onSyncAndRestart() {
        if (syncing) return;
        syncing = true;
        syncButton.active = false;
        disconnectButton.active = false;
        statusMessage = "Downloading mods...";

        syncManager.downloadAll().thenRun(() -> {
            Minecraft.getInstance().execute(() -> {
                try {
                    statusMessage = "Applying changes...";
                    syncManager.applySync();
                    syncComplete = true;

                    // Spawn janitor to apply file changes after MC exits
                    ModSyncManager.spawnJanitor(
                            Minecraft.getInstance().gameDirectory.toPath());

                    Minecraft.getInstance().setScreen(new SyncCompleteScreen());
                } catch (Exception e) {
                    statusMessage = "Error: " + e.getMessage();
                    syncFailed = true;
                    disconnectButton.active = true;
                }
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                statusMessage = "Download failed: " + ex.getMessage();
                syncFailed = true;
                disconnectButton.active = true;
            });
            return null;
        });
    }

    private void onDisconnect() {
        networkContext.responseSender().sendPacket(new ManifestAckC2SPayload(false));
        // Return to multiplayer screen
        if (this.minecraft != null) {
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                    new net.minecraft.client.gui.screens.TitleScreen()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Title (0xFF prefix = full alpha)
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Subtitle
        graphics.drawCenteredString(this.font,
                "The server requires mod changes:",
                this.width / 2, 30, 0xFFAAAAAA);

        // List of actions
        int y = 50;
        int leftMargin = this.width / 2 - 150;

        for (SyncAction action : actions) {
            if (y > this.height - 60) break;

            int color = switch (action.action()) {
                case INSTALL -> 0xFF55FF55;
                case UPDATE -> 0xFFFFFF55;
                case REMOVE -> 0xFFFF5555;
            };

            String actionLabel = switch (action.action()) {
                case INSTALL -> "[INSTALL]";
                case UPDATE -> "[UPDATE]";
                case REMOVE -> "[REMOVE]";
            };

            String line = actionLabel + " " + action.entry().filename();
            if (!action.entry().slug().isEmpty()) {
                line = actionLabel + " " + action.entry().slug() + " (" + action.entry().filename() + ")";
            }

            graphics.drawString(this.font, line, leftMargin, y, color);
            y += 14;
        }

        // Status message
        if (!statusMessage.isEmpty()) {
            int statusColor = syncFailed ? 0xFFFF5555 : (syncComplete ? 0xFF55FF55 : 0xFFFFFF55);
            graphics.drawCenteredString(this.font, statusMessage,
                    this.width / 2, this.height - 55, statusColor);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
